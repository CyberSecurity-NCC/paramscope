package org.paramscope.set.state;

public enum StopReason {
    RETURN,
    REACHED_TARGET,
    DEPTH_LIMIT,
    STATE_LIMIT,
    LOOP_LIMIT,
    UNSUPPORTED_STMT,
    INTERNAL_ERROR
}

