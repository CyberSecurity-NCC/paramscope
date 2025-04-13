package org.paramscope.reflection;

import sootup.core.jimple.common.constant.*;

public class ConstantResolve {
    public static Object resolve(Constant constant) {

        if (constant instanceof StringConstant stringConstant) {
            return stringConstant.getValue();
        }

        if (constant instanceof EnumConstant enumConstant) {
            return enumConstant.getValue();
        }

        if (constant instanceof FloatConstant floatConstant) {
            return floatConstant.getValue();
        }

        if (constant instanceof DoubleConstant doubleConstant) {
            return doubleConstant.getValue();
        }

        if (constant instanceof IntConstant intConstant) {
            return intConstant.getValue();
        }

        if (constant instanceof LongConstant longConstant) {
            return longConstant.getValue();
        }

        if (constant instanceof BooleanConstant booleanConstant) {
            return booleanConstant;
        }

        if (constant instanceof ClassConstant classConstant) {
            return classConstant.getValue();
        }

        return constant;
    }

    public static Object resolve(Constant constant, Class<?> constantClass) {
        if ((constantClass == char.class || constantClass == Character.class) && constant instanceof IntConstant intConstant) {
            return (char) intConstant.getValue();
        }

        if ((constantClass == byte.class) && constant instanceof IntConstant intConstant) {
            return (byte) intConstant.getValue();
        }
        return resolve(constant);
    }
}
