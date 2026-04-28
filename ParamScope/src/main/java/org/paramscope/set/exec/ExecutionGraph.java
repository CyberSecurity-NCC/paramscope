package org.paramscope.set.exec;

import org.paramscope.set.state.ProgramPoint;
import org.paramscope.set.state.StopReason;
import org.paramscope.set.state.SymbolicState;

import java.util.*;

public final class ExecutionGraph {
    private final Map<Long, SymbolicState> states = new LinkedHashMap<>();
    private final Map<Long, List<Long>> edges = new LinkedHashMap<>();
    private final Map<ProgramPoint, List<Long>> statesAtPoint = new LinkedHashMap<>();

    private final Map<Long, StopReason> terminalStates = new LinkedHashMap<>();
    private final Map<Long, StopReason> activeStates = new LinkedHashMap<>();

    public void addState(SymbolicState state) {
        states.put(state.stateId(), state);
        statesAtPoint.computeIfAbsent(state.loc(), k -> new ArrayList<>()).add(state.stateId());
    }

    public void addEdge(long from, long to) {
        edges.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
    }

    public void markTerminal(long stateId, StopReason reason) {
        terminalStates.put(stateId, reason);
    }

    public void markActive(long stateId, StopReason reason) {
        activeStates.put(stateId, reason);
    }

    public Map<Long, SymbolicState> states() {
        return Collections.unmodifiableMap(states);
    }

    public Map<Long, List<Long>> edges() {
        Map<Long, List<Long>> out = new LinkedHashMap<>();
        for (var e : edges.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return out;
    }

    public Map<ProgramPoint, List<Long>> statesAtPoint() {
        Map<ProgramPoint, List<Long>> out = new LinkedHashMap<>();
        for (var e : statesAtPoint.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return out;
    }

    public Map<Long, StopReason> terminalStates() {
        return Collections.unmodifiableMap(terminalStates);
    }

    public Map<Long, StopReason> activeStates() {
        return Collections.unmodifiableMap(activeStates);
    }
}

