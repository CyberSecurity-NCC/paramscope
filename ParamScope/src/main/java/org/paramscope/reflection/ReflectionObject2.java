package org.paramscope.reflection;

import sootup.core.types.ArrayType;
import sootup.core.types.Type;

import java.lang.reflect.Array;

public class ReflectionObject2 {
    private final String name;
    private final Type dataType;
    private Object instance;
    private Class<?> objectClass;
    private ArrayState arrayState;

    public ReflectionObject2(Type type, String name) {
        this.name = name;
        this.dataType = type;

        this.objectClass = GetClassFromType2.get(type);
    }

    public String getName() {
        return this.name;
    }

    public Class<?> getObjectClass() {
        return this.objectClass;
    }

    public void setObjectClass(Class<?> objectClass) {
        this.objectClass = objectClass;
    }

    public Object getInstance() {
        return this.instance;
    }

    public void setInstance(Object instance) {
        this.instance = instance;
    }

    public Type getDataType() {
        return this.dataType;
    }

    public ArrayState getArrayState() {
        return arrayState;
    }

    public void setArrayState(ArrayState arrayState) {
        this.arrayState = arrayState;
    }

    public void setInstance(Object instance, int index) {
        assert this.dataType instanceof ArrayType;
        if (instance == null) {
            return;
        }
        Array.set(this.instance, index, instance);
    }

    public void resetClassLoader() throws ClassNotFoundException, NullPointerException {
        this.objectClass = this.instance.getClass().getClassLoader().loadClass(this.objectClass.getName());
    }

}


