package org.paramscope.set.util;

import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.expr.SymExpr;
import org.paramscope.set.state.MemoryKey;
import org.paramscope.set.state.StopReason;
import org.paramscope.set.state.SymbolicState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExecutionGraphToDot {
    private ExecutionGraphToDot() {}

    public static String toDot(ExecutionGraph g) {
        return toDot(g, null, null);
    }

    /**
     * Optional focus view: when {@code focusStmtId} and {@code focusLocalName} are non-null,
     * nodes at that stmtId will print the focused local's current SymExpr.
     */
    public static String toDot(ExecutionGraph g, Integer focusStmtId, String focusLocalName) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph SET {\n");
        sb.append("  node [shape=box];\n");

        for (Map.Entry<Long, SymbolicState> e : g.states().entrySet()) {
            long id = e.getKey();
            SymbolicState st = e.getValue();

            String nodeId = "s" + id;
            StringBuilder label = new StringBuilder();
            label.append("S").append(id)
                    .append(" @ ").append(st.loc().stmtId())
                    .append("\\n")
                    .append(st.branchLabel())
                    .append(" depth=").append(st.depth());

            StopReason tr = g.terminalStates().get(id);
            StopReason ar = g.activeStates().get(id);
            if (tr != null) label.append("\\nTERMINAL: ").append(tr);
            if (ar != null) label.append("\\nACTIVE: ").append(ar);

            if (!st.loopCounters().isEmpty()) {
                label.append("\\nloops=").append(st.loopCounters());
            }

            boolean isFocusNode = focusStmtId != null && st.loc().stmtId() == focusStmtId;

            // For readability: only print full PC on focus nodes; elsewhere print a count.
            if (isFocusNode) {
                label.append("\\nPC=").append(escape(st.pathCond().constraints().toString()));
            } else {
                label.append("\\nPC#=").append(st.pathCond().constraints().size());
            }

            if (isFocusNode && focusLocalName != null) {
                Optional<SymExpr> focus = st.store().snapshot().entrySet().stream()
                        .filter(en -> en.getKey() instanceof MemoryKey.LocalKey lk && lk.localName().equals(focusLocalName))
                        .map(Map.Entry::getValue)
                        .findFirst();
                label.append("\\nFOCUS ").append(escape(focusLocalName)).append("=")
                        .append(escape(focus.map(Object::toString).orElse("<missing>")));
            }

            sb.append("  ").append(nodeId)
                    .append(" [label=\"").append(escape(label.toString())).append("\"];\n");
        }

        for (Map.Entry<Long, List<Long>> e : g.edges().entrySet()) {
            long from = e.getKey();
            for (long to : e.getValue()) {
                sb.append("  s").append(from).append(" -> s").append(to).append(";\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

