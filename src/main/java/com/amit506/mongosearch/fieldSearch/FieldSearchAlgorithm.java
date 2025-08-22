/* Copyright 2024 HT-VCCEdge*/
package com.amit506.mongosearch.fieldSearch;

import com.amit506.mongosearch.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Data;

public class FieldSearchAlgorithm {

  /**
   * Main function to search for the last node, handles both single and dot notation queries. It
   * returns the relevant parent-child structure of the found node.
   */
  public Optional<FieldTree> searchLastNode(FieldTree tree, String query) {
    if (query == null) {
      return Optional.empty();
    }
    // If the query contains dot notation (e.g., investorInfo.address.street.name)
    if (query.contains(Constants.DOT)) {
      // Split the query into multiple levels based on dots
      String[] queryLevels = query.split(Constants.DOT_SEPARATOR_REGEX);

      // Recursive function to process each level of the query
      List<FieldTree> path = new ArrayList<>();
      Optional<FieldTree> result = searchInLevels(tree, queryLevels, 0, path);

      // If result is found, return the path as the full parent-child structure
      if (result.isPresent()) {
        return Optional.of(buildHierarchy(path)); // Return the last node in the path
      } else {
        return Optional.empty(); // Node not found
      }
    } else {
      // If the query does not have dot notation (e.g., "name")
      Optional<FieldTree> foundNode = searchBestMatch(tree, query);

      // After finding the node, rebuild the parent-child path from the root
      if (foundNode.isPresent()) {
        return buildRelevantParentChildPath(tree, foundNode.get());
      } else {
        return Optional.empty(); // Node not found
      }
    }
  }

  /**
   * Method to create a parent-child hierarchy from a list of FieldTree elements.
   *
   * @param path List of FieldTree elements to be arranged in a parent-child hierarchy.
   * @return The root FieldTree node with the constructed hierarchy.
   */
  public static FieldTree buildHierarchy(List<FieldTree> path) {
    if (path == null || path.isEmpty()) {
      return null; // Return null if path is empty or null
    }

    // Create a copy of the root (deep copy if needed)
    FieldTree root = new FieldTree(path.get(0)); // Assuming FieldTree has a copy constructor

    // Iterate through the remaining elements and set them as children
    FieldTree currentParent = root;
    for (int i = 1; i < path.size(); i++) {
      FieldTree currentChild = path.get(i);

      // Create a copy of the current child before adding it to the parent
      FieldTree childCopy =
          new FieldTree(currentChild); // Assuming FieldTree has a copy constructor
      currentParent.addOneChild(childCopy);
      // Update the current parent to the newly added child
      currentParent = childCopy;
    }

    // Return the root of the hierarchy
    return root;
  }

  /**
   * Method to find the parent node based on the given child node and update the parent's children
   * list.
   *
   * @param root The starting point (root) of the tree.
   * @param targetChild The FieldTree child node whose parent we want to find.
   * @return An Optional containing the parent node with only the matched child, or empty if not
   *     found.
   */
  public static Optional<FieldTree> findParentNodeByChild(FieldTree root, FieldTree targetChild) {
    // Recursively search the tree and update the parent node if needed
    return searchParentAndUpdate(root, targetChild);
  }

  /**
   * Recursive method to search for the parent node by its child node and update the parent.
   *
   * @param node The current node to search in.
   * @param targetChild The child node whose parent we want to find.
   * @return An Optional containing the updated parent node, or empty if not found.
   */
  private static Optional<FieldTree> searchParentAndUpdate(FieldTree node, FieldTree targetChild) {
    // Loop through the children of the current node
    for (FieldTree child : node.getChildren()) {
      // If this child matches the target child, update the parent node
      if (child.equals(targetChild)) {
        // Remove other children and keep only the matched child
        List<FieldTree> filteredChildren = new ArrayList<>();
        filteredChildren.add(child); // Keep only the matched child
        node.setChildren(
            filteredChildren); // Update the parent node's children list with the filteredChildren;

        return Optional.of(node); // Return the updated parent node
      }

      // Recursively search the child nodes of the current child node
      Optional<FieldTree> parent = searchParentAndUpdate(child, targetChild);
      if (parent.isPresent()) {
        return parent; // Return the updated parent if found
      }
    }

    // If no match is found, return empty
    return Optional.empty();
  }

  /**
   * Recursive helper method to process each level of the query and build the path.
   *
   * @param tree - The current tree node to search in.
   * @param queryLevels - The array of query levels.
   * @param level - The current index in the queryLevels array.
   * @param path - The list to store the path from root to the current node.
   * @return - An Optional containing the found node, or empty if not found.
   */
  private Optional<FieldTree> searchInLevels(
      FieldTree tree, String[] queryLevels, int level, List<FieldTree> path) {
    // Add the current node to the path
    addToPath(tree, path);

    // Base case: if we've reached the last level of the query
    if (level == queryLevels.length - 1) {
      // Search for the best match at this level (leaf node)
      Optional<FieldTree> foundNode = searchBestMatch(tree, queryLevels[level]);
      if (foundNode.isPresent()) {
        // Add the found node to the path and return the entire path
        addToPath(foundNode.get(), path);
        return Optional.of(foundNode.get()); // Node found, return it
      } else {
        return Optional.empty(); // Node not found
      }
    }

    // If not the last level, find the parent node at the current level
    Optional<FieldTree> bestParentMatch = searchBestMatchWithChildren(tree, queryLevels[level]);

    if (bestParentMatch.isPresent()) {
      // Recursively search the next level within the best parent node
      return searchInLevels(bestParentMatch.get(), queryLevels, level + 1, path);
    } else {
      return Optional.empty(); // Parent node not found
    }
  }

  /**
   * Recursive method to build the path from the root node to the target node.
   *
   * @param currentNode - The current node to search from.
   * @param targetNode - The target node to find.
   * @param path - The list to store the path.
   * @return - A boolean indicating whether the path was found.
   */
  private boolean buildPathRecursive(
      FieldTree currentNode, FieldTree targetNode, List<FieldTree> path) {
    // Add the current node to the path
    addToPath(currentNode, path);

    // If we've found the target node, return true
    if (currentNode.equals(targetNode)) {
      return true;
    }

    // Recursively check children
    for (FieldTree child : currentNode.getChildren()) {
      if (buildPathRecursive(child, targetNode, path)) {
        return true; // Path found in a child
      }
    }

    // If not found, remove the current node from the path and backtrack
    path.remove(path.size() - 1);
    return false;
  }

  /**
   * Helper function to search for the best matching node based on the query. Uses fuzzy search to
   * prioritize nodes based on similarity score and depth.
   */
  private Optional<FieldTree> searchBestMatch(FieldTree tree, String query) {
    List<SearchResult> searchResults = new ArrayList<>();
    // Perform a fuzzy search across the entire tree to collect potential matches
    fuzzySearchAcrossTree(tree, query, 0, searchResults);

    // Strong matches (>= 0.7)
    Optional<SearchResult> bestStrongMatch =
        searchResults.stream()
            .filter(result -> result.getSimilarityScore() >= 0.7)
            .max((a, b) -> Double.compare(a.getWeight(), b.getWeight())); // Get the strongest match

    if (bestStrongMatch.isPresent()) {
      // If a strong match is found, return it
      return Optional.of(bestStrongMatch.get().getFieldTree());
    } else {
      // Moderate matches (0.5 <= score < 0.7)
      Optional<SearchResult> bestModerateMatch =
          searchResults.stream()
              .filter(
                  result -> result.getSimilarityScore() >= 0.5 && result.getSimilarityScore() < 0.7)
              .max(
                  (a, b) ->
                      Double.compare(a.getWeight(), b.getWeight())); // Get the best moderate match

      // Return the best moderate match if found, otherwise return empty
      return bestModerateMatch.map(SearchResult::getFieldTree);
    }
  }

  /**
   * Helper function to search for the best matching node, prioritizing nodes with children when
   * handling dot notation queries.
   */
  private Optional<FieldTree> searchBestMatchWithChildren(FieldTree tree, String query) {
    List<SearchResult> searchResults = new ArrayList<>();
    // Perform a fuzzy search across the entire tree to collect potential matches
    fuzzySearchAcrossTree(tree, query, 0, searchResults);

    // Strong matches (>= 0.7) with children
    Optional<SearchResult> bestStrongMatchWithChildren =
        searchResults.stream()
            .filter(
                result ->
                    result.getSimilarityScore() >= 0.7
                        && result.hasChildren()) // Prioritize nodes with children
            .max((a, b) -> Double.compare(a.getWeight(), b.getWeight())); // Get the strongest match

    if (bestStrongMatchWithChildren.isPresent()) {
      // If a strong match with children is found, return it
      return Optional.of(bestStrongMatchWithChildren.get().getFieldTree());
    } else {
      // Moderate matches (0.5 <= score < 0.7), still prioritize nodes with children
      Optional<SearchResult> bestModerateMatchWithChildren =
          searchResults.stream()
              .filter(result -> result.getSimilarityScore() >= 0.5 && result.hasChildren())
              .max(
                  (a, b) ->
                      Double.compare(a.getWeight(), b.getWeight())); // Get the best moderate match

      // Return the best moderate match if found, otherwise return empty
      return bestModerateMatchWithChildren.map(SearchResult::getFieldTree);
    }
  }

  /**
   * Fuzzy search across the tree recursively. Collects results with a similarity score and tracks
   * the depth of each node. Also checks if the node has children.
   */
  private void fuzzySearchAcrossTree(
      FieldTree tree, String query, int currentDepth, List<SearchResult> results) {
    // Calculate the similarity score between the query and the current field name
    double similarityScore = LevenshteinDistance.similarity(query, tree.getFieldName());

    // Add the result with its similarity score, depth, and whether it has children
    if (similarityScore >= 0.8) { // Only consider matches with a score of 0.5 or above
      results.add(
          new SearchResult(tree, similarityScore, currentDepth, !tree.getChildren().isEmpty()));
    }

    // Recursively search through the children of the current node
    for (FieldTree child : tree.getChildren()) {
      fuzzySearchAcrossTree(child, query, currentDepth + 1, results);
    }
  }

  /**
   * Builds the parent-child path from the root to the target node using the target's unique ID.
   * This is done recursively by traversing the tree.
   */
  public Optional<FieldTree> buildParentChildPath(FieldTree root, String targetId) {
    // Check if the current root node matches the target ID
    if (root.getId().equals(targetId)) {
      // Return the target node as a new tree structure with only this node
      return Optional.of(new FieldTree(root.getFieldName(), root.getFieldType(), root.isList()));
    }

    // Recursively check the children of the current node
    for (FieldTree child : root.getChildren()) {
      Optional<FieldTree> result = buildParentChildPath(child, targetId);
      if (result.isPresent()) {
        // If the target is found in one of the children, build the parent-child relationship
        FieldTree newRoot = new FieldTree(root.getFieldName(), root.getFieldType(), root.isList());
        newRoot.addChild(result.get()); // Add the found node as a child
        return Optional.of(newRoot);
      }
    }

    return Optional.empty(); // Return empty if the target is not found in this subtree
  }

  /**
   * Constructs the relevant parent-child structure for the found node and returns the path from
   * root to the found node.
   */
  public Optional<FieldTree> buildRelevantParentChildPath(FieldTree root, FieldTree targetNode) {
    // Build the path from root to the target node using its unique ID
    return buildParentChildPath(root, targetNode.getId());
  }

  /**
   * Utility class to compute the Levenshtein Distance and similarity between two strings. This is
   * used to determine how closely the query matches a field name.
   */
  public static class LevenshteinDistance {

    // Calculates the Levenshtein Distance between two strings
    public static int calculate(String a, String b) {
      int[][] dp = new int[a.length() + 1][b.length() + 1];

      // Fill the dp matrix
      for (int i = 0; i <= a.length(); i++) {
        for (int j = 0; j <= b.length(); j++) {
          if (i == 0) {
            dp[i][j] = j; // Initializing base case
          } else if (j == 0) {
            dp[i][j] = i; // Initializing base case
          } else {
            // Calculate substitution cost and find the minimum operation needed
            dp[i][j] =
                Math.min(
                    dp[i - 1][j - 1] + costOfSubstitution(a.charAt(i - 1), b.charAt(j - 1)),
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1));
          }
        }
      }

      return dp[a.length()][b.length()]; // The final distance value
    }

    // Determines the cost of substitution between two characters
    public static int costOfSubstitution(char a, char b) {
      return a == b ? 0 : 1; // No cost if characters are the same
    }

    // Computes the similarity between two strings as a fraction of their Levenshtein distance
    public static double similarity(String a, String b) {
      // Check if  a is id and b is _id should be considered identical
      if (isIdField(a, b)) {
        return 1.0; // Treat them as identical
      }
      int maxLength = Math.max(a.length(), b.length());
      if (maxLength > 0) {
        return (maxLength - calculate(a, b)) / (double) maxLength;
      }
      return 1.0; // If both strings are empty, they are considered identical
    }
  }

  public static String buildParentPath(FieldTree target) {
    if (target.getChildren().isEmpty()) {
      return "";
    }
    for (FieldTree child : target.getChildren()) {
      String childPath = buildParentPath(child);

      if (childPath != null) {
        if (childPath.isEmpty()) {
          return target.getFieldName();
        }
        return target.getFieldName() + Constants.DOT + childPath;
      }
    }

    return null;
  }

  public static String buildDotNotationPath(FieldTree target) {
    if (target.getChildren().isEmpty()) {
      return target.getFieldName();
    }
    for (FieldTree child : target.getChildren()) {
      String childPath = buildDotNotationPath(child);

      if (childPath != null) {
        if (childPath.isEmpty()) {
          return child.getFieldName();
        }
        return target.getFieldName() + Constants.DOT + childPath;
      }
    }
    return null;
  }

  @Data
  public static class SearchResult {
    private FieldTree fieldTree;
    private double similarityScore;
    private int depth;
    private boolean hasChildren;

    public SearchResult(
        FieldTree fieldTree, double similarityScore, int depth, boolean hasChildren) {
      this.fieldTree = fieldTree;
      this.similarityScore = similarityScore;
      this.depth = depth;
      this.hasChildren = hasChildren;
    }

    public double getWeight() {
      double childWeight = hasChildren ? 1.0 : 0.5;
      return (similarityScore * childWeight) / (depth + 1);
    }

    public boolean hasChildren() {
      return hasChildren;
    }
  }

  private static boolean isIdField(String a, String b) {
    return (a.equals(Constants.MONGO_ID) && b.equals(Constants.ID));
  }

  private static void addToPath(FieldTree tree, List<FieldTree> path) {
    if (tree != null) {
      FieldTree treeCopy = new FieldTree(tree);
      treeCopy.setChildren(Collections.emptyList());
      path.add(treeCopy);
    }
  }

  public static FieldTree findFieldNode(String path, FieldTree node) {
    if (node == null) return null;
    if (path.equals(node.getFieldName())) return node;

    if (path.startsWith(node.getFieldName() + ".")) {
      String remaining = path.substring(node.getFieldName().length() + 1);
      for (FieldTree child : node.getChildren()) {
        FieldTree found = findFieldNode(remaining, child);
        if (found != null) return found;
      }
    }
    return null;
  }

  public static Optional<FieldTree> findFieldTreeForPath(FieldTree root, String path) {
    FieldSearchAlgorithm algorithm = new FieldSearchAlgorithm();

    Optional<FieldTree> parentNode = algorithm.searchLastNode(root, path);

    if (parentNode.isPresent()) {
      if (!parentNode.get().getChildren().isEmpty()) {
        return Optional.of(parentNode.get().getChildren().get(0));
      }
    } else {

      return Optional.of(FieldTree.createNonExistentField(path, "Custom", false));
    }
    return Optional.empty();
  }

  public static FieldTree findLastLeafNode(FieldTree fieldTree, String fieldName) {

    Optional<FieldTree> parentFieldTree = findFieldTreeForPath(fieldTree, fieldName);

    if (parentFieldTree.isPresent()) {

      FieldTree leafNode = findLastLeafNode(parentFieldTree.get());
      return leafNode;
    }
    return null;
  }

  public static FieldTree findLastLeafNode(FieldTree node) {
    if (node.getChildren().isEmpty()) {
      return node;
    }

    for (FieldTree child : node.getChildren()) {
      FieldTree lastLeaf = findLastLeafNode(child);
      if (lastLeaf != null) {
        return lastLeaf;
      }
    }
    return null;
  }
}
