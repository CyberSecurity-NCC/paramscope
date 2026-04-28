package org.paramscope.set.exec.post;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.state.BranchLabel;
import org.paramscope.set.state.SymbolicState;
import org.paramscope.set.expr.SymConst;
import org.paramscope.set.expr.SymExpr;
import org.paramscope.set.state.MemoryKey;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootMethod;

import java.util.*;
import java.util.Objects;

/**
 * Rule1: prune infeasible branch edges after building an ExecutionGraph (post-pass).
 *
 * <p>Concrete-first: use valueflow reconstruction to obtain concrete values for the Locals used
 * directly in the if condition, then evaluate the if condition. If decidable, prune the inconsistent edge.</p>
 */
public final class BranchPruner {

    public record Options(
            int nondetMaxRuns
    ) {
        public static Options defaults() {
            return new Options(20);
        }
    }

    private BranchPruner() {
    }

    public static ExecutionGraph prune(ExecutionGraph raw, Options opt) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(opt, "opt");

        ExecutionGraph out = copyGraph(raw);

        // Find entry(s)
        Set<Long> entries = new LinkedHashSet<>();
        for (SymbolicState st : out.states().values()) {
            if (st.parentStateId().isEmpty() || st.branchLabel() == BranchLabel.ENTRY) {
                entries.add(st.stateId());
            }
        }
        if (entries.isEmpty() && !out.states().isEmpty()) {
            entries.add(out.states().keySet().iterator().next());
        }

        // For each if-state, decide pruning (state-sensitive).
        Map<Long, List<Long>> edges = new LinkedHashMap<>();
        for (var e : out.edges().entrySet()) {
            edges.put(e.getKey(), new ArrayList<>(e.getValue()));
        }

        for (var e : edges.entrySet()) {
            long from = e.getKey();
            SymbolicState fromSt = out.states().get(from);
            if (fromSt == null) continue;
            Stmt stmt = fromSt.loc().stmt();
            if (!(stmt instanceof JIfStmt ifStmt)) continue;

            MethodSignature ms = fromSt.loc().method();
            Objects.requireNonNull(ms, "methodSignature");
            JavaSootMethod m = AnalysisEnv.view().getMethod(ms).orElse(null);
            if (m == null) continue;

            BranchPruneDecision decision = decideOne(fromSt, ifStmt, opt);
            // If we can decide "if condition is true", then false-branch edge is UNSAT, and vice versa.
            if (decision.thenBranch() == SatResult.UNSAT || decision.elseBranch() == SatResult.UNSAT) {
                List<Long> succ = e.getValue();
                List<Long> kept = new ArrayList<>();
                for (Long to : succ) {
                    SymbolicState child = out.states().get(to);
                    if (child == null) continue;
                    if (decision.thenBranch() == SatResult.UNSAT && child.branchLabel() == BranchLabel.TRUE_BRANCH) {
                        continue;
                    }
                    if (decision.elseBranch() == SatResult.UNSAT && child.branchLabel() == BranchLabel.FALSE_BRANCH) {
                        continue;
                    }
                    kept.add(to);
                }
                e.setValue(kept);
            }

        }

        // Rebuild out edges from 'edges'
        ExecutionGraph pruned = new ExecutionGraph();
        for (SymbolicState st : out.states().values()) {
            pruned.addState(st);
        }
        for (var e : edges.entrySet()) {
            for (Long to : e.getValue()) {
                pruned.addEdge(e.getKey(), to);
            }
        }
        // preserve terminal/active markers
        for (var t : out.terminalStates().entrySet()) pruned.markTerminal(t.getKey(), t.getValue());
        for (var a : out.activeStates().entrySet()) pruned.markActive(a.getKey(), a.getValue());

        // Remove unreachable states to keep graph clean
        return keepReachable(pruned, entries);
    }

    private static BranchPruneDecision decideOne(SymbolicState state, JIfStmt ifStmt, Options opt) {
        Map<String, Object> env = envFromStore(state);
        SatResult cond = ConditionEvaluator.evalIf(ifStmt, env);
        if (cond == SatResult.UNKNOWN) {
            return new BranchPruneDecision(SatResult.UNKNOWN, SatResult.UNKNOWN, env, List.of());
        }
        // if condition is SAT, then-branch is feasible and else is infeasible (and vice versa)
        if (cond == SatResult.SAT) {
            return new BranchPruneDecision(SatResult.SAT, SatResult.UNSAT, env, List.of());
        }
        return new BranchPruneDecision(SatResult.UNSAT, SatResult.SAT, env, List.of());
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

    private static ExecutionGraph copyGraph(ExecutionGraph g) {
        ExecutionGraph out = new ExecutionGraph();
        for (SymbolicState st : g.states().values()) out.addState(st);
        for (var e : g.edges().entrySet()) {
            for (Long to : e.getValue()) out.addEdge(e.getKey(), to);
        }
        for (var t : g.terminalStates().entrySet()) out.markTerminal(t.getKey(), t.getValue());
        for (var a : g.activeStates().entrySet()) out.markActive(a.getKey(), a.getValue());
        return out;
    }

    private static ExecutionGraph keepReachable(ExecutionGraph g, Set<Long> entries) {
        Set<Long> seen = new LinkedHashSet<>();
        Deque<Long> dq = new ArrayDeque<>(entries);
        while (!dq.isEmpty()) {
            long id = dq.removeFirst();
            if (!seen.add(id)) continue;
            for (Long to : g.edges().getOrDefault(id, List.of())) {
                dq.add(to);
            }
        }

        ExecutionGraph out = new ExecutionGraph();
        for (Long id : seen) {
            SymbolicState st = g.states().get(id);
            if (st != null) out.addState(st);
        }
        for (Long from : seen) {
            for (Long to : g.edges().getOrDefault(from, List.of())) {
                if (seen.contains(to)) out.addEdge(from, to);
            }
        }
        for (var t : g.terminalStates().entrySet()) if (seen.contains(t.getKey())) out.markTerminal(t.getKey(), t.getValue());
        for (var a : g.activeStates().entrySet()) if (seen.contains(a.getKey())) out.markActive(a.getKey(), a.getValue());
        return out;
    }
}

