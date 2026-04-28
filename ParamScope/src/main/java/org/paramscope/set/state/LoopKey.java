package org.paramscope.set.state;

public record LoopKey(int headerStmtId) {
    @Override
    public String toString() {
        return "L" + headerStmtId;
    }
}

