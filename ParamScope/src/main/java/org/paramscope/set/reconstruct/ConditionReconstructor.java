package org.paramscope.set.reconstruct;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.call.CallSite;
import org.paramscope.valueflow.ValueFlowResultTree;
import org.paramscope.valueflow.ValueFlowRunner;
import org.paramscope.valueflow.check.NoopChecker;
import org.paramscope.valueflow.target.StmtLocalTarget;
import org.paramscope.valueflow.target.ValueTarget;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reconstruct Local values used in a branch condition statement.
 *
 * <p>Implementation strategy (Phase-2, no SMT):
 * extract Locals from the condition stmt, then use valueflow's STMT_LOCAL replay to reconstruct each Local.</p>
 */
public final class ConditionReconstructor {

    private ConditionReconstructor() {
    }

    public static BranchConditionReconstructionResult reconstructBranchConditionLocals(MethodSignature methodSignature,
                                                                                       int branchStmtIndex) {
        Objects.requireNonNull(methodSignature, "methodSignature");
        JavaSootMethod m = AnalysisEnv.view().getMethod(methodSignature).orElseThrow();
        List<Stmt> stmts = m.getBody().getStmts();
        if (branchStmtIndex < 0 || branchStmtIndex >= stmts.size()) {
            throw new IllegalArgumentException("branchStmtIndex out of range: " + branchStmtIndex + ", total stmts: " + stmts.size());
        }
        Stmt branchStmt = stmts.get(branchStmtIndex);
        List<String> locals = ConditionTargetExtractor.extractLocalNamesWithDefContext(stmts, branchStmtIndex);

        Map<String, ReconstructedValue> out = new LinkedHashMap<>();
        for (String localName : locals) {
            int seedIndex;
            if (localName.startsWith("$stack")) {
                // Stack temporaries are usually defined by a nearby assignment stmt; seeding at the def improves replay.
                seedIndex = findLatestDefOfLocal(stmts, branchStmtIndex, localName);
            } else {
                seedIndex = findLatestUseOrDefOfLocal(stmts, branchStmtIndex, localName);
            }
            if (seedIndex < 0) {
                // Fallback: seed at branch stmt itself (valueflow supports seeding from uses)
                seedIndex = branchStmtIndex;
            }
            seedIndex = adjustSeedForConstructorInit(stmts, seedIndex, branchStmtIndex, localName);
            Stmt seedStmt = stmts.get(seedIndex);

            CallSite pseudo = new CallSite(methodSignature, methodSignature, seedStmt.getPositionInfo(), seedStmt);
            ValueTarget target = new StmtLocalTarget(
                    pseudo,
                    methodSignature,
                    seedIndex,
                    localName,
                    Optional.empty(),
                    Optional.empty()
            );
            ValueFlowResultTree tree = ValueFlowRunner.run(target, new NoopChecker());

            Object concrete = null;
            List<String> diagnostics = List.of();
            if (!tree.getResolved().isEmpty()) {
                var resolved0 = tree.getResolved().get(0);
                diagnostics = resolved0.diagnostics() == null ? List.of() : resolved0.diagnostics();
                if (resolved0.concreteValues() != null && resolved0.concreteValues().length > 0) {
                    concrete = resolved0.concreteValues()[0];
                }
            }
            out.put(localName, new ReconstructedValue(localName, seedIndex, concrete, diagnostics));
        }

        return new BranchConditionReconstructionResult(
                methodSignature,
                branchStmtIndex,
                String.valueOf(branchStmt),
                locals,
                out
        );
    }

    /**
     * Reconstruct only the direct Locals used by the branch stmt, and always seed at the latest DEF of that Local.
     *
     * <p>This is the right choice when the "value to reconstruct" is the variable appearing in the condition
     * (e.g. {@code if (m)} / {@code if (inputCondition)} / {@code if (x == 1)}), because the if-stmt itself
     * does not define that Local.</p>
     */
    public static BranchConditionReconstructionResult reconstructDirectConditionLocalsSeedAtDef(MethodSignature methodSignature,
                                                                                               int branchStmtIndex) {
        Objects.requireNonNull(methodSignature, "methodSignature");
        JavaSootMethod m = AnalysisEnv.view().getMethod(methodSignature).orElseThrow();
        List<Stmt> stmts = m.getBody().getStmts();
        if (branchStmtIndex < 0 || branchStmtIndex >= stmts.size()) {
            throw new IllegalArgumentException("branchStmtIndex out of range: " + branchStmtIndex + ", total stmts: " + stmts.size());
        }
        Stmt branchStmt = stmts.get(branchStmtIndex);
        List<String> locals = ConditionTargetExtractor.extractLocalNames(branchStmt);

        Map<String, ReconstructedValue> out = new LinkedHashMap<>();
        for (String localName : locals) {
            int seedIndex = findLatestDefOfLocal(stmts, branchStmtIndex, localName);
            if (seedIndex < 0) {
                seedIndex = branchStmtIndex;
            }
            seedIndex = adjustSeedForConstructorInit(stmts, seedIndex, branchStmtIndex, localName);
            Stmt seedStmt = stmts.get(seedIndex);

            CallSite pseudo = new CallSite(methodSignature, methodSignature, seedStmt.getPositionInfo(), seedStmt);
            ValueTarget target = new StmtLocalTarget(
                    pseudo,
                    methodSignature,
                    seedIndex,
                    localName,
                    Optional.empty(),
                    Optional.empty()
            );
            ValueFlowResultTree tree = ValueFlowRunner.run(target, new NoopChecker());

            Object concrete = null;
            List<String> diagnostics = List.of();
            if (!tree.getResolved().isEmpty()) {
                var resolved0 = tree.getResolved().get(0);
                diagnostics = resolved0.diagnostics() == null ? List.of() : resolved0.diagnostics();
                if (resolved0.concreteValues() != null && resolved0.concreteValues().length > 0) {
                    concrete = resolved0.concreteValues()[0];
                }
            }
            out.put(localName, new ReconstructedValue(localName, seedIndex, concrete, diagnostics));
        }

        return new BranchConditionReconstructionResult(
                methodSignature,
                branchStmtIndex,
                String.valueOf(branchStmt),
                locals,
                out
        );
    }

    /**
     * Scan backwards to find the latest definition of the given Local name.
     */
    static int findLatestDefOfLocal(List<Stmt> stmts, int beforeIndexInclusive, String localName) {
        for (int i = Math.min(beforeIndexInclusive, stmts.size() - 1); i >= 0; i--) {
            Stmt s = stmts.get(i);
            if (s instanceof AbstractDefinitionStmt defStmt
                    && defStmt.getDef().isPresent()
                    && defStmt.getDef().get() instanceof Local l) {
                if (localName.equals(l.getName())) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * For branch reconstruction we prefer seeding at a statement that USES the local (e.g. virtualinvoke chosen.equals(...)),
     * because it tends to include necessary control-flow-dependent definitions (e.g. chosen assigned in multiple branches).
     * Fallback to definition scan if no use is found.
     */
    static int findLatestUseOrDefOfLocal(List<Stmt> stmts, int beforeIndexInclusive, String localName) {
        for (int i = Math.min(beforeIndexInclusive, stmts.size() - 1); i >= 0; i--) {
            Stmt s = stmts.get(i);
            for (var u : s.getUses().toList()) {
                if (u instanceof Local l && localName.equals(l.getName())) {
                    return i;
                }
            }
            if (s instanceof AbstractDefinitionStmt defStmt
                    && defStmt.getDef().isPresent()
                    && defStmt.getDef().get() instanceof Local l) {
                if (localName.equals(l.getName())) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Heuristic (mirrors valueflow motivation runner):
     * if seed is "x = new T" and there is a following "specialinvoke x.<init>(...)" before the branch stmt,
     * move seed to that <init> stmt so that slicing can pick up constructor args / side effects.
     */
    static int adjustSeedForConstructorInit(List<Stmt> stmts, int seedIndex, int branchIndexExclusive, String localName) {
        if (seedIndex < 0 || seedIndex >= stmts.size()) return seedIndex;
        Stmt seed = stmts.get(seedIndex);
        boolean isNewDef = false;
        if (seed instanceof AbstractDefinitionStmt defStmt
                && defStmt.getDef().isPresent()
                && defStmt.getDef().get() instanceof Local l) {
            if (localName.equals(l.getName()) && String.valueOf(defStmt.getRightOp()).startsWith("new ")) {
                isNewDef = true;
            }
        }
        if (!isNewDef) return seedIndex;
        for (int i = seedIndex + 1; i < Math.min(branchIndexExclusive, stmts.size()); i++) {
            String s = String.valueOf(stmts.get(i));
            if (s.contains("specialinvoke " + localName + ".<") && s.contains("<init>")) {
                return i;
            }
        }
        return seedIndex;
    }
}

