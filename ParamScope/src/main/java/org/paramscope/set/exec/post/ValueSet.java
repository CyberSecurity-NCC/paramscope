package org.paramscope.set.exec.post;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A small value-set container for Rule4 (engineering approximation of VSA).
 */
public final class ValueSet {
    private final Set<Object> values;

    private ValueSet(Set<Object> values) {
        this.values = values;
    }

    public static ValueSet of(Set<Object> values) {
        Objects.requireNonNull(values, "values");
        return new ValueSet(Collections.unmodifiableSet(new LinkedHashSet<>(values)));
    }

    public Set<Object> values() {
        return values;
    }

    public boolean isSingleton() {
        return values.size() == 1;
    }

    public Object singletonOrNull() {
        if (values.size() != 1) return null;
        return values.iterator().next();
    }
}

