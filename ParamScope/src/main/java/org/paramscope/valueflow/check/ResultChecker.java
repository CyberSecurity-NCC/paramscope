package org.paramscope.valueflow.check;

import org.paramscope.reflection.ReflectionObject2;
import org.paramscope.slice.OneResult;
import org.paramscope.valueflow.target.ValueTarget;

public interface ResultChecker {

    /**
     * Whether the analysis should re-run the value reconstruction twice to support
     * "repeated generation" checks (e.g. array randomization, SecureRandom.nextLong).
     */
    default boolean requiresTwiceResolve(ValueTarget target) {
        return false;
    }

    CheckResult check(ValueTarget target,
                      Object[] concreteValues,
                      ReflectionObject2[] reflectionObjects,
                      OneResult pathMeta);
}

