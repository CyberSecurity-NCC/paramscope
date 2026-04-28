package org.paramscope.set.expr;

import sootup.core.types.Type;

import java.util.List;

public record SymOp(Op op, List<SymExpr> args, Type type) implements SymExpr {
    public SymOp {
        args = List.copyOf(args);
    }

    @Override
    public String toString() {
        if (op == Op.NOT && args.size() == 1) {
            return "(!" + args.get(0) + ")";
        }
        if (args.size() == 2) {
            return "(" + args.get(0) + " " + op + " " + args.get(1) + ")";
        }
        return op + args.toString();
    }
}

