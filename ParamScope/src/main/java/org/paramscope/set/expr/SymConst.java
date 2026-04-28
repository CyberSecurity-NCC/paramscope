package org.paramscope.set.expr;

import sootup.core.types.Type;

public record SymConst(Object value, Type type) implements SymExpr {
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

