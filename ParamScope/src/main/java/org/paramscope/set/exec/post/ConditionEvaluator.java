package org.paramscope.set.exec.post;

import org.paramscope.reflection.ConstantResolve;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.stmt.JIfStmt;

import java.util.Map;
import java.util.Objects;

/**
 * Concrete-first evaluation of a JIfStmt condition using reconstructed values.
 *
 * <p>Phase-3 scope: only handle common Jimple condition expressions (==, !=, <, <=, >, >=)
 * over Local/Constant operands.</p>
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    public static SatResult evalIf(JIfStmt ifStmt, Map<String, Object> env) {
        Objects.requireNonNull(ifStmt, "ifStmt");
        Objects.requireNonNull(env, "env");
        AbstractConditionExpr cond = ifStmt.getCondition();
        Boolean b = evalCondition(cond, env);
        if (b == null) return SatResult.UNKNOWN;
        return b ? SatResult.SAT : SatResult.UNSAT;
    }

    private static Boolean evalCondition(AbstractConditionExpr expr, Map<String, Object> env) {
        if (expr instanceof JLeExpr leExpr) {
            Long a = evalLong(leExpr.getOp1(), env);
            Long b = evalLong(leExpr.getOp2(), env);
            return (a == null || b == null) ? null : a <= b;
        }
        if (expr instanceof JGeExpr geExpr) {
            Long a = evalLong(geExpr.getOp1(), env);
            Long b = evalLong(geExpr.getOp2(), env);
            return (a == null || b == null) ? null : a >= b;
        }
        if (expr instanceof JGtExpr gtExpr) {
            Long a = evalLong(gtExpr.getOp1(), env);
            Long b = evalLong(gtExpr.getOp2(), env);
            return (a == null || b == null) ? null : a > b;
        }
        if (expr instanceof JLtExpr ltExpr) {
            Long a = evalLong(ltExpr.getOp1(), env);
            Long b = evalLong(ltExpr.getOp2(), env);
            return (a == null || b == null) ? null : a < b;
        }
        if (expr instanceof JEqExpr eqExpr) {
            Object a = evalObject(eqExpr.getOp1(), env);
            Object b = evalObject(eqExpr.getOp2(), env);
            if (a == null || b == null) return null;
            return Objects.equals(normalizeBool01(a), normalizeBool01(b));
        }
        if (expr instanceof JNeExpr neExpr) {
            Object a = evalObject(neExpr.getOp1(), env);
            Object b = evalObject(neExpr.getOp2(), env);
            if (a == null || b == null) return null;
            return !Objects.equals(normalizeBool01(a), normalizeBool01(b));
        }
        return null;
    }

    private static Object evalObject(Immediate imm, Map<String, Object> env) {
        if (imm instanceof Local l) {
            return env.get(l.getName());
        }
        if (imm instanceof Constant c) {
            Object v = ConstantResolve.resolve(c);
            return v == null ? String.valueOf(c) : v;
        }
        return null;
    }

    private static Long evalLong(Immediate imm, Map<String, Object> env) {
        Object o = evalObject(imm, env);
        if (o == null) return null;
        Object n = normalizeBool01(o);
        if (n instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(String.valueOf(n));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Object normalizeBool01(Object o) {
        if (o instanceof Boolean b) return b ? 1L : 0L;
        return o;
    }
}

