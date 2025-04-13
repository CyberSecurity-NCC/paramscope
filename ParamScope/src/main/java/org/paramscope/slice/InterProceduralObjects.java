package org.paramscope.slice;

import org.paramscope.reflection.ReflectionObject2;
import sootup.core.jimple.common.ref.JStaticFieldRef;

import java.util.HashMap;

public class InterProceduralObjects {
    HashMap<Integer, ReflectionObject2> paramObjects;
    HashMap<JStaticFieldRef, ReflectionObject2> staticFieldObjects;
    ReflectionObject2 thisObject;

    public InterProceduralObjects() {
        paramObjects = new HashMap<>();
        staticFieldObjects = new HashMap<>();
    }

    public InterProceduralObjects(InterProceduralObjects interProceduralObjects) {
        this.paramObjects = new HashMap<>(interProceduralObjects.paramObjects);
        this.staticFieldObjects = new HashMap<>(interProceduralObjects.staticFieldObjects);
        this.thisObject = interProceduralObjects.thisObject;
    }

    public HashMap<Integer, ReflectionObject2> getParamObjects() {
        return paramObjects;
    }

    public HashMap<JStaticFieldRef, ReflectionObject2> getStaticFieldObjects() {
        return staticFieldObjects;
    }

    public ReflectionObject2 getThisObject() {
        return thisObject;
    }

    public void setThisObject(ReflectionObject2 thisObject) {
        this.thisObject = thisObject;
    }


}
