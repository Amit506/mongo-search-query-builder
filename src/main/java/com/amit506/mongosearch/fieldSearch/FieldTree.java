/* Copyright 2024 HT-VCCEdge*/
package com.amit506.mongosearch.fieldSearch;

import com.amit506.mongosearch.Example;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;

@Data
public class FieldTree {
  private String fieldName;
  private String fieldType;
  private List<FieldTree> children;
  private String id;
  private boolean isList;
  private boolean fieldExist;

  public FieldTree(String fieldName, String fieldType, boolean isList) {
    this.fieldName = fieldName;
    this.fieldType = fieldType;
    this.isList = isList;
    this.children = new ArrayList<>();
    this.fieldExist = true;
    this.id = UUID.randomUUID().toString();
  }

  public static FieldTree createNonExistentField(
      String fieldName, String fieldType, boolean isList) {
    FieldTree fieldTree = new FieldTree(fieldName, fieldType, isList);
    fieldTree.setFieldExist(false);
    return fieldTree;
  }

  public void addChild(FieldTree child) {
    this.children.add(child);
  }

  public void printTree(String indent) {
    System.out.println(indent + this);
    for (FieldTree child : children) {
      child.printTree(indent + "  ");
    }
  }

  public static void main(String[] args) {
    TreeBuilder treeBuilder = new TreeBuilder();

    // Build tree from the CompanySourcing class
    FieldTree tree = treeBuilder.buildTree(Example.class);

    FieldSearchAlgorithm algorithm = new FieldSearchAlgorithm();
    //                Optional<FieldTree> resultWithDotNotation = algorithm.fuzzySearch(tree,
    // "financialIn.annualRevenue");
    Optional<FieldTree> resultWithoutDotNotation =
        algorithm.searchLastNode(tree, "investorInfo.investmentType");

    //                // Output the result tree
    //                System.out.println("Result with dot notation:");
    //                resultWithDotNotation.ifPresent(root -> root.printTree(""));

    System.out.println("Result without dot notation:");
    resultWithoutDotNotation.ifPresent(
        fieldTree -> System.out.println(algorithm.buildDotNotationPath(fieldTree)));
  }

  // Copy Constructor to create a deep copy of the FieldTree object
  public FieldTree(FieldTree other) {
    this.fieldName = other.fieldName;
    this.fieldType = other.fieldType;
    this.isList = other.isList;
    this.fieldExist = other.fieldExist;
    this.id = UUID.randomUUID().toString(); // Generate new ID for the copy
    this.children = new ArrayList<>(); // Initialize empty children list
    for (FieldTree child : other.children) {
      this.children.add(new FieldTree(child)); // Recursively copy children
    }
  }

  public void addOneChild(FieldTree child) {
    this.children.clear(); // Remove all existing children
    this.children.add(child); // Add the new child
  }
}
