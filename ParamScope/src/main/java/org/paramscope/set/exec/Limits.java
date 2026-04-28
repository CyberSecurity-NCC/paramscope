package org.paramscope.set.exec;

public record Limits(
        int maxStatesTotal,
        int maxStatesPerPoint,
        int maxDepth,
        int loopUnrollLimit
) {
    public static Limits defaults() {
        return new Limits(10_000, 50, 200, 1);
    }
}

