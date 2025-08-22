/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch;

import com.amit506.mongosearch.enums.QueryType;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;

@Data
@Builder
@NoArgsConstructor
public class SearchCriteria {
  private String fieldName;
  private QueryType queryType;
  private Object value;
  @Builder.Default private Boolean includeBlanks = false;

  // Autocomplete and phrase-specific fields
  private String tokenOrder;
  private Document fuzzy;
  private Document score;
  private Integer minGrams;
  private Integer maxGrams;
  private Integer slop; // Use Integer for consistency and nullability

  public SearchCriteria(
      String fieldName,
      QueryType queryType,
      Object value,
      Boolean includeBlanks,
      String tokenOrder,
      Document fuzzy,
      Document score,
      Integer minGrams,
      Integer maxGrams,
      Integer slop) {
    this.fieldName = fieldName;
    this.queryType = queryType;
    this.value = value;
    this.includeBlanks = includeBlanks != null ? includeBlanks : false;
    this.tokenOrder = tokenOrder;
    this.fuzzy = fuzzy;
    this.score = score;
    this.minGrams = minGrams;
    this.maxGrams = maxGrams;
    this.slop = slop;
  }

  public SearchCriteria(String fieldName, QueryType queryType, Object value) {
    this.fieldName = fieldName;
    this.queryType = queryType;
    this.value = value;
  }

  @Override
  public String toString() {
    return toString(0);
  }

  private String toString(int indentLevel) {
    String indent = "  ".repeat(indentLevel);
    StringBuilder sb = new StringBuilder();

    sb.append(indent)
        .append("SearchCriteria{")
        .append("fieldName='")
        .append(fieldName)
        .append('\'')
        .append(", queryType=")
        .append(queryType)
        .append(", value=")
        .append(value)
        .append(", includeBlanks=")
        .append(includeBlanks)
        .append(", tokenOrder=")
        .append(tokenOrder)
        .append(", fuzzy=")
        .append(fuzzy)
        .append(", score=")
        .append(score)
        .append(", minGrams=")
        .append(minGrams)
        .append(", maxGrams=")
        .append(maxGrams)
        .append(", slop=")
        .append(slop);

    sb.append("}");

    return sb.toString();
  }
}
