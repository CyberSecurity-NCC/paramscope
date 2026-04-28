package org.paramscope.valueflow.target;

import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import sootup.core.signatures.MethodSignature;

import java.util.List;
import java.util.Optional;

/**
 * Target a specific Local at a specific Stmt index (0-based) within a method body.
 *
 * <p>Callers are expected to build {@link #callSite()} as a pseudo callsite whose
 * caller == callee == target method signature, and invokeStmt == the target stmt.</p>
 */
public record StmtLocalTarget(
        CallSite callSite,
        MethodSignature calleeSignature,
        int stmtIndex,
        String localName,
        Optional<String> stmtTextHash,
        Optional<APIParamInfo> legacyApiParamInfo
) implements ValueTarget {

    public StmtLocalTarget {
        if (stmtIndex < 0) {
            throw new IllegalArgumentException("stmtIndex must be >= 0");
        }
        if (localName == null) {
            localName = "";
        }
        if (stmtTextHash == null) {
            stmtTextHash = Optional.empty();
        }
        if (legacyApiParamInfo == null) {
            legacyApiParamInfo = Optional.empty();
        }
    }

    @Override
    public ValueTargetKind kind() {
        return ValueTargetKind.STMT_LOCAL;
    }

    @Override
    public List<Integer> argIndices() {
        return List.of();
    }

    @Override
    public boolean trackBase() {
        return false;
    }
}

