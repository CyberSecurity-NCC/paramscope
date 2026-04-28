package org.paramscope.valueflow.slice;

import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.data.CallRelation;
import org.paramscope.slice.*;
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

/**
 * Valueflow-specific intra-procedural backward slicing.
 *
 * <p>This is a copy of {@link org.paramscope.slice.IntraSlicing} with a generalized seed:
 * either a callsite (args/base) or a specific Local at a specific stmt (0-based stmtIndex).</p>
 */
public class ValueFlowIntraSlicing extends BackwardFlowAnalysis<List<Value>> {
    private final APIParamInfo apiParamInfo;
    private final CallSite callSite;
    private final IntraResult intraResult;
    private final FocusedValues trackingValues;
    private final boolean trackBaseOfTheMethod;

    private final Stmt seedStmt;
    private final String seedLocalName;
    private boolean haveFoundSeed;

    private boolean haveFoundCallSite;

    public ValueFlowIntraSlicing(SootMethod callerSM,
                                StmtGraph<?> graph,
                                CallSite callSite,
                                APIParamInfo apiParamInfo,
                                List<JStaticFieldRef> trackingStaticFields,
                                boolean trackBaseOfTheMethod,
                                Stmt seedStmt,
                                String seedLocalName) {
        super(graph);
        this.callSite = callSite;
        this.apiParamInfo = apiParamInfo;
        this.trackingValues = new FocusedValues(new ArrayList<>(), trackingStaticFields, new ArrayList<>(), new ArrayList<>(), callSite.getCaller());
        this.intraResult = new IntraResult(apiParamInfo, callSite);
        this.trackBaseOfTheMethod = trackBaseOfTheMethod;
        this.seedStmt = seedStmt;
        this.seedLocalName = seedLocalName == null ? "" : seedLocalName;
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
            if (intraResult.getStaticFieldRefTrackers().containsKey(staticFieldRef)
                    && intraResult.getStaticFieldRefTrackers().get(staticFieldRef).getTrackedObj() != null) {
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
    protected void flowThrough(@Nonnull List<Value> in, Stmt stmt, @Nonnull List<Value> out) {
        // analysis in/out are not used by this implementation; keep them consistent
        out.addAll(in);

        // Seed at an arbitrary stmt/local
        if (!haveFoundSeed && seedStmt != null && stmt.equivTo(seedStmt)) {
            this.haveFoundSeed = true;
            Local targetLocal = pickSeedLocal(stmt);
            if (targetLocal != null) {
                // Treat the seed as "we want to reconstruct targetLocal defined here".
                // For backward slicing, seed with RHS uses so we can resolve dependencies.
                trackingValues.addAllFromStmtUses(stmt);

                // Make sure replayer will reflect this stmt (needs a def-values entry).
                try {
                    FocusedValues defs = new FocusedValues(
                            java.util.List.of(new MethodJavaLocal((JavaLocal) targetLocal, callSite.getCaller())),
                            java.util.List.of(),
                            java.util.List.of(),
                            java.util.List.of(),
                            callSite.getCaller()
                    );
                    intraResult.getStmtDefValues().put(stmt, defs);
                } catch (ClassCastException ignored) {
                    // If local isn't a JavaLocal, we still record an empty defValues (best-effort).
                    FocusedValues defs = new FocusedValues(
                            java.util.List.of(),
                            java.util.List.of(),
                            java.util.List.of(),
                            java.util.List.of(),
                            callSite.getCaller()
                    );
                    intraResult.getStmtDefValues().put(stmt, defs);
                }
            }
            intraResult.getResultStmts().add(stmt);
            // Do NOT return: we still need to run the normal def-use logic on the seed stmt
            // to record tracingParamRefs / instance/static field effects, and to continue slicing.

            // If seed is an identity/parameter binding, explicitly mark it as needing inter-procedural tracking.
            if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getRightOp() instanceof JParameterRef pref) {
                intraResult.getTracingParamRefs().add(new MethodParamRef(pref, callSite.getCaller()));
            }
        }

        // Legacy seed: callsite args/base
        if (needsCheck() && findCallSite(stmt)) {
            this.haveFoundCallSite = true;
            AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
            MethodSignature calledMS = invokeExpr.getMethodSignature();
            for (int param : apiParamInfo.getParamPosList()) {
                Type paramType = calledMS.getSubSignature().getParameterTypes().get(param);
                Value paramValue = invokeExpr.getArg(param);
                MethodParamRef methodParamRef = new MethodParamRef(new JParameterRef(paramType, param), apiParamInfo.getMethodSignature());

                if (paramValue instanceof Constant constant) {
                    intraResult.getConstResults().put(methodParamRef, constant);
                } else if (paramValue instanceof JavaLocal local) {
                    intraResult.getMethodParamRefs().add(methodParamRef);
                    trackingValues.add(local);
                }
            }

            if (this.trackBaseOfTheMethod) {
                if (invokeExpr instanceof JVirtualInvokeExpr virtualInvokeExpr) {
                    trackingValues.add(virtualInvokeExpr.getBase());
                }
            }
            intraResult.getResultStmts().add(stmt);
            return;
        }

        if (trackingValues.isEmpty()) {
            return;
        }

        if (haveFoundCallSite || haveFoundSeed) {
            try {
                FocusedValues defs = SideEffect.defUseAnalysis(stmt, trackingValues, intraResult.getTrackedValues(), callSite.getCaller());
                FocusedValues uses = new FocusedValues(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), callSite.getCaller());

                ArrayList<JStaticFieldRef> definedStaticFields = new ArrayList<>();
                List<Integer> params = new ArrayList<>();
                if (!trackingValues.isEmptyStaticField() && stmt.containsInvokeExpr()) {
                    FocusedValues originStaticFields = new FocusedValues(new ArrayList<>(), new ArrayList<>(trackingValues.getFocusedStaticFields()), new ArrayList<>(), new ArrayList<>(), callSite.getCaller());
                    params.addAll(SideEffect.SFdefUseAnalysis(stmt, trackingValues.getFocusedStaticFields(), intraResult));
                    originStaticFields.removeAll(trackingValues);
                    definedStaticFields.addAll(originStaticFields.getFocusedStaticFields());
                }

                List<Local> alreadySecureRandomizedArrays = List.copyOf(trackingValues.getSecureRandomizedArrays());
                List<Local> alreadyInsecureRandomizedArrays = List.copyOf(trackingValues.getInsecureRandomizedArrays());

                List<Local> newSecureRandomizedArrays = new ArrayList<>(List.copyOf(trackingValues.getSecureRandomizedArrays()));
                newSecureRandomizedArrays.removeAll(alreadySecureRandomizedArrays);
                List<Local> newInsecureRandomizedArrays = new ArrayList<>(List.copyOf(trackingValues.getInsecureRandomizedArrays()));
                newInsecureRandomizedArrays.removeAll(alreadyInsecureRandomizedArrays);

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

                if (defs.isEmpty() && !definedStaticFields.isEmpty()) {
                    uses.addAll(params.stream().map(index -> stmt.getInvokeExpr().getArg(index)).toList());
                } else {
                    if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getRightOp() instanceof JParameterRef parameterRef) {
                        intraResult.getTracingParamRefs().add(new MethodParamRef(parameterRef, callSite.getCaller()));
                    }
                    if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getRightOp() instanceof JThisRef) {
                        intraResult.setThisOfCallerNeedsTracking(true);
                    }
                    uses.addAllFromStmtUses(stmt);
                }

                defs.addAll(definedStaticFields);
                intraResult.getStmtDefValues().put(stmt, defs);

                trackingValues.removeAll(defs);
                trackingValues.addAll(uses);

                if (!intraResult.getResultStmts().contains(stmt)) {
                    intraResult.getResultStmts().add(stmt);
                }
            } catch (ResolveException ignored) {
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private Local pickSeedLocal(Stmt stmt) {
        if (seedLocalName == null || seedLocalName.isBlank()) {
            if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getDef().isPresent() && defStmt.getDef().get() instanceof Local l) {
                return l;
            }
            return null;
        }
        if (stmt instanceof AbstractDefinitionStmt defStmt && defStmt.getDef().isPresent() && defStmt.getDef().get() instanceof Local l) {
            if (seedLocalName.equals(l.getName())) {
                return l;
            }
        }
        for (Value u : stmt.getUses().toList()) {
            if (u instanceof Local l && seedLocalName.equals(l.getName())) {
                return l;
            }
        }
        return null;
    }

    private boolean needsCheck() {
        return intraResult.getMethodParamRefs().size() < apiParamInfo.getParamPosList().size() || !trackingValues.isEmptyStaticField() || trackBaseOfTheMethod;
    }

    private boolean findCallSite(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
            MethodSignature calledMS = invokeExpr.getMethodSignature();
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
    protected List<Value> newInitialFlow() {
        return new ArrayList<>();
    }

    @Override
    protected void merge(@Nonnull List<Value> in1, @Nonnull List<Value> in2, @Nonnull List<Value> out) {
    }

    @Override
    protected void copy(@Nonnull List<Value> source, @Nonnull List<Value> dest) {
    }

    public IntraResult getIntraResult() {
        return intraResult;
    }
}

