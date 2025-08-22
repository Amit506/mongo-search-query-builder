/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch;

import java.util.List;
import org.bson.Document;

public interface SearchQueryOperation {

  Document toDocument();

  /** The full query with negation applied (compound.mustNot). */
  default Document toNegatedDocument() {
    return new Document("compound", new Document("mustNot", List.of(toDocument())));
  }

  default boolean isNegated() {
    return false;
  }

  /** Resolves the final query based on whether it is negated or not. */
  default Document resolve() {
    return isNegated() ? toNegatedDocument() : toDocument();
  }

  /** Converts the query to its non-negated form. */
}
