package com.amit506.mongosearch;


import com.amit506.mongosearch.enums.AtlasSearchOperator;
import com.amit506.mongosearch.tokens.*;
import java.util.ArrayList;
import java.util.List;

public class QueryBuilder {
    private final List<Object> elements = new ArrayList<>(); 

    public QueryBuilder and(SearchCriteria criteria) {
        elements.add(AtlasSearchOperator.MUST);
        elements.add(criteria);
        return this;
    }

    public QueryBuilder or(SearchCriteria criteria) {
        elements.add(AtlasSearchOperator.SHOULD);
        elements.add(criteria);
        return this;
    }

    public QueryBuilder first(SearchCriteria criteria) {
        elements.add(criteria);
        return this;
    }
public QueryBuilder openParen() {
    elements.add(new ParenthesisSearchToken(true));
    return this;
}

public QueryBuilder closeParen() {
    elements.add(new ParenthesisSearchToken(false));
    return this;
}
    public List<SearchToken> defaultBuild() {
    List<SearchToken> output = new ArrayList<>();
    int idx = 0;
    while (idx < elements.size()) {
        Object elem = elements.get(idx);
        if (elem instanceof SearchCriteria sc) {
            if (output.isEmpty()) {
                output.add(new OperandSearchToken(sc));
            } else {
                List<SearchToken> newOutput = new ArrayList<>();
                newOutput.add(new ParenthesisSearchToken(true)); // (
                newOutput.addAll(output);
                newOutput.add(new OperatorSearchToken((AtlasSearchOperator) elements.get(idx - 1)));
                newOutput.add(new OperandSearchToken(sc));
                newOutput.add(new ParenthesisSearchToken(false)); // )
                output = newOutput;
            }
        }
        idx++;
    }
    return output;
}

public List<SearchToken> build(boolean useOwnGrouping) {
    if (useOwnGrouping) {
        List<SearchToken> output = new ArrayList<>();
        for (Object elem : elements) {
            if (elem instanceof SearchCriteria sc) {
                output.add(new OperandSearchToken(sc));
            } else if (elem instanceof AtlasSearchOperator op) {
                output.add(new OperatorSearchToken(op));
            } else if (elem instanceof ParenthesisSearchToken paren) {
                output.add(paren);
            }
        }
        return output;
    } else {
        return defaultBuild();
    }
}
}