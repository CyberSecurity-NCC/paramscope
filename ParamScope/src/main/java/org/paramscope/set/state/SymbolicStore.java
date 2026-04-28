package org.paramscope.set.state;

import org.paramscope.set.expr.SymExpr;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SymbolicStore {
    private final Map<MemoryKey, SymExpr> map;

    private SymbolicStore(Map<MemoryKey, SymExpr> map) {
        this.map = map;
    }

    public static SymbolicStore empty() {
        return new SymbolicStore(new HashMap<>());
    }

    public Optional<SymExpr> read(MemoryKey key) {
        return Optional.ofNullable(map.get(key));
    }

    public boolean contains(MemoryKey key) {
        return map.containsKey(key);
    }

    public SymbolicStore write(MemoryKey key, SymExpr value) {
        Map<MemoryKey, SymExpr> next = new HashMap<>(map);
        next.put(key, value);
        return new SymbolicStore(next);
    }

    public Map<MemoryKey, SymExpr> snapshot() {
        return Collections.unmodifiableMap(map);
    }
}

