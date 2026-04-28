package org.paramscope.set.tools;

import org.paramscope.set.cfg.MethodCFG;
import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.exec.Limits;
import org.paramscope.set.exec.SETBuilder;
import org.paramscope.set.exec.TraversalStrategy;
import org.paramscope.set.util.DotUtil;
import org.paramscope.set.util.ExecutionGraphToDot;
import org.paramscope.set.util.MethodCFGToDot;
import sootup.core.cache.provider.LRUCacheProvider;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.bytecode.inputlocation.JrtFileSystemAnalysisInputLocation;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase-1 runner to build and visualize method-intra SET for motivation examples only.
 *
 * All outputs are forced under: motivationExample/test
 */
public final class SETMotivationRunner {
    private static final Path OUTPUT_ROOT = Paths.get("motivationExample", "test").toAbsolutePath();
    private static final Path INPUT_JAR = OUTPUT_ROOT.resolve("motivationExample.jar");

    public static void main(String[] args) {
        List<Target> targets = List.of(
                new Target("TC01_AssignArithmetic", "target"),
                new Target("TC02_IfTrivialUnsatPrune", "target"),
                new Target("TC03_StringConcatBranch", "target"),
                new Target("TC04_MultiBranchStatesAtCallsite", "target"),
                new Target("TC05_DepthAndReturnTermination", "target"),
                new Target("motivationExample", "main")
        );

        JavaView view = buildJarView(INPUT_JAR);

        for (Target t : targets) {
            Optional<JavaSootMethod> m = findMethod(view, t.className, t.methodName);
            if (m.isEmpty()) {
                System.out.println("[WARN] Method not found: " + t.className + "." + t.methodName);
                continue;
            }

            JavaSootMethod method = m.get();
            MethodCFG cfg = new MethodCFG(method);
            SETBuilder builder = new SETBuilder(TraversalStrategy.BFS, Limits.defaults());
            ExecutionGraph g = builder.build(method);

            String outBase = OUTPUT_ROOT.resolve(t.className + "_" + t.methodName).toString();
            DotUtil.writeDotAndMaybeRenderPng(MethodCFGToDot.toDot(cfg), outBase + "_cfg");
            if (t.className.equals("motivationExample") && t.methodName.equals("main")) {
                Focus focus = findMessageDigestGetInstanceArgLocal(method).orElse(new Focus(null, null));
                DotUtil.writeDotAndMaybeRenderPng(ExecutionGraphToDot.toDot(g, focus.stmtId, focus.arg0Local), outBase + "_set");
            } else {
                DotUtil.writeDotAndMaybeRenderPng(ExecutionGraphToDot.toDot(g), outBase + "_set");
            }

            System.out.println("[OK] Wrote outputs: " + outBase + "_{cfg,set}.{dot,png}");
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

    private static Optional<Focus> findMessageDigestGetInstanceArgLocal(JavaSootMethod method) {
        var stmts = method.getBody().getStmts();
        for (int i = 0; i < stmts.size(); i++) {
            Stmt s = stmts.get(i);
            if (!s.containsInvokeExpr()) continue;
            var ms = s.getInvokeExpr().getMethodSignature();
            if (ms.getDeclClassType().toString().equals("java.security.MessageDigest") && ms.getName().equals("getInstance")) {
                var arg0 = s.getInvokeExpr().getArg(0);
                if (arg0 instanceof sootup.core.jimple.basic.Local l) {
                    return Optional.of(new Focus(i, l.getName()));
                }
                return Optional.of(new Focus(i, "<nonLocalArg0>"));
            }
        }
        return Optional.empty();
    }

    private record Target(String className, String methodName) {}
    private record Focus(Integer stmtId, String arg0Local) {}
}

