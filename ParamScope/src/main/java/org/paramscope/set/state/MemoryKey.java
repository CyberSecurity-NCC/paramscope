package org.paramscope.set.state;

import sootup.core.signatures.MethodSignature;
import sootup.core.types.Type;

public sealed interface MemoryKey permits MemoryKey.LocalKey, MemoryKey.ParamKey, MemoryKey.ThisKey,
        MemoryKey.StaticFieldKey, MemoryKey.InstanceFieldKey, MemoryKey.ArrayElemKey {

    Type type();

    record LocalKey(MethodSignature method, String localName, Type type) implements MemoryKey {
        @Override
        public String toString() {
            return "Local(" + localName + ")";
        }
    }

    record ParamKey(MethodSignature method, int index, Type type) implements MemoryKey {
        @Override
        public String toString() {
            return "Param(" + index + ")";
        }
    }

    record ThisKey(MethodSignature method, Type type) implements MemoryKey {
        @Override
        public String toString() {
            return "This";
        }
    }

    record StaticFieldKey(String declClass, String fieldName, Type type) implements MemoryKey {
        @Override
        public String toString() {
            return "SField(" + declClass + "." + fieldName + ")";
        }
    }

    record InstanceFieldKey(String baseAlias, String declClass, String fieldName, Type type) implements MemoryKey {
        @Override
        public String toString() {
            return "IField(" + baseAlias + "." + declClass + "." + fieldName + ")";
        }
    }

    record ArrayElemKey(String baseAlias, String index, Type type) implements MemoryKey {
        @Override
        public String toString() {
            return "Arr(" + baseAlias + ")[" + index + "]";
        }
    }
}

