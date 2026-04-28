package org.paramscope.set.exec;

import org.paramscope.set.cfg.MethodCFG;
import org.paramscope.set.state.LoopKey;
import org.paramscope.set.state.ProgramPoint;
import org.paramscope.set.state.SymbolicState;

import java.util.HashMap;
import java.util.Map;

public final class LoopPolicy {
    public boolean shouldAllowEdge(MethodCFG cfg, SymbolicState state, ProgramPoint from, ProgramPoint to, Limits limits) {
        // Simple heuristic: treat backward stmtId edges as loop edges
        if (to.stmtId() <= from.stmtId()) {
            LoopKey loopKey = new LoopKey(to.stmtId());
            int count = state.loopCounters().getOrDefault(loopKey, 0);
            return count < limits.loopUnrollLimit();
        }
        return true;
    }

    public SymbolicState onTakeEdge(SymbolicState state, ProgramPoint from, ProgramPoint to) {
        if (to.stmtId() <= from.stmtId()) {
            LoopKey loopKey = new LoopKey(to.stmtId());
            Map<LoopKey, Integer> next = new HashMap<>(state.loopCounters());
            next.put(loopKey, next.getOrDefault(loopKey, 0) + 1);
            return state.withLoopCounters(next);
        }
        return state;
    }
}

