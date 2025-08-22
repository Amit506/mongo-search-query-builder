package com.amit506.mongosearch;

@FunctionalInterface
public interface DataPreprocessor {
  SearchCriteria process(SearchCriteria searchCriteria);
}
