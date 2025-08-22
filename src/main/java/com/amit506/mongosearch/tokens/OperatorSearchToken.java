/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch.tokens;

import com.amit506.mongosearch.enums.AtlasSearchOperator;
import lombok.Getter;

@Getter
public class OperatorSearchToken implements SearchToken {
  private final AtlasSearchOperator operator;
  private final boolean separateGroup;

  public OperatorSearchToken(AtlasSearchOperator op) {
    this.operator = op;
    this.separateGroup = false;
  }

  public OperatorSearchToken(AtlasSearchOperator op, boolean forceSeparateGroup) {
    this.operator = op;
    this.separateGroup = forceSeparateGroup;
  }

  public static OperatorSearchToken forceSeparateGroup(AtlasSearchOperator op) {
    return new OperatorSearchToken(op, true);
  }

  public String toString() {
    return operator.name();
  }
}
