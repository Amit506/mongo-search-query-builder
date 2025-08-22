/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch.enums;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class QueryTypeDeserializer extends JsonDeserializer<QueryType> {

  @Override
  public QueryType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    String value = p.getText();
    return QueryType.fromExpression(value);
  }
}
