package org.paramscope.set.cfg;

import org.paramscope.set.state.ProgramPoint;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MethodCFG {
    private final JavaSootMethod method;
    private final StmtGraph graph;
    private final List<Stmt> stmts;
    private final Map<Stmt, Integer> stmtIds;

    public MethodCFG(JavaSootMethod method) {
        this.method = method;
        this.graph = method.getBody().getStmtGraph();
        this.stmts = method.getBody().getStmts();
        this.stmtIds = new HashMap<>();
        for (int i = 0; i < stmts.size(); i++) {
            stmtIds.put(stmts.get(i), i);
        }
    }

    public MethodSignature methodSignature() {
        return method.getSignature();
    }

    public ProgramPoint entry() {
        Stmt first = stmts.get(0);
        return pointOf(first);
    }

    public ProgramPoint pointOf(Stmt stmt) {
        Integer id = stmtIds.get(stmt);
        if (id == null) {
            // fallback: should not happen if stmt came from this method body
            id = stmt.hashCode();
        }
        return new ProgramPoint(method.getSignature(), id, stmt);
    }

    public List<ProgramPoint> succ(ProgramPoint p) {
        List<?> succ = graph.successors(p.stmt());
        return succ.stream().map(o -> pointOf((Stmt) o)).toList();
    }

    public List<ProgramPoint> pred(ProgramPoint p) {
        List<?> pred = graph.predecessors(p.stmt());
        return pred.stream().map(o -> pointOf((Stmt) o)).toList();
    }

    public int stmtId(Stmt stmt) {
        return stmtIds.getOrDefault(stmt, -1);
    }
}

