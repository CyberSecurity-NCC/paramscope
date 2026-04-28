package org.paramscope.set.exec.post;

import java.util.List;
import java.util.Map;

/**
 * Decision for pruning a branch at one if-statement.
 */
public record BranchPruneDecision(
        SatResult thenBranch,
        SatResult elseBranch,
        Map<String, Object> reconstructedEnv,
        List<String> diagnostics
) {
}

