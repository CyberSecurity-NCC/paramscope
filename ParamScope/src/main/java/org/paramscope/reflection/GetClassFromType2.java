package org.paramscope.reflection;

import org.paramscope.analysis.AnalysisEnv;
import sootup.core.types.ArrayType;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;
import sootup.java.core.types.JavaClassType;

public class GetClassFromType2 {

    public static Class<?> get(Type sootType) {
        if (sootType instanceof PrimitiveType.LongType) {
            return long.class;
        }
        if (sootType instanceof PrimitiveType.FloatType) {
            return float.class;
        }
        if (sootType instanceof PrimitiveType.DoubleType) {
            return double.class;
        }
        if (sootType instanceof PrimitiveType.ShortType) {
            return short.class;
        }
        if (sootType instanceof PrimitiveType.CharType) {
            return char.class;
        }
        if (sootType instanceof PrimitiveType.BooleanType) {
            return boolean.class;
        }
        if (sootType instanceof PrimitiveType.ByteType) {
            return byte.class;
        }
        if (sootType instanceof PrimitiveType.IntType) {
            return int.class;
        }
        if (sootType instanceof ArrayType arrayType) {
            Class<?> baseClass = get(arrayType.getBaseType());
            return baseClass.arrayType();
        }
        if (sootType instanceof JavaClassType classType) {
            try {
                return Class.forName(classType.getFullyQualifiedName());
            } catch (ClassNotFoundException e) {
                try {
                    ClassLoader classLoader = AnalysisEnv.ClassLoader();
                    return classLoader.loadClass(classType.getFullyQualifiedName());
                } catch (ClassNotFoundException ex) {
                    // System.out.println("INFO: ClassNotFoundException: \"" + e.getMessage() + "\"");
                    // System.out.println("    This may be caused by the lack of" + e.getMessage() + "\" when analysing, may influence the reflection process.");
                } catch (NoClassDefFoundError ex) {
                    // System.out.println("INFO: NoClassDefFoundError: \"" + e.getMessage() + "\"");
                    // System.out.println("    This may be caused by the lack of" + e.getMessage() + "\" when analysing, may influence the reflection process.");
                } catch (Error error) {
                    // System.out.println("INFO: Error: \"" + e.getMessage() + "\"");
                }
            }
        }
        return null;
    }
}
