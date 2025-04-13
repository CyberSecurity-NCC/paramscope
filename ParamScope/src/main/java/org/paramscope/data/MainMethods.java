package org.paramscope.data;

import org.paramscope.analysis.AnalysisEnv;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

import java.util.ArrayList;
import java.util.List;

public class MainMethods {
    private static final List<MethodSignature> mainMethods = new ArrayList<>();

    public static List<MethodSignature> getMainMethods() {
        return mainMethods;
    }

    public static void setMainMethods(List<String> classNames) {
        for (String className : classNames) {
            JavaClassType classType = AnalysisEnv.view().getIdentifierFactory().getClassType(className);
            JavaSootClass javaSootClass = AnalysisEnv.view().getClass(classType).get();
            for (JavaSootMethod javaSootMethod : javaSootClass.getMethods()) {
                if (javaSootMethod.isMain(AnalysisEnv.view().getIdentifierFactory())) {
                    mainMethods.add(javaSootMethod.getSignature());
                }
            }
        }
    }
}
