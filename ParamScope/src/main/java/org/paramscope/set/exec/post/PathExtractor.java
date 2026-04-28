package org.paramscope.set.exec.post;

import org.paramscope.call.CallSite;
import org.paramscope.set.state.BranchLabel;
import org.paramscope.set.state.ProgramPoint;
import org.paramscope.set.state.SymbolicState;
import org.paramscope.set.exec.ExecutionGraph;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.common.stmt.Stmt;

import java.util.*;

/**
 * Extract feasible path decisions from a (pruned) method-internal SET execution graph.
 *
 * <p>A path is represented as a list of branch decisions (ifStmtId -> takeTrue/False) along the
 * unique parent chain of a leaf state reaching the target callsite statement.</p>
 */
public final class PathExtractor {

    private PathExtractor() {}

    public record BranchDecision(int ifStmtId, boolean takeTrue) {}

    public record PathKey(List<BranchDecision> decisions) {
        public PathKey {
            decisions = List.copyOf(decisions);
        }
    }

    /**
     * Find distinct path keys that reach the callsite invoke statement.
     */
    public static List<PathKey> extractToCallsite(ExecutionGraph g, CallSite callSite, int maxPaths) {
        Objects.requireNonNull(g, "g");
        Objects.requireNonNull(callSite, "callSite");
        maxPaths = (maxPaths <= 0) ? 64 : maxPaths;

        Stmt targetStmt = callSite.getInvokeStmt();
        var targetPos = callSite.getPos() == null ? null : callSite.getPos().getStmtPosition();

        List<Long> hits = new ArrayList<>();
        for (var e : g.states().entrySet()) {
            SymbolicState st = e.getValue();
            ProgramPoint p = st.loc();
            if (p == null || p.stmt() == null) continue;
            Stmt s = p.stmt();
            if (targetPos != null) {
                if (s.getPositionInfo() == null || !Objects.equals(s.getPositionInfo().getStmtPosition(), targetPos)) {
                    continue;
                }
            }
            if (targetStmt != null && !s.equivTo(targetStmt)) continue;
            hits.add(st.stateId());
            if (hits.size() >= maxPaths * 2) break;
        }

        LinkedHashMap<String, PathKey> uniq = new LinkedHashMap<>();
        for (Long leafId : hits) {
            PathKey k = buildKeyFromLeaf(g, leafId);
            String fp = fingerprint(k);
            uniq.putIfAbsent(fp, k);
            if (uniq.size() >= maxPaths) break;
        }
        return List.copyOf(uniq.values());
    }

    private static PathKey buildKeyFromLeaf(ExecutionGraph g, long leafId) {
        ArrayList<BranchDecision> rev = new ArrayList<>();
        SymbolicState cur = g.states().get(leafId);
        while (cur != null && cur.parentStateId().isPresent()) {
            long parentId = cur.parentStateId().get();
            SymbolicState parent = g.states().get(parentId);
            if (parent != null && parent.loc() != null && parent.loc().stmt() instanceof JIfStmt) {
                BranchLabel bl = cur.branchLabel();
                if (bl == BranchLabel.TRUE_BRANCH || bl == BranchLabel.FALSE_BRANCH) {
                    rev.add(new BranchDecision(parent.loc().stmtId(), bl == BranchLabel.TRUE_BRANCH));
                }
            }
            cur = parent;
        }
        Collections.reverse(rev);
        return new PathKey(rev);
    }

    private static String fingerprint(PathKey k) {
        StringBuilder sb = new StringBuilder();
        for (BranchDecision d : k.decisions()) {
            sb.append(d.ifStmtId()).append(d.takeTrue() ? "T" : "F").append(";");
        }
        return sb.toString();
    }
}

