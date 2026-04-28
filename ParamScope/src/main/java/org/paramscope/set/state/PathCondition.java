package org.paramscope.set.state;

import org.paramscope.set.expr.Op;
import org.paramscope.set.expr.SymConst;
import org.paramscope.set.expr.SymExpr;
import org.paramscope.set.expr.SymOp;
import org.paramscope.set.expr.SymVar;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PathCondition {
    private final List<SymExpr> constraints;
    private final boolean unsatTrivially;

    private PathCondition(List<SymExpr> constraints, boolean unsatTrivially) {
        this.constraints = constraints;
        this.unsatTrivially = unsatTrivially;
    }

    public static PathCondition empty() {
        return new PathCondition(List.of(), false);
    }

    public List<SymExpr> constraints() {
        return constraints;
    }

    public boolean isUnsatTrivially() {
        return unsatTrivially;
    }

    public PathCondition assume(SymExpr boolExpr) {
        if (unsatTrivially) return this;
        TrivialResult tr = simplifyBool(boolExpr);
        if (tr.isFalse) {
            return new PathCondition(constraints, true);
        }
        if (tr.isTrue) {
            return this;
        }

        List<SymExpr> next = new ArrayList<>(constraints);
        next.add(tr.expr);
        return new PathCondition(List.copyOf(next), detectTrivialConflicts(next));
    }

    private static TrivialResult simplifyBool(SymExpr e) {
        if (e instanceof SymConst c && c.value() instanceof Boolean b) {
            return new TrivialResult(e, b, !b);
        }
        if (e instanceof SymOp op && op.args().size() == 2) {
            SymExpr a = op.args().get(0);
            SymExpr b = op.args().get(1);
            if (a instanceof SymConst ca && b instanceof SymConst cb) {
                Boolean r = evalConstCompare(op.op(), ca.value(), cb.value());
                if (r != null) {
                    return new TrivialResult(new SymConst(r, boolType()), r, !r);
                }
            }
        }
        if (e instanceof SymOp op && op.op() == Op.NOT && op.args().size() == 1) {
            SymExpr inner = op.args().get(0);
            if (inner instanceof SymConst c && c.value() instanceof Boolean b) {
                return new TrivialResult(new SymConst(!b, op.type()), !b, b);
            }
            // NOT(const-compare) folding
            TrivialResult innerTr = simplifyBool(inner);
            if (innerTr.isTrue || innerTr.isFalse) {
                boolean r = !innerTr.isTrue;
                return new TrivialResult(new SymConst(r, boolType()), r, !r);
            }
        }
        return new TrivialResult(e, false, false);
    }

    private static Boolean evalConstCompare(Op op, Object a, Object b) {
        String as = String.valueOf(a).replace("\"", "");
        String bs = String.valueOf(b).replace("\"", "");

        // boolean encoded as 0/1
        if (a instanceof Boolean ab && (bs.equals("0") || bs.equals("1"))) {
            boolean bb = bs.equals("1");
            return evalBoolCompare(op, ab, bb);
        }
        if (b instanceof Boolean bb && (as.equals("0") || as.equals("1"))) {
            boolean ab = as.equals("1");
            return evalBoolCompare(op, ab, bb);
        }

        if (a instanceof Boolean ab && b instanceof Boolean bb) {
            return evalBoolCompare(op, ab, bb);
        }

        // integer-like
        try {
            long ai = Long.parseLong(as);
            long bi = Long.parseLong(bs);
            return switch (op) {
                case EQ -> ai == bi;
                case NEQ -> ai != bi;
                case LT -> ai < bi;
                case LE -> ai <= bi;
                case GT -> ai > bi;
                case GE -> ai >= bi;
                default -> null;
            };
        } catch (NumberFormatException ignored) {
        }

        // string equality only
        return switch (op) {
            case EQ -> as.equals(bs);
            case NEQ -> !as.equals(bs);
            default -> null;
        };
    }

    private static Boolean evalBoolCompare(Op op, boolean a, boolean b) {
        return switch (op) {
            case EQ -> a == b;
            case NEQ -> a != b;
            default -> null;
        };
    }

    private static Type boolType() {
        return PrimitiveType.getBoolean();
    }

    private static boolean detectTrivialConflicts(List<SymExpr> constraints) {
        // Very conservative: only detect x == const conflicts when x is a MemoryKey-backed SymVar is NOT allowed here.
        // But we still detect const == const contradictions and repeated false.
        for (SymExpr c : constraints) {
            if (c instanceof SymConst sc && sc.value() instanceof Boolean b && !b) {
                return true;
            }
        }

        // Simple pattern: (a EQ b) where both sides are SymConst.
        for (SymExpr c : constraints) {
            if (c instanceof SymOp op && op.op() == Op.EQ && op.args().size() == 2) {
                SymExpr a = op.args().get(0);
                SymExpr b = op.args().get(1);
                if (a instanceof SymConst ca && b instanceof SymConst cb) {
                    if (!String.valueOf(ca.value()).equals(String.valueOf(cb.value()))) {
                        return true;
                    }
                }
            }
        }

        // Param-level contradiction patterns:
        // - (Var(k) EQ const) plus (Var(k) NEQ same const) => UNSAT
        // - (Var(k) EQ c1) plus (Var(k) EQ c2) with c1!=c2 => UNSAT
        Map<Object, String> eqConsts = new HashMap<>();
        Map<Object, String> neqConsts = new HashMap<>();
        for (SymExpr c : constraints) {
            SymExpr norm = normalizeNot(c);
            if (norm instanceof SymOp op && op.args().size() == 2) {
                SymExpr a = op.args().get(0);
                SymExpr b = op.args().get(1);
                if (a instanceof SymVar va && b instanceof SymConst cb) {
                    String val = String.valueOf(cb.value());
                    if (op.op() == Op.EQ) {
                        String prev = eqConsts.putIfAbsent(va.key(), val);
                        if (prev != null && !prev.equals(val)) return true;
                        if (neqConsts.containsKey(va.key()) && neqConsts.get(va.key()).equals(val)) return true;
                    }
                    if (op.op() == Op.NEQ) {
                        neqConsts.putIfAbsent(va.key(), val);
                        if (eqConsts.containsKey(va.key()) && eqConsts.get(va.key()).equals(val)) return true;
                    }
                }
            }
        }

        return false;
    }

    private static SymExpr normalizeNot(SymExpr e) {
        if (e instanceof SymOp not && not.op() == Op.NOT && not.args().size() == 1) {
            SymExpr inner = not.args().get(0);
            if (inner instanceof SymOp cmp && cmp.args().size() == 2) {
                if (cmp.op() == Op.EQ) return new SymOp(Op.NEQ, cmp.args(), cmp.type());
                if (cmp.op() == Op.NEQ) return new SymOp(Op.EQ, cmp.args(), cmp.type());
            }
        }
        return e;
    }

    public static SymExpr notExpr(SymExpr e) {
        Type t = e.type();
        // fallback type: boolean primitive when unknown
        if (t == null) {
            t = boolType();
        }
        return new SymOp(Op.NOT, List.of(e), t);
    }

    private record TrivialResult(SymExpr expr, boolean isTrue, boolean isFalse) {}
}

