package org.paramscope.set.exec;

import org.paramscope.set.cfg.MethodCFG;
import org.paramscope.set.state.*;
import sootup.core.jimple.common.stmt.JReturnStmt;
import sootup.core.jimple.common.stmt.JReturnVoidStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.core.JavaSootMethod;

import java.util.*;

public final class SETBuilder {
    private final TraversalStrategy strategy;
    private final Limits limits;
    private final TransferFunction transfer;
    private final LoopPolicy loopPolicy;

    private long nextStateId = 1;

    public SETBuilder(TraversalStrategy strategy, Limits limits) {
        this.strategy = strategy;
        this.limits = limits;
        this.transfer = new TransferFunction();
        this.loopPolicy = new LoopPolicy();
    }

    public ExecutionGraph build(JavaSootMethod method) {
        MethodCFG cfg = new MethodCFG(method);
        ExecutionGraph g = new ExecutionGraph();

        SymbolicState init = new SymbolicState(
                nextStateId++,
                cfg.entry(),
                SymbolicStore.empty(),
                PathCondition.empty(),
                0,
                Optional.empty(),
                BranchLabel.ENTRY,
                Map.of()
        );
        g.addState(init);

        Deque<Long> work = new ArrayDeque<>();
        work.add(init.stateId());

        Map<ProgramPoint, Integer> statesPerPoint = new HashMap<>();
        statesPerPoint.put(init.loc(), 1);

        while (!work.isEmpty()) {
            long sid = pop(work);
            SymbolicState state = g.states().get(sid);
            if (state == null) continue;

            if (state.depth() >= limits.maxDepth()) {
                g.markActive(sid, StopReason.DEPTH_LIMIT);
                continue;
            }
            if (g.states().size() >= limits.maxStatesTotal()) {
                g.markActive(sid, StopReason.STATE_LIMIT);
                continue;
            }

            Stmt stmt = state.loc().stmt();
            if (stmt instanceof JReturnStmt || stmt instanceof JReturnVoidStmt) {
                g.markTerminal(sid, StopReason.RETURN);
                continue;
            }

            List<ProgramPoint> succ = cfg.succ(state.loc());
            List<TransferFunction.BranchTransition> transitions;
            try {
                transitions = transfer.transfer(cfg, state);
            } catch (Exception e) {
                g.markActive(sid, StopReason.INTERNAL_ERROR);
                continue;
            }

            if (succ.isEmpty()) {
                g.markTerminal(sid, StopReason.RETURN);
                continue;
            }

            // Map transitions to successors.
            // For JIfStmt we expect up to 2 transitions and 2 successors.
            if (transitions.isEmpty()) {
                // terminal already handled
                continue;
            }

            if (transitions.size() == 1) {
                var tr = transitions.get(0);
                if ((tr.branchLabel() == BranchLabel.TRUE_BRANCH || tr.branchLabel() == BranchLabel.FALSE_BRANCH) && succ.size() >= 2) {
                    ProgramPoint cur = state.loc();
                    ProgramPoint fallthrough = succ.stream()
                            .filter(p -> p.stmtId() == cur.stmtId() + 1)
                            .findFirst()
                            .orElse(succ.get(0));
                    ProgramPoint target = succ.stream()
                            .filter(p -> p.stmtId() != fallthrough.stmtId())
                            .findFirst()
                            .orElse(succ.get(1));
                    ProgramPoint to = (tr.branchLabel() == BranchLabel.TRUE_BRANCH) ? target : fallthrough;
                    addNext(cfg, g, work, statesPerPoint, state, sid, to, tr);
                } else {
                    ProgramPoint to = succ.get(0);
                    addNext(cfg, g, work, statesPerPoint, state, sid, to, tr);
                }
            } else {
                // For JIfStmt, successor ordering is not guaranteed; map by target stmt.
                if (transitions.stream().anyMatch(tr -> tr.branchLabel() == BranchLabel.TRUE_BRANCH)
                        && transitions.stream().anyMatch(tr -> tr.branchLabel() == BranchLabel.FALSE_BRANCH)
                        && succ.size() >= 2) {
                    ProgramPoint cur = state.loc();
                    ProgramPoint fallthrough = succ.stream()
                            .filter(p -> p.stmtId() == cur.stmtId() + 1)
                            .findFirst()
                            .orElse(succ.get(0));
                    ProgramPoint target = succ.stream()
                            .filter(p -> p.stmtId() != fallthrough.stmtId())
                            .findFirst()
                            .orElse(succ.get(1));

                    TransferFunction.BranchTransition trTrue = transitions.stream()
                            .filter(t -> t.branchLabel() == BranchLabel.TRUE_BRANCH)
                            .findFirst().orElse(null);
                    TransferFunction.BranchTransition trFalse = transitions.stream()
                            .filter(t -> t.branchLabel() == BranchLabel.FALSE_BRANCH)
                            .findFirst().orElse(null);

                    if (trTrue != null) addNext(cfg, g, work, statesPerPoint, state, sid, target, trTrue);
                    if (trFalse != null) addNext(cfg, g, work, statesPerPoint, state, sid, fallthrough, trFalse);
                } else {
                    for (int i = 0; i < transitions.size() && i < succ.size(); i++) {
                        ProgramPoint to = succ.get(i);
                        addNext(cfg, g, work, statesPerPoint, state, sid, to, transitions.get(i));
                    }
                }
            }
        }

        return g;
    }

    private void addNext(
            MethodCFG cfg,
            ExecutionGraph g,
            Deque<Long> work,
            Map<ProgramPoint, Integer> statesPerPoint,
            SymbolicState cur,
            long curId,
            ProgramPoint to,
            TransferFunction.BranchTransition tr
    ) {
        if (!loopPolicy.shouldAllowEdge(cfg, cur, cur.loc(), to, limits)) {
            g.markActive(curId, StopReason.LOOP_LIMIT);
            return;
        }

        int countAt = statesPerPoint.getOrDefault(to, 0);
        if (countAt >= limits.maxStatesPerPoint()) {
            g.markActive(curId, StopReason.STATE_LIMIT);
            return;
        }

        long nid = nextStateId++;
        SymbolicState next = new SymbolicState(
                nid,
                to,
                tr.store(),
                tr.pathCond(),
                cur.depth() + 1,
                Optional.of(curId),
                tr.branchLabel(),
                cur.loopCounters()
        );
        next = loopPolicy.onTakeEdge(next, cur.loc(), to);

        g.addState(next);
        g.addEdge(curId, nid);
        statesPerPoint.put(to, countAt + 1);
        work.add(nid);
    }

    private long pop(Deque<Long> work) {
        return strategy == TraversalStrategy.BFS ? work.removeFirst() : work.removeLast();
    }
}

