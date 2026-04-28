package org.paramscope.set.exec.post;

import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.expr.SymConst;
import org.paramscope.set.expr.SymExpr;
import org.paramscope.set.state.MemoryKey;
import org.paramscope.set.state.SymbolicState;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.core.JavaSootMethod;

import java.util.*;

/**
 * Reduce state-sensitive Rule1 decisions from the method-internal SET into a conservative,
 * CFG-level edge blocking set.
 *
 * <p>Conservative rule: only block a CFG branch edge if that branch is UNSAT for all SET states
 * reaching the corresponding {@link JIfStmt}, and there is no SAT/UNKNOWN counterexample.</p>
 */
public final class CfgBranchPruneReducer {

    private CfgBranchPruneReducer() {
    }

    public record EdgeKey(Stmt from, Stmt to) {
    }

    public static Set<EdgeKey> computeBlockedEdges(JavaSootMethod method,
                                                   StmtGraph<?> baseGraph,
                                                   ExecutionGraph raw) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(baseGraph, "baseGraph");
        Objects.requireNonNull(raw, "raw");

        Map<Stmt, BranchAgg> agg = new LinkedHashMap<>();

        for (SymbolicState st : raw.states().values()) {
            Stmt stmt = st.loc().stmt();
            if (!(stmt instanceof JIfStmt ifStmt)) continue;

            Map<String, Object> env = envFromStore(st);
            SatResult cond = ConditionEvaluator.evalIf(ifStmt, env);
            SatResult thenSat;
            SatResult elseSat;
            if (cond == SatResult.UNKNOWN) {
                thenSat = SatResult.UNKNOWN;
                elseSat = SatResult.UNKNOWN;
            } else if (cond == SatResult.SAT) {
                thenSat = SatResult.SAT;
                elseSat = SatResult.UNSAT;
            } else {
                thenSat = SatResult.UNSAT;
                elseSat = SatResult.SAT;
            }

            BranchAgg a = agg.computeIfAbsent(ifStmt, k -> new BranchAgg());
            a.thenObs.add(thenSat);
            a.elseObs.add(elseSat);
        }

        Set<EdgeKey> blocked = new LinkedHashSet<>();

        for (var e : agg.entrySet()) {
            JIfStmt ifStmt = (JIfStmt) e.getKey();
            BranchAgg a = e.getValue();

            IfSuccs succs = resolveIfSuccs(baseGraph, ifStmt);
            if (!succs.resolved()) continue;

            if (a.thenObs.size() == 1 && a.thenObs.contains(SatResult.UNSAT)) {
                blocked.add(new EdgeKey(ifStmt, succs.trueSucc));
            }
            if (a.elseObs.size() == 1 && a.elseObs.contains(SatResult.UNSAT)) {
                blocked.add(new EdgeKey(ifStmt, succs.falseSucc));
            }
        }

        return blocked;
    }

    private static final class BranchAgg {
        final EnumSet<SatResult> thenObs = EnumSet.noneOf(SatResult.class);
        final EnumSet<SatResult> elseObs = EnumSet.noneOf(SatResult.class);
    }

    private record IfSuccs(Stmt trueSucc, Stmt falseSucc) {
        boolean resolved() {
            return trueSucc != null && falseSucc != null;
        }
    }

    @SuppressWarnings("null")
    private static IfSuccs resolveIfSuccs(StmtGraph<?> g, JIfStmt ifStmt) {
        Stmt ifAsStmt = ifStmt;
        List<Stmt> succ = g.successors(ifAsStmt);
        if (succ == null || succ.size() != 2) {
            return new IfSuccs(null, null);
        }

        Stmt target = null;
        sootup.core.jimple.common.stmt.BranchingStmt bs = (sootup.core.jimple.common.stmt.BranchingStmt) ifStmt;
        List<Stmt> targets = g.getBranchTargetsOf(bs);
        if (targets != null && !targets.isEmpty()) {
            target = targets.get(0);
        }
        if (target == null) {
            return new IfSuccs(null, null);
        }

        Stmt trueSucc = null;
        for (Stmt s : succ) {
            if (s == target || s.equivTo(target)) {
                trueSucc = s;
                break;
            }
        }
        if (trueSucc == null) {
            return new IfSuccs(null, null);
        }
        Stmt falseSucc = (succ.get(0) == trueSucc) ? succ.get(1) : succ.get(0);
        return new IfSuccs(trueSucc, falseSucc);
    }

    private static Map<String, Object> envFromStore(SymbolicState st) {
        Map<String, Object> env = new LinkedHashMap<>();
        for (var e : st.store().snapshot().entrySet()) {
            if (e.getKey() instanceof MemoryKey.LocalKey lk) {
                SymExpr v = e.getValue();
                if (v instanceof SymConst sc) {
                    env.put(lk.localName(), normalize(sc.value()));
                }
            }
        }
        return env;
    }

    private static Object normalize(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return v;
    }
}

