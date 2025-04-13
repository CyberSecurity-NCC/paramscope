package org.paramscope.slice;

import sootup.core.jimple.common.ref.JParameterRef;
import sootup.core.signatures.MethodSignature;

public record MethodParamRef(JParameterRef parameterRef, MethodSignature methodSignature) {
}
