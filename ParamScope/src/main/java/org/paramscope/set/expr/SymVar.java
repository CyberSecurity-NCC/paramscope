package org.paramscope.set.expr;

import org.paramscope.set.state.MemoryKey;
import sootup.core.types.Type;

public record SymVar(MemoryKey key, Type type) implements SymExpr {
    @Override
    public String toString() {
        return "Var(" + key + ")";
    }
}

