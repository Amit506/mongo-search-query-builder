package com.amit506.mongosearch.tokens;

import lombok.Getter;

@Getter
public class ParenthesisSearchToken implements SearchToken {
  private final boolean isLeft;

  public ParenthesisSearchToken(boolean isLeft) {
    this.isLeft = isLeft;
  }

  public String toString() {
    return isLeft ? "(" : ")";
  }
}
