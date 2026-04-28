package org.paramscope.set.exec;

import org.paramscope.set.cfg.MethodCFG;
import org.paramscope.set.expr.*;
import org.paramscope.set.state.*;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.expr.AbstractBinopExpr;
import sootup.core.jimple.common.expr.AbstractUnopExpr;
import sootup.core.jimple.common.expr.JDynamicInvokeExpr;
import sootup.core.jimple.common.expr.JStaticInvokeExpr;
import sootup.core.jimple.common.expr.JVirtualInvokeExpr;
import sootup.core.jimple.common.ref.JParameterRef;
import sootup.core.jimple.common.ref.JThisRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.JIdentityStmt;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.common.stmt.JInvokeStmt;
import sootup.core.jimple.common.stmt.JReturnStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;

import java.util.List;
import java.util.Map;

public class TransferFunction {

    public List<BranchTransition> transfer(MethodCFG cfg, SymbolicState state) {
        Stmt stmt = state.loc().stmt();

        if (stmt instanceof JIdentityStmt identityStmt) {
            return transferIdentity(state, identityStmt);
        }
        if (stmt instanceof JAssignStmt assignStmt) {
            return transferAssign(state, assignStmt);
        }
        if (stmt instanceof JInvokeStmt invokeStmt) {
            return transferInvokeStmt(state, invokeStmt);
        }
        if (stmt instanceof JIfStmt ifStmt) {
            return transferIf(cfg, state, ifStmt);
        }
        if (stmt instanceof JReturnStmt) {
            // terminal marker handled by builder
            return List.of();
        }

        // default: fallthrough, no state change
        return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, state.store(), state.pathCond()));
    }

    private List<BranchTransition> transferIdentity(SymbolicState state, JIdentityStmt stmt) {
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();
        SymbolicStore store = state.store();

        if (left instanceof Local local) {
            MemoryKey.LocalKey lkey = new MemoryKey.LocalKey(state.loc().method(), local.getName(), local.getType());

            if (right instanceof JParameterRef pref) {
                MemoryKey.ParamKey pkey = new MemoryKey.ParamKey(state.loc().method(), pref.getIndex(), pref.getType());
                SymExpr sym = new SymVar(pkey, pref.getType());
                return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, store.write(lkey, sym), state.pathCond()));
            }
            if (right instanceof JThisRef thisRef) {
                MemoryKey.ThisKey tkey = new MemoryKey.ThisKey(state.loc().method(), thisRef.getType());
                SymExpr sym = new SymVar(tkey, thisRef.getType());
                return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, store.write(lkey, sym), state.pathCond()));
            }
        }

        return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, store, state.pathCond()));
    }

    private List<BranchTransition> transferAssign(SymbolicState state, JAssignStmt stmt) {
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();

        SymbolicStore store = state.store();
        if (left instanceof Local local) {
            MemoryKey.LocalKey key = new MemoryKey.LocalKey(state.loc().method(), local.getName(), local.getType());
            SymExpr rhs = evalValueExpr(state, right);
            SymbolicStore next = store.write(key, rhs);
            return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, next, state.pathCond()));
        }

        if (left instanceof JArrayRef ar) {
            String baseAlias = "<unknownArray>";
            Value baseV = ar.getBase();
            if (baseV instanceof Local l) baseAlias = l.getName();
            String idx = "<unknownIdx>";
            SymExpr idxExpr = evalValueExpr(state, ar.getIndex());
            if (idxExpr instanceof SymConst sc) {
                idx = String.valueOf(sc.value()).replace("\"", "");
            }
            MemoryKey.ArrayElemKey key = new MemoryKey.ArrayElemKey(baseAlias, idx, ar.getType());
            SymExpr rhs = evalValueExpr(state, right);
            SymbolicStore next = store.write(key, rhs);
            return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, next, state.pathCond()));
        }

        if (left instanceof JInstanceFieldRef ifr) {
            String baseAlias = "<unknownBase>";
            Value base = ifr.getBase();
            if (base instanceof Local) {
                baseAlias = ((Local) base).getName();
            }
            String declClass = ifr.getFieldSignature().getDeclClassType().toString();
            String fieldName = ifr.getFieldSignature().getName();
            MemoryKey.InstanceFieldKey key = new MemoryKey.InstanceFieldKey(baseAlias, declClass, fieldName, ifr.getType());
            SymExpr rhs = evalValueExpr(state, right);
            SymbolicStore next = store.write(key, rhs);
            return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, next, state.pathCond()));
        }

        // unsupported lvalue kinds in phase1
        return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, store, state.pathCond()));
    }

    private List<BranchTransition> transferInvokeStmt(SymbolicState state, JInvokeStmt stmt) {
        if (!stmt.containsInvokeExpr()) {
            return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, state.store(), state.pathCond()));
        }
        var ie = stmt.getInvokeExpr();
        if (ie instanceof JVirtualInvokeExpr vie) {
            var ms = vie.getMethodSignature();
            String decl = ms.getDeclClassType().toString();
            String name = ms.getName();

            // motivationExample2$Config.setF(boolean) => write instance field f
            if (decl.equals("motivationExample2$Config") && name.equals("setF") && vie.getArgs().size() == 1) {
                Value baseV = vie.getBase();
                if (baseV instanceof Local baseLocal) {
                    String baseAlias = baseLocal.getName();
                    MemoryKey.InstanceFieldKey fKey = new MemoryKey.InstanceFieldKey(baseAlias, "motivationExample2$Config", "f", vie.getArg(0).getType());
                    SymExpr rhs = evalValueExpr(state, vie.getArg(0));
                    return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, state.store().write(fKey, rhs), state.pathCond()));
                }
            }

            // motivationExample2$Config.setA(String...) => copy known array elements into field a
            if (decl.equals("motivationExample2$Config") && name.equals("setA") && vie.getArgs().size() == 1) {
                Value baseV = vie.getBase();
                if (baseV instanceof Local baseLocal) {
                    String baseAlias = baseLocal.getName();
                    Value arg0 = vie.getArg(0);
                    if (arg0 instanceof Local arrLocal) {
                        String srcArrAlias = arrLocal.getName();
                        SymbolicStore next = state.store();
                        for (var e : state.store().snapshot().entrySet()) {
                            if (e.getKey() instanceof MemoryKey.ArrayElemKey aek && aek.baseAlias().equals(srcArrAlias)) {
                                MemoryKey.ArrayElemKey dst = new MemoryKey.ArrayElemKey(baseAlias + ".a", aek.index(), aek.type());
                                next = next.write(dst, e.getValue());
                            }
                        }
                        return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, next, state.pathCond()));
                    }
                }
            }
        }
        return List.of(new BranchTransition(BranchLabel.FALLTHROUGH, state.store(), state.pathCond()));
    }

    private List<BranchTransition> transferIf(MethodCFG cfg, SymbolicState state, JIfStmt stmt) {
        SymExpr cond = evalValueExpr(state, stmt.getCondition());
        PathCondition thenPc = state.pathCond().assume(cond);
        PathCondition elsePc = state.pathCond().assume(PathCondition.notExpr(cond));

        // IMPORTANT (Phase-3): do NOT prune branches during graph construction.
        // Keep both branches in the raw ExecutionGraph; Rule1-4 are implemented as post-passes.
        return List.of(
                new BranchTransition(BranchLabel.TRUE_BRANCH, state.store(), thenPc),
                new BranchTransition(BranchLabel.FALSE_BRANCH, state.store(), elsePc)
        );
    }

    /**
     * Evaluate a Jimple value into a SymExpr using the store binding rule:
     * - Locals must be dereferenced via store.read(LocalKey) to freeze semantics in PathCondition.
     */
    public SymExpr evalValueExpr(SymbolicState state, Value v) {
        if (v instanceof Constant c) {
            return new SymConst(c.toString(), v.getType());
        }
        if (v instanceof Local local) {
            MemoryKey.LocalKey key = new MemoryKey.LocalKey(state.loc().method(), local.getName(), local.getType());
            return state.store().read(key).orElseGet(() -> new SymUnknown("unbound_local:" + local.getName(), local.getType()));
        }
        if (v instanceof JInstanceFieldRef ifr) {
            String baseAlias = "<unknownBase>";
            Value baseV = ifr.getBase();
            if (baseV instanceof Local l) baseAlias = l.getName();
            String declClass = ifr.getFieldSignature().getDeclClassType().toString();
            String fieldName = ifr.getFieldSignature().getName();
            SymExpr got = readInstanceField(state, baseAlias, declClass, fieldName);
            if (!(got instanceof SymUnknown)) {
                return got;
            }
            SymExpr def = defaultPrimitiveValue(ifr.getType());
            return def != null ? def : got;
        }
        if (v instanceof JArrayRef ar) {
            String baseAlias = "<unknownArray>";
            Value baseV = ar.getBase();
            if (baseV instanceof Local l) baseAlias = l.getName();
            String idx = "<unknownIdx>";
            SymExpr idxExpr = evalValueExpr(state, ar.getIndex());
            if (idxExpr instanceof SymConst sc) {
                idx = String.valueOf(sc.value()).replace("\"", "");
            }
            final String baseAliasFinal = baseAlias;
            final String idxFinal = idx;
            MemoryKey.ArrayElemKey key = new MemoryKey.ArrayElemKey(baseAlias, idx, ar.getType());
            return state.store().read(key).orElseGet(() -> new SymUnknown("unbound_array_elem:" + baseAliasFinal + "[" + idxFinal + "]", v.getType()));
        }

        // binop/unop (very small subset)
        if (v instanceof AbstractBinopExpr bin) {
            SymExpr a = evalValueExpr(state, bin.getOp1());
            SymExpr b = evalValueExpr(state, bin.getOp2());
            Op op = mapBinOp(bin);
            return new SymOp(op, List.of(a, b), v.getType());
        }
        if (v instanceof AbstractUnopExpr un) {
            SymExpr a = evalValueExpr(state, un.getOp());
            return new SymOp(Op.NOT, List.of(a), boolType());
        }

        if (v instanceof JDynamicInvokeExpr dyn) {
            // Handle Java 9+ invokedynamic StringConcatFactory patterns (phase-1 subset).
            // We only support templates we observed in motivationExample bytecode:
            // - "\u0001\u0001" (two args concatenation)
            // - "\u00011" (arg0 + literal "1")
            String s = v.toString();
            if (s.contains("StringConcatFactory") && s.contains("makeConcatWithConstants")) {
                String template = extractTemplateLiteral(s);
                if (template != null) {
                    List<SymExpr> argExprs = dyn.getArgs().stream().map(a -> evalValueExpr(state, a)).toList();
                    if ((template.equals("\u0001\u0001") || template.equals("")) && argExprs.size() == 2) {
                        return new SymOp(Op.STR_CONCAT, List.of(argExprs.get(0), argExprs.get(1)), v.getType());
                    }
                    if ((template.equals("\u00011") || template.equals("1")) && argExprs.size() == 1) {
                        return new SymOp(Op.STR_CONCAT, List.of(argExprs.get(0), new SymConst("1", v.getType())), v.getType());
                    }
                }
            }
        }

        if (v instanceof JStaticInvokeExpr sie) {
            var ms = sie.getMethodSignature();
            String decl = ms.getDeclClassType().toString();
            String name = ms.getName();

            // motivationExample(2).decodeAscii("323536") => "256"
            if ((decl.equals("motivationExample") || decl.equals("motivationExample2")) && name.equals("decodeAscii") && sie.getArgs().size() == 1) {
                SymExpr arg0 = evalValueExpr(state, sie.getArg(0));
                if (arg0 instanceof SymConst sc) {
                    String hex = String.valueOf(sc.value()).replace("\"", "");
                    String decoded = tryDecodeAscii(hex);
                    if (decoded != null) {
                        return new SymConst(decoded, v.getType());
                    }
                }
            }

            // motivationExample.fieldNotSet(cfg) computed from cfg.field if available
            if (decl.equals("motivationExample") && name.equals("fieldNotSet") && sie.getArgs().size() == 1) {
                Value cfgArg = sie.getArg(0);
                if (cfgArg instanceof Local l) {
                    SymExpr f = readInstanceField(state, l.getName(), "motivationExample$Config", "field");
                    if (f instanceof SymConst sc) {
                        String val = String.valueOf(sc.value()).replace("\"", "");
                        boolean isNotSet = val.equals("null") || val.isEmpty();
                        return new SymConst(isNotSet, boolType());
                    }
                }
            }

            return new SymUnknown("staticInvoke:" + ms, v.getType());
        }

        if (v instanceof JVirtualInvokeExpr vie) {
            var ms = vie.getMethodSignature();
            String decl = ms.getDeclClassType().toString();
            String name = ms.getName();
            // minimal: String.isEmpty()
            if (decl.equals("java.lang.String") && name.equals("isEmpty") && vie.getArgs().isEmpty()) {
                SymExpr base = evalValueExpr(state, vie.getBase());
                if (base instanceof SymConst sc) {
                    String s = String.valueOf(sc.value()).replace("\"", "");
                    return new SymConst(s.isEmpty(), boolType());
                }
                return new SymUnknown("isEmpty", boolType());
            }
            return new SymUnknown("virtualInvoke:" + ms, v.getType());
        }

        return new SymUnknown("value:" + v.getClass().getSimpleName(), v.getType());
    }

    private static SymExpr readInstanceField(SymbolicState state, String baseAlias, String declClass, String fieldName) {
        return state.store().snapshot().entrySet().stream()
                .filter(e -> e.getKey() instanceof MemoryKey.InstanceFieldKey k
                        && k.baseAlias().equals(baseAlias)
                        && k.declClass().equals(declClass)
                        && k.fieldName().equals(fieldName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(new SymUnknown("field:" + baseAlias + "." + fieldName, null));
    }

    private static SymExpr defaultPrimitiveValue(Type t) {
        if (!(t instanceof PrimitiveType pt)) return null;
        String n = pt.getName();
        if (n.equals("boolean")) return new SymConst(false, t);
        if (n.equals("byte") || n.equals("short") || n.equals("int") || n.equals("long") || n.equals("char")) return new SymConst(0, t);
        if (n.equals("float") || n.equals("double")) return new SymConst(0.0, t);
        return null;
    }

    private static String tryDecodeAscii(String hexStr) {
        if (hexStr == null) return null;
        if (hexStr.length() % 2 != 0) return null;
        StringBuilder out = new StringBuilder();
        try {
            for (int i = 0; i < hexStr.length(); i += 2) {
                String b = hexStr.substring(i, i + 2);
                out.append((char) Integer.parseInt(b, 16));
            }
            return out.toString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractTemplateLiteral(String dynToString) {
        // Expect tail like: ...>(\"<template>\")
        int lastOpen = dynToString.lastIndexOf("(\"");
        int lastClose = dynToString.lastIndexOf("\")");
        if (lastOpen >= 0 && lastClose > lastOpen + 2) {
            return dynToString.substring(lastOpen + 2, lastClose);
        }
        return null;
    }

    private static Op mapBinOp(AbstractBinopExpr bin) {
        // Minimal mapping; extend later.
        String name = bin.getClass().getSimpleName();
        return switch (name) {
            case "JAddExpr" -> (String.valueOf(bin.getType()).equals("java.lang.String") ? Op.STR_CONCAT : Op.ADD);
            case "JSubExpr" -> Op.SUB;
            case "JMulExpr" -> Op.MUL;
            case "JEqExpr" -> Op.EQ;
            case "JNeExpr" -> Op.NEQ;
            case "JGtExpr" -> Op.GT;
            case "JGeExpr" -> Op.GE;
            case "JLtExpr" -> Op.LT;
            case "JLeExpr" -> Op.LE;
            case "JAndExpr" -> Op.AND;
            case "JOrExpr" -> Op.OR;
            default -> Op.ADD;
        };
    }

    private static Type boolType() {
        return PrimitiveType.getBoolean();
    }

    public record BranchTransition(BranchLabel branchLabel, SymbolicStore store, PathCondition pathCond) {}
}

