package org.paramscope.set.tools;

import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.exec.Limits;
import org.paramscope.set.exec.SETBuilder;
import org.paramscope.set.exec.TraversalStrategy;
import org.paramscope.set.expr.SymExpr;
import org.paramscope.set.state.MemoryKey;
import org.paramscope.set.state.ProgramPoint;
import org.paramscope.set.state.SymbolicState;
import org.paramscope.set.util.DebugNdjsonLogger;
import sootup.core.cache.provider.LRUCacheProvider;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.bytecode.inputlocation.JrtFileSystemAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Debug watcher for motivationExample.java (case-level runtime evidence).
 * Logs all states reaching MessageDigest.getInstance(...) callsite.
 *
 * Writes logs to debug-69dd00.log (NDJSON).
 * Does NOT write any intermediate artifacts outside motivationExample/test.
 */
public final class MotivationExampleDebugWatcher {
    private static final Path OUTPUT_ROOT = Paths.get("motivationExample", "test").toAbsolutePath();
    private static final Path INPUT_JAR = OUTPUT_ROOT.resolve("motivationExample.jar");

    public static void main(String[] args) {
        DebugNdjsonLogger.log("pre-fix", "H0", "MotivationExampleDebugWatcher:main",
                "start", Map.of("inputJar", INPUT_JAR.toString()));

        JavaView view = buildJarView(INPUT_JAR);
        JavaSootMethod method = findMethod(view, "motivationExample", "main")
                .orElseThrow(() -> new IllegalStateException("Method not found: motivationExample.main"));

        CallsiteInfo callsite = findMessageDigestCallsite(method);
        DebugNdjsonLogger.log("pre-fix", "H0", "MotivationExampleDebugWatcher:main",
                "located_callsite", Map.of("found", callsite.point != null, "stmtId", callsite.point != null ? callsite.point.stmtId() : -1,
                        "arg0Local", String.valueOf(callsite.arg0LocalName)));

        SETBuilder builder = new SETBuilder(TraversalStrategy.BFS, Limits.defaults());
        ExecutionGraph g = builder.build(method);

        if (callsite.point == null) {
            DebugNdjsonLogger.log("pre-fix", "H0", "MotivationExampleDebugWatcher:main",
                    "callsite_missing", Map.of("terminal", g.terminalStates().size(), "active", g.activeStates().size()));
            return;
        }

        ProgramPoint p = callsite.point;
        List<Long> stateIds = g.statesAtPoint().getOrDefault(p, List.of());
        DebugNdjsonLogger.log("pre-fix", "H0", "MotivationExampleDebugWatcher:main",
                "states_at_callsite", Map.of("count", stateIds.size(), "programPoint", p.toString()));

        for (long sid : stateIds) {
            SymbolicState st = g.states().get(sid);
            if (st == null) continue;

            String pc = st.pathCond().constraints().toString();
            String storeSize = String.valueOf(st.store().snapshot().size());

            // Best-effort extraction: expression for the callsite arg0 local
            Optional<SymExpr> argExpr = st.store().snapshot().entrySet().stream()
                    .filter(e -> e.getKey() instanceof MemoryKey.LocalKey lk && lk.localName().equals(callsite.arg0LocalName))
                    .map(Map.Entry::getValue)
                    .findFirst();

            DebugNdjsonLogger.log("pre-fix", "H1", "MotivationExampleDebugWatcher:state",
                    "state_at_callsite",
                    Map.of(
                            "stateId", sid,
                            "depth", st.depth(),
                            "branchLabel", st.branchLabel().toString(),
                            "pc", pc,
                            "storeSize", storeSize,
                            "arg0Local", callsite.arg0LocalName,
                            "argExprPresent", argExpr.isPresent(),
                            "argExpr", argExpr.map(Object::toString).orElse("<missing>")
                    ));
        }
    }

    private static JavaView buildJarView(Path jarPath) {
        List<AnalysisInputLocation> inputLocations = new ArrayList<>();
        inputLocations.add(new JavaClassPathAnalysisInputLocation(jarPath.toString()));
        inputLocations.add(new JrtFileSystemAnalysisInputLocation());
        return new JavaView(inputLocations, new LRUCacheProvider(200));
    }

    private static Optional<JavaSootMethod> findMethod(JavaView view, String className, String methodName) {
        JavaClassType ct = view.getIdentifierFactory().getClassType(className);
        Optional<JavaSootClass> c = view.getClass(ct);
        if (c.isEmpty()) return Optional.empty();
        for (JavaSootMethod m : c.get().getMethods()) {
            if (m.getSignature().getSubSignature().getName().equals(methodName)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    private static CallsiteInfo findMessageDigestCallsite(JavaSootMethod method) {
        var body = method.getBody();
        var stmts = body.getStmts();
        for (int i = 0; i < stmts.size(); i++) {
            Stmt s = stmts.get(i);
            if (!s.containsInvokeExpr()) continue;
            var ms = s.getInvokeExpr().getMethodSignature();
            if (ms.getDeclClassType().toString().equals("java.security.MessageDigest")
                    && ms.getName().equals("getInstance")) {
                String localName = null;
                try {
                    var arg0 = s.getInvokeExpr().getArg(0);
                    if (arg0 instanceof sootup.core.jimple.basic.Local l) {
                        localName = l.getName();
                    }
                } catch (Exception ignored) {
                }
                return new CallsiteInfo(new ProgramPoint(method.getSignature(), i, s), localName);
            }
        }
        return new CallsiteInfo(null, null);
    }

    private record CallsiteInfo(ProgramPoint point, String arg0LocalName) {}
}

