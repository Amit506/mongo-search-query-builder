package com.amit506.mongosearch;

import com.amit506.mongosearch.fieldSearch.FieldTree;

@FunctionalInterface
public interface QueryOperationResolver {
  SearchQueryOperation resolve(SearchCriteria searchCriteria, FieldTree fieldTree);
}
