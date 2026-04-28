package org.paramscope.set.expr;

import sootup.core.types.Type;

public record SymUnknown(String reason, Type type) implements SymExpr {
    @Override
    public String toString() {
        return "Unknown(" + reason + ")";
    }
}

