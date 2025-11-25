package org.paramscope.slice;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.analysis.slice.Slicing;
import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.data.APIList;
import org.paramscope.reflection.*;
import org.paramscope.rule.*;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.constant.NullConstant;
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

import javax.crypto.NoSuchPaddingException;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class IntraResultTree {
    IntraResultNode root;
    String filePath;
    List<OneResult> results;
    HashMap<OneResult, Object[]> solvedResults;
    HashMap<OneResult, String> arrayInfo;
    HashMap<OneResult, String> nullReason;
    HashMap<OneResult, String> resultSecurity;
    ArrayList<String> runningExceptions;

    public IntraResultTree(IntraResultNode root) {
        this.root = root;
        results = new ArrayList<>();
        solvedResults = new HashMap<>();
        arrayInfo = new HashMap<>();
        runningExceptions = new ArrayList<>();
        resultSecurity = new HashMap<>();
        nullReason = new HashMap<>();
    }

    private static String checkSecurity(Object[] resObjects, APIParamInfo apiParamInfo) {
        Object res = resObjects[0];
        if (res != null) {
            if (APIList.getMessageDigestGetInstance_Algo_String().contains(apiParamInfo)) {
                assert res instanceof String;
                try {
                    if (MessageDigestGetInstance_Algo_String_Rule.check((String) res)) {
                        return "Secure value: " + res;
                    } else {
                        Slicing.setInSecurityFlagTrue();
                        return "Not in whitelist value: " + res;
                    }
                } catch (NoSuchAlgorithmException e) {
                    return "No such algorithm: " + res;
                }
            }
            if (APIList.getCipherGetInstance_Algo_String().contains(apiParamInfo)) {
                assert res instanceof String;
                if (res.equals("AES")) {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res + "(the value AES uses ECB mode by default)";
                }
                try {
                    if (CipherGetInstance_Algo_String_Rule.check((String) res)) {
                        return "Secure value: " + res;
                    } else {
                        Slicing.setInSecurityFlagTrue();
                        return "Not in whitelist value: " + res;
                    }
                } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                    return "No such algorithm: " + res;
                }

            }
            if (APIList.getSecretKeySpecInit_Algo_String().contains(apiParamInfo)) {
                assert res instanceof String;
                if (SecretKeySpecInit_Algo_String_Rule.check((String) res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getMacGetInstance_Algo_String().contains(apiParamInfo)) {
                assert res instanceof String;
                if (MacGetInstance_Algo_String_Rule.check((String) res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getKeyPairGeneratorGetInstance_Algo_String().contains(apiParamInfo)) {
                assert res instanceof String;
                Slicing.getKeyPairGenerator_algo().replace(Slicing.getCurrentCaller(), (String) res);
                if (KeyPairGeneratorGetInstance_Algo_String_Rule.check((String) res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getSecretKeyFactoryGetInstance_Algo_String().contains(apiParamInfo)) {
                assert res instanceof String;
                if (SecretKeyFactoryGetInstance_Algo_String_Rule.check((String) res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getECGenParameterSpecInit_ECStandard_String().contains(apiParamInfo)) {
                assert res.getClass().equals(String.class);
                if (ECGenParameterSpecInit_Standard_Rule.check((String) res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getRSAKeyGenParameterSpecInit_RSAkeySize_int().contains(apiParamInfo)) {
                assert res instanceof Integer || res instanceof Byte;
                if (RSAKeyGenParameterSpecInit_keySize_int_Rule.check(res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getRSAKeyGenParameterSpecInit_RSAPubExp_BigInteger().contains(apiParamInfo)) {
                assert res instanceof BigInteger || res instanceof NullConstant;
                if (res instanceof NullConstant) {
                    return "Not in whitelist value: " + res;
                }
                if (RSAKeyGenParameterSpecInit_pubExp_BigInteger_Rule.check((BigInteger) res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getDSAGenParameterSpecInit_DSAprimePLen_int().contains(apiParamInfo)) {
                assert res instanceof Integer || res instanceof Byte;
                if (DSAGenParameterSpecInit_DSAprimePLen_int_Rule.check(res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getDSAGenParameterSpecInit_DSASubprimeQLen_int().contains(apiParamInfo)) {
                assert res instanceof Integer || res instanceof Byte;
                if (DSAGenParameterSpecInit_DSASubprimeQLen_int_Rule.check(res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getDHGenParameterSpecInit_DHPrimeSize_int().contains(apiParamInfo)) {
                assert res instanceof Integer || res instanceof Byte;
                if (DHGenParameterSpecInit_DHPrimeSize_int_Rule.check(res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getDHGenParameterSpecInit_DHExpSize_int().contains(apiParamInfo)) {
                assert res instanceof Integer || res instanceof Byte;
                if (DHGenParameterSpecInit_DHExpSize_int_Rule.check(res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getPBEParameterSpecInit_Iter_int().contains(apiParamInfo)) {
                assert res instanceof Integer || res instanceof Byte;
                if (PBEParameterSpecInit_Iter_int_Rule.check(res)) {
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
            if (APIList.getKeyPairGeneratorInitialize_KeySize_int().contains(apiParamInfo)) {
                assert res instanceof Integer || res instanceof Byte;
                if (Slicing.getKeyPairGenerator_algo().containsKey(Slicing.getCurrentCaller())) {
                    String algo = Slicing.getKeyPairGenerator_algo().get(Slicing.getCurrentCaller());
                    if (KeyPairGeneratorInitialize_Keysize_int_Rule.check(res, algo)) {
                        return "Secure keysize: " + res + " with " + algo + " algorithm";
                    } else {
                        Slicing.setInSecurityFlagTrue();
                        return "Not in whitelist keysize: " + res + " with " + algo + " algorithm";
                    }
                } else {
                    return "Check algorithm failed";
                }
            }
            if(APIList.getSignatureGetInstance_Algo_String().contains(apiParamInfo)){
                assert res instanceof String;
                if(SignatureGetInstance_Algo_String_Rule.check((String) res)){
                    return "Secure value: " + res;
                } else {
                    Slicing.setInSecurityFlagTrue();
                    return "Not in whitelist value: " + res;
                }
            }
        }
        return "null value, possibly caused by no actual callers";
    }

    public void resolveResults() {
        // oneResult会记录IR树中的一条路径
        OneResult oneResult = new OneResult();
        // 递归地将所有路径记录到results中
        resolveAllResults(root, oneResult, results);
        // 对于每条路径进行模拟执行的分析
        for (OneResult result : results) {
            // 记录执行中遇到的Exceptions，没用上
            runningExceptions = new ArrayList<>();
            // 获取方法内分析结果的所有语句的迭代器
            ListIterator<IntraResult> iterator = result.getIntraResults().listIterator(result.getIntraResults().size());
            // interProceduralObjects记录了所有跨方法分析的值的实例，包括目标参数值
            InterProceduralObjects interProceduralObjects = new InterProceduralObjects();
            while (iterator.hasPrevious()) {
                IntraResult intraResult = iterator.previous();
                // 如果是常量，则不用模拟执行了
                if (!intraResult.getConstResults().isEmpty()) {
                    for (MethodParamRef methodParamRef : intraResult.getConstResults().keySet()) {
                        Constant constant = intraResult.getConstResults().get(methodParamRef);
                        ReflectionObject2 constObject = new ReflectionObject2(constant.getType(), "ConstVal");
                        constObject.setInstance(ConstantResolve.resolve(constant, GetClassFromType2.get(methodParamRef.parameterRef().getType())));
                        interProceduralObjects.getParamObjects().put(methodParamRef.parameterRef().getIndex(), constObject);
                    }
                    continue;
                }
                // （重要）intraResult记录了单个方法内分析的结果，对此方法内分析进行模拟执行分析
                // 注意ReflectionObject2类，interProceduralObjects包含的是ReflectionObject2类
                interProceduralObjects = resolveOneIntraResult(intraResult, interProceduralObjects);
            }
            // ReflectionObject2 封装了模拟执行中的每一个值对象，结果记录在interProceduralObjects的paramObjects中
            Object[] oneResultObjects = new Object[interProceduralObjects.getParamObjects().size()];
            ReflectionObject2[] reflectionObjects = new ReflectionObject2[interProceduralObjects.getParamObjects().size()];
            int index = 0;
            for (Integer paramIndex : interProceduralObjects.getParamObjects().keySet()) {
                reflectionObjects[index] = interProceduralObjects.getParamObjects().get(paramIndex);
                oneResultObjects[index] = reflectionObjects[index].getInstance();
                index++;
            }
            for (int i = 0; i < oneResultObjects.length; i++) {
                if (oneResultObjects[i] == null) {
                    // 可能是null的几个原因，没用上
                    if (result.getIntraResults().get(result.getIntraResults().size() - 1).needsTracking()) {
                        nullReason.put(result, "No callers found. Actually not called");
                    }
                    if (reflectionObjects[i].getArrayState() == ArrayState.SECURE_RANDOMIZED) {
                        nullReason.put(result, "SecureRandomized Array(java.security.SecureRandom)");
                    }
                    if (reflectionObjects[i].getArrayState() == ArrayState.NOT_SECURE_RANDOMIZED) {
                        nullReason.put(result, "Not SecureRandomized Array(java.util.Random)");
                    }
                }
                // 数组类型随机性检查，不用看
                if (reflectionObjects[i].getDataType() instanceof ArrayType && reflectionObjects[i].getArrayState() != ArrayState.SECURE_RANDOMIZED) {
                    arrayInfo.put(result, "Not SecureRandomized Array(Constant array/Credential in String/Insecure PRNG)");
                }
                if (reflectionObjects[i].getDataType() instanceof ArrayType && reflectionObjects[i].getArrayState() == ArrayState.SECURE_RANDOMIZED) {
                    arrayInfo.put(result, "SecureRandomized Array(java.security.SecureRandom)");
                }
            }
            result.getRunningExceptions().addAll(runningExceptions);
            // 结果值对象
            result.setReflectionObject(reflectionObjects);
            solvedResults.put(result, oneResultObjects);

            // 如果是数组类型，进行重复生成测试（执行两次检查是否随机化）
            boolean twiceResolveForArrayRandomization = (APIList.getTrackArrayApiParamInfoList().contains(root.getIntraResult().getApiParamInfo())
                    || APIList.getTrackLongApiParamInfoList().contains(root.getIntraResult().getApiParamInfo()))
                    && oneResultObjects[0] != null;
            // Temporarily, APIParamInfo only contains one parameter, just check index 0.
            if (twiceResolveForArrayRandomization) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                iterator = result.getIntraResults().listIterator(result.getIntraResults().size());
                InterProceduralObjects interProceduralObjects_twice = new InterProceduralObjects();
                while (iterator.hasPrevious()) {
                    IntraResult intraResult = iterator.previous();
                    if (!intraResult.getConstResults().isEmpty()) {
                        for (MethodParamRef methodParamRef : intraResult.getConstResults().keySet()) {
                            Constant constant = intraResult.getConstResults().get(methodParamRef);
                            ReflectionObject2 constObject = new ReflectionObject2(constant.getType(), "ConstVal");
                            constObject.setInstance(ConstantResolve.resolve(constant, GetClassFromType2.get(methodParamRef.parameterRef().getType())));
                            interProceduralObjects_twice.getParamObjects().put(methodParamRef.parameterRef().getIndex(), constObject);
                        }
                        continue;
                    }
                    interProceduralObjects_twice = resolveOneIntraResult(intraResult, interProceduralObjects_twice);
                }
                Object[] oneResultObjects_twice = new Object[interProceduralObjects_twice.getParamObjects().size()];
                ReflectionObject2[] reflectionObjects_twice = new ReflectionObject2[interProceduralObjects_twice.getParamObjects().size()];
                index = 0;
                for (Integer paramIndex : interProceduralObjects_twice.getParamObjects().keySet()) {
                    reflectionObjects_twice[index] = interProceduralObjects_twice.getParamObjects().get(paramIndex);
                    oneResultObjects_twice[index] = reflectionObjects_twice[index].getInstance();
                    index++;
                }
                // Temporarily, APIParamInfo only contains one parameter, just check all.
                for (int i = 0; i < oneResultObjects_twice.length; i++) {
                    if (oneResultObjects[i] != null && oneResultObjects_twice[i] != null && oneResultObjects[i].getClass().isArray() && oneResultObjects_twice[i].getClass().isArray()) {
                        if (checkArrayRandomization(oneResultObjects[i], oneResultObjects_twice[i])) {
                            resultSecurity.put(result, "(Repeated Generation Test: Randomized)");
                        } else {
                            Slicing.setInSecurityFlagTrue();
                            resultSecurity.put(result, "(Repeated Generation Test: Constant)");
                        }
                    }
                    if (oneResultObjects[i] != null && oneResultObjects_twice[i] != null && oneResultObjects[i] instanceof Long && oneResultObjects_twice[i] instanceof Long) {
                        if (!oneResultObjects[i].equals(oneResultObjects_twice[i])) {
                            resultSecurity.put(result, "(Repeated Generation Test: Randomized)");
                        } else {
                            Slicing.setInSecurityFlagTrue();
                            resultSecurity.put(result, "(Repeated Generation Test: Constant)");
                        }
                    }
                }
            } else {
                // 对每条规则进行安全性检查
                String security = checkSecurity(oneResultObjects, root.getIntraResult().getApiParamInfo());
                resultSecurity.put(result, " (" + security + ")");
            }
            result.setSecurityInfo(resultSecurity.get(result));
        }
    }

    private void resolveAllResults(IntraResultNode node, OneResult currOneResult, List<OneResult> results) {
        currOneResult.getIntraResults().add(node.getIntraResult());

        if (node.getCallerResults().isEmpty()) {
            results.add(currOneResult);
            return;
        }
        for (CallSite callSite : node.getCallerResults().keySet()) {
            OneResult childOneResult = new OneResult(currOneResult);
            childOneResult.getCallRelations().add(callSite);
            resolveAllResults(node.getCallerResults().get(callSite), childOneResult, results);
        }
    }

    private InterProceduralObjects resolveOneIntraResult(IntraResult intraResult, InterProceduralObjects interProceduralObjects) {
        // nextInterProceduralObjects初始化，记录了在这个方法内分析中的所有ReflectionObject
        InterProceduralObjects nextInterProceduralObjects = new InterProceduralObjects();
        for (JStaticFieldRef staticFieldRef : intraResult.getStaticFieldRefTrackers().keySet()) {
            StaticFieldRefTracker staticFieldRefTracker = intraResult.getStaticFieldRefTrackers().get(staticFieldRef);
            if (staticFieldRefTracker.getTrackedObjState() == SFState.TRACKED && staticFieldRefTracker.getTrackedObj() != null && staticFieldRefTracker.getTrackedReflectionObject() != null) {
                interProceduralObjects.getStaticFieldObjects().put(staticFieldRef, staticFieldRefTracker.getTrackedReflectionObject());
            }
        }
        // FocusedValueObjects就的是方法内分析过程中所有的值对象
        FocusedValueObjects valueObjects = new FocusedValueObjects(intraResult.getCallSite().getCaller(), interProceduralObjects);

        // 从intraResult中获取方法内分析的所有切片语句
        ListIterator<Stmt> resultStmtIterator = intraResult.getResultStmts().listIterator(intraResult.getResultStmts().size());
        while (resultStmtIterator.hasPrevious()) {
            Stmt valueFlowStmt = resultStmtIterator.previous();

            // 如果语句包含方法调用，检查其是都是关注的调用点，并检查追踪的参数和是否需要追踪方法调用者实例（this）
            if (valueFlowStmt.containsInvokeExpr()
                    && valueFlowStmt.getInvokeExpr().getMethodSignature().equals(intraResult.getCallSite().getCallee())
                    && valueFlowStmt.getPositionInfo().getStmtPosition().equals(intraResult.getCallSite().getPos().getStmtPosition())) {
                if (valueFlowStmt.getInvokeExpr() instanceof AbstractInstanceInvokeExpr instanceInvokeExpr && valueObjects.contains(instanceInvokeExpr.getBase())) {
                    nextInterProceduralObjects.setThisObject(valueObjects.getReflectionObject(instanceInvokeExpr.getBase()));
                }
                for (MethodParamRef param : intraResult.getMethodParamRefs()) {
                    nextInterProceduralObjects.getParamObjects().put(param.parameterRef().getIndex(), valueObjects.getReflectionObject(valueFlowStmt.getInvokeExpr().getArg(param.parameterRef().getIndex())));
                }
            }

            // 一条语句中的被定义的值
            FocusedValues defValues = intraResult.getStmtDefValues().get(valueFlowStmt);
            if (defValues != null) {

                // 如果只包含方法调用语句，则反射执行
                if (!defValues.onlyContainsStaticFieldRef()) {
                    try {
                        stmtReflection(valueFlowStmt, valueObjects);
                    } catch (NullPointerException e) {
                        // System.out.println("[INFO] NullPointerException at stmtReflection, stmt: " + valueFlowStmt);
                    } catch (ExceptionInInitializerError | NoClassDefFoundError | ClassCastException |
                             IllegalArgumentException e) {
                        // System.out.println("[INFO] Exception :" + e.getMessage() + " while stmtReflection, stmt: " + valueFlowStmt);
                    } catch (UnsatisfiedLinkError e) {
                        // System.out.println("[INFO] Exception :" + e.getMessage() + " while stmtReflection, stmt: " + valueFlowStmt + ", may caused by missing libraries/environment required by the program.");
                    }
                }

                // （不用看）只包含一个被定义值，且为数组类型，检查是否被安全随机化
                if (defValues.allValues().size() == 1 && defValues.allValues().get(0).getType() instanceof ArrayType && intraResult.getSecureRandomizedArrays().contains(defValues.allValues().get(0))) {
                    if (valueObjects.contains(defValues.allValues().get(0))) {
                        valueObjects.getReflectionObject(defValues.allValues().get(0)).setArrayState(ArrayState.SECURE_RANDOMIZED);
                    }
                }

                // （不用看）只包含一个被定义值，且为数组类型，检查是否安全随机化
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
                // （不用看）只包含一个被定义值，且为数组类型，检查是否不安全随机化
                if (defValues.allValues().size() == 1 && defValues.allValues().get(0).getType() instanceof ArrayType && valueFlowStmt.containsInvokeExpr()) {
                    if (valueFlowStmt.getInvokeExpr().getMethodSignature().getDeclClassType().getFullyQualifiedName().equals("java.util.Random")
                            && valueFlowStmt.getInvokeExpr().getMethodSignature().getName().equals("nextBytes")
                            && valueFlowStmt.getInvokeExpr().getArg(0).equivTo(defValues.allValues().get(0))) {
                        if (valueObjects.contains(defValues.allValues().get(0))) {
                            Slicing.setInSecurityFlagTrue();
                            valueObjects.getReflectionObject(defValues.allValues().get(0)).setArrayState(ArrayState.NOT_SECURE_RANDOMIZED);
                        }
                    }
                }

                // 语句包含方法调用语句且含被定义的静态字段，则启发式解静态字段的值（很多逻辑和stmtReflection是重复的，内部不用看了）
                if (valueFlowStmt.containsInvokeExpr() && !defValues.isEmptyStaticField()) {
                    HashMap<Value, List<ValueAssign>> staticFieldAssigns = intraResult.getStmtFieldAssigns().get(valueFlowStmt);
                    solveStmtFieldAssigns(staticFieldAssigns, valueObjects, valueFlowStmt.getInvokeExpr().getMethodSignature());
                }
            }
        }

        nextInterProceduralObjects.getStaticFieldObjects().putAll(valueObjects.getStaticFieldObjects());
        return nextInterProceduralObjects;
    }

    private void solveStmtFieldAssigns(HashMap<Value, List<ValueAssign>> staticFieldAssigns, FocusedValueObjects callerValueObjects, MethodSignature calledMS) {
        FocusedValueObjects valueObjectMap = new FocusedValueObjects(callerValueObjects);
        valueObjectMap.setMethodSignature(calledMS);
        HashMap<Value, List<Value>> valueDependencyMap = new HashMap<>();
        ArrayList<Value> solvedValues = new ArrayList<>();
        for (Value value : staticFieldAssigns.keySet()) {
            ReflectionObject2 reflectionObject2 = new ReflectionObject2(value.getType(), value.toString());
            valueObjectMap.putValue(value, reflectionObject2);

            for (ValueAssign valueAssign : staticFieldAssigns.get(value)) {
                ArrayList<Value> usedValues = new ArrayList<>();
                if ((valueAssign.value() instanceof JFieldRef || valueAssign.value() instanceof Local) && staticFieldAssigns.containsKey(valueAssign.value())) {
                    usedValues.add(valueAssign.value());
                } else {
                    usedValues.addAll(valueAssign.value().getUses().filter(val -> (((val instanceof Local || val instanceof JFieldRef) && !val.equals(value)) && staticFieldAssigns.containsKey(val))).distinct().toList());
                }
                valueDependencyMap.put(value, usedValues);
            }
        }

        for (Value value : staticFieldAssigns.keySet()) {
            for (Value usedValue : valueDependencyMap.get(value)) {
                solveOneVal(usedValue, staticFieldAssigns.get(usedValue), valueObjectMap, solvedValues, valueDependencyMap, staticFieldAssigns);
            }
            try {
                solveOneVal(value, staticFieldAssigns.get(value), valueObjectMap, solvedValues, valueDependencyMap, staticFieldAssigns);
            } catch (NullPointerException e) {
                // System.out.println("[INFO] NullPointerException while solving \"" + value.toString() + "\"");
            } catch (ExceptionInInitializerError | NoClassDefFoundError | ClassCastException |
                     IllegalArgumentException e) {
                // System.out.println("[INFO] Exception :" + e.getMessage() + " while solving \"" + value.toString() + "\"");
            }
        }

        for (Value value : staticFieldAssigns.keySet()) {
            if (value instanceof JStaticFieldRef staticFieldRef) {
                callerValueObjects.getStaticFieldObjects().put(staticFieldRef, valueObjectMap.getReflectionObject(value));
            }
        }
    }

    private void solveOneVal(Value value, List<ValueAssign> valueAssigns, FocusedValueObjects valueObjectMap, ArrayList<Value> solvedValues, HashMap<Value, List<Value>> valueDependencyMap, HashMap<Value, List<ValueAssign>> allValueAssigns) {
        if (solvedValues.contains(value)) {
            return;
        }
        for (Value dependencyValue : valueDependencyMap.get(value)) {
            if (!solvedValues.contains(dependencyValue)) {
                solveOneVal(dependencyValue, allValueAssigns.get(dependencyValue), valueObjectMap, solvedValues, valueDependencyMap, allValueAssigns);
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
                        if (fieldRef instanceof JStaticFieldRef staticFieldRef) {
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
                            argClasses[i] = GetClassFromType2.get(invokeExpr.getMethodSignature().getParameterTypes().get(i));
                            argInstances[i] = ConstantResolve.resolve(argConstant, argClasses[i]);
                        }
                    }
                    if (invokeExpr instanceof AbstractInstanceInvokeExpr instanceInvokeExpr) {
                        Local base = instanceInvokeExpr.getBase();
                        ReflectionObject2 baseObject = valueObjectMap.getReflectionObject(base);
                        try {
                            Method invokeMethod = getMethod(baseObject.getObjectClass(), instanceInvokeExpr.getMethodSignature().getName(), argClasses);
                            invokeMethod.setAccessible(true);
                            reflectionObject.setInstance(invokeMethod.invoke(baseObject.getInstance(), argInstances));
                        } catch (InvocationTargetException | IllegalAccessException e) {
                            runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + instanceInvokeExpr);
                        } catch (IllegalArgumentException e) {
                            try {
                                baseObject.resetClassLoader();
                                Method invokeMethod = getMethod(baseObject.getObjectClass(), instanceInvokeExpr.getMethodSignature().getName(), argClasses);
                                invokeMethod.setAccessible(true);
                                reflectionObject.setInstance(invokeMethod.invoke(baseObject.getInstance(), argInstances));
                                runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + instanceInvokeExpr);
                            } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException ex) {
                                runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + instanceInvokeExpr);
                            }
                        } catch (Exception e) {
                            runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + instanceInvokeExpr);
                        }
                    }
                    if (invokeExpr instanceof JStaticInvokeExpr) {
                        try {
                            ClassLoader classLoader = AnalysisEnv.ClassLoader();
                            Class<?> invokeClass = classLoader.loadClass(invokeExpr.getMethodSignature().getDeclClassType().getFullyQualifiedName());
                            Method method = getMethod(invokeClass, invokeExpr.getMethodSignature().getName(), argClasses);
                            method.setAccessible(true);
                            method.invoke(null, argInstances);
                        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException e) {
                            runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + invokeExpr);
                        } catch (IllegalArgumentException e) {
                            runningExceptions.add("Caught IllegalArgumentException \"" + e.getMessage() + "\" at " + invokeExpr);
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

    private void stmtReflection(Stmt valueFlowStmt, FocusedValueObjects valueObjects) {
        Class<?> baseType = null;
        ReflectionObject2 baseObject = null;
        Class<?>[] paramTypes = null;
        Object[] paramInstances = null;

        ReflectionObject2 defObject;

        // 如果包含方法调用，解析方法调用的所有参数的数据类型和对应实例，放到paramTypes和paramInstances中
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

        // 方法调用语句是实例调用语句，则通过实例对象反射调用
        if (valueFlowStmt instanceof AbstractDefinitionStmt defStmt && defStmt.containsInvokeExpr() && defStmt.getInvokeExpr() instanceof AbstractInstanceInvokeExpr invokeExpr) {
            defObject = valueObjects.getReflectionObject(defStmt.getDef().get());
            try {
                Method invokeMethod = getMethod(baseType, invokeExpr.getMethodSignature().getName(), paramTypes);
                invokeMethod.setAccessible(true);
                defObject.setInstance(invokeMethod.invoke(baseObject.getInstance(), paramInstances));
            } catch (NullPointerException | InvocationTargetException | IllegalAccessException e) {
                runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
            } catch (IllegalArgumentException e) {
                try {
                    runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                    baseObject.resetClassLoader();
                    Method invokeMethod = getMethod(baseType, invokeExpr.getMethodSignature().getName(), paramTypes);
                    invokeMethod.setAccessible(true);
                    defObject.setInstance(invokeMethod.invoke(baseObject.getInstance(), paramInstances));
                } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
                         NullPointerException ex) {
                    runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                }
            }
        }
        // 方法调用语句是静态方法调用，则直接反射调用
        if (valueFlowStmt instanceof AbstractDefinitionStmt defStmt && defStmt.containsInvokeExpr() && defStmt.getInvokeExpr() instanceof JStaticInvokeExpr invokeExpr) {
            defObject = valueObjects.getReflectionObject(defStmt.getDef().get());
            ArrayList<Class<?>> exceptionTypes = new ArrayList<>();
            try {
                ClassLoader classLoader = AnalysisEnv.ClassLoader();
                Class<?> invokeClass = classLoader.loadClass(invokeExpr.getMethodSignature().getDeclClassType().getFullyQualifiedName());
                Method method = getMethod(invokeClass, invokeExpr.getMethodSignature().getName(), paramTypes);
                method.setAccessible(true);
                Collections.addAll(exceptionTypes, method.getExceptionTypes());
                defObject.setInstance(method.invoke(null, paramInstances));
            } catch (Exception e) {
                runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
            }
        }
        // 其他方法调用语句的情况（如InterfaceInvoke涉及及继承关系的方法调用），其中包含了一些数组的特殊处理
        if (valueFlowStmt instanceof AbstractDefinitionStmt defStmt && !defStmt.containsInvokeExpr()) {
            defObject = valueObjects.getReflectionObject(defStmt.getDef().get());
            if (defStmt.getRightOp() instanceof Constant constant) {
                try {
                    if (defStmt.getDef().get() instanceof JArrayRef arrayRef && defStmt.getRightOp() instanceof IntConstant) {
                        // Improvement: "transform into IntConstant" didn't deal with situation where index is a variable
                        defObject.setInstance(IntConstantResolve.cast((IntConstant) constant, defObject.getObjectClass().getComponentType()), ((IntConstant) arrayRef.getIndex()).getValue());
                    } else if (defStmt.getDef().get() instanceof JArrayRef arrayRef && defStmt.getRightOp() instanceof StringConstant) {
                        defObject.setInstance(ConstantResolve.resolve(constant), ((IntConstant) arrayRef.getIndex()).getValue());
                    } else {
                        defObject.setInstance(ConstantResolve.resolve(constant, GetClassFromType2.get(defObject.getDataType())));
                    }
                } catch (ClassCastException e) {
                    runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                }
            } else if (defStmt.getRightOp() instanceof JNewArrayExpr newArrayExpr && newArrayExpr.getSize() instanceof IntConstant) {
                // improvement: didn't deal with multi-dimension array.
                Class<?> arrayClass = GetClassFromType2.get(newArrayExpr.getBaseType());
                int arrayLength = ((IntConstant) newArrayExpr.getSize()).getValue();
                defObject.setInstance(Array.newInstance(arrayClass, arrayLength));
            } else if (defStmt.getRightOp() instanceof JNewArrayExpr newArrayExpr) {
                Class<?> arrayClass = GetClassFromType2.get(newArrayExpr.getBaseType());
                int arrayLength = (int) valueObjects.getReflectionObject(newArrayExpr.getSize()).getInstance();
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
                        defObject.setInstance(tryGetInstanceField(instanceFieldRef));
                    }
                }
            } else {
                try {
                    if (defStmt.getDef().get() instanceof JArrayRef arrayRef) {
                        // Improvement: didn't deal with situation where rightOp is a Expression
                        // Improvement: "transform into IntConstant" didn't deal with situation where index is a variable
                        defObject.setInstance(valueObjects.getReflectionObject(defStmt.getRightOp()).getInstance(), ((IntConstant) arrayRef.getIndex()).getValue());
                    } else {
                        defObject.setInstance(valueObjects.getReflectionObject(defStmt.getRightOp()).getInstance());
                    }
                } catch (NullPointerException e) {
                    runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                }
            }
        }
        // 方法调用语句，但不是赋值语句（例如secureRandom.nextBytes(keyBytes)是方法调用语句，但不是赋值语句， str = stringBuilder.toString()是赋值语句，且包含方法调用的赋值语句）
        if (valueFlowStmt instanceof JInvokeStmt) {
            AbstractInvokeExpr invokeExpr = valueFlowStmt.getInvokeExpr();
            // 实例调用语句
            if (invokeExpr instanceof AbstractInstanceInvokeExpr abstractInstanceInvokeExpr) {
                // specialInvoke语句，一般是构造方法的调用语句
                if (abstractInstanceInvokeExpr instanceof JSpecialInvokeExpr specialInvokeExpr && specialInvokeExpr.getMethodSignature().getName().equals("<init>")) {
                    try {
                        Constructor<?> constructor = baseType.getDeclaredConstructor(paramTypes);
                        constructor.setAccessible(true);
                        baseObject.setInstance(constructor.newInstance(paramInstances));
                    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                             InvocationTargetException e) {
                        runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                    }
                } else {
                    // 一般的实例调用语句
                    try {
                        Method invokeMethod = getMethod(baseType, abstractInstanceInvokeExpr.getMethodSignature().getName(), paramTypes);
                        invokeMethod.setAccessible(true);
                        invokeMethod.invoke(baseObject.getInstance(), paramInstances);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                    } catch (IllegalArgumentException e) {
                        try {
                            baseObject.resetClassLoader();
                            Method invokeMethod = getMethod(baseType, abstractInstanceInvokeExpr.getMethodSignature().getName(), paramTypes);
                            invokeMethod.setAccessible(true);
                            invokeMethod.invoke(baseObject.getInstance(), paramInstances);
                        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException ex) {
                            runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                        }
                    }
                }
            } else {
                // 静态方法调用
                try {
                    ClassLoader classLoader = AnalysisEnv.ClassLoader();
                    Class<?> invokeClass = classLoader.loadClass(invokeExpr.getMethodSignature().getDeclClassType().getFullyQualifiedName());
                    Method method = getMethod(invokeClass, invokeExpr.getMethodSignature().getName(), paramTypes);
                    method.setAccessible(true);
                    method.invoke(null, paramInstances);
                } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
                    runningExceptions.add("Caught Exception \"" + e.getMessage() + "\" at " + valueFlowStmt);
                }
            }
        }

    }

    // 启发式分析静态字段
    private Object tryGetStaticField(FieldSignature fieldSignature) {
        Class<?> baseClass = GetClassFromType2.get(fieldSignature.getDeclClassType());
        try {
            Field field = baseClass.getDeclaredField(fieldSignature.getName());
            field.setAccessible(true);
            assert Modifier.isStatic(field.getModifiers());
            return field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 启发式分析动态字段
    private Object tryGetInstanceField(JInstanceFieldRef instanceFieldRef) {
        Object resObject = null;
        FieldSignature fieldSignature = instanceFieldRef.getFieldSignature();
        if (AnalysisEnv.view().getClass(fieldSignature.getDeclClassType()).isPresent()) {
            SootClass sootClass = AnalysisEnv.view().getClass(fieldSignature.getDeclClassType()).get();
            if (!sootClass.getMethodsByName("<init>").isEmpty()) {
                SootMethod initMethod = sootClass.getMethodsByName("<init>").iterator().next();
                ListIterator<Stmt> stmtIterator = initMethod.getBody().getStmts().listIterator(initMethod.getBody().getStmts().size());

                List<Value> trackingValues = new ArrayList<>();
                HashMap<Value, List<ValueAssign>> valueAssigns = new HashMap<>();
                List<Stmt> resultStmts = new ArrayList<>();
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
                                resultStmts.add(stmt);
                            }
                        }
                        trackingValues.removeAll(removeValues);
                        trackingValues.addAll(addValues);
                    }
                }

                FocusedValueObjects valueObjects = new FocusedValueObjects();
                valueObjects.setMethodSignature(initMethod.getSignature());
                valueObjects.interProceduralObjects = new InterProceduralObjects();
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
                        solveOneVal(usedValue, valueAssigns.get(usedValue), valueObjects, solvedValues, valueDependencyMap, valueAssigns);
                    }
                    try {
                        solveOneVal(value, valueAssigns.get(value), valueObjects, solvedValues, valueDependencyMap, valueAssigns);
                    } catch (NullPointerException e) {
                        // System.out.println("[INFO] NullPointerException while solving \"" + value.toString() + "\"");
                    } catch (ExceptionInInitializerError | NoClassDefFoundError | ClassCastException |
                             IllegalArgumentException e) {
                        // System.out.println("[INFO] Exception :" + e.getMessage() + " while solving \"" + value.toString() + "\"");
                    }
                }
                resObject = valueObjects.getReflectionObject(instanceFieldRef).getInstance();
            }
        }
        return resObject;
    }

    // 根据Class对象，方法名，参数类型列表获取方法
    private Method getMethod(Class<?> baseClass, String methodName, Class<?>[] paramClasses) {
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

    // 检查两次执行结果的数据是否一样来检查随机性（重复生成测试）
    private boolean checkArrayRandomization(Object arr1, Object arr2) {
        boolean randomized = false;
        assert arr1.getClass().getComponentType().equals(arr2.getClass().getComponentType());
        Class<?> componentType = arr1.getClass().getComponentType();
        if (componentType.equals(int.class)) {
            randomized = !Arrays.equals((int[]) arr1, (int[]) arr2);
        }
        if (componentType.equals(byte.class)) {
            randomized = !Arrays.equals((byte[]) arr1, (byte[]) arr2);
        }
        if (componentType.equals(short.class)) {
            randomized = !Arrays.equals((short[]) arr1, (short[]) arr2);
        }
        if (componentType.equals(long.class)) {
            randomized = !Arrays.equals((long[]) arr1, (long[]) arr2);
        }
        if (componentType.equals(float.class)) {
            randomized = !Arrays.equals((float[]) arr1, (float[]) arr2);
        }
        if (componentType.equals(double.class)) {
            randomized = !Arrays.equals((double[]) arr1, (double[]) arr2);
        }
        if (componentType.equals(char.class)) {
            randomized = !Arrays.equals((char[]) arr1, (char[]) arr2);
        }
        if (componentType.equals(boolean.class)) {
            randomized = !Arrays.equals((boolean[]) arr1, (boolean[]) arr2);
        }
        return randomized;
    }

    public IntraResultNode getRoot() {
        return root;
    }

    public List<OneResult> getResults() {
        return results;
    }

    public HashMap<OneResult, Object[]> getSolvedResults() {
        return solvedResults;
    }

    public HashMap<OneResult, String> getArrayInfo() {
        return arrayInfo;
    }

    public HashMap<OneResult, String> getNullReason() {
        return nullReason;
    }

    public HashMap<OneResult, String> getResultSecurity() {
        return resultSecurity;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

}
