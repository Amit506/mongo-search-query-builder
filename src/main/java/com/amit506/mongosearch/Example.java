/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch;

import com.amit506.mongosearch.enums.QueryType;
import com.amit506.mongosearch.fieldSearch.FieldTree;
import com.amit506.mongosearch.fieldSearch.TreeBuilder;
import com.amit506.mongosearch.tokens.SearchToken;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;

public class Example {
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Product {
    private String id;
    private String name;
    private String category;
    private double price;
    private double rating;
    private int stock;
    private String description;
    private String brand;
    private boolean available;
  }

  public static void main(String[] args) {
    // Example Product usage
    Product product =
        new Product(
            "p1",
            "Laptop",
            "Electronics",
            999.99,
            4.5,
            20,
            "High performance laptop",
            "BrandX",
            true);
    System.out.println("Sample Product: " + product);

    // Example SearchCriteria for Product fields
    SearchCriteria q1 =
        SearchCriteria.builder()
            .fieldName("name")
            .queryType(QueryType.EQUAL_TO)
            .value("Laptop")
            .build();

    SearchCriteria q2 =
        SearchCriteria.builder()
            .fieldName("price")
            .queryType(QueryType.LESS_THAN)
            .value(1200)
            .build();

    SearchCriteria q3 =
        SearchCriteria.builder()
            .fieldName("rating")
            .queryType(QueryType.GREATER_THAN_OR_EQUAL_TO)
            .value(4)
            .build();

    SearchCriteria q4 =
        SearchCriteria.builder()
            .fieldName("available")
            .queryType(QueryType.EQUAL_TO)
            .value(true)
            .build();

    // Build tokens for (((q1 AND q2) OR q3) AND q4)
    List<SearchToken> tokens =
        new QueryBuilder()
            .first(q1)
            .and(q2)
            .or(q3)
            .and(q4)
            .build(false); // false = use default left-nested grouping

    FieldTree fieldTree = new TreeBuilder().buildTree(Product.class);

    // Build MongoDB Atlas Search Document
    MongoSearchQueryBuilder.Builder builder =
        new MongoSearchQueryBuilder.Builder(fieldTree, "default", tokens);
    MongoSearchQueryBuilder searchQueryBuilder = builder.build();

    Document searchDoc = searchQueryBuilder.getSearchDocument();
    System.out.println("MongoDB Atlas Search Document:");
    System.out.println(searchDoc.toJson());
  }
}
