/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch.enums;

import lombok.Getter;

public enum QueryType {
  LESS_THAN(1, "<"),
  LESS_THAN_OR_EQUAL_TO(2, "<="),
  GREATER_THAN(3, ">"),
  GREATER_THAN_OR_EQUAL_TO(4, ">="),
  EQUAL_TO(5, "="),
  NOT_EQUAL_TO(6, "!="),
  IS_BETWEEN(7, "IS_BETWEEN"),
  IS_NOT_BETWEEN(8, "IS_NOT_BETWEEN"),
  IN(9, "IN"),
  NOT_IN(10, "NOT_IN"),
  STARTS_WITH(11, "STARTS_WITH"),
  ENDS_WITH(12, "ENDS_WITH"),
  CONTAINS(13, "CONTAINS"),
  REGEX(14, "~"),
  NOT_NULL(15, "NOT_NULL"),
  IS_NULL(16, "IS_NULL"),
  EXIST(17, "EXIST"),
  SEARCH(17, "SEARCH"),
  STARTS_WITH_NOT(18, "STARTS_WITH_NOT"),
  NOT_DEFINED(19, "NOT_DEFINED"),
  ENDS_WITH_NOT(20, "ENDS_WITH_NOT"),
  CONTAINS_NOT(21, "CONTAINS_NOT"),
  REGEX_NOT(22, "REGEX_NOT"),
  AUTOCOMPLETE(23, "AUTOCOMPLETE");
  private final int value;
  @Getter private final String expression;

  QueryType(int value, String expression) {
    this.value = value;
    this.expression = expression;
  }

  public static QueryType fromExpression(String expression) {
    for (QueryType type : QueryType.values()) {
      if (type.expression.equals(expression)) {
        return type;
      }
    }
    return NOT_DEFINED;
  }
}
