package org.paramscope.valueflow.tools;

import org.paramscope.call.CallSite;
import org.paramscope.slice.IntraResultNode;
import org.paramscope.slice.OneResult;
import org.paramscope.valueflow.ValueFlowResolvedPath;
import org.paramscope.valueflow.ValueFlowResultTree;
import sootup.core.jimple.common.stmt.Stmt;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

final class ValueFlowTreeToDot {

    private ValueFlowTreeToDot() {
    }

    static String toDot(ValueFlowResultTree tree) {
        if (tree == null || tree.getRoot() == null) {
            return "digraph Tree { }";
        }

        StringBuilder dot = new StringBuilder();
        dot.append("digraph Tree {\n");
        dot.append("    labelloc=a\n");
        dot.append("    node [shape=box]\n\n");

        Queue<IntraResultNode> queue = new LinkedList<>();
        queue.add(tree.getRoot());

        while (!queue.isEmpty()) {
            IntraResultNode node = queue.poll();
            IntraResult2Dot nodeDOT = new IntraResult2Dot(node);
            dot.append(nodeDOT.dot);
            for (CallSite callSite : node.getCallerResults().keySet()) {
                IntraResultNode callerNode = node.getCallerResults().get(callSite);
                IntraResult2Dot callerNodeDOT = new IntraResult2Dot(callerNode);
                dot.append("\n");
                dot.append("    ").append(callerNodeDOT.startHash).append(" -> ").append(nodeDOT.lastHash).append("\n\n");
                queue.add(callerNode);
            }
        }

        dot.append("\n");
        dot.append("    label = \"Target: ").append(escape(String.valueOf(tree.getTarget()))).append("\\nResults:[ ");
        Iterator<ValueFlowResolvedPath> it = tree.getResolved().iterator();
        while (it.hasNext()) {
            ValueFlowResolvedPath rp = it.next();
            OneResult oneResult = rp.path();
            dot.append(" [");
            Object[] vals = rp.concreteValues();
            if (vals != null) {
                for (Object obj : vals) {
                    if (obj == null) {
                        dot.append("\\\"null\\\"");
                        if (oneResult.getNullReason() != null) {
                            dot.append("(\\\"").append(oneResult.getNullReason()).append("\\\")");
                        }
                    } else {
                        dot.append("\\\"").append(String.valueOf(obj)).append("\\\"");
                    }
                }
            }
            dot.append(rp.securityInfo());
            dot.append(" ]");
            if (it.hasNext()) {
                dot.append(", ");
            }
        }
        dot.append(" ]\"\n");
        dot.append("}\n");
        return dot.toString();
    }

    private static final class IntraResult2Dot {
        final String startHash;
        final String lastHash;
        final String dot;

        IntraResult2Dot(IntraResultNode node) {
            String methodSignature = node.getIntraResult().getCallSite().getCaller().toString();
            StringBuilder b = new StringBuilder();

            String clusterId = String.valueOf(methodSignature.hashCode() & 0x7FFFFFFF);
            b.append("    subgraph cluster_").append(clusterId).append(" {\n");
            b.append("        label = \"").append(escape(methodSignature)).append("\"\n");

            String prev = null;
            for (Stmt stmt : node.getIntraResult().getResultStmts()) {
                String id = "n" + (stmt.toString().hashCode() & 0x7FFFFFFF) + "_" + clusterId;
                b.append("        ").append(id).append(" [label=\"").append(escape(stmt.toString())).append("\"]\n");
                if (prev != null) {
                    b.append("        ").append(prev).append(" -> ").append(id).append("\n");
                }
                prev = id;
            }
            if (prev == null) {
                prev = "empty_" + clusterId;
                b.append("        ").append(prev).append(" [label=\"(empty)\"]\n");
            }
            b.append("    }\n");

            this.startHash = prev;
            this.lastHash = prev;
            this.dot = b.toString();
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

