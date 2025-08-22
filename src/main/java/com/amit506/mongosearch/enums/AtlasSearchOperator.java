package com.amit506.mongosearch.enums;

import lombok.Getter;

@Getter
public enum AtlasSearchOperator {
  MUST("must"),
  SHOULD("should"),
  FILTER("filter"),
  ;
  private final String value;

  AtlasSearchOperator(String value) {
    this.value = value;
  }
}
