package org.paramscope.slice;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.stmt.Stmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class IntraResult {
    private final List<Value> secureRandomizedArrays;
    private final List<Value> insecureRandomizedArrays;
    APIParamInfo apiParamInfo;
    List<MethodParamRef> methodParamRefs;
    CallSite callSite;
    HashMap<Stmt, HashMap<Value, List<ValueAssign>>> stmtFieldAssigns;
    List<MethodParamRef> tracingParamRefs;
    List<JStaticFieldRef> tracingStaticFieldRefs;
    HashMap<JStaticFieldRef, StaticFieldRefTracker> staticFieldRefTrackers;
    List<Stmt> resultStmts;
    HashMap<Stmt, FocusedValues> stmtDefValues;
    HashMap<Value, List<ValueAssign>> trackedValues;
    HashMap<MethodParamRef, Constant> constResults;
    boolean thisOfCallerNeedsTracking;

    public IntraResult(APIParamInfo apiParamInfo, CallSite callSite) {
        this.apiParamInfo = apiParamInfo;
        this.methodParamRefs = new ArrayList<>();
        this.callSite = callSite;
        this.tracingStaticFieldRefs = new ArrayList<>();
        this.stmtFieldAssigns = new HashMap<>();
        this.tracingParamRefs = new ArrayList<>();
        this.resultStmts = new ArrayList<>();
        this.stmtDefValues = new HashMap<>();
        this.trackedValues = new HashMap<>();
        this.constResults = new HashMap<>();
        this.secureRandomizedArrays = new ArrayList<>();
        this.insecureRandomizedArrays = new ArrayList<>();
        this.staticFieldRefTrackers = new HashMap<>();
    }

    public boolean needsTracking() {
        boolean tracingFieldsAreConcrete = true;
        ArrayList<JStaticFieldRef> ghostFields = new ArrayList<>();
        for (JStaticFieldRef staticFieldRef : tracingStaticFieldRefs) {
            if (!AnalysisEnv.view().getClass(staticFieldRef.getFieldSignature().getDeclClassType()).isPresent()) {
                tracingFieldsAreConcrete = false;
                ghostFields.add(staticFieldRef);
            }
        }
        tracingStaticFieldRefs.removeAll(ghostFields);

        return (!tracingStaticFieldRefs.isEmpty() && tracingFieldsAreConcrete) || !tracingParamRefs.isEmpty() || thisOfCallerNeedsTracking;
    }

    public APIParamInfo getApiParamInfo() {
        return apiParamInfo;
    }

    public List<MethodParamRef> getMethodParamRefs() {
        return methodParamRefs;
    }

    public CallSite getCallSite() {
        return callSite;
    }

    public List<JStaticFieldRef> getTracingStaticFieldRefs() {
        return tracingStaticFieldRefs;
    }

    public HashMap<Stmt, HashMap<Value, List<ValueAssign>>> getStmtFieldAssigns() {
        return stmtFieldAssigns;
    }

    public List<MethodParamRef> getTracingParamRefs() {
        return tracingParamRefs;
    }

    public List<Stmt> getResultStmts() {
        return resultStmts;
    }

    public HashMap<Stmt, FocusedValues> getStmtDefValues() {
        return stmtDefValues;
    }

    public HashMap<Value, List<ValueAssign>> getTrackedValues() {
        return trackedValues;
    }

    public HashMap<MethodParamRef, Constant> getConstResults() {
        return constResults;
    }

    public boolean getThisOfCallerNeedsTracking() {
        return thisOfCallerNeedsTracking;
    }

    public void setThisOfCallerNeedsTracking(boolean trackCallerBase) {
        this.thisOfCallerNeedsTracking = trackCallerBase;
    }

    public HashMap<JStaticFieldRef, StaticFieldRefTracker> getStaticFieldRefTrackers() {
        return staticFieldRefTrackers;
    }

    public List<Value> getSecureRandomizedArrays() {
        return secureRandomizedArrays;
    }

    public List<Value> getInsecureRandomizedArrays() {
        return insecureRandomizedArrays;
    }

}
