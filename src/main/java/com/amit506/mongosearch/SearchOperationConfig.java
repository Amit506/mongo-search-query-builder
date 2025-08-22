package com.amit506.mongosearch;

public class SearchOperationConfig {
  public DataPreprocessor dataPreprocessor = (s) -> s;
  public QueryOperationResolver queryOperationResolver =
      SearchOperation::defaultQueryOperationResolver;

  public SearchOperationConfig withDataPreprocessor(DataPreprocessor preprocessor) {
    this.dataPreprocessor = preprocessor;
    return this;
  }

  public SearchOperationConfig withQueryOperationResolver(QueryOperationResolver resolver) {
    this.queryOperationResolver = resolver;
    return this;
  }
}
