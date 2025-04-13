package org.paramscope.call;

import com.google.gson.annotations.Expose;
import sootup.core.jimple.basic.StmtPositionInfo;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;

import java.util.Objects;

public class CallSite {
    @Expose
    private final MethodSignature caller;
    @Expose
    private final MethodSignature callee;
    @Expose
    private final StmtPositionInfo pos;
    private final Stmt invokeStmt;

    public CallSite(MethodSignature caller, MethodSignature callee, StmtPositionInfo pos, Stmt invokeStmt) {
        this.caller = caller;
        this.callee = callee;
        this.pos = pos;
        this.invokeStmt = invokeStmt;
    }

    public MethodSignature getCaller() {
        return caller;
    }

    public MethodSignature getCallee() {
        return callee;
    }

    public StmtPositionInfo getPos() {
        return pos;
    }

    public Stmt getInvokeStmt() {
        return invokeStmt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallSite callSite = (CallSite) o;
        return Objects.equals(caller, callSite.caller) && Objects.equals(callee, callSite.callee) && Objects.equals(pos, callSite.pos) && Objects.equals(invokeStmt, callSite.invokeStmt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caller, callee, pos, invokeStmt);
    }

    @Override
    public String toString() {
        return "CallSite{" +
                "caller=" + caller +
                ", callee=" + callee +
                ", pos=" + pos +
                ", invokeStmt=" + invokeStmt +
                '}';
    }
}
