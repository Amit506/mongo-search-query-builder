/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch;

import com.amit506.mongosearch.enums.AtlasSearchOperator;
import com.amit506.mongosearch.enums.OrderByEnum;
import com.amit506.mongosearch.enums.QueryType;
import com.amit506.mongosearch.fieldSearch.FieldSearchAlgorithm;
import com.amit506.mongosearch.fieldSearch.FieldTree;
import com.amit506.mongosearch.tokens.OperandSearchToken;
import com.amit506.mongosearch.tokens.OperatorSearchToken;
import com.amit506.mongosearch.tokens.ParenthesisSearchToken;
import com.amit506.mongosearch.tokens.SearchToken;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@Slf4j
public class MongoSearchQueryBuilder {
  private final Document searchDocument;
  private final Document searchMetaDocument;

  private MongoSearchQueryBuilder(Document searchDocument, Document searchMetaDocument) {
    this.searchDocument = searchDocument;
    this.searchMetaDocument = searchMetaDocument;
  }

  public Document getSearchDocument() {
    return searchDocument;
  }

  public Document getSearchMetaDocument() {
    if (searchMetaDocument == null) {
      throw new IllegalArgumentException("searchMetaDocument is not initialized");
    }
    return searchMetaDocument;
  }

  /**
   * CriteriaBuilder is responsible for converting a flat infix list of {@link SearchToken}s
   * (representing logical query expressions) into a {@code Document} used in MongoDB Atlas Search.
   *
   * <p>It performs the following four steps:
   *
   * <ol>
   *   <li><b>Infix Token Collection:</b> Gather a flat list of tokens including operands
   *       (criteria), operators (AND/OR/MUST/NOT), and parentheses to define the logical
   *       expression.
   *   <li><b>Infix to Postfix Conversion:</b> Convert the infix token list to postfix (Reverse
   *       Polish Notation) using operator precedence and proper parenthesis handling.
   *   <li><b>AST Generation:</b> Build an Abstract Syntax Tree (AST) from the postfix tokens, where
   *       each node represents a logical operation or operand.
   *   <li><b>MongoDB Document Construction:</b> Traverse the AST and convert it into a valid
   *       MongoDB Atlas Search query {@link org.bson.Document}.
   * </ol>
   */
  public static class CriteriaDocumentBuilder {
    private final FieldTree fieldTreeRoot;

    /**
     * The infix list of SearchTokens representing a logical expression. For example: [ A, AND, (,
     * B, OR, C, ) ]
     */
    private final List<SearchToken> searchTokens;

    /**
     * Constructs a CriteriaBuilder with a list of infix SearchTokens.
     *
     * @param searchTokens List of SearchToken representing the infix logical expression.
     */
    public CriteriaDocumentBuilder(List<SearchToken> searchTokens, FieldTree fieldTree) {
      this.searchTokens = searchTokens;
      this.fieldTreeRoot = fieldTree;
    }

    public List<SearchToken> getSearchTokens() {
      return this.searchTokens;
    }

    public Document build() {
      List<SearchToken> infixToPostfix = infixToPostfix(searchTokens);
      AstNode astNode = postfixToAst(infixToPostfix);
      Document result = buildFromAst(astNode);
      return result;
    }

    private static int precedence(AtlasSearchOperator operator) {
      return switch (operator) {
        case MUST -> 2;
        case SHOULD -> 1;
        default -> 3;
      };
    }

    /**
     * Converts an infix list of SearchTokens into a postfix list using the Shunting Yard algorithm.
     *
     * <p>Example: Input: [A, MUST, (, B, SHOULD, C, )] Output: [A, B, C, SHOULD, MUST]
     *
     * @param tokens The infix list of SearchTokens.
     * @return The postfix list of SearchTokens.
     */
    public static List<SearchToken> infixToPostfix(List<SearchToken> tokens) {
      log.info("---- Infix to Postfix Conversion ----");
      log.info("Input Infix Tokens: {}", tokens);

      List<SearchToken> output = new ArrayList<>();
      Deque<SearchToken> stack = new ArrayDeque<>();

      for (SearchToken token : tokens) {
        if (token instanceof OperandSearchToken) {
          output.add(token);
        } else if (token instanceof OperatorSearchToken opToken) {
          while (!stack.isEmpty()) {
            SearchToken top = stack.peek();
            if (top instanceof OperatorSearchToken topOp
                && precedence(topOp.getOperator()) >= precedence(opToken.getOperator())) {
              output.add(stack.pop());
            } else {
              break;
            }
          }
          stack.push(opToken);
        } else if (token instanceof ParenthesisSearchToken paren) {
          if (paren.isLeft()) {
            stack.push(paren);
          } else {
            while (!stack.isEmpty()
                && !(stack.peek() instanceof ParenthesisSearchToken p && p.isLeft())) {
              output.add(stack.pop());
            }
            if (stack.isEmpty() || !(stack.peek() instanceof ParenthesisSearchToken)) {
              throw new IllegalArgumentException("Mismatched parentheses");
            }
            stack.pop(); // Pop the left parenthesis
          }
        }
      }

      while (!stack.isEmpty()) {
        SearchToken token = stack.pop();
        if (token instanceof ParenthesisSearchToken) {
          throw new IllegalArgumentException("Mismatched parentheses");
        }
        output.add(token);
      }

      log.info("Output Postfix Tokens: {}", output);
      log.info("--------------------------------------");

      return output;
    }

    /**
     * Builds an Abstract Syntax Tree (AST) from a list of postfix SearchTokens. This tree structure
     * represents the logical hierarchy of the query.
     *
     * <p>Example: Input: [A, B, C, SHOULD, MUST] Output: MUST(A, SHOULD(B, C))
     *
     * @param postfixTokens A list of SearchTokens in postfix order.
     * @return The root of the constructed AST.
     * @throws IllegalStateException if the postfix expression is invalid.
     */
    public static AstNode postfixToAst(List<SearchToken> postfixTokens) {
      log.info("---- Postfix to AST Conversion ----");
      log.info("Postfix Tokens: {}", postfixTokens);

      Deque<AstNode> stack = new ArrayDeque<>();

      for (SearchToken token : postfixTokens) {
        if (token instanceof OperandSearchToken operand) {
          stack.push(new AstNode(operand));
        } else if (token instanceof OperatorSearchToken operatorToken) {
          AtlasSearchOperator operator = operatorToken.getOperator();
          boolean forceSeparate = operatorToken.isSeparateGroup();
          AstNode right = stack.pop();
          AstNode left = (stack.isEmpty()) ? null : stack.pop();

          // Merge if existing node is of same operator
          List<AstNode> groupedChildren = new ArrayList<>();
          if (left != null && isSameOperator(left, operator) && !forceSeparate) {
            groupedChildren.addAll(left.getChildren());
          } else if (left != null) {
            groupedChildren.add(left);
          }

          if (right != null && isSameOperator(right, operator) && !forceSeparate) {
            groupedChildren.addAll(right.getChildren());
          } else if (right != null) {
            groupedChildren.add(right);
          }

          AstNode groupNode = new AstNode(operatorToken);
          groupNode.setChildren(groupedChildren);
          stack.push(groupNode);
        } else {
          throw new IllegalArgumentException("Unexpected token: " + token);
        }
      }

      if (stack.size() != 1) {
        throw new IllegalStateException("Invalid postfix expression");
      }

      AstNode root = stack.pop();
      log.info("Generated AST:\n{}", root.printTree());
      return root;
    }

    private Document buildFromAst(AstNode node) {
      if (node == null || node.getValue() == null) return null;

      if (node.getValue() instanceof OperandSearchToken operandToken) {
        SearchCriteria criteria = operandToken.getSearchCriteria();

        if (isEmbeddedField(criteria)) {
          return buildEmbeddedDocument(criteria);
        }

        return buildBaseClause(criteria);
      }

      if (node.getValue() instanceof OperatorSearchToken operatorToken) {
        AtlasSearchOperator operator = operatorToken.getOperator();
        String operatorKey = operator.getValue();

        List<Document> clauses;

        if (operator == AtlasSearchOperator.MUST) {
          List<Document> baseClauses = new ArrayList<>();
          List<Document> embeddedClauses = new ArrayList<>();

          for (AstNode child : node.getChildren()) {
            if (child.getValue() instanceof OperandSearchToken operand) {
              SearchCriteria criteria = operand.getSearchCriteria();
              if (!isEmbeddedField(criteria)) {
                baseClauses.add(buildBaseClause(criteria));
              }
            } else {
              Document subClause = buildFromAst(child);
              if (subClause != null && !subClause.isEmpty()) {
                baseClauses.add(subClause);
              }
            }
          }
          Map<String, List<SearchCriteria>> embeddedGroups = new LinkedHashMap<>();
          for (AstNode child : node.getChildren()) {
            if (child.getValue() instanceof OperandSearchToken operand) {
              SearchCriteria criteria = operand.getSearchCriteria();
              if (isEmbeddedField(criteria)) {
                String embeddedPath = getEmbeddedParentPath(criteria.getFieldName());
                embeddedGroups.computeIfAbsent(embeddedPath, k -> new ArrayList<>()).add(criteria);
              }
            }
          }
          for (Map.Entry<String, List<SearchCriteria>> entry : embeddedGroups.entrySet()) {
            embeddedClauses.add(buildEmbeddedDocumentGroup(entry.getKey(), entry.getValue()));
          }

          clauses = new ArrayList<>();
          clauses.addAll(baseClauses);
          clauses.addAll(embeddedClauses);
        } else {
          clauses =
              node.getChildren().stream()
                  .map(this::buildFromAst)
                  .filter(Objects::nonNull)
                  .filter(doc -> !doc.isEmpty())
                  .collect(Collectors.toList());
        }

        return new Document("compound", new Document(operatorKey, clauses));
      }

      throw new IllegalStateException(
          "Unknown AST node type: " + node.getValue().getClass().getSimpleName());
    }

    private static boolean isSameOperator(AstNode node, AtlasSearchOperator operator) {
      if (node == null || node.getValue() == null) {
        return false;
      }

      if (node.getValue() instanceof OperatorSearchToken token) {
        return token.getOperator() == operator;
      }

      return false;
    }

    private Document buildBaseClause(SearchCriteria c) {
      try {
        String fieldName = c.getFieldName();
        Object data = c.getValue();
        QueryType queryType = c.getQueryType();
        boolean includeBlanks = c.getIncludeBlanks() != null ? c.getIncludeBlanks() : false;
        FieldTree fieldTree = FieldSearchAlgorithm.findLastLeafNode(fieldTreeRoot, fieldName);
        FieldTreeContext.set(fieldTree);

        if (queryType == null && includeBlanks == false) {
          return getOnlyNonIncludeBlankCriteria(fieldName);
        }
        if (includeBlanks) {
          return getIncludeBlankCriteria(c, fieldTree);
        }

        return SearchOperation.getSearchCriteria(c, fieldTree).resolve();
      } finally {
        FieldTreeContext.clear();
      }
    }

    public Document buildBaseClauseWithEmbeddedDocument(SearchCriteria c) {
      try {
        String fieldName = c.getFieldName();
        Object data = c.getValue();
        QueryType queryType = c.getQueryType();
        boolean includeBlanks = c.getIncludeBlanks() != null ? c.getIncludeBlanks() : false;
        FieldTree fieldTree = FieldSearchAlgorithm.findLastLeafNode(fieldTreeRoot, fieldName);
        FieldTreeContext.set(fieldTree);

        if (queryType == null && includeBlanks == false) {
          return getOnlyNonIncludeBlankCriteria(fieldName);
        }
        if (includeBlanks) {
          return getIncludeBlankCriteria(c, fieldTree);
        }
        if (isEmbeddedField(c)) {
          return buildEmbeddedDocument(c);
        }

        return SearchOperation.getSearchCriteria(c, fieldTree).resolve();
      } finally {
        FieldTreeContext.clear();
      }
    }

    Document getOnlyNonIncludeBlankCriteria(String fieldName) {
      return SearchOperation.NotNullOperation.isNotNull(fieldName).resolve();
    }

    Document getIncludeBlankCriteria(SearchCriteria searchCriteria, FieldTree fieldTree) {
      String fieldName = searchCriteria.getFieldName();
      return new Document(
          "compound",
          new Document(
              AtlasSearchOperator.SHOULD.getValue(),
              Arrays.asList(
                  SearchOperation.getSearchCriteria(searchCriteria, fieldTree).resolve(),
                  SearchOperation.NullOperation.isNull(fieldName).resolve())));
    }

    private Document buildEmbeddedDocument(SearchCriteria criteria) {
      Document embeddedClause =
          new Document()
              .append("path", getEmbeddedParentPath(criteria.getFieldName()))
              .append("operator", buildBaseClause(criteria));

      return new Document("embeddedDocument", embeddedClause);
    }

    private Document buildEmbeddedDocumentGroup(
        String embededPath, List<SearchCriteria> searchCriteria) {
      List<Document> documents =
          searchCriteria.stream().map(e -> buildBaseClause(e)).collect(Collectors.toList());
      Document embeddedClause =
          new Document()
              .append("path", getEmbeddedParentPath(embededPath))
              .append("operator", new Document("compound", new Document("must", documents)));

      return new Document("embeddedDocument", embeddedClause);
    }

    private String getEmbeddedParentPath(String path) {
      int lastDot = path.indexOf('.');
      return lastDot == -1 ? path : path.substring(0, lastDot);
    }

    private boolean isEmbeddedField(FieldTree node) {
      return node.isList();
    }

    private boolean isEmbeddedField(SearchCriteria criteria) {
      String fieldName = criteria.getFieldName();
      if (fieldName.startsWith("standaloneFinancialInfo")
          || fieldName.startsWith("consolidatedFinancialInfo")) {
        return false;
      }
      if (!fieldName.contains(".")) {
        return false;
      }
      if (fieldName.startsWith("investmentInfo")) {
        return true;
      }
      if (fieldName.startsWith("linkedCompanies")) {
        return true;
      }
      Optional<FieldTree> nodeOpt =
          FieldSearchAlgorithm.findFieldTreeForPath(fieldTreeRoot, criteria.getFieldName());

      return nodeOpt.isPresent() && isEmbeddedField(nodeOpt.get());
    }

    public CriteriaDocumentBuilder addMustCriteria(SearchCriteria... criteriaArr) {
      if (criteriaArr == null || criteriaArr.length == 0) return this;

      log.info("before must criteria {}", MongoSearchQueryBuilder.stringifyTokens(searchTokens));

      List<SearchToken> mustBlock = new ArrayList<>();
      mustBlock.add(new ParenthesisSearchToken(true));
      for (int i = 0; i < criteriaArr.length; i++) {
        if (i > 0) {
          mustBlock.add(new OperatorSearchToken(AtlasSearchOperator.MUST));
        }
        mustBlock.add(new OperandSearchToken(criteriaArr[i]));
      }
      mustBlock.add(new ParenthesisSearchToken(false));

      if (!searchTokens.isEmpty()) {
        List<SearchToken> wrappedExisting = new ArrayList<>();
        wrappedExisting.add(new ParenthesisSearchToken(true));
        wrappedExisting.addAll(searchTokens);
        wrappedExisting.add(new ParenthesisSearchToken(false));

        searchTokens.clear();
        searchTokens.addAll(wrappedExisting);
        searchTokens.add(new OperatorSearchToken(AtlasSearchOperator.MUST));
        searchTokens.addAll(mustBlock);
      } else {
        searchTokens.addAll(mustBlock);
      }

      log.info("after must criteria {}", MongoSearchQueryBuilder.stringifyTokens(searchTokens));

      return this;
    }

    public CriteriaDocumentBuilder addShouldCriteria(SearchCriteria... criteriaArr) {
      if (criteriaArr == null || criteriaArr.length == 0) return this;

      List<SearchToken> shouldBlock = new ArrayList<>();
      shouldBlock.add(new ParenthesisSearchToken(true));
      for (int i = 0; i < criteriaArr.length; i++) {
        if (i > 0) {
          shouldBlock.add(new OperatorSearchToken(AtlasSearchOperator.SHOULD));
        }
        shouldBlock.add(new OperandSearchToken(criteriaArr[i]));
      }
      shouldBlock.add(new ParenthesisSearchToken(false));

      if (!searchTokens.isEmpty()) {
        List<SearchToken> wrappedExisting = new ArrayList<>();
        wrappedExisting.add(new ParenthesisSearchToken(true));
        wrappedExisting.addAll(searchTokens);
        wrappedExisting.add(new ParenthesisSearchToken(false));

        searchTokens.clear();
        searchTokens.addAll(wrappedExisting);
        searchTokens.add(new OperatorSearchToken(AtlasSearchOperator.SHOULD));
        searchTokens.addAll(shouldBlock);
      } else {
        searchTokens.addAll(shouldBlock);
      }

      return this;
    }

    public void addCriteriaWithOperator(
        AtlasSearchOperator operator, SearchCriteria... newCriteriaArr) {

      if (newCriteriaArr == null || newCriteriaArr.length == 0) return;

      List<SearchToken> updated = new ArrayList<>();

      // Wrap existing tokens in parentheses, if present
      if (!this.searchTokens.isEmpty()) {
        updated.add(new ParenthesisSearchToken(true)); // (
        updated.addAll(this.searchTokens); // existing tokens
        updated.add(new ParenthesisSearchToken(false)); // )

        updated.add(new OperatorSearchToken(AtlasSearchOperator.MUST));
      }

      updated.add(new ParenthesisSearchToken(true)); // (
      for (int i = 0; i < newCriteriaArr.length; i++) {
        updated.add(new OperandSearchToken(newCriteriaArr[i]));
        if (i < newCriteriaArr.length - 1) {
          updated.add(new OperatorSearchToken(operator)); // operator between them
        }
      }
      updated.add(new ParenthesisSearchToken(false)); // )

      this.searchTokens.clear();
      this.searchTokens.addAll(updated);
    }

    /**
     * Appends a single SearchCriteria as a MUST group to the provided SearchToken list. Returns a
     * new list with the appended group.
     *
     * @param tokens the original list of SearchToken
     * @param criteria the SearchCriteria to append as a MUST group
     * @return a new List<SearchToken> with the MUST group appended
     */
    public static List<SearchToken> appendMustToken(
        List<SearchToken> tokens, SearchCriteria criteria) {
      if (criteria == null) {
        throw new IllegalArgumentException("SearchCriteria cannot be null");
      }
      List<SearchToken> result = new ArrayList<>(tokens);
      result.add(new ParenthesisSearchToken(true));
      result.add(new OperandSearchToken(criteria));
      result.add(new ParenthesisSearchToken(false));
      result.add(new OperatorSearchToken(AtlasSearchOperator.MUST));
      return result;
    }
  }

  public static class SearchDocumentBuilder {
    private final String searchIndex;
    private Document searchDocument;

    public SearchDocumentBuilder(String searchIndex) {
      this.searchIndex = searchIndex;
    }

    public SearchDocumentBuilder withCriteria(Document criteriaDocument) {

      Document searchOperator = new Document();
      searchOperator.putAll(criteriaDocument);
      searchOperator.append("index", searchIndex);
      this.searchDocument = new Document("$search", searchOperator);
      return this;
    }

    public SearchDocumentBuilder addTotalCount() {
      if (searchDocument == null) {
        throw new IllegalStateException(
            "Search document not initialized. Call withCriteria first.");
      }
      Document modifiDocument = (Document) searchDocument.get("$search");
      modifiDocument.append("count", new Document("type", "total"));
      return this;
    }

    public SearchDocumentBuilder addSort(OrderByEnum orderBy, String... sortBy) {
      if (sortBy == null || sortBy.length == 0 || sortBy[0] == null) {
        throw new IllegalArgumentException("At least one field must be provided for sorting.");
      }
      if (searchDocument == null) {
        throw new IllegalStateException(
            "Search document not initialized. Call withCriteria first.");
      }
      Document sortCriteria = new Document();
      for (String field : sortBy) {
        sortCriteria.append(field, new Document("order", orderBy == OrderByEnum.ASC ? 1 : -1));
      }
      ((Document) searchDocument.get("$search")).append("sort", sortCriteria);
      return this;
    }

    public Document build() {
      if (searchDocument == null) {
        throw new IllegalStateException(
            "Search document not initialized. Call withCriteria first.");
      }
      return searchDocument;
    }
  }

  public static class SearchMetaDocumentBuilder {
    private final String searchIndex;
    private final FieldTree fieldTreeRoot;
    private Document searchMetaDocument;

    public SearchMetaDocumentBuilder(String searchIndex, FieldTree fieldTree) {
      this.searchIndex = searchIndex;
      this.fieldTreeRoot = fieldTree;
    }

    public SearchMetaDocumentBuilder withFacetCriteria(Document criteriaDocument) {
      this.searchMetaDocument =
          new Document(
              "$searchMeta",
              new Document()
                  .append("index", searchIndex)
                  .append("facet", new Document("operator", criteriaDocument)));
      return this;
    }

    public SearchMetaDocumentBuilder withCriteria(Document criteriaDocument) {
      Document searchOperator = new Document();
      searchOperator.putAll(criteriaDocument);
      searchOperator.append("index", searchIndex);
      this.searchMetaDocument = new Document("$searchMeta", searchOperator);
      return this;
    }

    public SearchMetaDocumentBuilder count() {
      if (searchMetaDocument == null) {
        throw new IllegalStateException(
            "Search document not initialized. Call withCriteria first.");
      }
      Document modifiDocument = (Document) searchMetaDocument.get("$searchMeta");
      modifiDocument.append("count", new Document("type", "total"));
      return this;
    }

    public SearchMetaDocumentBuilder addFacet(String... fieldNames) {
      if (fieldNames == null || fieldNames.length == 0) {
        throw new IllegalArgumentException(
            "At least one field name must be provided for faceting.");
      }
      if (searchMetaDocument == null) {
        throw new IllegalStateException(
            "Search meta document not initialized. Call withCriteria first.");
      }
      Document facets = new Document();
      for (String fieldName : fieldNames) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
          throw new IllegalArgumentException("Field name for facet cannot be null or empty.");
        }
        if (isEmbeddedField(fieldName)) {
          String[] parts = fieldName.split("\\.", 2);
          if (!parts[0].contains("Faced")) fieldName = parts[0] + "Faced." + parts[1];
        }
        facets.append(
            fieldName,
            new Document()
                .append("type", "string")
                .append("path", fieldName)
                .append("numBuckets", 1000));
      }
      Document searchMeta = (Document) searchMetaDocument.get("$searchMeta");
      Document facetDocument = searchMeta.get("facet", Document.class);
      facetDocument.append("facets", facets);
      return this;
    }

    private boolean isEmbeddedField(String fieldName) {
      if (!fieldName.contains(".")) {
        return false;
      }
      Optional<FieldTree> nodeOpt =
          FieldSearchAlgorithm.findFieldTreeForPath(fieldTreeRoot, fieldName);

      return nodeOpt.isPresent() && nodeOpt.get().isList();
    }

    public Document build() {
      if (searchMetaDocument == null) {
        throw new IllegalStateException(
            "Search meta document not initialized. Call withCriteria first.");
      }
      return searchMetaDocument;
    }
  }

  public static class Builder {
    private final FieldTree fieldTreeRoot;
    private final String searchIndex;

    private CriteriaDocumentBuilder criteriaBuilder;

    private final List<TypedAction> actions = new ArrayList<>();

    public Builder(FieldTree root, String searchIndex, List<SearchToken> searchTokens) {
      this.fieldTreeRoot = root;
      this.searchIndex = searchIndex;
      this.criteriaBuilder =
          new CriteriaDocumentBuilder(
              searchTokens == null ? new ArrayList<>() : new ArrayList<>(searchTokens), root);
    }

    public Builder addMustCriteria(SearchCriteria... criteriaArr) {
      SearchCriteria[] nonNullCriterias =
          Arrays.stream(criteriaArr).filter(Objects::nonNull).toArray(SearchCriteria[]::new);
      actions.add(
          new TypedAction(
              ctx -> ctx.criteriaBuilder.addMustCriteria(nonNullCriterias),
              BuilderAction.Target.CRITERIA));
      return this;
    }

    public Builder addSouldCriteria(SearchCriteria... criteriaArr) {
      actions.add(
          new TypedAction(
              ctx -> ctx.criteriaBuilder.addShouldCriteria(criteriaArr),
              BuilderAction.Target.CRITERIA));
      return this;
    }

    public Builder addSort(OrderByEnum orderBy, String... sortBy) {
      actions.add(
          new TypedAction(
              ctx -> ctx.searchDocBuilder.addSort(orderBy, sortBy),
              BuilderAction.Target.SEARCH_DOC));
      return this;
    }

    public Builder addTotalCount() {
      actions.add(
          new TypedAction(
              ctx -> ctx.searchDocBuilder.addTotalCount(), BuilderAction.Target.SEARCH_DOC));
      return this;
    }

    public Builder onlyCount() {
      actions.add(
          new TypedAction(ctx -> ctx.metaDocBuilder.count(), BuilderAction.Target.META_DOC));
      return this;
    }

    public Builder addFacetToSearchMeta(String... fieldNames) {
      actions.add(
          new TypedAction(
              ctx -> ctx.metaDocBuilder.addFacet(fieldNames), BuilderAction.Target.FACET_META_DOC));
      return this;
    }

    public MongoSearchQueryBuilder build() {
      Iterator<TypedAction> iterator = actions.iterator();
      while (iterator.hasNext()) {
        TypedAction action = iterator.next();
        if (action.target() == BuilderAction.Target.CRITERIA) {
          action.action().apply(new BuilderContext(criteriaBuilder));
          iterator.remove();
        }
      }

      Document criteriaDoc = criteriaBuilder.build();

      SearchDocumentBuilder searchDocBuilder =
          new SearchDocumentBuilder(searchIndex).withCriteria(criteriaDoc);

      SearchMetaDocumentBuilder metaDocBuilder = null;

      if (actions.stream().anyMatch(e -> e.target() == BuilderAction.Target.META_DOC)) {
        metaDocBuilder =
            new SearchMetaDocumentBuilder(searchIndex, fieldTreeRoot).withCriteria(criteriaDoc);

      } else if (actions.stream()
          .anyMatch(e -> e.target() == BuilderAction.Target.FACET_META_DOC)) {
        metaDocBuilder =
            new SearchMetaDocumentBuilder(searchIndex, fieldTreeRoot)
                .withFacetCriteria(criteriaDoc);
      }

      BuilderContext context =
          new BuilderContext(criteriaBuilder, searchDocBuilder, metaDocBuilder);

      actions.forEach(a -> a.action().apply(context));

      return new MongoSearchQueryBuilder(
          searchDocBuilder.build(), metaDocBuilder != null ? metaDocBuilder.build() : null);
    }

    public interface BuilderAction {
      void apply(BuilderContext context);

      enum Target {
        CRITERIA,
        SEARCH_DOC,
        META_DOC,
        FACET_META_DOC
      }
    }

    public class BuilderContext {
      public CriteriaDocumentBuilder criteriaBuilder;
      public SearchDocumentBuilder searchDocBuilder;
      public SearchMetaDocumentBuilder metaDocBuilder;

      public BuilderContext(
          CriteriaDocumentBuilder cb, SearchDocumentBuilder sb, SearchMetaDocumentBuilder mb) {
        this.criteriaBuilder = cb;
        this.searchDocBuilder = sb;
        this.metaDocBuilder = mb;
      }

      public BuilderContext(CriteriaDocumentBuilder cb) {
        this.criteriaBuilder = cb;
      }
    }

    public record TypedAction(BuilderAction action, BuilderAction.Target target) {}
  }

  public static String stringifyTokens(List<SearchToken> tokens) {
    return tokens.stream().map(Object::toString).collect(Collectors.joining(" ")).trim();
  }

  public static Document buildDocumentFromSearchCriteria(SearchCriteria c, boolean embedded) {

    if (embedded) {
      return buildEmbeddedDocument(c);
    }

    return SearchOperation.getSearchCriteria(c, null).resolve();
  }

  public static Document buildEmbeddedDocument(SearchCriteria criteria) {
    Document embeddedClause =
        new Document()
            .append("path", getEmbeddedParentPath(criteria.getFieldName()))
            .append("operator", buildBaseClause(criteria));

    return new Document("embeddedDocument", embeddedClause);
  }

  public static String getEmbeddedParentPath(String path) {
    int lastDot = path.indexOf('.');
    return lastDot == -1 ? path : path.substring(0, lastDot);
  }

  public static Document buildBaseClause(SearchCriteria c) {

    return SearchOperation.getSearchCriteria(c, null).resolve();
  }
}
