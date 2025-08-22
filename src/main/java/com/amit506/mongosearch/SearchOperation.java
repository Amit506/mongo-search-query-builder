/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch;

import com.amit506.mongosearch.enums.QueryType;
import com.amit506.mongosearch.fieldSearch.FieldTree;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.ObjectId;

public class SearchOperation {

  public static SearchQueryOperation getSearchCriteria(
      SearchCriteria searchCriteria, FieldTree fieldTree) {
    return getSearchCriteria(searchCriteria, fieldTree, new SearchOperationConfig());
  }

  public static SearchQueryOperation getSearchCriteria(
      SearchCriteria searchCriteria, FieldTree fieldTree, SearchOperationConfig config) {
    SearchCriteria processedSearchCriteria = config.dataPreprocessor.process(searchCriteria);

    return config.queryOperationResolver.resolve(processedSearchCriteria, fieldTree);
  }

  public static SearchQueryOperation defaultQueryOperationResolver(
      SearchCriteria searchCriteria, FieldTree fieldTree) {

    QueryType queryType = searchCriteria.getQueryType();
    String fieldName = searchCriteria.getFieldName();
    Object data = searchCriteria.getValue();
    return switch (queryType) {
      case LESS_THAN -> SearchOperation.RangeOperation.lt(fieldName, data);
      case LESS_THAN_OR_EQUAL_TO -> SearchOperation.RangeOperation.lte(fieldName, data);
      case GREATER_THAN -> SearchOperation.RangeOperation.gt(fieldName, data);
      case GREATER_THAN_OR_EQUAL_TO -> SearchOperation.RangeOperation.gte(fieldName, data);
      case EQUAL_TO -> SearchOperation.EqualityOperation.equals(fieldName, data);
      case NOT_EQUAL_TO -> SearchOperation.NotEqualityOperation.notEquals(fieldName, data);
      case IS_BETWEEN -> SearchOperation.RangeOperation.between(fieldName, (List<?>) data);
      case IS_NOT_BETWEEN -> SearchOperation.RangeOperation.notBetween(fieldName, (List<?>) data);
      case IN -> SearchOperation.InOperation.in(fieldName, (List<?>) data);
      case NOT_IN -> SearchOperation.NotInOperation.notIn(fieldName, (List<?>) data);
      case IS_NULL -> SearchOperation.NullOperation.isNull(fieldName);
      case EXIST -> SearchOperation.ExistOperation.exist(fieldName);
      case NOT_NULL -> SearchOperation.NotNullOperation.isNotNull(fieldName);
      case STARTS_WITH -> SearchOperation.WildCardOperation.startsWith(fieldName, (String) data);
      case CONTAINS, REGEX -> (data instanceof List)
          ? SearchOperation.WildCardOperation.contains(fieldName, (List<String>) data)
          : SearchOperation.WildCardOperation.contains(fieldName, (String) data);
      case CONTAINS_NOT -> SearchOperation.NotWildCardOperation.notContains(
          fieldName, (String) data);
      case AUTOCOMPLETE -> new SearchOperation.AutoCompleteOperation(
          searchCriteria.getFieldName(),
          searchCriteria.getValue() != null ? searchCriteria.getValue().toString() : null,
          searchCriteria.getTokenOrder(),
          searchCriteria.getFuzzy(),
          searchCriteria.getScore(),
          searchCriteria.getMinGrams(),
          searchCriteria.getMaxGrams());
      default -> throw new UnsupportedOperationException("Unsupported query type: " + queryType);
    };
  }

  public static class EqualityOperation implements SearchQueryOperation {
    private final String fieldName;
    private final Object value;

    public EqualityOperation(String fieldName, Object value) {
      this.fieldName = fieldName;
      this.value = value;
    }

    @Override
    public Document toDocument() {

      if (value instanceof Boolean || value instanceof Number) {
        return new Document("equals", new Document("path", fieldName).append("value", value));
      }

      return new Document("text", new Document("path", fieldName).append("query", value));
    }

    public static EqualityOperation equals(String fieldName, Object value) {
      return new EqualityOperation(fieldName, value);
    }
  }

  public static class ExistOperation implements SearchQueryOperation {
    private final String field;

    public ExistOperation(String field) {
      this.field = field;
    }

    @Override
    public Document toDocument() {
      return new Document("exists", new Document("path", field));
    }

    public static ExistOperation exist(String field) {
      return new ExistOperation(field);
    }
  }

  public static class InOperation implements SearchQueryOperation {
    private final String field;
    private final List<?> values;

    public InOperation(String field, List<?> values) {
      this.field = field;
      this.values = values;
    }

    @Override
    public Document toDocument() {
      if (values == null || values.isEmpty()) {
        throw new IllegalArgumentException("'values' must not be null or empty.");
      }

      Object firstValue = values.get(0);

      if (firstValue instanceof String) {
        return new Document("text", new Document("path", field).append("query", values));
      } else if (firstValue instanceof Number || firstValue instanceof ObjectId) {
        return new Document("in", new Document("path", field).append("value", values));
      } else {
        throw new IllegalArgumentException(
            "Unsupported value type in 'values': " + firstValue.getClass().getSimpleName());
      }
    }

    public static InOperation in(String field, List<?> values) {
      return new InOperation(field, values);
    }
  }

  public static class NotEqualityOperation extends EqualityOperation {
    public NotEqualityOperation(String fieldName, Object value) {
      super(fieldName, value);
    }

    @Override
    public boolean isNegated() {
      return true;
    }

    public static NotEqualityOperation notEquals(String fieldName, Object value) {
      return new NotEqualityOperation(fieldName, value);
    }
  }

  public static class NotInOperation extends InOperation {
    public NotInOperation(String field, List<?> values) {
      super(field, values);
    }

    @Override
    public boolean isNegated() {
      return true;
    }

    public static NotInOperation notIn(String field, List<?> values) {
      return new NotInOperation(field, values);
    }
  }

  public static class NullOperation implements SearchQueryOperation {
    private final String field;

    public NullOperation(String field) {
      this.field = field;
    }

    @Override
    public Document toDocument() {
      FieldTree fieldTree = FieldTreeContext.get();
      String fieldType = fieldTree != null ? fieldTree.getFieldType() : null;

      Object defaultValue =
          switch (fieldType != null ? fieldType.toLowerCase() : null) {
            case "integer", "double", "long" -> 0;
            case "string" -> "";
            default -> null;
          };

      List<Document> shouldConditions = new ArrayList<>();

      shouldConditions.add(
          new Document("equals", new Document("path", field).append("value", null)));

      shouldConditions.add(
          new Document(
              "compound",
              new Document(
                  "mustNot", List.of(new Document("exists", new Document("path", field))))));

      if (defaultValue != null) {
        shouldConditions.add(
            new Document("equals", new Document("path", field).append("value", defaultValue)));
      }

      return new Document("compound", new Document("should", shouldConditions));
    }

    public static NullOperation isNull(String field) {
      return new NullOperation(field);
    }
  }

  public static class NotNullOperation implements SearchQueryOperation {
    private final String field;

    public NotNullOperation(String field) {
      this.field = field;
    }

    @Override
    public Document toDocument() {
      FieldTree fieldTree = FieldTreeContext.get();
      String fieldType = fieldTree != null ? fieldTree.getFieldType() : null;
      boolean isList = fieldTree != null ? fieldTree.isList() : false;

      Object defaultValue =
          switch (fieldType != null ? fieldType.toLowerCase() : null) {
            case "integer", "double", "long" -> 0;
            case "string" -> "";
            default -> null;
          };
      List<Document> mustNotConditions = new ArrayList<>();
      mustNotConditions.add(
          new Document("equals", new Document("path", field).append("value", null)));

      if (!isList && defaultValue != null) {
        mustNotConditions.add(
            new Document("equals", new Document("path", field).append("value", defaultValue)));
      }

      if (isList && fieldType != null) {
        Document listExcludeDoc =
            switch (fieldType.toLowerCase()) {
              case "integer", "double", "long" -> {
                yield new Document(
                    "range", new Document("path", field).append("gt", Integer.MIN_VALUE));
              }
              case "string" -> {
                yield new Document(
                    "text",
                    new Document("query", "*").append("path", field).append("wildcard", true));
              }
              default -> {
                String listKeyExistName = null;
                if (fieldTree != null && fieldTree.isList() && !fieldTree.getChildren().isEmpty()) {
                  listKeyExistName = field + "." + fieldTree.getChildren().get(0).getFieldName();
                }

                if (listKeyExistName != null) {
                  yield new Document("exists", new Document("path", listKeyExistName));
                } else {
                  yield null;
                }
              }
            };

        if (listExcludeDoc != null) {
          mustNotConditions.add(listExcludeDoc);
        }
      }

      return new Document(
          "compound",
          new Document("must", List.of(new Document("exists", new Document("path", field))))
              .append("mustNot", mustNotConditions));
    }

    public static NotNullOperation isNotNull(String field) {
      return new NotNullOperation(field);
    }
  }

  public static class RangeOperation implements SearchQueryOperation {
    private final String field;
    private final String operator;
    private final Object lowerBound;
    private final Object upperBound;
    private final boolean negated;
    private final Document compoundQuery;

    public RangeOperation(String field, String operator, Object value) {
      if (!Set.of("gt", "gte", "lt", "lte").contains(operator)) {
        throw new IllegalArgumentException("Invalid range operator: " + operator);
      }

      this.field = field;
      this.operator = operator;
      this.lowerBound = value;
      this.upperBound = null;
      this.negated = false;
      this.compoundQuery = null;
    }

    public RangeOperation(String field, List<?> range, boolean negated) {
      if (range == null || range.size() != 2) {
        throw new IllegalArgumentException("Between query requires exactly 2 values.");
      }

      this.field = field;
      this.operator = "range";
      this.lowerBound = range.get(0);
      this.upperBound = range.get(1);
      this.negated = negated;
      this.compoundQuery = null;
    }

    // private String getOppositeOperator(String operator) {
    // switch (operator) {
    // case "gt":
    // return "lte";
    // case "gte":
    // return "lt";
    // case "lt":
    // return "gte";
    // case "lte":
    // return "gt";
    // default:
    // throw new IllegalArgumentException("Invalid operator: " + operator);
    // }
    // }

    @Override
    public boolean isNegated() {
      return negated;
    }

    @Override
    public Document toDocument() {
      if (compoundQuery != null) {
        return compoundQuery;
      }

      if (upperBound != null) {
        Document rangeDoc =
            new Document("path", field).append("gte", lowerBound).append("lte", upperBound);

        if (negated) {
          return new Document(
              "compound", new Document("mustNot", Arrays.asList(new Document("range", rangeDoc))));
        }
        return new Document("range", rangeDoc);
      } else {
        Document rangeDoc = new Document("path", field).append(operator, lowerBound);

        if (negated) {
          return new Document(
              "compound", new Document("mustNot", Arrays.asList(new Document("range", rangeDoc))));
        }
        return new Document("range", rangeDoc);
      }
    }

    public static RangeOperation gte(String field, Object value) {
      return new RangeOperation(field, "gte", value);
    }

    public static RangeOperation lte(String field, Object value) {
      return new RangeOperation(field, "lte", value);
    }

    public static RangeOperation gt(String field, Object value) {
      return new RangeOperation(field, "gt", value);
    }

    public static RangeOperation lt(String field, Object value) {
      return new RangeOperation(field, "lt", value);
    }

    public static RangeOperation between(String field, List<?> range) {
      return new RangeOperation(field, range, false);
    }

    public static RangeOperation notBetween(String field, List<?> range) {
      return new RangeOperation(field, range, true);
    }
  }

  public static class WildCardOperation implements SearchQueryOperation {
    private final String[] fieldNames;
    private final String pattern;
    private final List<String> patterns;

    public WildCardOperation(String fieldName, String value) {
      if (fieldName == null || fieldName.isEmpty()) {
        throw new IllegalArgumentException("Field name must not be null or empty.");
      }
      this.fieldNames = new String[] {fieldName};
      this.pattern = value;
      this.patterns = null;
    }

    public WildCardOperation(String[] fieldNames, String value) {
      if (fieldNames == null || fieldNames.length == 0) {
        throw new IllegalArgumentException("At least one field name must be provided.");
      }
      for (String fieldName : fieldNames) {
        if (fieldName == null || fieldName.isEmpty()) {
          throw new IllegalArgumentException("Field names must not be null or empty.");
        }
      }
      this.fieldNames = fieldNames.clone();
      this.pattern = value;
      this.patterns = null;
    }

    public WildCardOperation(String fieldName, List<String> values) {
      this(new String[] {fieldName}, values);
    }

    public WildCardOperation(String[] fieldNames, List<String> values) {
      if (fieldNames == null || fieldNames.length == 0) {
        throw new IllegalArgumentException("At least one field name must be provided.");
      }
      for (String fieldName : fieldNames) {
        if (fieldName == null || fieldName.isEmpty()) {
          throw new IllegalArgumentException("Field names must not be null or empty.");
        }
      }
      if (values == null || values.isEmpty()) {
        throw new IllegalArgumentException("Patterns list must not be null or empty.");
      }
      this.fieldNames = fieldNames.clone();
      this.pattern = null;
      this.patterns = new ArrayList<>(values);
    }

    @Override
    public Document toDocument() {
      Document wildcardBody = new Document();
      if (fieldNames.length == 1) {
        wildcardBody.append("path", fieldNames[0]);
      } else {
        wildcardBody.append("path", fieldNames);
      }

      if (patterns != null) {
        wildcardBody.append("query", patterns);
      } else {
        wildcardBody.append("query", pattern);
      }

      wildcardBody.append("allowAnalyzedField", true);
      return new Document("wildcard", wildcardBody);
    }

    public static WildCardOperation startsWith(String fieldName, String value) {
      return new WildCardOperation(fieldName, value + "*");
    }

    public static WildCardOperation startsWith(String[] fieldNames, String value) {
      return new WildCardOperation(fieldNames, value + "*");
    }

    public static WildCardOperation endsWith(String fieldName, String value) {
      return new WildCardOperation(fieldName, "*" + value);
    }

    public static WildCardOperation endsWith(String[] fieldNames, String value) {
      return new WildCardOperation(fieldNames, "*" + value);
    }

    public static WildCardOperation contains(String fieldName, String value) {
      return new WildCardOperation(fieldName, "*" + value + "*");
    }

    public static WildCardOperation contains(String[] fieldNames, String value) {
      return new WildCardOperation(fieldNames, "*" + value + "*");
    }

    public static WildCardOperation exact(String fieldName, String value) {
      return new WildCardOperation(fieldName, value);
    }

    public static WildCardOperation exact(String[] fieldNames, String value) {
      return new WildCardOperation(fieldNames, value);
    }

    public static WildCardOperation startsWith(String fieldName, List<String> values) {
      return new WildCardOperation(
          fieldName, values.stream().map(v -> v + "*").collect(Collectors.toList()));
    }

    public static WildCardOperation startsWith(String[] fieldNames, List<String> values) {
      return new WildCardOperation(
          fieldNames, values.stream().map(v -> v + "*").collect(Collectors.toList()));
    }

    public static WildCardOperation endsWith(String fieldName, List<String> values) {
      return new WildCardOperation(
          fieldName, values.stream().map(v -> "*" + v).collect(Collectors.toList()));
    }

    public static WildCardOperation endsWith(String[] fieldNames, List<String> values) {
      return new WildCardOperation(
          fieldNames, values.stream().map(v -> "*" + v).collect(Collectors.toList()));
    }

    public static WildCardOperation contains(String fieldName, List<String> values) {
      return new WildCardOperation(
          fieldName, values.stream().map(v -> "*" + v + "*").collect(Collectors.toList()));
    }

    public static WildCardOperation contains(String[] fieldNames, List<String> values) {
      return new WildCardOperation(
          fieldNames, values.stream().map(v -> "*" + v + "*").collect(Collectors.toList()));
    }

    public static WildCardOperation exact(String fieldName, List<String> values) {
      return new WildCardOperation(fieldName, values);
    }

    public static WildCardOperation exact(String[] fieldNames, List<String> values) {
      return new WildCardOperation(fieldNames, values);
    }
  }

  public static class NotWildCardOperation extends WildCardOperation {
    public NotWildCardOperation(String[] fieldName, String value) {
      super(fieldName, value);
    }

    public NotWildCardOperation(String fieldName, String value) {
      super(fieldName, value);
    }

    @Override
    public boolean isNegated() {
      return true;
    }

    public static NotWildCardOperation notStartsWith(String fieldName, String value) {
      return new NotWildCardOperation(fieldName, value + "*");
    }

    public static NotWildCardOperation notEndsWith(String fieldName, String value) {
      return new NotWildCardOperation(fieldName, "*" + value);
    }

    public static NotWildCardOperation notContains(String fieldName, String value) {
      return new NotWildCardOperation(fieldName, "*" + value + "*");
    }

    public static NotWildCardOperation notContains(String[] fieldNames, String value) {
      return new NotWildCardOperation(fieldNames, "*" + value + "*");
    }
  }

  // ...existing code...
  public static class AutoCompleteOperation implements SearchQueryOperation {
    private final String fieldName;
    private final String value;
    private final String tokenOrder;
    private final Document fuzzy;
    private final Document score;
    private final Integer minGrams;
    private final Integer maxGrams;

    public AutoCompleteOperation(
        String fieldName,
        String value,
        String tokenOrder,
        Document fuzzy,
        Document score,
        Integer minGrams,
        Integer maxGrams) {
      this.fieldName = fieldName;
      this.value = value;
      this.tokenOrder = tokenOrder;
      this.fuzzy = fuzzy;
      this.score = score;
      this.minGrams = minGrams;
      this.maxGrams = maxGrams;
    }

    public AutoCompleteOperation(String fieldName, String value) {
      this(fieldName, value, null, null, null, null, null);
    }

    public AutoCompleteOperation(String fieldName, String value, String tokenOrder) {
      this(fieldName, value, tokenOrder, null, null, null, null);
    }

    // Simple constructor: fieldName, value, fuzzy
    public AutoCompleteOperation(String fieldName, String value, Document fuzzy) {
      this(fieldName, value, null, fuzzy, null, null, null);
    }

    // Simple constructor: fieldName, value, score
    public AutoCompleteOperation(String fieldName, String value, Document score, boolean isScore) {
      this(fieldName, value, null, null, score, null, null);
    }

    // Simple constructor: fieldName, value, minGrams, maxGrams
    public AutoCompleteOperation(
        String fieldName, String value, Integer minGrams, Integer maxGrams) {
      this(fieldName, value, null, null, null, minGrams, maxGrams);
    }

    @Override
    public Document toDocument() {
      Document autocomplete = new Document("query", value).append("path", fieldName);

      if (tokenOrder != null) autocomplete.append("tokenOrder", tokenOrder);
      if (fuzzy != null) autocomplete.append("fuzzy", fuzzy);
      if (score != null) autocomplete.append("score", score);
      if (minGrams != null) autocomplete.append("minGrams", minGrams);
      if (maxGrams != null) autocomplete.append("maxGrams", maxGrams);

      return new Document("autocomplete", autocomplete);
    }

    // Add builder/static factory methods as needed
  }

  public static class PhraseOperation implements SearchQueryOperation {
    private final List<String> fields;
    private final String phrase;
    private final int slop;

    public PhraseOperation(String phrase, String field, int slop) {
      this(phrase, List.of(field), slop);
    }

    public PhraseOperation(String phrase, List<String> fields, int slop) {
      this.phrase = phrase;
      this.fields = fields;
      this.slop = slop;
    }

    @Override
    public Document toDocument() {
      Object pathValue = (fields.size() == 1) ? fields.get(0) : fields;

      return new Document(
          "phrase", new Document("query", phrase).append("path", pathValue).append("slop", slop));
    }

    public static PhraseOperation phrase(String phrase, String field) {
      return new PhraseOperation(phrase, field, 0);
    }

    public static PhraseOperation phrase(String phrase, List<String> fields) {
      return new PhraseOperation(phrase, fields, 0);
    }

    public static PhraseOperation phrase(String phrase, String field, int slop) {
      return new PhraseOperation(phrase, field, slop);
    }

    public static PhraseOperation phrase(String phrase, List<String> fields, int slop) {
      return new PhraseOperation(phrase, fields, slop);
    }
  }
}
