package com.amit506.mongosearch;

import com.amit506.mongosearch.fieldSearch.FieldTree;

public class FieldTreeContext {
  private static final ThreadLocal<FieldTree> context = new ThreadLocal<>();

  public static void set(FieldTree tree) {
    context.set(tree);
  }

  public static FieldTree get() {
    FieldTree tree = context.get();
    return tree;
  }

  public static void clear() {
    context.remove();
  }
}
