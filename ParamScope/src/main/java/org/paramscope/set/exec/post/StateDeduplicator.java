package org.paramscope.set.exec.post;

import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.expr.SymExpr;
import org.paramscope.set.state.PathCondition;
import org.paramscope.set.state.ProgramPoint;
import org.paramscope.set.state.SymbolicState;
import org.paramscope.set.state.SymbolicStore;

import java.util.*;

/**
 * Rule3 (weak form): deduplicate/merge states at the same ProgramPoint
 * using syntactic fingerprints of Store and PathCondition.
 *
 * <p>This does NOT attempt SMT-based implication; it only supports:
 * - exact-equality dedup
 * - store-equal and PC-subset subsumption (set containment on constraint strings)</p>
 */
public final class StateDeduplicator {

    private StateDeduplicator() {
    }

    public static ExecutionGraph dedup(ExecutionGraph g) {
        Objects.requireNonNull(g, "g");

        Map<Long, SymbolicState> states = new LinkedHashMap<>(g.states());
        Map<Long, List<Long>> succ = new LinkedHashMap<>();
        for (var e : g.edges().entrySet()) succ.put(e.getKey(), new ArrayList<>(e.getValue()));

        // group by ProgramPoint
        Map<ProgramPoint, List<Long>> byPoint = new LinkedHashMap<>();
        for (SymbolicState st : states.values()) {
            byPoint.computeIfAbsent(st.loc(), k -> new ArrayList<>()).add(st.stateId());
        }

        Map<Long, Long> redirect = new HashMap<>(); // old -> kept

        for (var e : byPoint.entrySet()) {
            List<Long> ids = e.getValue();
            if (ids.size() <= 1) continue;

            // First pass: exact fingerprint dedup.
            Map<StateKey, Long> first = new LinkedHashMap<>();
            for (Long id : ids) {
                SymbolicState st = states.get(id);
                if (st == null) continue;
                StateKey k = StateKey.of(st.store(), st.pathCond());
                if (!first.containsKey(k)) {
                    first.put(k, id);
                } else {
                    redirect.put(id, first.get(k));
                }
            }

            // Second pass: store-equal PC-subset subsumption (very weak).
            // Among remaining representatives per store, keep the weakest PC (smallest constraint set).
            Map<String, Long> bestPerStore = new LinkedHashMap<>();
            for (Long id : new ArrayList<>(first.values())) {
                SymbolicState st = states.get(id);
                if (st == null) continue;
                String sfp = StateKey.storeFingerprint(st.store());
                Long curBest = bestPerStore.get(sfp);
                if (curBest == null) {
                    bestPerStore.put(sfp, id);
                } else {
                    SymbolicState best = states.get(curBest);
                    if (best == null) {
                        bestPerStore.put(sfp, id);
                        continue;
                    }
                    Set<String> pcA = StateKey.pcSet(st.pathCond());
                    Set<String> pcB = StateKey.pcSet(best.pathCond());
                    // If A ⊆ B, then A is weaker/equal; keep A and redirect B.
                    if (pcA.containsAll(pcB) && !pcB.containsAll(pcA)) {
                        // B ⊂ A, keep B (already best)
                        redirect.put(id, curBest);
                    } else if (pcB.containsAll(pcA) && !pcA.containsAll(pcB)) {
                        // A ⊂ B, replace best with A
                        redirect.put(curBest, id);
                        bestPerStore.put(sfp, id);
                    }
                }
            }
        }

        if (redirect.isEmpty()) {
            return g;
        }

        // Apply redirections on edges
        for (var e : succ.entrySet()) {
            List<Long> next = new ArrayList<>();
            for (Long to : e.getValue()) {
                long t = chase(redirect, to);
                next.add(t);
            }
            succ.put(e.getKey(), dedupPreserveOrder(next));
        }
        // Also redirect from-nodes if needed (if a redirected node still has outgoing edges)
        Map<Long, List<Long>> succ2 = new LinkedHashMap<>();
        for (var e : succ.entrySet()) {
            long from = chase(redirect, e.getKey());
            succ2.computeIfAbsent(from, k -> new ArrayList<>()).addAll(e.getValue());
        }
        for (var e : succ2.entrySet()) {
            succ2.put(e.getKey(), dedupPreserveOrder(e.getValue()));
        }

        // Remove redirected states
        Set<Long> removed = new HashSet<>(redirect.keySet());
        for (Long id : removed) states.remove(id);

        // Rebuild ExecutionGraph
        ExecutionGraph out = new ExecutionGraph();
        for (SymbolicState st : states.values()) out.addState(st);
        for (var e : succ2.entrySet()) {
            if (!states.containsKey(e.getKey())) continue;
            for (Long to : e.getValue()) {
                if (states.containsKey(to)) out.addEdge(e.getKey(), to);
            }
        }
        for (var t : g.terminalStates().entrySet()) if (states.containsKey(t.getKey())) out.markTerminal(t.getKey(), t.getValue());
        for (var a : g.activeStates().entrySet()) if (states.containsKey(a.getKey())) out.markActive(a.getKey(), a.getValue());
        return out;
    }

    private static long chase(Map<Long, Long> redirect, long id) {
        long cur = id;
        for (int i = 0; i < 8; i++) {
            Long nxt = redirect.get(cur);
            if (nxt == null || nxt == cur) return cur;
            cur = nxt;
        }
        return cur;
    }

    private static List<Long> dedupPreserveOrder(List<Long> ids) {
        LinkedHashSet<Long> s = new LinkedHashSet<>(ids);
        return new ArrayList<>(s);
    }

    private record StateKey(String storeFp, Set<String> pcFp) {
        static StateKey of(SymbolicStore store, PathCondition pc) {
            return new StateKey(storeFingerprint(store), pcSet(pc));
        }

        static String storeFingerprint(SymbolicStore store) {
            // stable-ish: sort by key string
            List<String> parts = new ArrayList<>();
            for (var e : store.snapshot().entrySet()) {
                parts.add(e.getKey() + "=" + e.getValue());
            }
            parts.sort(String::compareTo);
            return String.join("|", parts);
        }

        static Set<String> pcSet(PathCondition pc) {
            Set<String> s = new LinkedHashSet<>();
            for (SymExpr c : pc.constraints()) {
                s.add(String.valueOf(c));
            }
            return s;
        }
    }
}

