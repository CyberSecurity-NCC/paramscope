package org.paramscope.set.reconstruct;

import sootup.core.signatures.MethodSignature;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reconstruction result for a single branch condition statement.
 */
public record BranchConditionReconstructionResult(
        MethodSignature methodSignature,
        int branchStmtIndex,
        String branchStmtText,
        List<String> extractedLocalNames,
        Map<String, ReconstructedValue> reconstructedLocals
) {
    public BranchConditionReconstructionResult {
        Objects.requireNonNull(methodSignature, "methodSignature");
        Objects.requireNonNull(branchStmtText, "branchStmtText");
        Objects.requireNonNull(extractedLocalNames, "extractedLocalNames");
        Objects.requireNonNull(reconstructedLocals, "reconstructedLocals");
        if (branchStmtIndex < 0) {
            throw new IllegalArgumentException("branchStmtIndex must be >= 0");
        }
    }
}

