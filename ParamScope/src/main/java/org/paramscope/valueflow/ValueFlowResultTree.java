package org.paramscope.valueflow;

import org.paramscope.reflection.ReflectionObject2;
import org.paramscope.slice.IntraResult;
import org.paramscope.slice.IntraResultNode;
import org.paramscope.slice.InterProceduralObjects;
import org.paramscope.slice.OneResult;
import org.paramscope.valueflow.check.ArrayRandomizationChecker;
import org.paramscope.valueflow.check.CheckResult;
import org.paramscope.valueflow.check.ResultChecker;
import org.paramscope.valueflow.target.ValueTarget;
import org.paramscope.valueflow.target.ValueTargetKind;
import sootup.core.jimple.common.constant.Constant;

import java.util.*;

public final class ValueFlowResultTree {
    private final IntraResultNode root;
    private final ValueTarget target;
    private final ResultChecker checker;

    private final List<OneResult> results = new ArrayList<>();
    private final List<ValueFlowResolvedPath> resolved = new ArrayList<>();

    public ValueFlowResultTree(IntraResultNode root, ValueTarget target, ResultChecker checker) {
        this.root = root;
        this.target = target;
        this.checker = checker;
    }

    public IntraResultNode getRoot() {
        return root;
    }

    public ValueTarget getTarget() {
        return target;
    }

    public List<OneResult> getResults() {
        return List.copyOf(results);
    }

    public List<ValueFlowResolvedPath> getResolved() {
        return List.copyOf(resolved);
    }

    public void resolveResults() {
        OneResult oneResult = new OneResult();
        resolveAllResults(root, oneResult, results);

        for (OneResult path : results) {
            List<String> diagnostics = new ArrayList<>();

            ListIterator<IntraResult> iterator = path.getIntraResults().listIterator(path.getIntraResults().size());
            InterProceduralObjects interProceduralObjects = new InterProceduralObjects();

            while (iterator.hasPrevious()) {
                IntraResult intraResult = iterator.previous();
                if (!intraResult.getConstResults().isEmpty()) {
                    // Keep existing constant handling for argument targets.
                    for (var methodParamRef : intraResult.getConstResults().keySet()) {
                        Constant constant = intraResult.getConstResults().get(methodParamRef);
                        ReflectionObject2 constObject = new ReflectionObject2(constant.getType(), "ConstVal");
                        constObject.setInstance(org.paramscope.reflection.ConstantResolve.resolve(
                                constant,
                                org.paramscope.reflection.GetClassFromType2.get(methodParamRef.parameterRef().getType())
                        ));
                        interProceduralObjects.getParamObjects().put(methodParamRef.parameterRef().getIndex(), constObject);
                    }
                    continue;
                }
                interProceduralObjects = ValueFlowReplayer.resolveOneIntraResult(intraResult, interProceduralObjects, diagnostics, target);
            }

            Object[] concrete = pickConcreteValues(interProceduralObjects);
            ReflectionObject2[] reflectionObjects = pickReflectionObjects(interProceduralObjects);

            CheckResult checkRes;
            Object[] concreteTwice = null;
            if (checker.requiresTwiceResolve(target) && concrete.length > 0 && concrete[0] != null) {
                // re-run once more
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // ignore
                }
                concreteTwice = resolveConcreteAgain(path);
            }

            if (concreteTwice != null && checker instanceof ArrayRandomizationChecker arc) {
                checkRes = arc.checkTwice(concrete, concreteTwice);
            } else {
                checkRes = checker.check(target, concrete, reflectionObjects, path);
            }

            diagnostics.addAll(checkRes.messages());
            String securityInfo = " (" + String.join("; ", checkRes.messages()) + ")";
            path.setSecurityInfo(securityInfo);

            resolved.add(new ValueFlowResolvedPath(path, concrete, diagnostics, securityInfo));
        }
    }

    private Object[] resolveConcreteAgain(OneResult path) {
        ListIterator<IntraResult> iterator = path.getIntraResults().listIterator(path.getIntraResults().size());
        InterProceduralObjects interProceduralObjects = new InterProceduralObjects();
        while (iterator.hasPrevious()) {
            IntraResult intraResult = iterator.previous();
            if (!intraResult.getConstResults().isEmpty()) {
                for (var methodParamRef : intraResult.getConstResults().keySet()) {
                    Constant constant = intraResult.getConstResults().get(methodParamRef);
                    ReflectionObject2 constObject = new ReflectionObject2(constant.getType(), "ConstVal");
                    constObject.setInstance(org.paramscope.reflection.ConstantResolve.resolve(
                            constant,
                            org.paramscope.reflection.GetClassFromType2.get(methodParamRef.parameterRef().getType())
                    ));
                    interProceduralObjects.getParamObjects().put(methodParamRef.parameterRef().getIndex(), constObject);
                }
                continue;
            }
            interProceduralObjects = ValueFlowReplayer.resolveOneIntraResult(intraResult, interProceduralObjects, new ArrayList<>(), target);
        }
        return pickConcreteValues(interProceduralObjects);
    }

    private ReflectionObject2[] pickReflectionObjects(InterProceduralObjects interProceduralObjects) {
        if (target.kind() == ValueTargetKind.CALLSITE_BASE) {
            ReflectionObject2 base = interProceduralObjects.getThisObject();
            return new ReflectionObject2[]{base};
        }
        if (target.kind() == ValueTargetKind.STMT_LOCAL) {
            ReflectionObject2 ro = interProceduralObjects.getLocalTargetObject();
            return new ReflectionObject2[]{ro};
        }
        ReflectionObject2[] ros = new ReflectionObject2[target.argIndices().size()];
        for (int i = 0; i < target.argIndices().size(); i++) {
            Integer idx = target.argIndices().get(i);
            ros[i] = interProceduralObjects.getParamObjects().get(idx);
        }
        return ros;
    }

    private Object[] pickConcreteValues(InterProceduralObjects interProceduralObjects) {
        if (target.kind() == ValueTargetKind.CALLSITE_BASE) {
            ReflectionObject2 base = interProceduralObjects.getThisObject();
            return new Object[]{base == null ? null : base.getInstance()};
        }
        if (target.kind() == ValueTargetKind.STMT_LOCAL) {
            ReflectionObject2 ro = interProceduralObjects.getLocalTargetObject();
            return new Object[]{ro == null ? null : ro.getInstance()};
        }
        Object[] objects = new Object[target.argIndices().size()];
        for (int i = 0; i < target.argIndices().size(); i++) {
            Integer idx = target.argIndices().get(i);
            ReflectionObject2 ro = interProceduralObjects.getParamObjects().get(idx);
            objects[i] = ro == null ? null : ro.getInstance();
        }
        return objects;
    }

    private void resolveAllResults(IntraResultNode node, OneResult curr, List<OneResult> out) {
        curr.getIntraResults().add(node.getIntraResult());

        if (node.getCallerResults().isEmpty()) {
            out.add(curr);
            return;
        }
        for (var callSite : node.getCallerResults().keySet()) {
            OneResult child = new OneResult(curr);
            child.getCallRelations().add(callSite);
            resolveAllResults(node.getCallerResults().get(callSite), child, out);
        }
    }
}

