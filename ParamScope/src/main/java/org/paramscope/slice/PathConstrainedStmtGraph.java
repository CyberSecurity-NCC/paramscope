package org.paramscope.slice;

import org.paramscope.set.exec.post.PathExtractor;
import org.paramscope.set.exec.post.CfgBranchPruneReducer;
import sootup.core.graph.BasicBlock;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.basic.Trap;
import sootup.core.jimple.common.stmt.BranchingStmt;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.types.ClassType;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * A CFG overlay that constrains {@link JIfStmt} branches according to a {@link PathExtractor.PathKey}.
 *
 * <p>Only if-stmts present in the path decisions are constrained; other branches remain unconstrained.</p>
 */
@SuppressWarnings({"rawtypes", "null"})
public final class PathConstrainedStmtGraph extends StmtGraph {

    private final StmtGraph<?> delegate;
    private final Map<Integer, Boolean> decisionsByStmtId; // stmtId -> takeTrue
    private final Map<Stmt, Integer> stmtIds;
    private final Set<CfgBranchPruneReducer.EdgeKey> blockedEdges;

    private final Set<Stmt> reachable;
    private final List<Stmt> reachableStmts;
    private final List<Stmt> reachableTails;

    public PathConstrainedStmtGraph(StmtGraph<?> delegate, PathExtractor.PathKey pathKey) {
        this(delegate, pathKey, Set.of(), delegate.getStmts());
    }

    public PathConstrainedStmtGraph(StmtGraph<?> delegate,
                                    PathExtractor.PathKey pathKey,
                                    Set<CfgBranchPruneReducer.EdgeKey> blockedEdges,
                                    List<Stmt> stmtListForIds) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(pathKey, "pathKey");
        this.decisionsByStmtId = new HashMap<>();
        for (var d : pathKey.decisions()) {
            decisionsByStmtId.put(d.ifStmtId(), d.takeTrue());
        }
        this.stmtIds = buildStmtIds(stmtListForIds);
        this.blockedEdges = (blockedEdges == null) ? Set.of() : Set.copyOf(blockedEdges);
        this.reachable = computeReachable();
        this.reachableStmts = List.copyOf(this.reachable);
        this.reachableTails = computeTails();
    }

    private static Map<Stmt, Integer> buildStmtIds(List<Stmt> stmts) {
        HashMap<Stmt, Integer> m = new HashMap<>();
        if (stmts != null) {
            for (int i = 0; i < stmts.size(); i++) {
                m.put(stmts.get(i), i);
            }
        }
        return m;
    }

    @Override
    @Nonnull
    public Stmt getStartingStmt() {
        return delegate.getStartingStmt();
    }

    @Override
    @Nonnull
    public BasicBlock<?> getStartingStmtBlock() {
        return delegate.getStartingStmtBlock();
    }

    @Override
    @Nonnull
    public Collection<Stmt> getNodes() {
        return reachable;
    }

    @Override
    @Nonnull
    public Collection<? extends BasicBlock<?>> getBlocks() {
        return delegate.getBlocks();
    }

    @Override
    @Nonnull
    public List<? extends BasicBlock<?>> getBlocksSorted() {
        return delegate.getBlocksSorted();
    }

    @Override
    @Nonnull
    public BasicBlock<?> getBlockOf(@Nonnull Stmt stmt) {
        return delegate.getBlockOf(stmt);
    }

    @Override
    public boolean containsNode(@Nonnull Stmt stmt) {
        return reachable.contains(stmt);
    }

    @Override
    @Nonnull
    public List<Stmt> predecessors(@Nonnull Stmt stmt) {
        List<Stmt> preds = delegate.predecessors(stmt);
        if (preds == null || preds.isEmpty()) return List.of();
        ArrayList<Stmt> out = new ArrayList<>(preds.size());
        for (Stmt p : preds) {
            if (!reachable.contains(p)) continue;
            if (isBlocked(p, stmt)) continue;
            out.add(p);
        }
        return out;
    }

    @Override
    @Nonnull
    public List<Stmt> exceptionalPredecessors(@Nonnull Stmt stmt) {
        List<Stmt> preds = delegate.exceptionalPredecessors(stmt);
        if (preds == null || preds.isEmpty()) return List.of();
        ArrayList<Stmt> out = new ArrayList<>(preds.size());
        for (Stmt p : preds) if (reachable.contains(p)) out.add(p);
        return out;
    }

    @Override
    @Nonnull
    public List<Stmt> successors(@Nonnull Stmt stmt) {
        List<Stmt> succ = delegate.successors(stmt);
        if (succ == null || succ.isEmpty()) return List.of();
        ArrayList<Stmt> out = new ArrayList<>(succ.size());
        for (Stmt s : succ) {
            if (!reachable.contains(s)) continue;
            if (isBlocked(stmt, s)) continue;
            out.add(s);
        }
        return out;
    }

    @Override
    @Nonnull
    public Map<ClassType, Stmt> exceptionalSuccessors(@Nonnull Stmt stmt) {
        Map<ClassType, Stmt> ex = delegate.exceptionalSuccessors(stmt);
        if (ex == null || ex.isEmpty()) return Map.of();
        Map<ClassType, Stmt> out = new LinkedHashMap<>();
        for (var e : ex.entrySet()) {
            if (reachable.contains(e.getValue())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    @Override
    public int inDegree(@Nonnull Stmt stmt) {
        return predecessors(stmt).size() + exceptionalPredecessors(stmt).size();
    }

    @Override
    public int outDegree(@Nonnull Stmt stmt) {
        return successors(stmt).size() + exceptionalSuccessors(stmt).size();
    }

    @Override
    public boolean hasEdgeConnecting(@Nonnull Stmt from, @Nonnull Stmt to) {
        if (!reachable.contains(from) || !reachable.contains(to)) return false;
        if (isBlocked(from, to)) return false;
        return delegate.hasEdgeConnecting(from, to);
    }

    @Override
    @Nonnull
    public List<Trap> buildTraps() {
        return delegate.buildTraps();
    }

    @Override
    @Nonnull
    public List<Stmt> getTails() {
        return reachableTails;
    }

    @Override
    @Nonnull
    public Iterator<Stmt> iterator() {
        return reachableStmts.iterator();
    }

    @Override
    @Nonnull
    public List<Stmt> getBranchTargetsOf(BranchingStmt stmt) {
        return delegate.getBranchTargetsOf(stmt);
    }

    @Override
    public boolean isStmtBranchTarget(@Nonnull Stmt stmt) {
        return delegate.isStmtBranchTarget(stmt);
    }

    @Override
    @Nonnull
    public Collection<Stmt> getLabeledStmts() {
        return delegate.getLabeledStmts();
    }

    private boolean isBlocked(Stmt from, Stmt to) {
        if (isGloballyBlocked(from, to)) return true;
        if (!(from instanceof JIfStmt ifStmt)) return false;
        Integer id = stmtIds.get(from);
        if (id == null) return false;
        Boolean takeTrue = decisionsByStmtId.get(id);
        if (takeTrue == null) return false;

        List<Stmt> succ = delegate.successors(from);
        if (succ == null || succ.size() != 2) return false;

        Stmt trueSucc = resolveTrueSucc(ifStmt, succ);
        if (trueSucc == null) return false;
        Stmt falseSucc = (succ.get(0) == trueSucc) ? succ.get(1) : succ.get(0);

        if (takeTrue) {
            return (to == falseSucc) || to.equivTo(falseSucc);
        } else {
            return (to == trueSucc) || to.equivTo(trueSucc);
        }
    }

    private boolean isGloballyBlocked(Stmt from, Stmt to) {
        for (CfgBranchPruneReducer.EdgeKey e : blockedEdges) {
            if ((e.from() == from || e.from().equivTo(from)) && (e.to() == to || e.to().equivTo(to))) {
                return true;
            }
        }
        return false;
    }

    private Stmt resolveTrueSucc(JIfStmt ifStmt, List<Stmt> succ) {
        List<Stmt> targets = delegate.getBranchTargetsOf((BranchingStmt) ifStmt);
        if (targets == null || targets.isEmpty()) return null;
        Stmt target = targets.get(0);
        for (Stmt s : succ) {
            if (s == target || s.equivTo(target)) return s;
        }
        return null;
    }

    private Set<Stmt> computeReachable() {
        Stmt start = delegate.getStartingStmt();
        if (start == null) return Set.of();

        LinkedHashSet<Stmt> seen = new LinkedHashSet<>();
        ArrayDeque<Stmt> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);

        while (!q.isEmpty()) {
            Stmt cur = q.removeFirst();
            for (Stmt s : delegate.successors(cur)) {
                if (isBlocked(cur, s)) continue;
                if (seen.add(s)) q.addLast(s);
            }
            Map<ClassType, Stmt> ex = delegate.exceptionalSuccessors(cur);
            if (ex != null) {
                for (Stmt s : ex.values()) {
                    if (seen.add(s)) q.addLast(s);
                }
            }
        }
        return Collections.unmodifiableSet(seen);
    }

    private List<Stmt> computeTails() {
        ArrayList<Stmt> tails = new ArrayList<>();
        for (Stmt s : reachable) {
            boolean hasNormal = false;
            for (Stmt t : delegate.successors(s)) {
                if (!reachable.contains(t)) continue;
                if (isBlocked(s, t)) continue;
                hasNormal = true;
                break;
            }
            boolean hasEx = false;
            Map<ClassType, Stmt> ex = delegate.exceptionalSuccessors(s);
            if (ex != null) {
                for (Stmt t : ex.values()) {
                    if (reachable.contains(t)) {
                        hasEx = true;
                        break;
                    }
                }
            }
            if (!hasNormal && !hasEx) tails.add(s);
        }
        return List.copyOf(tails);
    }
}

