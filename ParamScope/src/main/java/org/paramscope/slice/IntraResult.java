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
    // 这两个关于数组的不用管，写的逻辑不是很清晰，不用关注与数组安全性相关的分析，在结果层根据字符串进行了一定的分析
    private final List<Value> secureRandomizedArrays;
    private final List<Value> insecureRandomizedArrays;
    // 单个目标API信息的封装类
    APIParamInfo apiParamInfo;
    // API关注的参数的封装类
    List<MethodParamRef> methodParamRefs;
    // 调用点封装类
    CallSite callSite;
    // 记录了在分析过程中每条语句，每个被赋值的对应的赋值方式
    HashMap<Stmt, HashMap<Value, List<ValueAssign>>> stmtFieldAssigns;
    //（重要）如果关注的值中包含方法参数（则需要跨方法分析），将记录到此列表中
    List<MethodParamRef> tracingParamRefs;
    //（重要）如果关注的值中包含静态字段（则需要跨方法分析），将记录到此列表中
    List<JStaticFieldRef> tracingStaticFieldRefs;
    // 这个关于静态字段的启发式分析的不用管，写的逻辑不是很清晰
    HashMap<JStaticFieldRef, StaticFieldRefTracker> staticFieldRefTrackers;
    // （重要）一次方法内分析的切片语句的结果
    List<Stmt> resultStmts;
    // 记录了每条切片语句的被定义的值，一个FocusedValues类封装了“关注的值”数据结构（该FocusedValues类比较重要）
    HashMap<Stmt, FocusedValues> stmtDefValues;
    // 记录了每个被赋值的值对应的赋值方式
    HashMap<Value, List<ValueAssign>> trackedValues;
    // 如果目标API参数是常量，直接记录
    HashMap<MethodParamRef, Constant> constResults;
    // 记录“动态字段的实例”（例如a.fieldA中的a）是否需要追踪
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
        // 正在关注的值中 1.包含此来自方法参数，2.包含静态字段（静态字段可能被跨方法赋值）
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
