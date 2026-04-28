package org.paramscope.set.tools;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.exec.Limits;
import org.paramscope.set.exec.SETBuilder;
import org.paramscope.set.exec.TraversalStrategy;
import org.paramscope.set.exec.post.CfgBranchPruneReducer;
import org.paramscope.slice.IntraResult;
import org.paramscope.slice.IntraSlicing;
import org.paramscope.slice.PrunedStmtGraph;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootMethod;

import javax.tools.*;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Compare IntraSlicing results on base CFG vs SET-pruned CFG overlay.
 *
 * <p>Usage: run with exec args: <mainClassSimpleName> (default: motivationExample2)</p>
 */
public final class SetPrunedIntraSlicingDiffRunner {

    private SetPrunedIntraSlicingDiffRunner() {
    }

    public static void main(String[] args) throws Exception {
        String mainClassSimpleName = (args != null && args.length > 0 && args[0] != null && !args[0].isBlank())
                ? args[0].trim()
                : "motivationExample2";

        Path wd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path repoRoot = Files.exists(wd.resolve("motivationExample"))
                ? wd
                : (wd.getParent() == null ? wd : wd.getParent());
        Path srcDir = repoRoot.resolve("motivationExample");
        Path outDir = srcDir.resolve("test");
        Path buildDir = outDir.resolve("build_phase3");
        Path classesDir = buildDir.resolve("classes");
        Path jarPath = outDir.resolve("motivationExample_phase3.jar");

        Files.createDirectories(outDir);
        Files.createDirectories(classesDir);

        List<Path> sources = listJavaSources(srcDir);
        compileJavaWithDebug(sources, classesDir);
        createJar(classesDir, jarPath);

        AnalysisEnv.initJarOnly(jarPath.toString(), false);

        JavaSootMethod mainMethod = findMain(mainClassSimpleName);
        CallSite cs = findMessageDigestGetInstanceCallsite(mainMethod);
        APIParamInfo api = new APIParamInfo("java.security.MessageDigest", "java.security.MessageDigest getInstance(java.lang.String)", List.of(0));
        api.setMethodSignature(cs.getCallee());

        StmtGraph<?> baseGraph = mainMethod.getBody().getStmtGraph();
        IntraResult baseRes = new IntraSlicing(mainMethod, baseGraph, cs, api, new ArrayList<>(), false).getIntraResult2();

        Limits limits = Limits.defaults();
        ExecutionGraph raw = new SETBuilder(TraversalStrategy.BFS, limits).build(mainMethod);
        Set<CfgBranchPruneReducer.EdgeKey> blocked = CfgBranchPruneReducer.computeBlockedEdges(mainMethod, baseGraph, raw);
        StmtGraph<?> prunedGraph = new PrunedStmtGraph(baseGraph, blocked);
        IntraResult prunedRes = new IntraSlicing(mainMethod, prunedGraph, cs, api, new ArrayList<>(), false).getIntraResult2();

        System.out.println("== IntraSlicing diff ==");
        System.out.println("class=" + mainClassSimpleName);
        System.out.println("callsite=" + cs);
        System.out.println("base.resultStmts=" + baseRes.getResultStmts().size());
        System.out.println("pruned.resultStmts=" + prunedRes.getResultStmts().size());
        System.out.println("blockedEdges=" + blocked.size());
    }

    private static JavaSootMethod findMain(String mainClassSimpleName) {
        MethodSignature ms = AnalysisEnv.view().getClasses().stream()
                .filter(c -> c.getType().getClassName().equals(mainClassSimpleName))
                .flatMap(c -> c.getMethods().stream())
                .map(m -> m.getSignature())
                .filter(sig -> sig.getSubSignature().getName().equals("main"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find main() in class: " + mainClassSimpleName));
        java.util.Objects.requireNonNull(ms, "mainMethodSignature");
        return AnalysisEnv.view().getMethod(ms).orElseThrow();
    }

    private static CallSite findMessageDigestGetInstanceCallsite(JavaSootMethod caller) {
        for (Stmt s : caller.getBody().getStmts()) {
            if (!s.containsInvokeExpr()) continue;
            AbstractInvokeExpr ie = s.getInvokeExpr();
            var ms = ie.getMethodSignature();
            if (ms.getDeclClassType().toString().equals("java.security.MessageDigest") && ms.getName().equals("getInstance")) {
                MethodSignature callerMs = java.util.Objects.requireNonNull(caller.getSignature(), "callerMethodSignature");
                return new CallSite(callerMs, ms, s.getPositionInfo(), s);
            }
        }
        throw new IllegalStateException("Cannot find MessageDigest.getInstance callsite in: " + caller.getSignature());
    }

    private static List<Path> listJavaSources(Path srcDir) throws IOException {
        try (var stream = Files.walk(srcDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains(File.separator + "test" + File.separator))
                    .sorted()
                    .toList();
        }
    }

    private static void compileJavaWithDebug(List<Path> sources, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available. Run with a JDK, not a JRE.");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(sources);

        List<String> options = new ArrayList<>();
        options.add("-g");
        options.add("-d");
        options.add(classesDir.toString());

        boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();
        fileManager.close();
        if (!ok) {
            StringBuilder sb = new StringBuilder();
            for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                sb.append(d).append("\n");
            }
            throw new IllegalStateException("Compilation failed:\n" + sb);
        }
    }

    private static void createJar(Path classesDir, Path jarPath) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarPath)))) {
            try (var stream = Files.walk(classesDir)) {
                for (Path p : stream.filter(Files::isRegularFile).toList()) {
                    String rel = classesDir.relativize(p).toString().replace('\\', '/');
                    JarEntry entry = new JarEntry(rel);
                    jos.putNextEntry(entry);
                    jos.write(Files.readAllBytes(p));
                    jos.closeEntry();
                }
            }
        }
    }
}

