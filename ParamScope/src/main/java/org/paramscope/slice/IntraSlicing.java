package org.paramscope.slice;

import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.data.APIList;
import org.paramscope.data.CallRelation;
import sootup.analysis.intraprocedural.BackwardFlowAnalysis;
import sootup.core.frontend.ResolveException;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JVirtualInvokeExpr;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.ref.JParameterRef;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.ref.JThisRef;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.Type;
import sootup.java.core.jimple.basic.JavaLocal;
import sootup.java.core.types.JavaClassType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

// // 方法内逆向数据流分析需要继承 BackwardFlowAnalysis 类，在字段中可以记录一些分析结果，继承的 flowThrough 方法中写分析逻辑
public class IntraSlicing extends BackwardFlowAnalysis {
    // 目标API的封装类
    private final APIParamInfo apiParamInfo;
    // 调用点
    private final CallSite callSite;
    // （重要）记录了方法内逆向切片分析结果的封装类
    private final IntraResult intraResult;
    // （重要）记录了分析过程中“正在关注的值”的封装类（即切片过程中需要追踪的值）
    private final FocusedValues trackingValues;
    // 当方法不是静态方法时，记录了该方法的this实例是否需要追踪（如this.methodA()中的this）
    private final boolean trackBaseOfTheMethod;
    // 从方法的最后一条语句逆向分析时，记录是否找到了关注的调用点（从调用点开始分析）
    private boolean haveFoundCallSite;

    ConstantFolding constantFolding;
    SootMethod callerSM;

    public IntraSlicing(SootMethod callerSM, StmtGraph graph, CallSite callSite, APIParamInfo apiParamInfo, List<JStaticFieldRef> trackingStaticFields, boolean trackBaseOfTheMethod) {
        super(graph);
        this.callSite = callSite;
        this.apiParamInfo = apiParamInfo;
        this.trackingValues = new FocusedValues(new ArrayList<>(), trackingStaticFields, new ArrayList<>(), new ArrayList<>(), callSite.getCaller());
        this.intraResult = new IntraResult(apiParamInfo, callSite);
        this.trackBaseOfTheMethod = trackBaseOfTheMethod || APIList.getTrackBaseApiParamInfoList().contains(apiParamInfo);
        this.callerSM = callerSM;
        this.constantFolding = new ConstantFolding(callerSM);
        // 调用 execute() 方法对每条语句进行 flowThrough() 中的分析逻辑
        execute();
        intraResult.getTracingStaticFieldRefs().addAll(trackingValues.getFocusedStaticFields());
        intraResult.getStaticFieldRefTrackers().putAll(trackingValues.getStaticFieldRefTrackers());

        if (!trackingValues.isEmptyInstanceField()) {
            for (JInstanceFieldRef instanceFieldRef : trackingValues.getFocusedInstanceFields()) {
                if (instanceFieldRef.getBase().getName().equals("this")) {
                    intraResult.setThisOfCallerNeedsTracking(true);
                }
            }
        }

        ArrayList<JStaticFieldRef> definedSFs = new ArrayList<>();
        for (JStaticFieldRef staticFieldRef : intraResult.getTracingStaticFieldRefs()) {
            if (intraResult.getStaticFieldRefTrackers().containsKey(staticFieldRef) && intraResult.getStaticFieldRefTrackers().get(staticFieldRef).getTrackedObj() != null) {
                definedSFs.add(staticFieldRef);
            }
        }
        intraResult.getTracingStaticFieldRefs().removeAll(definedSFs);

        if (!trackingValues.getInsecureRandomizedArrays().isEmpty()) {
            intraResult.getInsecureRandomizedArrays().addAll(trackingValues.getInsecureRandomizedArrays());
        }
        if (!trackingValues.getSecureRandomizedArrays().isEmpty()) {
            intraResult.getSecureRandomizedArrays().addAll(trackingValues.getSecureRandomizedArrays());
        }
    }

    @Override
    protected void flowThrough(@Nonnull Object in, Stmt stmt, @Nonnull Object out) {
        // in和out 是 sootUp 封装的每个语句的 in和out 的逻辑，没用上
        List<Value> inValues = (List<Value>) in;
        List<Value> outValues = (List<Value>) out;
        outValues.addAll(inValues);
        if(!constantFolding.getFilteredStmts().contains(stmt)){
            return;
        }

        // 找到调用点时进入以下逻辑
        if (needsCheck() && findCallSite(stmt)) {
            // 找到调用点
            this.haveFoundCallSite = true;
            AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
            MethodSignature calledMS = invokeExpr.getMethodSignature();
            // 获取关注的API的关注的参数位置（第0/1/2/3/...个参数）
            for (int param : apiParamInfo.getParamPosList()) {
                Type paramType = calledMS.getSubSignature().getParameterTypes().get(param);
                Value paramValue = invokeExpr.getArg(param);
                MethodParamRef methodParamRef = new MethodParamRef(new JParameterRef(paramType, param), apiParamInfo.getMethodSignature());

                // 若实际情况是常量则记录常量，若实际情况是变量（临时变量/栈变量）则加入到trackingValues中（正在追踪的值）
                if (paramValue instanceof Constant constant) {
                    intraResult.getConstResults().put(methodParamRef, constant);
                } else if (paramValue instanceof JavaLocal local) {
                    intraResult.getMethodParamRefs().add(methodParamRef);
                    trackingValues.add(local);
                }
            }

            // 如果需要追踪方法的实例，则同样需要将该实例的变量加入trackingValues
            if (this.trackBaseOfTheMethod) {
                if (invokeExpr instanceof JVirtualInvokeExpr virtualInvokeExpr) {
                    trackingValues.add(virtualInvokeExpr.getBase());
                }
            }
            // 将语句加入切片结果
            intraResult.getResultStmts().add(stmt);
            return;
        }

        if (trackingValues.isEmpty()) {

        } else if (haveFoundCallSite) {     // trackingValues不为空且找到调用点则进入以下逻辑
            try {
                // 数组安全性相关，不用管
                List<Local> alreadySecureRandomizedArrays = List.copyOf(trackingValues.getSecureRandomizedArrays());
                List<Local> alreadyInsecureRandomizedArrays = List.copyOf(trackingValues.getInsecureRandomizedArrays());

                // 将def-use分析抽象为SideEffect.defUseAnalysis()方法，获取一条语句中被定义的值
                // （重要）内部逻辑可以视作“六个值传递/值变换”的分析过程，返回该语句中被定义的值
                // 例： 对于 str = str1 + str2 语句，则defs包含“str”
                // 例： 对于 secureRandom.nextbytes(keyBytes) 语句，则defs包含“keyBytes”
                // 例： 对于 staticMethodA(); （其中该静态方法修改了某个关注的静态变量，例如fieldA），则defs包含“fieldA”
                FocusedValues defs = SideEffect.defUseAnalysis(stmt, trackingValues, intraResult.getTrackedValues(), callSite.getCaller());
                // （重要）将def-use分析抽象为SideEffect.defUseAnalysis()方法，获取一条语句中被定义的值
                FocusedValues uses = new FocusedValues(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), callSite.getCaller());

                // 一些对于静态字段的分析，这里的逻辑（line 136-150）写的不好，可以不用看
                ArrayList<JStaticFieldRef> definedStaticFields = new ArrayList<>();
                List<Integer> params = new ArrayList<>();
                if (!trackingValues.isEmptyStaticField() && haveFoundCallSite && stmt.containsInvokeExpr()) {
                    FocusedValues originStaticFields = new FocusedValues(new ArrayList<>(), new ArrayList<>(trackingValues.getFocusedStaticFields()), new ArrayList<>(), new ArrayList<>(), callSite.getCaller());
                    params.addAll(SideEffect.SFdefUseAnalysis(stmt, trackingValues.getFocusedStaticFields(), intraResult));

                    originStaticFields.removeAll(trackingValues);
                    definedStaticFields.addAll(originStaticFields.getFocusedStaticFields());
                }

                // 数组安全性相关逻辑，后面没用上，跳过
                List<Local> newSecureRandomizedArrays = new ArrayList<>(List.copyOf(trackingValues.getSecureRandomizedArrays()));
                newSecureRandomizedArrays.removeAll(alreadySecureRandomizedArrays);
                List<Local> newInsecureRandomizedArrays = new ArrayList<>(List.copyOf(trackingValues.getInsecureRandomizedArrays()));
                newInsecureRandomizedArrays.removeAll(alreadyInsecureRandomizedArrays);

                // def为空则跳过
                if (defs.isEmpty() && definedStaticFields.isEmpty() && (newSecureRandomizedArrays.isEmpty() && newInsecureRandomizedArrays.isEmpty())) {
                    return;
                }

                if (defs.isEmpty() && (!newSecureRandomizedArrays.isEmpty() || !newInsecureRandomizedArrays.isEmpty())) {
                    defs.addAll(trackingValues.getSecureRandomizedArrays());
                    defs.addAll(trackingValues.getInsecureRandomizedArrays());
                    intraResult.getResultStmts().add(stmt);
                    intraResult.getStmtDefValues().put(stmt, defs);
                    return;
                }

                // def中对静态字段的分析不是很完善，所以可能存在defs中不包含被定义的静态字段，但在definedStaticFields中分析到了，defs和definedStaticFields都可能包含被定义的值
                // defs 为空但 definedStaticFields 不为空，则存在被定义的静态字段，需要将所有使用的值加入uses
                if (defs.isEmpty() && !definedStaticFields.isEmpty()) {
                    uses.addAll(params.stream().map(index -> stmt.getInvokeExpr().getArg(index)).toList());
                } else {
                    // defs 不为空，则存在被定义的值，需要将所有使用的值加入uses
                    if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getRightOp() instanceof JParameterRef parameterRef) {
                        intraResult.getTracingParamRefs().add(new MethodParamRef(parameterRef, callSite.getCaller()));
                    }
                    if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getRightOp() instanceof JThisRef) {
                        intraResult.setThisOfCallerNeedsTracking(true);
                    }
                    uses.addAllFromStmtUses(stmt);
                }
                // 一条语句的defs，从trackingValues中移除被定义的值，并加入使用的值
                // 例如 line 10: str = str1 + str2;
                //     line 11: Cipher.getInstance(str);
                //     在分析 line 10 的语句前，trackingValues包含str
                //     在分析 line 10 的语句时，defs包含“str”，uses包含“str1”和“str2”
                //     在分析 line 10 的语句后，trackingValues将去掉str并添加“str1”和“str2”

                defs.addAll(definedStaticFields);
                intraResult.getStmtDefValues().put(stmt, defs);

                trackingValues.removeAll(defs);
                trackingValues.addAll(uses);
                // 将语句加入切片结果
                if (!intraResult.getResultStmts().contains(stmt)) {
                    intraResult.getResultStmts().add(stmt);
                }
            } catch (ResolveException e) {
                // System.out.println("[INFO] caught ResolveException at \"" + stmt.toString() + "\"");
                // System.out.println("    " + e.getMessage());
            } catch (IllegalStateException e) {
                // System.out.println("[INFO] caught IllegalStateException at \"" + stmt.toString() + "\"");
                // System.out.println("    " + e.getMessage());
            }
        }
    }

    private boolean needsCheck() {
        return intraResult.getMethodParamRefs().size() < apiParamInfo.getParamPosList().size() || !trackingValues.isEmptyStaticField() || trackBaseOfTheMethod;
    }

    // 匹配该语句是否包含调用点
    private boolean findCallSite(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
            MethodSignature calledMS = invokeExpr.getMethodSignature();
            // 在分析是否包含调用点时，会通过hierarchyCallAnalysis 检查继承自父类的方法
            return (calledMS.equals(apiParamInfo.getMethodSignature()) || hierarchyCallAnalysis(calledMS))
                    && callSite.getPos().getStmtPosition().equals(stmt.getPositionInfo().getStmtPosition())
                    && stmt.equivTo(callSite.getInvokeStmt());
        } else {
            return false;
        }
    }

    private boolean hierarchyCallAnalysis(MethodSignature calledMS) {
        if (CallRelation.getHierarchyMap().get((JavaClassType) calledMS.getDeclClassType()) == null) {
            return false;
        }
        List<JavaClassType> parentClassTypes = CallRelation.getHierarchyMap().get((JavaClassType) calledMS.getDeclClassType());
        JavaClassType apiClassType = (JavaClassType) apiParamInfo.getMethodSignature().getDeclClassType();
        return parentClassTypes.contains(apiClassType);
    }

    @Nonnull
    @Override
    protected Object newInitialFlow() {
        return new ArrayList<Value>();
    }

    @Override
    protected void merge(@Nonnull Object in1, @Nonnull Object in2, @Nonnull Object out) {

    }

    @Override
    protected void copy(@Nonnull Object source, @Nonnull Object dest) {

    }

    public IntraResult getIntraResult2() {
        return intraResult;
    }
}
