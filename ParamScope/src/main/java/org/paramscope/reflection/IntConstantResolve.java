package org.paramscope.reflection;

import sootup.core.jimple.common.constant.IntConstant;

public class IntConstantResolve {
    public static Object cast(IntConstant intConstant, Class<?> classType) {
        if (classType == byte.class) {
            return (byte) intConstant.getValue();
        } else if (classType == char.class) {
            return (char) intConstant.getValue();
        } else if (classType == short.class) {
            return (short) intConstant.getValue();
        } else {
            return intConstant.getValue();
        }
    }
}
