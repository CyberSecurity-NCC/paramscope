package org.paramscope.set.reconstruct;

import java.util.List;
import java.util.Objects;

/**
 * One reconstructed value for a Local used in a branch condition.
 */
public record ReconstructedValue(
        String localName,
        int seedStmtIndex,
        Object concreteValue,
        List<String> diagnostics
) {
    public ReconstructedValue {
        Objects.requireNonNull(localName, "localName");
        if (seedStmtIndex < 0) {
            throw new IllegalArgumentException("seedStmtIndex must be >= 0");
        }
        if (diagnostics == null) {
            diagnostics = List.of();
        }
    }
}

