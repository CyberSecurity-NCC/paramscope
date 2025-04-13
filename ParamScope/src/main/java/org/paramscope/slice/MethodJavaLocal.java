package org.paramscope.slice;

import sootup.core.signatures.MethodSignature;
import sootup.java.core.jimple.basic.JavaLocal;

public record MethodJavaLocal(JavaLocal javaLocal, MethodSignature methodSignature) {
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MethodJavaLocal that = (MethodJavaLocal) obj;
        // When Compare SootUp Value Object, please use "equivTo" method from SootUp
        return javaLocal.equivTo(that.javaLocal) && methodSignature.equals(that.methodSignature);
    }
}
