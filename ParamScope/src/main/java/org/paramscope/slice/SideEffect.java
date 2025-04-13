package org.paramscope.slice;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.api.ModifyBaseVirtualInvokeMethod;
import org.paramscope.data.ModifyBaseVirtualInvokeMethodSet;
import sootup.core.frontend.ResolveException;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JInterfaceInvokeExpr;
import sootup.core.jimple.common.expr.JSpecialInvokeExpr;
import sootup.core.jimple.common.expr.JVirtualInvokeExpr;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JParameterRef;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.stmt.*;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.*;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootField;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.jimple.basic.JavaLocal;
import sootup.java.core.types.JavaClassType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

public class SideEffect {

    public static FocusedValues defUseAnalysis(
            Stmt stmt,
            FocusedValues trackingValues,
            HashMap<Value, List<ValueAssign>> trackedValues,
            MethodSignature stmtMethodMS
    ) throws ResolveException {
        FocusedValues defValues = new FocusedValues(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), stmtMethodMS);

        checkRandomizedArrays(stmt, trackingValues, trackedValues);

        if (stmt instanceof AbstractDefinitionStmt defStmt && trackingValues.contains(defStmt.getLeftOp())) {
            LValue defValue = defStmt.getLeftOp();
            defValues.add(defValue);

            trackedValues.computeIfAbsent(defValue, k -> new ArrayList<>()).add(new ValueAssign(defStmt.getRightOp(), AssignWay.ASSIGN));
        }

        if (stmt.containsInvokeExpr()
                && stmt.getInvokeExpr() instanceof JSpecialInvokeExpr specialInvokeExpr
                && specialInvokeExpr.getMethodSignature().getName().equals("<init>")
                && trackingValues.contains(specialInvokeExpr.getBase())) {
            defValues.add(specialInvokeExpr.getBase());
            trackedValues.computeIfAbsent(specialInvokeExpr.getBase(), k -> new ArrayList<>()).add(new ValueAssign(specialInvokeExpr, AssignWay.INVOKE_AS_BASE));
            return defValues;
        }

        if (stmt.containsInvokeExpr()) {
            AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
            MethodSignature calledMethodMS = invokeExpr.getMethodSignature();
            ArrayList<Integer> focusedTracingParamList = new ArrayList<>();

            for (int paramNum = 0; paramNum < invokeExpr.getArgCount(); paramNum++) {
                Immediate paramVal = invokeExpr.getArg(paramNum);
                if (paramVal instanceof JavaLocal javaLocal && trackingValues.contains(javaLocal)) {
                    Type paramType = javaLocal.getType();
                    if (paramType instanceof ReferenceType rType && isMutableType(rType)) {
                        focusedTracingParamList.add(paramNum);
                    }
                }
            }

            if (invokeExpr.getArgCount() > 0 && !focusedTracingParamList.isEmpty()) {
                List<Integer> modifiesParams = modifiesTracingParameters(calledMethodMS, focusedTracingParamList, stmtMethodMS);
                if (modifiesParams.size() > 0) {
                    for (int paramNum : modifiesParams) {
                        // INFO: For InvokeExpr, the parameters will be translated into Immediate(Value) by SootUp, so only need to check JavaLocal parameters
                        JavaLocal paramVal = (JavaLocal) invokeExpr.getArg(paramNum);
                        defValues.add(paramVal);
                        trackedValues.computeIfAbsent(paramVal, k -> new ArrayList<>()).add(new ValueAssign(invokeExpr, AssignWay.INVOKE_AS_PARAM));
                    }
                }
            }

            if (invokeExpr instanceof JVirtualInvokeExpr virtualInvokeExpr) {
                if (modifiesInvokeBase(virtualInvokeExpr) && !checkMethodReturnThisAndHasBeenAssigned(stmt, trackingValues) && trackingValues.contains(virtualInvokeExpr.getBase())) {
                    trackedValues.computeIfAbsent(virtualInvokeExpr.getBase(), k -> new ArrayList<>()).add(new ValueAssign(virtualInvokeExpr, AssignWay.INVOKE_AS_BASE));
                    defValues.add(virtualInvokeExpr.getBase());
                }
            }

            if (invokeExpr instanceof JInterfaceInvokeExpr interfaceInvokeExpr) {
                if (modifiesInvokeBase(interfaceInvokeExpr) && !checkMethodReturnThisAndHasBeenAssigned(stmt, trackingValues) && trackingValues.contains(interfaceInvokeExpr.getBase())) {
                    trackedValues.computeIfAbsent(interfaceInvokeExpr.getBase(), k -> new ArrayList<>()).add(new ValueAssign(interfaceInvokeExpr, AssignWay.INVOKE_AS_BASE));
                    defValues.add(interfaceInvokeExpr.getBase());
                }
            }
        }
        return defValues;
    }

    public static List<Integer> SFdefUseAnalysis(Stmt stmt, List<JStaticFieldRef> trackingSFs, IntraResult intraResult) {
        FocusedValues useValues = new FocusedValues(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), intraResult.getCallSite().getCaller());
        ArrayList<Integer> trackingParams = new ArrayList<>();

        MethodSignature calledMS = stmt.getInvokeExpr().getMethodSignature();
        if (AnalysisEnv.view().getMethod(calledMS).isPresent()) {
            JavaSootMethod calledSM = AnalysisEnv.view().getMethod(calledMS).get();
            // construct intraTrackingValues with trackingSFs reference, when adding a StaticFieldRef in intraTracking, trackingSFs will also add.
            FocusedValues intraTrackingValues = new FocusedValues(new ArrayList<>(), trackingSFs, new ArrayList<>(), new ArrayList<>(), calledMS);
            HashMap<Value, List<ValueAssign>> intraTrackedValues = intraResult.getStmtFieldAssigns().computeIfAbsent(stmt, k -> new HashMap<>());

            if (calledSM.isConcrete()) {
                ListIterator<Stmt> backwardIterator = calledSM.getBody().getStmts().listIterator(calledSM.getBody().getStmts().size());
                while (backwardIterator.hasPrevious()) {
                    Stmt intraStmt = backwardIterator.previous();
                    if (intraStmt instanceof AbstractDefinitionStmt defStmt) {
                        if (intraTrackingValues.contains(defStmt.getDef().get())) {
                            List<ValueAssign> valueAssigns = intraTrackedValues.computeIfAbsent(defStmt.getDef().get(), k -> new ArrayList<>());
                            valueAssigns.add(new ValueAssign(defStmt.getRightOp(), AssignWay.ASSIGN));

                            intraTrackingValues.remove(defStmt.getDef().get());

                            List<JStaticFieldRef> removeSFs = new ArrayList<>();
                            for (JStaticFieldRef SFRef : trackingSFs) {
                                if (SFRef.equivTo(defStmt.getDef().get())) {
                                    removeSFs.add(SFRef);
                                }
                            }
                            trackingSFs.removeAll(removeSFs);

                            if (defStmt.getRightOp() instanceof JParameterRef paramRef) {
                                trackingParams.add(paramRef.getIndex());
                                continue;
                            }

                            intraTrackingValues.addAllFromStmtUses(intraStmt);
                            for (JStaticFieldRef staticFieldRef : intraTrackingValues.getStaticFieldRefTrackers().keySet()) {
                                intraResult.getStaticFieldRefTrackers().computeIfAbsent(staticFieldRef, k -> intraTrackingValues.getStaticFieldRefTrackers().get(k));
                            }
                        }
                    }
                    if (intraStmt.containsInvokeExpr()) {
                        // Side Effect analysis and others, for future work.
                    }
                }
            }

            if (!trackingSFs.isEmpty() && intraTrackingValues.isEmpty()) {
                intraResult.getStmtFieldAssigns().remove(stmt);
            }

            if (!intraTrackingValues.isEmptyStaticField()) {
                ArrayList<JStaticFieldRef> addSFs = new ArrayList<>();
                for (JStaticFieldRef staticFieldRef : intraTrackingValues.getFocusedStaticFields()) {
                    for (JStaticFieldRef trackingStaticFieldRef : trackingSFs) {
                        if (trackingStaticFieldRef.equivTo(staticFieldRef)) {
                            break;
                        }
                        if (!addSFs.contains(staticFieldRef)) {
                            addSFs.add(staticFieldRef);
                        }
                    }
                    addSFs.add(staticFieldRef);
                }
                ArrayList<JStaticFieldRef> removeSFs = new ArrayList<>();
                for (JStaticFieldRef addSF : addSFs) {
                    for (JStaticFieldRef trackingSF : trackingSFs) {
                        if (trackingSF.equivTo(addSF)) {
                            removeSFs.add(addSF);
                            break;
                        }
                    }
                    if (removeSFs.contains(addSF)) {
                        continue;
                    }
                }
                addSFs.removeAll(removeSFs);
                trackingSFs.addAll(addSFs);
            }
        }

        return trackingParams;
    }

    private static boolean isMutableType(ReferenceType rType) {
        if (rType instanceof ArrayType) {
            return true;
        }
        if (rType instanceof NullType) {
            return false;
        }
        if (rType instanceof JavaClassType classType) {
            if (AnalysisEnv.view().getClass(classType).isPresent()) {
                JavaSootClass sootClass = AnalysisEnv.view().getClass(classType).get();
                for (ClassType implementsClassType : sootClass.getInterfaces()) {
                    if (implementsClassType.toString().equals("java.lang.Constable")) {
                        return false;
                    }
                }
                for (JavaSootField sootField : sootClass.getFields()) {
                    if (!sootField.isPrivate() && !sootField.isFinal()) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
        return false;
    }

    private static void checkRandomizedArrays(Stmt stmt, FocusedValues trackingValues, HashMap<Value, List<ValueAssign>> trackedValues) {
        List<Value> defVals = new ArrayList<>();
        List<List<Value>> removeVals = new ArrayList<>();
        List<Local> addVals = new ArrayList<>();
        for (MethodJavaLocal methodJavaLocal : trackingValues.getFocusedLocals()) {
            JavaLocal local = methodJavaLocal.javaLocal();
            if (local.getType() instanceof ArrayType) {
                if (stmt instanceof JInvokeStmt invokeStmt) {
                    AbstractInvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                    if (isSecureRandomized(invokeExpr, local)) {
                        defVals.add(local);
                        trackingValues.getSecureRandomizedArrays().add(local);
                        trackedValues.computeIfAbsent(local, k -> new ArrayList<>()).add(new ValueAssign(invokeExpr, AssignWay.INVOKE_AS_PARAM));
                        removeVals.add(defVals);
                        if (stmt.containsInvokeExpr() && stmt.getInvokeExpr() instanceof JVirtualInvokeExpr virtualInvokeExpr) {
                            addVals.add(virtualInvokeExpr.getBase());
                        }
                    } else if (isInsecureRandomized(invokeExpr, local)) {
                        defVals.add(local);
                        trackingValues.getInsecureRandomizedArrays().add(local);
                        trackedValues.computeIfAbsent(local, k -> new ArrayList<>()).add(new ValueAssign(invokeExpr, AssignWay.INVOKE_AS_PARAM));
                        removeVals.add(defVals);
                        if (stmt.containsInvokeExpr() && stmt.getInvokeExpr() instanceof JVirtualInvokeExpr virtualInvokeExpr) {
                            addVals.add(virtualInvokeExpr.getBase());
                        }
                    }
                }
            }
        }
        for (List<Value> removeVal : removeVals) {

        }
        for (Local addVal : addVals) {
            trackingValues.add(addVal);
        }
    }

    private static boolean isSecureRandomized(AbstractInvokeExpr invokeExpr, JavaLocal local) {
        return invokeExpr.getMethodSignature().getDeclClassType().toString().equals("java.security.SecureRandom")
                && invokeExpr.getMethodSignature().getName().equals("nextBytes")
                && invokeExpr.getArg(0).equivTo(local);
    }

    private static boolean isInsecureRandomized(AbstractInvokeExpr invokeExpr, JavaLocal local) {
        return invokeExpr.getMethodSignature().getDeclClassType().toString().equals("java.util.Random")
                && invokeExpr.getMethodSignature().getName().equals("nextBytes")
                && invokeExpr.getArg(0).equivTo(local);
    }


    private static List<Integer> modifiesTracingParameters(MethodSignature methodSignature, ArrayList<Integer> paramList, MethodSignature stmtMethodSignature) {
        FocusedValues trackingValues = new FocusedValues(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), stmtMethodSignature);
        List<JParameterRef> result = new ArrayList<>();
        HashMap<Value, JParameterRef> valParamRefMap = new HashMap<>();
        if (AnalysisEnv.view().getMethod(methodSignature).isPresent()) {
            JavaSootMethod javaSM = AnalysisEnv.view().getMethod(methodSignature).get();

            for (Stmt stmt : javaSM.getBody().getStmts()) {
                if (stmt instanceof JIdentityStmt identityStmt
                        && identityStmt.getRightOp() instanceof JParameterRef paramRef) {

                    if (paramList.contains(paramRef.getIndex())) {
                        LValue leftVal = ((JIdentityStmt) stmt).getLeftOp();
                        valParamRefMap.put(leftVal, paramRef);
                        trackingValues.add(leftVal);
                    } else {
                        continue;
                    }
                }
                if (!trackingValues.isEmpty()) {
                    if (stmt instanceof JAssignStmt && !stmt.containsInvokeExpr()) {
                        if (stmt.getDef().isPresent() && trackingValues.contains(stmt.getDef().get())) {
                            if (stmt.getDef().get() instanceof JArrayRef arrayRef) {
                                Value base = arrayRef.getBase();
                                if (valParamRefMap.containsKey(base)) {
                                    result.add(valParamRefMap.get(base));
                                }
                            }
                            if (valParamRefMap.containsKey(stmt.getDef().get())) {
                                result.add(valParamRefMap.get(stmt.getDef().get()));
                            }
                        }
                    }
                    if (stmt instanceof JInvokeStmt || (stmt instanceof JAssignStmt && stmt.containsInvokeExpr())) {
                        if (!methodSignature.equals(stmt.getInvokeExpr().getMethodSignature())) {
                            try {
                                FocusedValues defValues = defUseAnalysis(stmt, trackingValues, new HashMap<>(), methodSignature);
                                result.addAll(defValues.allValues().stream().filter(trackingValues::contains).map(defVal -> valParamRefMap.get(defVal)).toList());
                            } catch (ResolveException e) {
                                // System.out.println("sootup.core.frontend.ResolveException: " + e.getMessage() + "while slicing analysis.");
                                // System.out.println("    Potentially caused by not concrete method.");
                            }
                        }
                    }
                }
            }
        }
        return result.stream().map(JParameterRef::getIndex).toList();
    }

    private static boolean modifiesInvokeBase(JVirtualInvokeExpr expr) {
        return ModifyBaseVirtualInvokeMethodSet.getSet().contains(new ModifyBaseVirtualInvokeMethod(expr.getMethodSignature().getDeclClassType().toString(), expr.getMethodSignature().getName()));
    }

    private static boolean modifiesInvokeBase(JInterfaceInvokeExpr expr) {
        return ModifyBaseVirtualInvokeMethodSet.getSet().contains(new ModifyBaseVirtualInvokeMethod(expr.getMethodSignature().getDeclClassType().toString(), expr.getMethodSignature().getName()));
    }

    private static boolean checkMethodReturnThisAndHasBeenAssigned(Stmt stmt, FocusedValues trackingValues) {
        if (stmt instanceof JAssignStmt && stmt.containsInvokeExpr() && stmt.getInvokeExpr() instanceof JVirtualInvokeExpr virtualInvokeExpr) {
            MethodSignature calleeMS = virtualInvokeExpr.getMethodSignature();
            JavaSootMethod calleeSM = AnalysisEnv.view().getMethod(calleeMS).get();
            // Only if ALL returnStmts returns "this", consider method return "this".
            for (Stmt returnStmt : calleeSM.getBody().getStmtGraph().getTails()) {
                if (returnStmt instanceof JReturnStmt retStmt
                        && retStmt.getOp() instanceof JavaLocal local
                        && !local.getName().equals("this")) {
                    return false;
                }
                return trackingValues.contains(stmt.getDef().get());
            }
        }
        return false;
    }
}

