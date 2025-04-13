package org.paramscope.slice;

import org.paramscope.reflection.ReflectionObject2;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.ref.*;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.jimple.basic.JavaLocal;

import java.lang.reflect.Field;
import java.util.HashMap;

public class FocusedValueObjects {
    MethodSignature methodSignature;
    InterProceduralObjects interProceduralObjects;
    HashMap<MethodJavaLocal, ReflectionObject2> localObjects;
    HashMap<JStaticFieldRef, ReflectionObject2> staticFieldObjects;
    HashMap<JInstanceFieldRef, ReflectionObject2> instanceFieldObjects;
    HashMap<JArrayRef, ReflectionObject2> arrayObjects;

    public FocusedValueObjects() {
        this.localObjects = new HashMap<>();
        this.staticFieldObjects = new HashMap<>();
        this.instanceFieldObjects = new HashMap<>();
        this.arrayObjects = new HashMap<>();
    }

    public FocusedValueObjects(MethodSignature methodSignature, InterProceduralObjects interProceduralObjects) {
        this.methodSignature = methodSignature;
        this.interProceduralObjects = new InterProceduralObjects(interProceduralObjects);
        this.localObjects = new HashMap<>();
        this.staticFieldObjects = new HashMap<>();
        this.staticFieldObjects.putAll(interProceduralObjects.getStaticFieldObjects());
        this.instanceFieldObjects = new HashMap<>();
        this.arrayObjects = new HashMap<>();
    }

    public FocusedValueObjects(FocusedValueObjects focusedValueObjects) {
        this.methodSignature = focusedValueObjects.methodSignature;
        this.interProceduralObjects = focusedValueObjects.interProceduralObjects;
        this.localObjects = new HashMap<>(focusedValueObjects.localObjects);
        this.staticFieldObjects = new HashMap<>(focusedValueObjects.staticFieldObjects);
        this.instanceFieldObjects = new HashMap<>(focusedValueObjects.instanceFieldObjects);
        this.arrayObjects = new HashMap<>(focusedValueObjects.arrayObjects);
    }

    public HashMap<MethodJavaLocal, ReflectionObject2> getLocalObjects() {
        return localObjects;
    }

    public HashMap<JStaticFieldRef, ReflectionObject2> getStaticFieldObjects() {
        return staticFieldObjects;
    }

    public HashMap<JInstanceFieldRef, ReflectionObject2> getInstanceFieldObjects() {
        return instanceFieldObjects;
    }

    public HashMap<JArrayRef, ReflectionObject2> getArrayObjects() {
        return arrayObjects;
    }

    public void putValue(Value value, ReflectionObject2 reflectionObject) {
        if (this.contains(value)) {
            if (value instanceof Local local) {
                try {
                    this.localObjects.put(new MethodJavaLocal((JavaLocal) local, this.methodSignature), reflectionObject);
                } catch (ClassCastException e) {
                    // System.out.println("INFO: ClassCastException e: \"" + e.getMessage() + "\", local: " + local);
                }
            }
            if (value instanceof JStaticFieldRef staticFieldRef) {
                this.staticFieldObjects.put(getEquivSF(staticFieldRef), reflectionObject);
            }
            if (value instanceof JInstanceFieldRef instanceFieldRef) {
                this.instanceFieldObjects.put(getEquivIF(instanceFieldRef), reflectionObject);
            }
            if (value instanceof JArrayRef arrayRef) {
//                this.arrayObjects.put(getEquivAR(arrayRef), reflectionObject);
                this.arrayObjects.put(arrayRef, reflectionObject);
            }
        } else {
            if (value instanceof Local local) {
                try {
                    this.localObjects.put(new MethodJavaLocal((JavaLocal) local, this.methodSignature), reflectionObject);
                } catch (ClassCastException e) {
                    // System.out.println("INFO: ClassCastException e: \"" + e.getMessage() + "\", local: " + local);
                }
            }
            if (value instanceof JStaticFieldRef staticFieldRef) {
                this.staticFieldObjects.put(staticFieldRef, reflectionObject);
            }
            if (value instanceof JInstanceFieldRef instanceFieldRef) {
                this.instanceFieldObjects.put(instanceFieldRef, reflectionObject);
            }
            if (value instanceof JArrayRef arrayRef) {
                this.arrayObjects.put(arrayRef, reflectionObject);
            }
        }
    }

    public void setMethodSignature(MethodSignature methodSignature) {
        this.methodSignature = methodSignature;
    }

    // public Object getObjInstance(MethodJavaLocal methodJavaLocal){
    //     return localObjects.get(methodJavaLocal).getInstance();
    // }

    // public Object getObjInstance(Value val){
    //     if(val instanceof JStaticFieldRef staticFieldRef){
    //         if(this.contains(staticFieldRef)){
    //             return staticFieldObjects.get(getEquivSF(staticFieldRef)).getInstance();
    //         }else{
    //             this.staticFieldObjects.put(staticFieldRef, new ReflectionObject2(staticFieldRef.getType(), staticFieldRef.getFieldSignature().toString()));
    //             return staticFieldObjects.get(staticFieldRef).getInstance();
    //         }

    //     }
    //     if(val instanceof JInstanceFieldRef instanceFieldRef){
    //         if(this.contains(instanceFieldRef)){
    //             return instanceFieldObjects.get(getEquivIF(instanceFieldRef)).getInstance();
    //         }else{
    //             this.instanceFieldObjects.put(instanceFieldRef, new ReflectionObject2(instanceFieldRef.getType(), instanceFieldRef.getFieldSignature().toString()));
    //             return instanceFieldObjects.get(instanceFieldRef).getInstance();
    //         }
    //     }
    //     if(val instanceof JArrayRef arrayRef){
    //         if(this.contains(arrayRef)){
    //             return arrayObjects.get(getEquivAR(arrayRef)).getInstance();
    //         }else{
    //             this.arrayObjects.put(arrayRef, new ReflectionObject2(arrayRef.getType(), arrayRef.toString()));
    //             return arrayObjects.get(arrayRef).getInstance();
    //         }
    //     }
    //     if(val instanceof JThisRef && interProceduralObjects.getThisObject().getInstance() != null){
    //         return interProceduralObjects.getThisObject().getInstance();
    //     }
    //     if(val instanceof JParameterRef parameterRef && interProceduralObjects.getParamObjects().containsKey(parameterRef.getIndex())){
    //         return interProceduralObjects.getParamObjects().get(parameterRef.getIndex()).getInstance();
    //     }
    //     return null;
    // }

    public ReflectionObject2 getReflectionObject(JavaLocal local) {
        return getReflectionObject(new MethodJavaLocal(local, methodSignature));
    }

    public ReflectionObject2 getReflectionObject(MethodJavaLocal local) {
        if (this.localObjects.containsKey(local)) {
            return this.localObjects.get(local);
        } else {
            ReflectionObject2 reflectionObject2 = new ReflectionObject2(local.javaLocal().getType(), local.javaLocal().getName());
            this.localObjects.put(local, reflectionObject2);
            return reflectionObject2;
        }
    }

    public ReflectionObject2 getReflectionObject(Value val) {
        if (val instanceof Local) {
            return getReflectionObject((JavaLocal) val);
        }
        if (val instanceof JStaticFieldRef staticFieldRef) {
            if (this.contains(staticFieldRef)) {
                return staticFieldObjects.get(getEquivSF(staticFieldRef));
            } else {
                this.staticFieldObjects.put(staticFieldRef, new ReflectionObject2(staticFieldRef.getType(), staticFieldRef.getFieldSignature().toString()));
                return staticFieldObjects.get(staticFieldRef);
            }

        }
        if (val instanceof JInstanceFieldRef instanceFieldRef) {
            if (this.contains(instanceFieldRef)) {
                return instanceFieldObjects.get(getEquivIF(instanceFieldRef));
            } else {
                if (this.contains(instanceFieldRef.getBase())) {
                    ReflectionObject2 baseObject;
                    if (instanceFieldRef.getBase().getName().equals("this")) {
                        baseObject = interProceduralObjects.getThisObject();
                    } else {
                        baseObject = this.localObjects.get(new MethodJavaLocal((JavaLocal) instanceFieldRef.getBase(), methodSignature));
                    }

                    try {
                        baseObject.resetClassLoader();
                        Field field = baseObject.getObjectClass().getDeclaredField(instanceFieldRef.getFieldSignature().getName());
                        field.setAccessible(true);
                        Object instanceFieldValue = field.get(baseObject.getInstance());
                        ReflectionObject2 instanceFieldObject = new ReflectionObject2(instanceFieldRef.getType(), instanceFieldRef.toString());
                        instanceFieldObject.setInstance(instanceFieldValue);
                        this.instanceFieldObjects.put(instanceFieldRef, instanceFieldObject);
                        return instanceFieldObject;
                    } catch (NoSuchFieldException | SecurityException e) {
                        // System.out.println("NoSuchFieldException | SecurityException e");
                    } catch (IllegalArgumentException | IllegalAccessException | ClassNotFoundException e) {
                        // System.out.println("IllegalArgumentException | IllegalAccessException |ClassNotFoundException e");
                    } catch (NullPointerException e) {
                        // System.out.println("INFO: baseObject: \"" + baseObject.getName() + "\" is null");
                    }
                } else {
                    this.instanceFieldObjects.put(instanceFieldRef, new ReflectionObject2(instanceFieldRef.getType(), instanceFieldRef.getFieldSignature().toString()));
                    return instanceFieldObjects.get(instanceFieldRef);
                }
            }
        }
        if (val instanceof JArrayRef arrayRef) {
            if (this.contains(arrayRef)) {
                // improvement: didn't deal with multi-dimension array.
                return getReflectionObject(arrayRef.getBase());
            } else {
                this.arrayObjects.put(arrayRef, new ReflectionObject2(arrayRef.getType(), arrayRef.toString()));
                return arrayObjects.get(arrayRef);
            }
        }
        if (val instanceof JThisRef && interProceduralObjects.getThisObject().getInstance() != null) {
            return interProceduralObjects.getThisObject();
        }
        if (val instanceof JParameterRef parameterRef && interProceduralObjects.getParamObjects().containsKey(parameterRef.getIndex())) {
            return interProceduralObjects.getParamObjects().get(parameterRef.getIndex());
        }

        ReflectionObject2 reflectionObject = new ReflectionObject2(val.getType(), val.toString());
        this.putValue(val, reflectionObject);
        return reflectionObject;
    }

    public boolean contains(Value val) {
        if (val instanceof Local local) {
            if (local.getName().equals("this") && interProceduralObjects.getThisObject() != null) {
                return true;
            }
            try {
                return localObjects.containsKey(new MethodJavaLocal((JavaLocal) local, this.methodSignature));
            } catch (ClassCastException e) {
                return false;
            }
        }
        if (val instanceof JStaticFieldRef staticFieldRefVal) {
            for (JStaticFieldRef staticFieldRef : staticFieldObjects.keySet()) {
                if (staticFieldRef.equivTo(staticFieldRefVal)) {
                    return true;
                }
            }
        }
        if (val instanceof JInstanceFieldRef instanceFieldRefVal) {
            for (JInstanceFieldRef instanceFieldRef : instanceFieldObjects.keySet()) {
                if (instanceFieldRef.equivTo(instanceFieldRefVal)) {
                    return true;
                }
            }
        }
        if (val instanceof JArrayRef arrayRefVal) {
            for (JArrayRef arrayRef : arrayObjects.keySet()) {
                if (arrayRef.equivTo(arrayRefVal)) {
                    return true;
                }
            }
            return contains(arrayRefVal.getBase());
        }
        return false;
    }

    public JStaticFieldRef getEquivSF(JStaticFieldRef staticFieldRefVal) {
        for (JStaticFieldRef staticFieldRef : staticFieldObjects.keySet()) {
            if (staticFieldRef.equivTo(staticFieldRefVal)) {
                return staticFieldRef;
            }
        }
        return null;
    }

    public JInstanceFieldRef getEquivIF(JInstanceFieldRef instanceFieldRefVal) {
        for (JInstanceFieldRef instanceFieldRef : instanceFieldObjects.keySet()) {
            if (instanceFieldRef.equivTo(instanceFieldRefVal)) {
                return instanceFieldRef;
            }
        }
        return null;
    }

//    public JArrayRef getEquivAR(JArrayRef arrayRefVal){
//        for(JArrayRef arrayRef: arrayObjects.keySet()){
//            if(arrayRef.equivTo(arrayRefVal)){
//                return arrayRef;
//            }
//        }
//        return null;
//    }
}
