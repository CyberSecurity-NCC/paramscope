package org.paramscope.set.exec.post;

import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.state.SymbolicState;
import sootup.core.jimple.common.stmt.JIfStmt;

import java.util.*;

/**
 * Rule2 (lightweight graph simplification):
 * after pruning, if an if-node has only one successor, bypass it in the graph.
 *
 * <p>Note: This is an engineering-friendly "redundant condition node elimination".</p>
 */
public final class ConditionNodeEliminator {

    private ConditionNodeEliminator() {
    }

    public static ExecutionGraph eliminate(ExecutionGraph g) {
        Objects.requireNonNull(g, "g");

        // Work on mutable copies.
        Map<Long, SymbolicState> states = new LinkedHashMap<>(g.states());
        Map<Long, List<Long>> succ = new LinkedHashMap<>();
        for (var e : g.edges().entrySet()) {
            succ.put(e.getKey(), new ArrayList<>(e.getValue()));
        }

        boolean changed;
        do {
            changed = false;
            Map<Long, List<Long>> pred = computePred(succ);

            List<Long> candidates = new ArrayList<>();
            for (var e : succ.entrySet()) {
                long sid = e.getKey();
                SymbolicState st = states.get(sid);
                if (st == null) continue;
                if (!(st.loc().stmt() instanceof JIfStmt)) continue;
                List<Long> outs = e.getValue();
                if (outs == null) continue;
                if (outs.size() == 1) {
                    candidates.add(sid);
                }
            }

            for (long ifId : candidates) {
                List<Long> outs = succ.getOrDefault(ifId, List.of());
                if (outs.size() != 1) continue;
                long onlySucc = outs.get(0);

                List<Long> ins = pred.getOrDefault(ifId, List.of());
                for (long p : ins) {
                    List<Long> ps = succ.getOrDefault(p, new ArrayList<>());
                    // replace p->ifId with p->onlySucc
                    List<Long> next = new ArrayList<>();
                    boolean replaced = false;
                    for (Long t : ps) {
                        if (t == ifId) {
                            if (onlySucc != p) {
                                next.add(onlySucc);
                            }
                            replaced = true;
                        } else {
                            next.add(t);
                        }
                    }
                    if (replaced) {
                        succ.put(p, dedupPreserveOrder(next));
                        changed = true;
                    }
                }

                // remove the if node itself
                succ.remove(ifId);
                // remove incoming edges to it already rewired; ensure no other edges point to it.
                for (var e : succ.entrySet()) {
                    e.setValue(e.getValue().stream().filter(x -> x != ifId).toList());
                }
                states.remove(ifId);
                changed = true;
            }
        } while (changed);

        // Rebuild ExecutionGraph
        ExecutionGraph out = new ExecutionGraph();
        for (SymbolicState st : states.values()) {
            out.addState(st);
        }
        for (var e : succ.entrySet()) {
            for (Long to : e.getValue()) {
                if (states.containsKey(to)) {
                    out.addEdge(e.getKey(), to);
                }
            }
        }
        // preserve terminal/active only if state still exists
        for (var t : g.terminalStates().entrySet()) if (states.containsKey(t.getKey())) out.markTerminal(t.getKey(), t.getValue());
        for (var a : g.activeStates().entrySet()) if (states.containsKey(a.getKey())) out.markActive(a.getKey(), a.getValue());
        return out;
    }

    private static Map<Long, List<Long>> computePred(Map<Long, List<Long>> succ) {
        Map<Long, List<Long>> pred = new LinkedHashMap<>();
        for (var e : succ.entrySet()) {
            long from = e.getKey();
            for (Long to : e.getValue()) {
                pred.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
            }
        }
        return pred;
    }

    private static List<Long> dedupPreserveOrder(List<Long> ids) {
        LinkedHashSet<Long> s = new LinkedHashSet<>(ids);
        return new ArrayList<>(s);
    }
}

