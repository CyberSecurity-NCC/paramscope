package org.paramscope.set.reconstruct;

import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.Stmt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extract reconstruction targets from a branch condition statement (if/switch).
 *
 * <p>Phase-2 focus: start from Locals referenced by the condition stmt, and reconstruct those Locals
 * via {@code valueflow}'s STMT_LOCAL pathway.</p>
 */
public final class ConditionTargetExtractor {

    private ConditionTargetExtractor() {
    }

    /**
     * Collect Local names used by the given branch stmt in appearance order.
     *
     * <p>Note: In Jimple 3-address form, many sub-expressions are materialized as temporaries
     * (e.g. {@code $stack1}), so focusing on locals yields good coverage.</p>
     */
    public static List<String> extractLocalNames(Stmt branchStmt) {
        if (branchStmt == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        for (Value u : branchStmt.getUses().toList()) {
            if (u instanceof Local l) {
                if (l.getName() != null && !l.getName().isBlank()) {
                    names.add(l.getName());
                }
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Extract locals used in the branch stmt, and if those locals are only stack-temporaries,
     * try to expand to the locals used by the defining statement(s) of those temporaries.
     *
     * <p>This handles common Jimple lowering patterns:
     * {@code $stack2 = virtualinvoke str.equals("TRIGGER"); if $stack2 == 0 ...}
     * where the interesting value is {@code str} not {@code $stack2}.</p>
     */
    public static List<String> extractLocalNamesWithDefContext(List<Stmt> stmts, int branchStmtIndex) {
        if (stmts == null || branchStmtIndex < 0 || branchStmtIndex >= stmts.size()) return List.of();
        Stmt branchStmt = stmts.get(branchStmtIndex);
        List<String> direct = extractLocalNames(branchStmt);
        if (direct.isEmpty()) return direct;

        // Expand through def-use for a small bounded number of hops.
        // This helps recover semantic locals behind stack temporaries and intermediate locals (e.g. chosen <- pickA ? a : b).
        final int maxHops = 3;
        final int maxNames = 12;
        Set<String> expanded = new LinkedHashSet<>(direct);
        Set<String> frontier = new LinkedHashSet<>(direct);
        for (int hop = 0; hop < maxHops; hop++) {
            if (frontier.isEmpty()) break;
            Set<String> next = new LinkedHashSet<>();
            for (String name : frontier) {
                List<Integer> defIdxs = findAllDefIndicesOfLocal(stmts, branchStmtIndex - 1, name, 2);
                if (defIdxs.isEmpty()) continue;

                // include uses from each def (handles multiple reaching defs under control flow)
                int minDef = Integer.MAX_VALUE;
                int maxDef = -1;
                for (int defIdx : defIdxs) {
                    minDef = Math.min(minDef, defIdx);
                    maxDef = Math.max(maxDef, defIdx);
                    Stmt defStmt = stmts.get(defIdx);
                    for (Value u : defStmt.getUses().toList()) {
                        if (u instanceof Local l && l.getName() != null && !l.getName().isBlank()) {
                            if (expanded.add(l.getName())) {
                                next.add(l.getName());
                                if (expanded.size() >= maxNames) return new ArrayList<>(expanded);
                            }
                        }
                    }
                }

                // additionally: include locals from guarding if statements between multiple defs (e.g. "if pickA == 0")
                if (defIdxs.size() > 1 && minDef <= maxDef) {
                    for (int i = minDef; i <= maxDef; i++) {
                        String s = String.valueOf(stmts.get(i));
                        if (s.startsWith("if ")) {
                            for (Value u : stmts.get(i).getUses().toList()) {
                                if (u instanceof Local l && l.getName() != null && !l.getName().isBlank()) {
                                    if (expanded.add(l.getName())) {
                                        next.add(l.getName());
                                        if (expanded.size() >= maxNames) return new ArrayList<>(expanded);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            frontier = next;
        }
        return new ArrayList<>(expanded);
    }

    /**
     * Collect up to {@code maxDefs} definition stmt indices for {@code localName}, scanning backwards from {@code beforeIndexInclusive}.
     */
    private static List<Integer> findAllDefIndicesOfLocal(List<Stmt> stmts, int beforeIndexInclusive, String localName, int maxDefs) {
        List<Integer> out = new ArrayList<>();
        for (int i = Math.min(beforeIndexInclusive, stmts.size() - 1); i >= 0; i--) {
            Stmt s = stmts.get(i);
            if (s instanceof AbstractDefinitionStmt defStmt
                    && defStmt.getDef().isPresent()
                    && defStmt.getDef().get() instanceof Local l) {
                if (localName.equals(l.getName())) {
                    out.add(i);
                    if (out.size() >= Math.max(1, maxDefs)) {
                        break;
                    }
                }
            }
        }
        return out;
    }
}

