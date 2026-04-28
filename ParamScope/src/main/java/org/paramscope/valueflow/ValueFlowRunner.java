package org.paramscope.valueflow;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.data.CallRelation;
import org.paramscope.call.MethodInfo;
import org.paramscope.slice.IntraResult;
import org.paramscope.slice.IntraResultNode;
import org.paramscope.slice.IntraSlicing;
import org.paramscope.valueflow.check.NoopChecker;
import org.paramscope.valueflow.check.ResultChecker;
import org.paramscope.valueflow.slice.ValueFlowIntraSlicing;
import org.paramscope.valueflow.target.StmtLocalTarget;
import org.paramscope.valueflow.target.ValueTarget;
import org.paramscope.valueflow.target.ValueTargetKind;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootMethod;

import java.util.ArrayList;
import java.util.List;

public final class ValueFlowRunner {

    private ValueFlowRunner() {
    }

    /**
     * Convenience API for STMT_LOCAL target:
     * locate method body stmt by index (0-based) and seed the given local.
     */
    public static ValueFlowResultTree runStmtLocal(MethodSignature methodSignature,
                                                   int stmtIndex,
                                                   String localName,
                                                   ResultChecker checker) {
        JavaSootMethod m = AnalysisEnv.view().getMethod(methodSignature).orElseThrow();
        List<sootup.core.jimple.common.stmt.Stmt> stmts = m.getBody().getStmts();
        if (stmtIndex < 0 || stmtIndex >= stmts.size()) {
            throw new IllegalArgumentException("stmtIndex out of range: " + stmtIndex + ", total stmts: " + stmts.size());
        }
        sootup.core.jimple.common.stmt.Stmt seedStmt = stmts.get(stmtIndex);
        // pseudo callsite
        CallSite pseudo = new CallSite(methodSignature, methodSignature, seedStmt.getPositionInfo(), seedStmt);
        org.paramscope.valueflow.target.StmtLocalTarget target = new org.paramscope.valueflow.target.StmtLocalTarget(
                pseudo,
                methodSignature,
                stmtIndex,
                localName == null ? "" : localName,
                java.util.Optional.empty(),
                java.util.Optional.empty()
        );
        return run(target, checker);
    }

    public static ValueFlowResultTree run(ValueTarget target) {
        return run(target, new NoopChecker());
    }

    public static ValueFlowResultTree run(ValueTarget target, ResultChecker checker) {
        IntraResultNode root = buildTree(target, new ArrayList<>(), target.trackBase());
        ValueFlowResultTree tree = new ValueFlowResultTree(root, target, checker);
        tree.resolveResults();
        return tree;
    }

    static IntraResultNode buildTree(ValueTarget target, List<JStaticFieldRef> trackingStaticFields, boolean trackBaseOfTheMethod) {
        CallSite callSite = target.callSite();
        MethodSignature caller = callSite.getCaller();
        JavaSootMethod callerSM = AnalysisEnv.view().getMethod(caller).get();

        APIParamInfo apiParamInfo = toApiParamInfo(target);
        IntraResult intraResult;
        if (target.kind() == ValueTargetKind.STMT_LOCAL && target instanceof StmtLocalTarget stmtLocalTarget) {
            // For STMT_LOCAL, seed at an arbitrary stmt/local.
            sootup.core.jimple.common.stmt.Stmt seedStmt = callSite.getInvokeStmt();
            ValueFlowIntraSlicing s = new ValueFlowIntraSlicing(
                    callerSM,
                    callerSM.getBody().getStmtGraph(),
                    callSite,
                    apiParamInfo,
                    trackingStaticFields,
                    trackBaseOfTheMethod,
                    seedStmt,
                    stmtLocalTarget.localName()
            );
            intraResult = s.getIntraResult();
        } else {
            IntraSlicing intraSlicing = new IntraSlicing(
                    callerSM,
                    callerSM.getBody().getStmtGraph(),
                    callSite,
                    apiParamInfo,
                    trackingStaticFields,
                    trackBaseOfTheMethod
            );
            intraResult = intraSlicing.getIntraResult2();
        }
        IntraResultNode node = new IntraResultNode(intraResult);

        if (intraResult.needsTracking()) {
            MethodInfo callerMI = CallRelation.getApplicationMethodAndapiMethodMap().get(callSite.getCaller());
            if (callerMI != null && !callerMI.getIsMain()) {
                for (CallSite callerOfCallerCallSite : callerMI.getCallSites()) {
                    ValueTarget upwardTarget = UpwardTargetBuilder.from(intraResult, callerOfCallerCallSite);
                    IntraResultNode child = buildTree(
                            upwardTarget,
                            intraResult.getTracingStaticFieldRefs(),
                            intraResult.getThisOfCallerNeedsTracking()
                    );
                    node.getCallerResults().put(callerOfCallerCallSite, child);
                }
            }
        }

        return node;
    }

    private static APIParamInfo toApiParamInfo(ValueTarget target) {
        MethodSignature callee = target.calleeSignature();
        APIParamInfo apiParamInfo = new APIParamInfo(
                callee.getDeclClassType().getClassName(),
                callee.getSubSignature().getName(),
                target.argIndices()
        );
        apiParamInfo.setMethodSignature(callee);
        return apiParamInfo;
    }
}

