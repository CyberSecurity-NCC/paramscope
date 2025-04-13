package org.paramscope.data;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.call.MethodInfo;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallRelation {
    private static final Map<MethodSignature, MethodInfo> applicationMethodAndapiMethodMap = new HashMap<>();
    private static final Map<JavaClassType, List<JavaClassType>> hierarchyMap = new HashMap<>();

    public static void buildCallRelation(List<String> classNames) {
        buildHierarchyRelation(classNames);
        applicationMethodsFillMethodMap(classNames);
        apiMethodsFillMethodMap();

        JavaView view = AnalysisEnv.view();

        int i = 0;
        for (String className : classNames) {
            i++;
            if (i % 100 == 0 || i == classNames.size()) {
                System.out.printf("\rClasses: " + i + " / " + classNames.size());
            }
            JavaClassType javaCT = view.getIdentifierFactory().getClassType(className);
            JavaSootClass javaSC = view.getClass(javaCT).get();
            if (javaSC.isConcrete() || (!javaSC.getMethods().isEmpty())) {
                for (JavaSootMethod javaSM : javaSC.getMethods()) {
                    MethodSignature callerMS = javaSM.getSignature();

                    if (javaSM.isConcrete()) {
                        try {
                            for (Stmt stmt : javaSM.getBody().getStmts()) {
                                if (stmt.containsInvokeExpr()) {
                                    AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
                                    MethodSignature calleeMS = invokeExpr.getMethodSignature();

                                    if (classNames.contains(calleeMS.getDeclClassType().toString()) && !applicationMethodAndapiMethodMap.containsKey(calleeMS)) {
                                        if (hierarchyMap.containsKey(calleeMS.getDeclClassType())) {
                                            for (JavaClassType parentJCT : hierarchyMap.get(calleeMS.getDeclClassType())) {

                                                boolean findParentMethod = false;
                                                if (view.getClass(parentJCT).isPresent()) {
                                                    for (JavaSootMethod parentMethodJST : view.getClass(parentJCT).get().getMethods()) {
                                                        if (parentMethodJST.getSignature().getSubSignature().equals(calleeMS.getSubSignature())) {
                                                            findParentMethod = true;
                                                            calleeMS = parentMethodJST.getSignature();
                                                            break;
                                                        }
                                                    }
                                                    if (findParentMethod) {
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (!(calleeMS.equals(callerMS)) && applicationMethodAndapiMethodMap.containsKey(calleeMS)) {
                                        CallSite callSite = new CallSite(callerMS, calleeMS, stmt.getPositionInfo(), stmt);
                                        MethodInfo callerMI = applicationMethodAndapiMethodMap.get(callerMS);
                                        MethodInfo calleeMI = applicationMethodAndapiMethodMap.get(calleeMS);
                                        callerMI.getCalleeList().add(calleeMS);
                                        calleeMI.getCallerList().add(callerMS);
                                        calleeMI.getCallSites().add(callSite);
                                    }
                                }

                            }
                        } catch (Exception e) {
                            System.out.println("[INFO] SootMethod get method body failed: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private static void apiMethodsFillMethodMap() {
        for (APIParamInfo apiMethod : APIList.getAllApiParamInfoList()) {
            JavaClassType apiJCT = AnalysisEnv.view().getIdentifierFactory().getClassType(apiMethod.getClassNameString());
            for (JavaSootMethod classJSM : AnalysisEnv.view().getClass(apiJCT).get().getMethods()) {
                MethodSignature methodSignature = classJSM.getSignature();
                if (methodSignature.getSubSignature().toString().equals(apiMethod.getSubMethodSignatureString())) {
                    apiMethod.setMethodSignature(methodSignature);
                    MethodInfo methodInfo = new MethodInfo(methodSignature);
                    methodInfo.setIsMain(false);
                    applicationMethodAndapiMethodMap.put(methodSignature, methodInfo);
                }
            }
        }
    }

    private static void applicationMethodsFillMethodMap(List<String> classNames) {
        for (String className : classNames) {
            JavaView view = AnalysisEnv.view();
            for (JavaSootMethod javaSM : view.getClass(view.getIdentifierFactory().getClassType(className)).get().getMethods()) {
                MethodSignature methodSignature = javaSM.getSignature();
                MethodInfo methodInfo = new MethodInfo(methodSignature);
                methodInfo.setIsMain(MainMethods.getMainMethods().contains(methodSignature));
                applicationMethodAndapiMethodMap.put(methodSignature, methodInfo);
            }
        }
    }

    public static void buildHierarchyRelation(List<String> classNames) {
        int i = 0;
        for (String className : classNames) {
            i++;
            if (i % 100 == 0 || i == classNames.size()) {
                System.out.printf("\rClasses: " + i + " / " + classNames.size());
            }
            JavaView view = AnalysisEnv.view();
            JavaClassType classType = view.getIdentifierFactory().getClassType(className);
            JavaSootClass javaSootClass = view.getClass(classType).get();
            List<JavaClassType> parents = new ArrayList<>();

            while (javaSootClass.hasSuperclass() && !(javaSootClass.getSuperclass().get().toString().equals("java.lang.Object"))) {
                JavaClassType parentClassType = javaSootClass.getSuperclass().get();
                parents.add(parentClassType);
                if (view.getClass(parentClassType).isPresent()) {
                    javaSootClass = view.getClass(parentClassType).get();
                } else {
                    break;
                }
            }
            hierarchyMap.put(classType, parents);
        }
        System.out.println();
    }

    public static Map<MethodSignature, MethodInfo> getApplicationMethodAndapiMethodMap() {
        return applicationMethodAndapiMethodMap;
    }

    public static Map<JavaClassType, List<JavaClassType>> getHierarchyMap() {
        return hierarchyMap;
    }
}
