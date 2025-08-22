package com.amit506.mongosearch.tokens;

import com.amit506.mongosearch.SearchCriteria;
import lombok.Getter;

@Getter
public class OperandSearchToken implements SearchToken {

  final SearchCriteria searchCriteria;

  public OperandSearchToken(SearchCriteria searchCriteria) {
    this.searchCriteria = searchCriteria;
  }

  public String toString() {
    return searchCriteria.getFieldName();
  }
}
