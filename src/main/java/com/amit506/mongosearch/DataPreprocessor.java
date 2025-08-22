/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch;

@FunctionalInterface
public interface DataPreprocessor {
  SearchCriteria process(SearchCriteria searchCriteria);
}
