package org.paramscope.valueflow.check;

import org.paramscope.api.APIParamInfo;
import org.paramscope.data.APIList;
import org.paramscope.reflection.ReflectionObject2;
import org.paramscope.slice.OneResult;
import org.paramscope.valueflow.target.ValueTarget;

import java.util.Optional;

public final class ArrayRandomizationChecker implements ResultChecker {

    @Override
    public boolean requiresTwiceResolve(ValueTarget target) {
        Optional<APIParamInfo> legacy = target.legacyApiParamInfo();
        if (legacy.isEmpty()) {
            return false;
        }
        return APIList.getTrackArrayApiParamInfoList().contains(legacy.get())
                || APIList.getTrackLongApiParamInfoList().contains(legacy.get());
    }

    @Override
    public CheckResult check(ValueTarget target, Object[] concreteValues, ReflectionObject2[] reflectionObjects, OneResult pathMeta) {
        // This checker mainly relies on repeated generation; if caller didn't run twice, just skip.
        return CheckResult.info("array/long randomization check requires repeated generation");
    }

    public CheckResult checkTwice(Object[] once, Object[] twice) {
        if (once == null || twice == null || once.length == 0 || twice.length == 0) {
            return CheckResult.info("no concrete values for repeated generation check");
        }
        for (int i = 0; i < Math.min(once.length, twice.length); i++) {
            Object a = once[i];
            Object b = twice[i];
            if (a == null || b == null) {
                continue;
            }
            if (a.getClass().isArray() && b.getClass().isArray()) {
                boolean randomized = !java.util.Objects.deepEquals(a, b);
                if (randomized) {
                    return CheckResult.info("Repeated Generation Test: Randomized");
                }
                return CheckResult.insecure("Repeated Generation Test: Constant");
            }
            if (a instanceof Long && b instanceof Long) {
                if (!a.equals(b)) {
                    return CheckResult.info("Repeated Generation Test: Randomized");
                }
                return CheckResult.insecure("Repeated Generation Test: Constant");
            }
        }
        return CheckResult.info("Repeated Generation Test: Unknown");
    }
}

