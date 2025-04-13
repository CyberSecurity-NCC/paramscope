package org.paramscope.slice;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.reflection.ConstantResolve;
import org.paramscope.reflection.GetClassFromType2;
import org.paramscope.reflection.IntConstantResolve;
import org.paramscope.reflection.ReflectionObject2;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JNewArrayExpr;
import sootup.core.jimple.common.expr.JSpecialInvokeExpr;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JFieldRef;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.JInvokeStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.FieldSubSignature;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.jimple.basic.JavaLocal;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

public class StaticFieldRefTracker {
    JFieldRef fieldRef;
    // Type fieldType;
    ClassType declaringClassType;

    private Object initObj = null;
    private SFState initObjState = SFState.UNTRACKED;

    private Object trackedObj = null;
    private SFState trackedObjState = SFState.UNTRACKED;
    private ReflectionObject2 trackedReflectionObject = null;

    private List<Stmt> stmtList;
    private HashMap<Value, List<ValueAssign>> valueAssigns;
    private MethodSignature methodSignature;

    public StaticFieldRefTracker(JFieldRef fieldRef) {
        this.fieldRef = fieldRef;
        this.declaringClassType = fieldRef.getFieldSignature().getDeclClassType();

        try {
            tryDirectlyTrack();
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException |
                 ExceptionInInitializerError e) {
            // System.out.println("[INFO] Exception at: Field \"" + fieldRef.getFieldSignature() + "\", Exception: " + e.getMessage());
        }
        if (trackedObj != null && initObjState == SFState.TRACKED) {
            return;
        }
        tryTrackInInitMethod();
    }

    private void tryDirectlyTrack() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ExceptionInInitializerError {
        FieldSubSignature subFieldSignature = fieldRef.getFieldSignature().getSubSignature();
        Class<?> declaringClass = GetClassFromType2.get(declaringClassType);
        if (declaringClass != null) {
            String fieldName = subFieldSignature.getName();
            try {
                Field field = declaringClass.getDeclaredField(fieldName);
                if (Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        trackedObj = field.get(null);
                        initObjState = SFState.TRACKED;
                    } catch (Exception e) {
                        // System.out.println("[INFO] Exception at field: " + field + ", Exception: " + e.getMessage());
                    } catch (Error e) {
                        // System.out.println("[INFO] Exception at field: " + field + ", Error: " + e.getMessage());
                    }
                }
            } catch (NoClassDefFoundError e) {
                // System.out.println("[INFO] NoClassDefFoundError at: " + fieldRef.getFieldSignature());
            }
        }
    }


    public void updateFieldState(JFieldRef fieldRef, Object obj, SFState state) {
        if (fieldRef.equivTo(this.fieldRef)) {
            this.trackedObj = obj;
            this.initObjState = state;
        }
    }

    private void tryTrackInInitMethod() {
        if (AnalysisEnv.view().getClass(declaringClassType).isPresent()) {
            JavaSootClass javaSC = AnalysisEnv.view().getClass(declaringClassType).get();
            for (JavaSootMethod javaSM : javaSC.getMethods()) {
                List<Stmt> stmtList = javaSM.getBody().getStmts();
                if (javaSM.getName().equals("<init>") || javaSM.getName().equals("<clinit>")) {
                    for (Stmt stmt : javaSM.getBody().getStmts()) {
                        if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getDef().get().equivTo(fieldRef) && defStmt.getRightOp() instanceof Constant constant) {
                            initObj = ConstantResolve.resolve(constant);
                            initObjState = SFState.TRACKED;
                            return;
                        }
                        // Consider Static Array defined when init.
                        if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getDef().get().equivTo(fieldRef) && fieldRef.getType() instanceof ArrayType && defStmt.getRightOp() instanceof Local) {
                            this.methodSignature = javaSM.getSignature();
                            trySliceStaticArrayField(stmtList, defStmt);
                            trySolveStaticArrayField();
                            return;
                        }
                        if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getDef().get().equivTo(fieldRef) && defStmt.getRightOp() instanceof Local) {
                            this.methodSignature = javaSM.getSignature();
                            trySliceStaticArrayField(stmtList, defStmt);
                            trySolveStaticArrayField();
                            return;
                        }
                    }
                }
            }
        }
        initObjState = SFState.NOT_FOUND;
    }

    private void trySliceStaticArrayField(List<Stmt> stmtList, AbstractDefinitionStmt startDefStmt) {
        List<Stmt> result = new ArrayList<>();
        HashMap<Value, List<ValueAssign>> valueAssigns = new HashMap<>();
        List<Value> trackingLocals = new ArrayList<>();

        ListIterator<Stmt> backwardIterator = stmtList.listIterator(stmtList.size());
        while (backwardIterator.hasPrevious()) {
            Stmt stmt = backwardIterator.previous();

            if (stmt.equals(startDefStmt)) {
                trackingLocals.addAll(startDefStmt.getUses().filter(value -> (value instanceof Local || value instanceof JFieldRef)).toList());
                valueAssigns.computeIfAbsent(startDefStmt.getDef().get(), k -> new ArrayList<>()).add(new ValueAssign(startDefStmt.getRightOp(), AssignWay.ASSIGN));
                result.add(stmt);
                continue;
            } else {
                if (stmt instanceof AbstractDefinitionStmt defStmt) {
                    Value defValue = defStmt.getDef().get();
                    if (defValue instanceof JArrayRef arrayRef) {
                        valueAssigns.computeIfAbsent(arrayRef, k -> new ArrayList<>()).add(new ValueAssign(defStmt.getRightOp(), AssignWay.ASSIGN));
                        result.add(stmt);
                        continue;
                    }
                    for (Value value : trackingLocals) {
                        if (value.equivTo(defValue)) {
                            trackingLocals.remove(defValue);
                            trackingLocals.addAll(defStmt.getUses().filter(val -> (val instanceof Local || val instanceof JFieldRef)).toList());
                            valueAssigns.computeIfAbsent(defStmt.getDef().get(), k -> new ArrayList<>()).add(new ValueAssign(startDefStmt.getRightOp(), AssignWay.ASSIGN));
                            result.add(stmt);
                            break;
                        }
                    }

                }
                if (stmt instanceof JInvokeStmt invokeStmt && invokeStmt.getInvokeExpr() instanceof JSpecialInvokeExpr specialInvokeExpr && trackingLocals.contains(specialInvokeExpr.getBase())) {
                    trackingLocals.remove(specialInvokeExpr.getBase());
                    trackingLocals.addAll(specialInvokeExpr.getArgs().stream().filter(Local.class::isInstance).toList());
                    valueAssigns.computeIfAbsent(specialInvokeExpr.getBase(), k -> new ArrayList<>()).add(new ValueAssign(specialInvokeExpr, AssignWay.INVOKE_AS_BASE));
                    result.add(stmt);
                }
            }
        }
        this.stmtList = result;
        this.valueAssigns = valueAssigns;
    }

    private void trySolveStaticArrayField() {
        FocusedValueObjects valueObjects = new FocusedValueObjects();
        valueObjects.setMethodSignature(this.methodSignature);

        ListIterator<Stmt> backwardIterator = stmtList.listIterator(stmtList.size());
        while (backwardIterator.hasPrevious()) {
            Stmt stmt = backwardIterator.previous();
            try {
                stmtExecution(stmt, valueObjects);
            } catch (Exception e) {
                // System.out.println("[INFO] Exception when executing stmt: " + stmt.toString());
                // System.out.println("    " + e.getMessage());
            }
        }
        this.trackedObjState = SFState.TRACKED;
        this.trackedObj = valueObjects.getReflectionObject(fieldRef).getInstance();
        this.trackedReflectionObject = valueObjects.getReflectionObject(fieldRef);
    }

    private void stmtExecution(Stmt stmt, FocusedValueObjects valueObjects) {
        if (stmt instanceof AbstractDefinitionStmt defStmt) {
            ReflectionObject2 defObject = valueObjects.getReflectionObject(defStmt.getDef().get());
            if (defStmt.getRightOp() instanceof Constant constant) {
                if (defStmt.getDef().get() instanceof JArrayRef arrayRef) {
                    // Improvement: "transform into IntConstant" didn't deal with situation where index is a variable
                    defObject.setInstance(IntConstantResolve.cast((IntConstant) constant, defObject.getObjectClass().getComponentType()), ((IntConstant) arrayRef.getIndex()).getValue());
                } else {
                    defObject.setInstance(ConstantResolve.resolve(constant));
                }
            } else if (defStmt.getRightOp() instanceof JNewArrayExpr newArrayExpr) {
                // improvement: didn't deal with multi-dimension array.
                Class<?> arrayClass = GetClassFromType2.get(newArrayExpr.getBaseType());
                // Improvement: "transform into IntConstant" didn't deal with situation where index is a variable
                int arrayLength = ((IntConstant) newArrayExpr.getSize()).getValue();
                defObject.setInstance(Array.newInstance(arrayClass, arrayLength));
            } else if (defStmt.getRightOp() instanceof AbstractInvokeExpr invokeExpr) {
                Class<?> baseType = null;
                ReflectionObject2 baseObject = null;
                Class<?>[] paramTypes = null;
                Object[] paramInstances = null;

                paramTypes = new Class[invokeExpr.getArgCount()];
                paramInstances = new Object[invokeExpr.getArgCount()];

                for (int i = 0; i < invokeExpr.getArgCount(); i++) {
                    Immediate value = invokeExpr.getArg(i);
                    // Immediate only contains Local and Constant
                    if (value instanceof JavaLocal local) {
                        paramTypes[i] = GetClassFromType2.get(local.getType());
                        paramInstances[i] = valueObjects.getReflectionObject(local).getInstance();
                    } else if (value instanceof Constant constant) {
                        paramTypes[i] = GetClassFromType2.get(defStmt.getInvokeExpr().getMethodSignature().getParameterTypes().get(i));
                        paramInstances[i] = ConstantResolve.resolve(constant, paramTypes[i]);
                    }
                }

                if (invokeExpr instanceof AbstractInstanceInvokeExpr instanceInvokeExpr) {
                    Local base = instanceInvokeExpr.getBase();
                    baseType = GetClassFromType2.get(base.getType());
                    baseObject = valueObjects.getReflectionObject(base);

                    try {
                        Method invokeMethod = getMethod(baseType, invokeExpr.getMethodSignature().getName(), paramTypes);
                        invokeMethod.setAccessible(true);
                        defObject.setInstance(invokeMethod.invoke(baseObject.getInstance(), paramInstances));
                    } catch (NullPointerException e) {
                        // System.out.println("INFO: " + e.getMessage());
                    } catch (InvocationTargetException | IllegalAccessException e) {
                    } catch (IllegalArgumentException e) {
                        try {
                            baseObject.resetClassLoader();
                            Method invokeMethod = getMethod(baseType, invokeExpr.getMethodSignature().getName(), paramTypes);
                            invokeMethod.setAccessible(true);
                            defObject.setInstance(invokeMethod.invoke(baseObject.getInstance(), paramInstances));
                        } catch (ClassNotFoundException ex) {
                        } catch (InvocationTargetException | IllegalAccessException ex) {
                            // System.out.println("ReflectionObject.class: NoSuchMethodException | InvocationTargetException | IllegalAccessException");
                        }
                    }
                } else {
                    try {
                        Method invokeMethod = getMethod(baseType, invokeExpr.getMethodSignature().getName(), paramTypes);
                        invokeMethod.setAccessible(true);
                        invokeMethod.invoke(baseObject.getInstance(), paramInstances);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                    } catch (IllegalArgumentException e) {
                        try {
                            baseObject.resetClassLoader();
                            Method invokeMethod = getMethod(baseType, invokeExpr.getMethodSignature().getName(), paramTypes);
                            invokeMethod.setAccessible(true);
                            invokeMethod.invoke(baseObject.getInstance(), paramInstances);
                        } catch (ClassNotFoundException ex) {
                        } catch (InvocationTargetException | IllegalAccessException ex) {
                            // System.out.println("ReflectionObject.class: NoSuchMethodException | InvocationTargetException | IllegalAccessException");
                        }
                    }
                }

            } else {
                try {
                    defObject.setInstance(valueObjects.getReflectionObject(defStmt.getRightOp()).getInstance());
                } catch (NullPointerException e) {
                    // System.out.println("INFO: NullPointerException at: \"" + stmt + "\"");
                }
            }
        }

        if (stmt instanceof JInvokeStmt) {
            Class<?> baseType = null;
            ReflectionObject2 baseObject = null;
            Class<?>[] paramTypes = null;
            Object[] paramInstances = null;

            ReflectionObject2 defObject;

            if (stmt.containsInvokeExpr()) {
                AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
                paramTypes = new Class[invokeExpr.getArgCount()];
                paramInstances = new Object[invokeExpr.getArgCount()];

                if (invokeExpr instanceof AbstractInstanceInvokeExpr abstractInstanceInvokeExpr) {
                    Local base = abstractInstanceInvokeExpr.getBase();
                    baseType = GetClassFromType2.get(base.getType());
                    baseObject = valueObjects.getReflectionObject(base);
                }
                for (int i = 0; i < invokeExpr.getArgCount(); i++) {
                    Immediate value = invokeExpr.getArg(i);
                    // Immediate only contains Local and Constant
                    if (value instanceof JavaLocal local) {
                        paramTypes[i] = GetClassFromType2.get(local.getType());
                        paramInstances[i] = valueObjects.getReflectionObject(local).getInstance();
                    } else if (value instanceof Constant constant) {
                        paramTypes[i] = GetClassFromType2.get(stmt.getInvokeExpr().getMethodSignature().getParameterTypes().get(i));
                        paramInstances[i] = ConstantResolve.resolve(constant, paramTypes[i]);
                    }
                }

            }
            AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
            if (invokeExpr instanceof AbstractInstanceInvokeExpr abstractInstanceInvokeExpr) {
                if (abstractInstanceInvokeExpr instanceof JSpecialInvokeExpr specialInvokeExpr && specialInvokeExpr.getMethodSignature().getName().equals("<init>")) {
                    try {
                        Constructor<?> constructor = baseType.getDeclaredConstructor(paramTypes);
                        constructor.setAccessible(true);
                        baseObject.setInstance(constructor.newInstance(paramInstances));
                    } catch (InstantiationException | IllegalAccessException e) {
                    } catch (InvocationTargetException e) {
                        // System.out.println("[INFO]: InvocationTargetException occured when initilizing Object: " + baseType.getName());
                    } catch (NoSuchMethodException e) {
                        // System.out.println("[INFO]: java.lang.NoSuchMethodException:" + e.getMessage());
                        // System.out.println("    If this Exception was thrown with a <init> Method, it may be caused by a constructor with parameters, but Sootup Jimple will default invoke the constructor without parameters.");
                    } catch (NoClassDefFoundError e) {
                        // System.out.println("[INFO] NoClassDefFoundError at: " + baseType.getName());
                    }
                } else {
                    try {
                        Method invokeMethod = getMethod(baseType, abstractInstanceInvokeExpr.getMethodSignature().getName(), paramTypes);
                        invokeMethod.setAccessible(true);
                        invokeMethod.invoke(baseObject.getInstance(), paramInstances);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                    } catch (IllegalArgumentException e) {
                        try {
                            baseObject.resetClassLoader();
                            Method invokeMethod = getMethod(baseType, abstractInstanceInvokeExpr.getMethodSignature().getName(), paramTypes);
                            invokeMethod.setAccessible(true);
                            invokeMethod.invoke(baseObject.getInstance(), paramInstances);
                        } catch (ClassNotFoundException ex) {
                        } catch (InvocationTargetException | IllegalAccessException ex) {
                            // System.out.println("ReflectionObject.class: NoSuchMethodException | InvocationTargetException | IllegalAccessException");
                        }
                    }
                }
            } else {
                try {
                    ClassLoader classLoader = AnalysisEnv.ClassLoader();
                    Class<?> invokeClass = classLoader.loadClass(invokeExpr.getMethodSignature().getDeclClassType().getFullyQualifiedName());
                    Method method = getMethod(invokeClass, invokeExpr.getMethodSignature().getName(), paramTypes);
                    method.setAccessible(true);
                    method.invoke(null, paramInstances);
                } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
                }
            }
        }
    }

    private Method getMethod(Class<?> baseClass, String methodName, Class<?>[] paramClasses) {
        for (Method method : baseClass.getMethods()) {
            if (method.getParameterCount() == paramClasses.length && method.getName().equals(methodName)) {
                boolean matchFlag = true;
                for (int i = 0; i < paramClasses.length; i++) {
                    if (!method.getParameterTypes()[i].isAssignableFrom(paramClasses[i])) {
                        matchFlag = false;
                        break;
                    }
                }
                if (matchFlag) {
                    return method;
                }

            }
        }
        return null;
    }

    public Object getTrackedStaticField() {
        if (trackedObjState == SFState.TRACKED) {
            return trackedObj;
        } else if (initObjState == SFState.TRACKED) {
            return initObj;
        } else {
            return null;
        }
    }

    public Object getTrackedObj() {
        return trackedObj;
    }

    public ReflectionObject2 getTrackedReflectionObject() {
        return trackedReflectionObject;
    }

    public SFState getTrackedObjState() {
        return trackedObjState;
    }
}


