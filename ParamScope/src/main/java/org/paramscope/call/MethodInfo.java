package org.paramscope.call;

import sootup.core.signatures.MethodSignature;

import java.util.ArrayList;
import java.util.List;

public class MethodInfo {
    private final MethodSignature methodSignature;
    private Boolean isMain;
    private final List<MethodSignature> callerList;
    private final List<MethodSignature> calleeList;
    private final List<CallSite> callSites;

    public MethodInfo(MethodSignature methodSignature) {
        this.methodSignature = methodSignature;
        this.callerList = new ArrayList<>();
        this.calleeList = new ArrayList<>();
        this.callSites = new ArrayList<>();
    }

    public Boolean getIsMain() {
        return isMain;
    }

    public void setIsMain(Boolean main) {
        isMain = main;
    }

    public List<MethodSignature> getCallerList() {
        return callerList;
    }

    public List<MethodSignature> getCalleeList() {
        return calleeList;
    }

    public List<CallSite> getCallSites() {
        return callSites;
    }

    public MethodSignature getMethodSignature() {
        return methodSignature;
    }
}
