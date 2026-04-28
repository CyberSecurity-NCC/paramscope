package org.paramscope.set.state;

import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;

public record ProgramPoint(MethodSignature method, int stmtId, Stmt stmt) {
    @Override
    public String toString() {
        return stmtId + ":" + stmt;
    }
}

