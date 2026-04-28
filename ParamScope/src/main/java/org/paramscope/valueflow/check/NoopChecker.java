package org.paramscope.valueflow.check;

import org.paramscope.reflection.ReflectionObject2;
import org.paramscope.slice.OneResult;
import org.paramscope.valueflow.target.ValueTarget;

public final class NoopChecker implements ResultChecker {

    @Override
    public CheckResult check(ValueTarget target, Object[] concreteValues, ReflectionObject2[] reflectionObjects, OneResult pathMeta) {
        return CheckResult.info("unchecked");
    }
}

