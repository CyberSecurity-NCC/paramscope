package org.paramscope.set.util;

import org.paramscope.set.cfg.MethodCFG;
import org.paramscope.set.state.ProgramPoint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MethodCFGToDot {
    private MethodCFGToDot() {}

    public static String toDot(MethodCFG cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CFG {\n");
        sb.append("  node [shape=box];\n");

        // BFS from entry to avoid printing unreachable noise
        Set<ProgramPoint> seen = new HashSet<>();
        Set<ProgramPoint> frontier = new HashSet<>();
        frontier.add(cfg.entry());

        while (!frontier.isEmpty()) {
            ProgramPoint p = frontier.iterator().next();
            frontier.remove(p);
            if (!seen.add(p)) continue;

            String nodeId = "n" + p.stmtId();
            String label = escape(p.stmtId() + ": " + p.stmt().toString());
            sb.append("  ").append(nodeId).append(" [label=\"").append(label).append("\"];\n");

            List<ProgramPoint> succ = cfg.succ(p);
            for (ProgramPoint s : succ) {
                String sid = "n" + s.stmtId();
                sb.append("  ").append(nodeId).append(" -> ").append(sid).append(";\n");
                if (!seen.contains(s)) frontier.add(s);
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

