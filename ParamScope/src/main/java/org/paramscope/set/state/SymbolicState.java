package org.paramscope.set.state;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record SymbolicState(
        long stateId,
        ProgramPoint loc,
        SymbolicStore store,
        PathCondition pathCond,
        int depth,
        Optional<Long> parentStateId,
        BranchLabel branchLabel,
        Map<LoopKey, Integer> loopCounters
) {
    public SymbolicState {
        loopCounters = Collections.unmodifiableMap(new HashMap<>(loopCounters));
    }

    public SymbolicState withLoc(ProgramPoint nextLoc, BranchLabel nextBranchLabel) {
        return new SymbolicState(stateId, nextLoc, store, pathCond, depth, parentStateId, nextBranchLabel, loopCounters);
    }

    public SymbolicState withStore(SymbolicStore nextStore) {
        return new SymbolicState(stateId, loc, nextStore, pathCond, depth, parentStateId, branchLabel, loopCounters);
    }

    public SymbolicState withPathCond(PathCondition nextPc) {
        return new SymbolicState(stateId, loc, store, nextPc, depth, parentStateId, branchLabel, loopCounters);
    }

    public SymbolicState bumpDepthAndParent(long newId, long parentId, ProgramPoint nextLoc, BranchLabel nextBranchLabel) {
        return new SymbolicState(newId, nextLoc, store, pathCond, depth + 1, Optional.of(parentId), nextBranchLabel, loopCounters);
    }

    public SymbolicState withLoopCounters(Map<LoopKey, Integer> nextCounters) {
        return new SymbolicState(stateId, loc, store, pathCond, depth, parentStateId, branchLabel, nextCounters);
    }
}

