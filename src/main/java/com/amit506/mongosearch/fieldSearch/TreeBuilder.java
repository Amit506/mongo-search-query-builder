/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch.fieldSearch;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(TreeBuilder.class);
  private static final String TARGET_PACKAGE = "com.ht.research_service";

  /**
   * Builds the field tree starting from the given class.
   *
   * @param clazz The class for which the tree needs to be built.
   * @return The root of the field tree.
   */
  public FieldTree buildTree(Class<?> clazz) {
    return buildTree(clazz, clazz.getSimpleName(), false);
  }

  private FieldTree buildTree(Class<?> clazz, String fieldName, boolean isList) {
    if (isJavaLangOrPrimitive(clazz)) {
      return new FieldTree(fieldName, clazz.getSimpleName(), isList);
    }

    // Create a new FieldTree node for the current class.
    FieldTree root = new FieldTree(fieldName, clazz.getSimpleName(), isList);

    // Get all fields of the class
    Field[] fields = clazz.getDeclaredFields();

    for (Field field : fields) {
      Class<?> fieldType = field.getType();

      // If the field is a List, handle the generic type.
      if (List.class.isAssignableFrom(fieldType)) {
        Class<?> listType = getListGenericType(field);
        if (listType != null) {
          // Recursively build the tree for list elements if they belong to the target
          // package.
          if (isInTargetPackage(listType)) {
            FieldTree childTree = buildTree(listType, field.getName(), true);
            if (childTree != null) {
              root.addChild(childTree);
            }
          }
        }
      } else {
        // If the field belongs to the target package, recursively build its tree.
        if (isInTargetPackage(fieldType)) {
          FieldTree childTree = buildTree(fieldType, field.getName(), false);
          if (childTree != null) {
            root.addChild(childTree);
          }
        } else {
          // Add primitive or non-target package fields directly to the tree.
          root.addChild(new FieldTree(field.getName(), fieldType.getSimpleName(), false));
        }
      }
    }
    return root;
  }

  /**
   * Checks if the class is a primitive, String, or belongs to the java.lang package.
   *
   * @param clazz The class to check.
   * @return True if the class is primitive, String, or java.lang, otherwise false.
   */
  private boolean isJavaLangOrPrimitive(Class<?> clazz) {
    return clazz.isPrimitive()
        || clazz.equals(String.class)
        || clazz.getPackage().getName().startsWith("java.lang");
  }

  /**
   * Checks if the class belongs to the target package or is a List.
   *
   * @param clazz The class to check.
   * @return True if the class belongs to the target package or is a List, otherwise false.
   */
  private boolean isInTargetPackage(Class<?> clazz) {
    if (List.class.isAssignableFrom(clazz)) {
      return true;
    }
    if (isJavaLangOrPrimitive(clazz)) {
      return true;
    }
    String packageName = clazz.getPackage().getName();
    return packageName.startsWith(TARGET_PACKAGE);
  }

  /**
   * Retrieves the generic type of a List field, if available.
   *
   * @param field The field to check.
   * @return The generic type of the List, or null if unavailable.
   */
  private Class<?> getListGenericType(Field field) {
    try {
      return (Class<?>)
          ((java.lang.reflect.ParameterizedType) field.getGenericType())
              .getActualTypeArguments()[0];
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Retrieves all root-level list fields from the FieldTree.
   *
   * @param root the root of the FieldTree (representing the main entity)
   * @return a list of field names that are lists and are directly under the root
   */
  public static List<String> getRootLevelListFields(FieldTree root) {
    List<String> rootListFields = new ArrayList<>();
    for (FieldTree child : root.getChildren()) {
      if (child.isList()) {
        rootListFields.add(child.getFieldName()); // Only consider root-level fields that are lists
      }
    }
    return rootListFields;
  }

  /**
   * Retrieves all second-level list fields from the FieldTree.
   *
   * @param root the root of the FieldTree (representing the main entity)
   * @return a map where the key is the parent field and the value is the second-level list field
   */
  public static Map<String, List<String>> getSecondLevelListFields(FieldTree root) {
    Map<String, List<String>> secondLevelListFields = new HashMap<>();

    // Traverse the root-level fields
    for (FieldTree parent : root.getChildren()) {
      List<String> listFields = new ArrayList<>();

      // Traverse the second-level fields
      findSecondLevelListFields(parent, listFields, 0); // 0 indicates the root level

      if (!listFields.isEmpty()) {
        secondLevelListFields.put(parent.getFieldName(), listFields);
      }
    }

    return secondLevelListFields;
  }

  private static void findSecondLevelListFields(
      FieldTree node, List<String> listFields, int level) {
    if (level == 1 && node.isList()) {
      // If it's the second level and the node is a list, add it to the listFields
      listFields.add(node.getFieldName());
    } else if (level < 2) {
      // Otherwise, recursively check its children
      for (FieldTree child : node.getChildren()) {
        findSecondLevelListFields(child, listFields, level + 1);
      }
    }
  }
}
