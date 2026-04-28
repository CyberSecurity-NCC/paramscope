package org.paramscope.valueflow;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.reflection.ArrayState;
import org.paramscope.reflection.ConstantResolve;
import org.paramscope.reflection.GetClassFromType2;
import org.paramscope.reflection.ReflectionObject2;
import org.paramscope.slice.*;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JFieldRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.JInvokeStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.signatures.FieldSignature;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ArrayType;
import sootup.core.types.Type;
import sootup.java.core.jimple.basic.JavaLocal;

import java.lang.reflect.*;
import java.util.*;

/**
 * A copy of the replay/evaluation logic from {@code org.paramscope.slice.IntraResultTree},
 * extracted as a reusable helper for the valueflow module.
 */
final class ValueFlowReplayer {

    private ValueFlowReplayer() {
    }

    static InterProceduralObjects resolveOneIntraResult(IntraResult intraResult,
                                                       InterProceduralObjects interProceduralObjects,
                                                       List<String> diagnostics,
                                                       org.paramscope.valueflow.target.ValueTarget target) {
        ArrayList<String> runningExceptions = new ArrayList<>();
        boolean seedIsLocalTarget = target != null && target.kind() == org.paramscope.valueflow.target.ValueTargetKind.STMT_LOCAL
                && target instanceof org.paramscope.valueflow.target.StmtLocalTarget;

        InterProceduralObjects nextInterProceduralObjects = new InterProceduralObjects();
        for (JStaticFieldRef staticFieldRef : intraResult.getStaticFieldRefTrackers().keySet()) {
            StaticFieldRefTracker staticFieldRefTracker = intraResult.getStaticFieldRefTrackers().get(staticFieldRef);
            if (staticFieldRefTracker.getTrackedObjState() == SFState.TRACKED
                    && staticFieldRefTracker.getTrackedObj() != null
                    && staticFieldRefTracker.getTrackedReflectionObject() != null) {
                interProceduralObjects.getStaticFieldObjects().put(staticFieldRef, staticFieldRefTracker.getTrackedReflectionObject());
            }
        }

        FocusedValueObjects valueObjects = new FocusedValueObjects(intraResult.getCallSite().getCaller(), interProceduralObjects);

        ListIterator<Stmt> resultStmtIterator = intraResult.getResultStmts().listIterator(intraResult.getResultStmts().size());
        while (resultStmtIterator.hasPrevious()) {
            Stmt valueFlowStmt = resultStmtIterator.previous();

            if (valueFlowStmt.containsInvokeExpr()
                    && valueFlowStmt.getInvokeExpr().getMethodSignature().equals(intraResult.getCallSite().getCallee())
                    && valueFlowStmt.getPositionInfo().getStmtPosition().equals(intraResult.getCallSite().getPos().getStmtPosition())) {
                if (valueFlowStmt.getInvokeExpr() instanceof AbstractInstanceInvokeExpr instanceInvokeExpr
                        && valueObjects.contains(instanceInvokeExpr.getBase())) {
                    nextInterProceduralObjects.setThisObject(valueObjects.getReflectionObject(instanceInvokeExpr.getBase()));
                }
                for (MethodParamRef param : intraResult.getMethodParamRefs()) {
                    nextInterProceduralObjects.getParamObjects().put(
                            param.parameterRef().getIndex(),
                            valueObjects.getReflectionObject(valueFlowStmt.getInvokeExpr().getArg(param.parameterRef().getIndex()))
                    );
                }
            }

            FocusedValues defValues = intraResult.getStmtDefValues().get(valueFlowStmt);
            if (defValues != null) {
                if (!defValues.onlyContainsStaticFieldRef()) {
                    try {
                        stmtReflection(valueFlowStmt, valueObjects, runningExceptions);
                    } catch (NullPointerException ignored) {
                    } catch (ExceptionInInitializerError | NoClassDefFoundError | ClassCastException |
                             IllegalArgumentException ignored) {
                    } catch (UnsatisfiedLinkError ignored) {
                    }
                }

                if (defValues.allValues().size() == 1
                        && defValues.allValues().get(0).getType() instanceof ArrayType
                        && intraResult.getSecureRandomizedArrays().contains(defValues.allValues().get(0))) {
                    if (valueObjects.contains(defValues.allValues().get(0))) {
                        valueObjects.getReflectionObject(defValues.allValues().get(0)).setArrayState(ArrayState.SECURE_RANDOMIZED);
                    }
                }

                if (defValues.allValues().size() == 1 && defValues.allValues().get(0).getType() instanceof ArrayType) {
                    for (Value value : valueFlowStmt.getUses().toList()) {
                        if (value.getType() instanceof ArrayType && intraResult.getSecureRandomizedArrays().contains(value)) {
                            intraResult.getSecureRandomizedArrays().add(defValues.allValues().get(0));
                            if (valueObjects.contains(value)) {
                                valueObjects.getReflectionObject(value).setArrayState(ArrayState.SECURE_RANDOMIZED);
                            }
                            break;
                        }
                    }
                }

                if (defValues.allValues().size() == 1
                        && defValues.allValues().get(0).getType() instanceof ArrayType
                        && valueFlowStmt.containsInvokeExpr()) {
                    if (valueFlowStmt.getInvokeExpr().getMethodSignature().getDeclClassType().getFullyQualifiedName().equals("java.util.Random")
                            && valueFlowStmt.getInvokeExpr().getMethodSignature().getName().equals("nextBytes")
                            && valueFlowStmt.getInvokeExpr().getArg(0).equivTo(defValues.allValues().get(0))) {
                        if (valueObjects.contains(defValues.allValues().get(0))) {
                            valueObjects.getReflectionObject(defValues.allValues().get(0)).setArrayState(ArrayState.NOT_SECURE_RANDOMIZED);
                        }
                    }
                }

                if (valueFlowStmt.containsInvokeExpr() && !defValues.isEmptyStaticField()) {
                    HashMap<Value, List<ValueAssign>> staticFieldAssigns = intraResult.getStmtFieldAssigns().get(valueFlowStmt);
                    solveStmtFieldAssigns(staticFieldAssigns, valueObjects, valueFlowStmt.getInvokeExpr().getMethodSignature(), runningExceptions);
                }
            }

            // For STMT_LOCAL, capture the target local value at the seed stmt.
            if (seedIsLocalTarget && target instanceof org.paramscope.valueflow.target.StmtLocalTarget stmtLocalTarget
                    && valueFlowStmt.equivTo(stmtLocalTarget.callSite().getInvokeStmt())) {
                Value seedValue = null;
                String localName = stmtLocalTarget.localName();
                if (localName == null || localName.isBlank()) {
                    if (valueFlowStmt instanceof AbstractDefinitionStmt defStmt && defStmt.getDef().isPresent()) {
                        seedValue = defStmt.getDef().get();
                    }
                } else {
                    if (valueFlowStmt instanceof AbstractDefinitionStmt defStmt && defStmt.getDef().isPresent()) {
                        Value d = defStmt.getDef().get();
                        if (d instanceof Local l && localName.equals(l.getName())) {
                            seedValue = d;
                        }
                    }
                    if (seedValue == null) {
                        for (Value u : valueFlowStmt.getUses().toList()) {
                            if (u instanceof Local l && localName.equals(l.getName())) {
                                seedValue = u;
                                break;
                            }
                        }
                    }
                }
                ReflectionObject2 ro = null;
                try {
                    ro = (seedValue == null) ? null : valueObjects.getReflectionObject(seedValue);
                } catch (Throwable ignored) {
                }
                if (seedValue != null && ro != null) {
                    // NOTE: do NOT gate on valueObjects.contains(seedValue).
                    // contains() checks pre-existing bindings, but getReflectionObject() will create one.
                    nextInterProceduralObjects.setLocalTargetObject(ro);
                }
            }
        }

        nextInterProceduralObjects.getStaticFieldObjects().putAll(valueObjects.getStaticFieldObjects());
        diagnostics.addAll(runningExceptions);
        return nextInterProceduralObjects;
    }

    private static void solveStmtFieldAssigns(HashMap<Value, List<ValueAssign>> staticFieldAssigns,
                                             FocusedValueObjects callerValueObjects,
                                             MethodSignature calledMS,
                                             ArrayList<String> runningExceptions) {
        if (staticFieldAssigns == null) {
            return;
        }
        FocusedValueObjects valueObjectMap = new FocusedValueObjects(callerValueObjects);
        valueObjectMap.setMethodSignature(calledMS);
        HashMap<Value, List<Value>> valueDependencyMap = new HashMap<>();
        ArrayList<Value> solvedValues = new ArrayList<>();
        for (Value value : staticFieldAssigns.keySet()) {
            ReflectionObject2 reflectionObject2 = new ReflectionObject2(value.getType(), value.toString());
            valueObjectMap.putValue(value, reflectionObject2);

            for (ValueAssign valueAssign : staticFieldAssigns.get(value)) {
                ArrayList<Value> usedValues = new ArrayList<>();
                if ((valueAssign.value() instanceof JFieldRef || valueAssign.value() instanceof Local)
                        && staticFieldAssigns.containsKey(valueAssign.value())) {
                    usedValues.add(valueAssign.value());
                } else {
                    usedValues.addAll(valueAssign.value().getUses()
                            .filter(val -> (((val instanceof Local || val instanceof JFieldRef) && !val.equals(value)) && staticFieldAssigns.containsKey(val)))
                            .distinct()
                            .toList());
                }
                valueDependencyMap.put(value, usedValues);
            }
        }

        for (Value value : staticFieldAssigns.keySet()) {
            for (Value usedValue : valueDependencyMap.get(value)) {
                solveOneVal(usedValue, staticFieldAssigns.get(usedValue), valueObjectMap, solvedValues, valueDependencyMap, staticFieldAssigns, runningExceptions);
            }
            try {
                solveOneVal(value, staticFieldAssigns.get(value), valueObjectMap, solvedValues, valueDependencyMap, staticFieldAssigns, runningExceptions);
            } catch (NullPointerException ignored) {
            } catch (ExceptionInInitializerError | NoClassDefFoundError | ClassCastException | IllegalArgumentException ignored) {
            }
        }

        for (Value value : staticFieldAssigns.keySet()) {
            if (value instanceof JStaticFieldRef staticFieldRef) {
                callerValueObjects.getStaticFieldObjects().put(staticFieldRef, valueObjectMap.getReflectionObject(value));
            }
        }
    }

    private static void solveOneVal(Value value,
                                   List<ValueAssign> valueAssigns,
                                   FocusedValueObjects valueObjectMap,
                                   ArrayList<Value> solvedValues,
                                   HashMap<Value, List<Value>> valueDependencyMap,
                                   HashMap<Value, List<ValueAssign>> allValueAssigns,
                                   ArrayList<String> runningExceptions) {
        if (solvedValues.contains(value) || valueAssigns == null) {
            return;
        }
        for (Value dependencyValue : valueDependencyMap.getOrDefault(value, List.of())) {
            if (!solvedValues.contains(dependencyValue)) {
                solveOneVal(dependencyValue, allValueAssigns.get(dependencyValue), valueObjectMap, solvedValues, valueDependencyMap, allValueAssigns, runningExceptions);
            }
        }
        ReflectionObject2 reflectionObject = new ReflectionObject2(value.getType(), value.toString());

        ListIterator<ValueAssign> assignIterator = valueAssigns.listIterator(valueAssigns.size());
        while (assignIterator.hasPrevious()) {
            ValueAssign valueAssign = assignIterator.previous();
            if (valueAssign.assignWay() == AssignWay.ASSIGN) {
                if (valueAssign.value() instanceof JFieldRef fieldRef) {
                    if (valueObjectMap.getReflectionObject(fieldRef).getInstance() != null) {
                        reflectionObject.setInstance(valueObjectMap.getReflectionObject(fieldRef).getInstance());
                    } else {
                        if (fieldRef instanceof JStaticFieldRef) {
                            Object tryGetStaticField = tryGetStaticField(fieldRef.getFieldSignature());
                            if (tryGetStaticField != null) {
                                reflectionObject.setInstance(tryGetStaticField);
                            }
                        }
                    }
                }
                if (valueAssign.value() instanceof Local local) {
                    reflectionObject.setInstance(valueObjectMap.getReflectionObject(local).getInstance());
                }
                if (valueAssign.value() instanceof Constant constant) {
                    reflectionObject.setInstance(ConstantResolve.resolve(constant));
                }
                if (valueAssign.value() instanceof JArrayRef arrayRef) {
                    reflectionObject.setInstance(valueObjectMap.getReflectionObject(arrayRef).getInstance());
                }
                if (valueAssign.value() instanceof AbstractInvokeExpr invokeExpr) {
                    MethodSignature invokeMS = invokeExpr.getMethodSignature();
                    Class<?>[] argClasses = new Class[invokeExpr.getArgCount()];
                    Object[] argInstances = new Object[invokeExpr.getArgCount()];
                    for (int i = 0; i < invokeExpr.getArgCount(); i++) {
                        Immediate arg = invokeExpr.getArg(i);
                        if (arg instanceof Local argLocal) {
                            argClasses[i] = GetClassFromType2.get(argLocal.getType());
                            argInstances[i] = valueObjectMap.getReflectionObject(argLocal).getInstance();
                        }
                        if (arg instanceof Constant argConstant) {
                            argClasses[i] = GetClassFromType2.get(invokeMS.getParameterTypes().get(i));
                            argInstances[i] = ConstantResolve.resolve(argConstant, argClasses[i]);
                        }
                    }
                    if (invokeExpr instanceof AbstractInstanceInvokeExpr instanceInvokeExpr) {
                        Local base = instanceInvokeExpr.getBase();
                        ReflectionObject2 baseObject = valueObjectMap.getReflectionObject(base);
                        try {
                            Method invokeMethod = getMethod(baseObject.getObjectClass(), instanceInvokeExpr.getMethodSignature().getName(), argClasses);
                            if (invokeMethod != null) {
                                invokeMethod.setAccessible(true);
                                reflectionObject.setInstance(invokeMethod.invoke(baseObject.getInstance(), argInstances));
                            }
                        } catch (InvocationTargetException | IllegalAccessException e) {
                            runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + instanceInvokeExpr);
                        } catch (Exception e) {
                            runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + instanceInvokeExpr);
                        }
                    }
                    if (invokeExpr instanceof JStaticInvokeExpr) {
                        try {
                            ClassLoader classLoader = AnalysisEnv.ClassLoader();
                            Class<?> invokeClass = classLoader.loadClass(invokeMS.getDeclClassType().getFullyQualifiedName());
                            Method method = getMethod(invokeClass, invokeMS.getName(), argClasses);
                            if (method != null) {
                                method.setAccessible(true);
                                method.invoke(null, argInstances);
                            }
                        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException e) {
                            runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + invokeExpr);
                        } catch (Exception e) {
                            runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at expr: " + invokeExpr);
                        }
                    }
                }
            }
        }
        solvedValues.add(value);
        valueObjectMap.putValue(value, reflectionObject);
    }

    private static void stmtReflection(Stmt valueFlowStmt, FocusedValueObjects valueObjects, ArrayList<String> runningExceptions) {
        Class<?> baseType = null;
        ReflectionObject2 baseObject = null;
        Class<?>[] paramTypes = null;
        Object[] paramInstances = null;

        ReflectionObject2 defObject;

        if (valueFlowStmt.containsInvokeExpr()) {
            AbstractInvokeExpr invokeExpr = valueFlowStmt.getInvokeExpr();
            paramTypes = new Class[invokeExpr.getArgCount()];
            paramInstances = new Object[invokeExpr.getArgCount()];

            if (invokeExpr instanceof AbstractInstanceInvokeExpr abstractInstanceInvokeExpr) {
                Local base = abstractInstanceInvokeExpr.getBase();
                baseType = GetClassFromType2.get(base.getType());
                baseObject = valueObjects.getReflectionObject(base);
            }
            for (int i = 0; i < invokeExpr.getArgCount(); i++) {
                Immediate value = invokeExpr.getArg(i);
                if (value instanceof JavaLocal local) {
                    paramTypes[i] = GetClassFromType2.get(local.getType());
                    paramInstances[i] = valueObjects.getReflectionObject(local).getInstance();
                } else if (value instanceof Constant constant) {
                    paramTypes[i] = GetClassFromType2.get(valueFlowStmt.getInvokeExpr().getMethodSignature().getParameterTypes().get(i));
                    paramInstances[i] = ConstantResolve.resolve(constant, paramTypes[i]);
                }
            }
        }

        // Handle invokedynamic string concat (Java 9+): makeConcatWithConstants(...)
        if (valueFlowStmt instanceof AbstractDefinitionStmt defStmt
                && defStmt.containsInvokeExpr()
                && defStmt.getInvokeExpr() instanceof JDynamicInvokeExpr dynInvoke) {
            try {
                ReflectionObject2 defObjectDyn = valueObjects.getReflectionObject(defStmt.getDef().get());
                Object resolved = tryResolveStringConcat(dynInvoke, valueObjects);
                if (resolved != null) {
                    defObjectDyn.setInstance(resolved);
                }
            } catch (Exception e) {
                runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
            }
            return;
        }

        if (valueFlowStmt instanceof AbstractDefinitionStmt defStmt
                && defStmt.containsInvokeExpr()
                && defStmt.getInvokeExpr() instanceof AbstractInstanceInvokeExpr invokeExpr) {
            defObject = valueObjects.getReflectionObject(defStmt.getDef().get());
            try {
                if (baseType == null || baseObject == null || baseObject.getInstance() == null) {
                    return;
                }
                Method invokeMethod = getMethod(baseType, invokeExpr.getMethodSignature().getName(), paramTypes);
                if (invokeMethod != null) {
                    invokeMethod.setAccessible(true);
                    defObject.setInstance(invokeMethod.invoke(baseObject.getInstance(), paramInstances));
                }
            } catch (Exception e) {
                runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
            }
        }

        if (valueFlowStmt instanceof AbstractDefinitionStmt defStmt
                && defStmt.containsInvokeExpr()
                && defStmt.getInvokeExpr() instanceof JStaticInvokeExpr invokeExpr) {
            defObject = valueObjects.getReflectionObject(defStmt.getDef().get());
            try {
                ClassLoader classLoader = AnalysisEnv.ClassLoader();
                Class<?> invokeClass = classLoader.loadClass(invokeExpr.getMethodSignature().getDeclClassType().getFullyQualifiedName());
                Method method = getMethod(invokeClass, invokeExpr.getMethodSignature().getName(), paramTypes);
                if (method != null) {
                    method.setAccessible(true);
                    defObject.setInstance(method.invoke(null, paramInstances));
                }
            } catch (Exception e) {
                runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
            }
        }

        if (valueFlowStmt instanceof AbstractDefinitionStmt defStmt && !defStmt.containsInvokeExpr()) {
            Value defVal = defStmt.getDef().get();
            defObject = valueObjects.getReflectionObject(defVal);
            if (defStmt.getRightOp() instanceof Constant constant) {
                try {
                    if (defStmt.getDef().get() instanceof JArrayRef arrayRef && defStmt.getRightOp() instanceof IntConstant) {
                        defObject.setInstance(org.paramscope.reflection.IntConstantResolve.cast((IntConstant) constant, defObject.getObjectClass().getComponentType()), ((IntConstant) arrayRef.getIndex()).getValue());
                    } else if (defStmt.getDef().get() instanceof JArrayRef arrayRef && defStmt.getRightOp() instanceof StringConstant) {
                        defObject.setInstance(ConstantResolve.resolve(constant), ((IntConstant) arrayRef.getIndex()).getValue());
                    } else {
                        defObject.setInstance(ConstantResolve.resolve(constant, GetClassFromType2.get(defObject.getDataType())));
                    }
                    if (defVal instanceof JInstanceFieldRef ifr) {
                        trySetInstanceField(ifr, defObject.getInstance(), valueObjects, runningExceptions);
                    }
                } catch (ClassCastException e) {
                    runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                }
            } else if (defStmt.getRightOp() instanceof JNewArrayExpr newArrayExpr && newArrayExpr.getSize() instanceof IntConstant) {
                Class<?> arrayClass = GetClassFromType2.get(newArrayExpr.getBaseType());
                int arrayLength = ((IntConstant) newArrayExpr.getSize()).getValue();
                defObject.setInstance(Array.newInstance(arrayClass, arrayLength));
            } else if (defStmt.getRightOp() instanceof JCastExpr castExpr) {
                try {
                    Type castType = castExpr.getType();
                    Immediate castImmediate = castExpr.getOp();
                    if (castImmediate instanceof Constant constant) {
                        defObject.setInstance(ConstantResolve.resolve(constant));
                    } else {
                        defObject.setInstance(valueObjects.getReflectionObject(castImmediate).getInstance());
                        defObject.setObjectClass(GetClassFromType2.get(castType));
                    }
                } catch (NullPointerException e) {
                    runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                }
            } else if (defStmt.getRightOp() instanceof JStaticFieldRef staticFieldRef && !valueObjects.contains(staticFieldRef)) {
                FieldSignature fieldSignature = staticFieldRef.getFieldSignature();
                defObject.setInstance(tryGetStaticField(fieldSignature));
            } else if (defStmt.getRightOp() instanceof JInstanceFieldRef instanceFieldRef) {
                if (valueObjects.contains(instanceFieldRef.getBase()) || valueObjects.contains(instanceFieldRef)) {
                    try {
                        defObject.setInstance(valueObjects.getReflectionObject(defStmt.getRightOp()).getInstance());
                    } catch (NullPointerException e) {
                        defObject.setInstance(tryGetInstanceField(instanceFieldRef, runningExceptions));
                    }
                }
            } else {
                try {
                    if (defStmt.getDef().get() instanceof JArrayRef arrayRef) {
                        defObject.setInstance(valueObjects.getReflectionObject(defStmt.getRightOp()).getInstance(), ((IntConstant) arrayRef.getIndex()).getValue());
                    } else {
                        defObject.setInstance(valueObjects.getReflectionObject(defStmt.getRightOp()).getInstance());
                    }
                    if (defVal instanceof JInstanceFieldRef ifr) {
                        trySetInstanceField(ifr, defObject.getInstance(), valueObjects, runningExceptions);
                    }
                } catch (NullPointerException e) {
                    runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                }
            }
        }

        if (valueFlowStmt instanceof JInvokeStmt) {
            AbstractInvokeExpr invokeExpr = valueFlowStmt.getInvokeExpr();
            if (invokeExpr instanceof AbstractInstanceInvokeExpr abstractInstanceInvokeExpr) {
                if (abstractInstanceInvokeExpr instanceof JSpecialInvokeExpr specialInvokeExpr
                        && specialInvokeExpr.getMethodSignature().getName().equals("<init>")) {
                    try {
                        if (baseType == null || baseObject == null) {
                            return;
                        }
                        Constructor<?> constructor = baseType.getDeclaredConstructor(paramTypes);
                        constructor.setAccessible(true);
                        baseObject.setInstance(constructor.newInstance(paramInstances));
                    } catch (Exception e) {
                        runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                    }
                } else {
                    try {
                        if (baseType == null || baseObject == null || baseObject.getInstance() == null) {
                            return;
                        }
                        Method invokeMethod = getMethod(baseType, abstractInstanceInvokeExpr.getMethodSignature().getName(), paramTypes);
                        if (invokeMethod != null) {
                            invokeMethod.setAccessible(true);
                            invokeMethod.invoke(baseObject.getInstance(), paramInstances);
                        }
                    } catch (Exception e) {
                        runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                    }
                }
            } else {
                try {
                    ClassLoader classLoader = AnalysisEnv.ClassLoader();
                    Class<?> invokeClass = classLoader.loadClass(invokeExpr.getMethodSignature().getDeclClassType().getFullyQualifiedName());
                    Method method = getMethod(invokeClass, invokeExpr.getMethodSignature().getName(), paramTypes);
                    if (method != null) {
                        method.setAccessible(true);
                        method.invoke(null, paramInstances);
                    }
                } catch (Exception e) {
                    runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                }
            }
        }
    }

    private static Object tryGetStaticField(FieldSignature fieldSignature) {
        Class<?> baseClass = GetClassFromType2.get(fieldSignature.getDeclClassType());
        try {
            Field field = baseClass.getDeclaredField(fieldSignature.getName());
            field.setAccessible(true);
            return field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }

    private static Object tryGetInstanceField(JInstanceFieldRef instanceFieldRef, ArrayList<String> runningExceptions) {
        Object resObject = null;
        FieldSignature fieldSignature = instanceFieldRef.getFieldSignature();
        if (AnalysisEnv.view().getClass(fieldSignature.getDeclClassType()).isPresent()) {
            SootClass sootClass = AnalysisEnv.view().getClass(fieldSignature.getDeclClassType()).get();
            if (!sootClass.getMethodsByName("<init>").isEmpty()) {
                SootMethod initMethod = sootClass.getMethodsByName("<init>").iterator().next();
                ListIterator<Stmt> stmtIterator = initMethod.getBody().getStmts().listIterator(initMethod.getBody().getStmts().size());

                List<Value> trackingValues = new ArrayList<>();
                HashMap<Value, List<ValueAssign>> valueAssigns = new HashMap<>();
                trackingValues.add(instanceFieldRef);
                while (stmtIterator.hasPrevious()) {
                    Stmt stmt = stmtIterator.previous();
                    if (stmt instanceof AbstractDefinitionStmt defStmt) {
                        ArrayList<Value> removeValues = new ArrayList<>();
                        ArrayList<Value> addValues = new ArrayList<>();
                        for (Value value : trackingValues) {
                            if (stmt.getDef().get().equivTo(value)) {
                                removeValues.add(value);
                                addValues.addAll(stmt.getUses().filter(val -> val instanceof Local).toList());
                                valueAssigns.computeIfAbsent(value, k -> new ArrayList<>()).add(new ValueAssign(defStmt.getRightOp(), AssignWay.ASSIGN));
                            }
                        }
                        trackingValues.removeAll(removeValues);
                        trackingValues.addAll(addValues);
                    }
                }

                FocusedValueObjects valueObjects = new FocusedValueObjects(initMethod.getSignature(), new InterProceduralObjects());
                HashMap<Value, List<Value>> valueDependencyMap = new HashMap<>();
                ArrayList<Value> solvedValues = new ArrayList<>();
                for (Value value : valueAssigns.keySet()) {
                    ReflectionObject2 reflectionObject2 = new ReflectionObject2(value.getType(), value.toString());
                    valueObjects.putValue(value, reflectionObject2);

                    for (ValueAssign valueAssign : valueAssigns.get(value)) {
                        ArrayList<Value> usedValues = new ArrayList<>();
                        if ((valueAssign.value() instanceof JFieldRef || valueAssign.value() instanceof Local) && valueAssigns.containsKey(valueAssign.value())) {
                            usedValues.add(valueAssign.value());
                        } else {
                            usedValues.addAll(valueAssign.value().getUses().filter(val -> (((val instanceof Local || val instanceof JFieldRef) && !val.equals(value)) && valueAssigns.containsKey(val))).distinct().toList());
                        }
                        valueDependencyMap.put(value, usedValues);
                    }
                }
                for (Value value : valueAssigns.keySet()) {
                    for (Value usedValue : valueDependencyMap.get(value)) {
                        solveOneVal(usedValue, valueAssigns.get(usedValue), valueObjects, solvedValues, valueDependencyMap, valueAssigns, runningExceptions);
                    }
                    try {
                        solveOneVal(value, valueAssigns.get(value), valueObjects, solvedValues, valueDependencyMap, valueAssigns, runningExceptions);
                    } catch (Exception ignored) {
                    }
                }
                resObject = valueObjects.getReflectionObject(instanceFieldRef).getInstance();
            }
        }
        return resObject;
    }

    private static void trySetInstanceField(JInstanceFieldRef instanceFieldRef,
                                            Object rhsValue,
                                            FocusedValueObjects valueObjects,
                                            ArrayList<String> runningExceptions) {
        try {
            ReflectionObject2 baseObj = valueObjects.getReflectionObject(instanceFieldRef.getBase());
            if (baseObj == null || baseObj.getInstance() == null) {
                return;
            }
            Object baseInstance = baseObj.getInstance();
            String fieldName = instanceFieldRef.getFieldSignature().getName();
            Field f = baseInstance.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(baseInstance, rhsValue);
        } catch (Throwable t) {
            runningExceptions.add("Caught Exception \"" + t.getMessage() + "\" at setInstanceField " + instanceFieldRef);
        }
    }

    private static Method getMethod(Class<?> baseClass, String methodName, Class<?>[] paramClasses) {
        if (baseClass == null || methodName == null || paramClasses == null) {
            return null;
        }
        Set<Method> methods = new HashSet<>();
        methods.addAll(Arrays.asList(baseClass.getMethods()));
        methods.addAll(Arrays.asList(baseClass.getDeclaredMethods()));
        for (Method method : methods) {
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

    private static Object tryResolveStringConcat(JDynamicInvokeExpr dynInvoke, FocusedValueObjects valueObjects) {
        try {
            // We parse recipe from dynInvoke.toString(), which contains something like ("\\u0001/\\u0001/\\u0001")
            String printed = dynInvoke.toString();
            int recipeIdx = printed.lastIndexOf("(\"");
            if (recipeIdx < 0) return null;
            int recipeEnd = printed.indexOf("\")", recipeIdx);
            if (recipeEnd < 0) return null;
            String recipeEscaped = printed.substring(recipeIdx + 2, recipeEnd);
            String recipe = recipeEscaped.replace("\\u0001", "\u0001");

            StringBuilder out = new StringBuilder();
            int argIndex = 0;
            for (int i = 0; i < recipe.length(); i++) {
                char ch = recipe.charAt(i);
                if (ch == '\u0001') {
                    if (argIndex >= dynInvoke.getArgCount()) {
                        out.append("");
                        continue;
                    }
                    Immediate arg = dynInvoke.getArg(argIndex++);
                    Object v;
                    if (arg instanceof Local l) {
                        v = valueObjects.getReflectionObject(l).getInstance();
                    } else if (arg instanceof Constant c) {
                        v = ConstantResolve.resolve(c);
                    } else {
                        v = null;
                    }
                    out.append(v == null ? "" : v);
                } else {
                    out.append(ch);
                }
            }
            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }

}

