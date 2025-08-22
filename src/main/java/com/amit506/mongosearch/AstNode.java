/*
 * https://github.com/amit506/mongo-search-query-builder
 */
package com.amit506.mongosearch;

import com.amit506.mongosearch.tokens.OperandSearchToken;
import com.amit506.mongosearch.tokens.OperatorSearchToken;
import com.amit506.mongosearch.tokens.SearchToken;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class AstNode {
  private SearchToken value;
  private List<AstNode> children;

  public AstNode(SearchToken val) {
    this.value = val;
  }

  public String printTree() {
    StringBuilder sb = new StringBuilder();
    printTree("", true, sb);
    return sb.toString();
  }

  private void printTree(String prefix, boolean isTail, StringBuilder sb) {
    sb.append(prefix).append(isTail ? "└── " : "├── ");

    if (this.getValue() instanceof OperandSearchToken operand) {
      sb.append(operand.toString()); // or operand.getCriteria() etc.
    } else if (this.getValue() instanceof OperatorSearchToken op) {
      sb.append(op.getOperator().name());
    } else {
      sb.append(this.getValue());
    }
    sb.append("\n");

    List<AstNode> children = this.getChildren();
    if (children == null) {
      return; // no children to print
    }
    for (int i = 0; i < children.size(); i++) {
      children.get(i).printTree(prefix + (isTail ? "    " : "│   "), i == children.size() - 1, sb);
    }
  }
}
