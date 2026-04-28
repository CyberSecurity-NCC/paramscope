package org.paramscope.valueflow.tools;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizException;
import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.call.CallSite;
import org.paramscope.call.MethodInfo;
import org.paramscope.data.CallRelation;
import org.paramscope.valueflow.ValueFlowResultTree;
import org.paramscope.valueflow.ValueFlowRunner;
import org.paramscope.valueflow.check.NoopChecker;
import org.paramscope.valueflow.target.CallsiteArgTarget;
import org.paramscope.valueflow.target.StmtLocalTarget;
import org.paramscope.valueflow.target.ValueTarget;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.jimple.basic.JavaLocal;
import sootup.core.signatures.MethodSignature;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.*;

/**
 * Test driver for motivationExample_valueFlow.
 *
 * Output directory requirement:
 *   <repo>/motivationExample_valueFlow/test/  (dot/png + build artifacts)
 */
public final class ValueFlowMotivationRunner {

    private ValueFlowMotivationRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path repoRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent();
        if (repoRoot == null) {
            repoRoot = Paths.get(".").toAbsolutePath();
        }
        Path srcDir = repoRoot.resolve("motivationExample_valueFlow");
        Path outDir = srcDir.resolve("test");
        Path buildDir = outDir.resolve("build");
        Path classesDir = buildDir.resolve("classes");
        Path jarPath = buildDir.resolve("motivationExample_valueFlow.jar");

        Files.createDirectories(outDir);
        cleanupOldOutputs(outDir);

        Files.createDirectories(classesDir);

        List<Path> sources = listJavaSources(srcDir);
        if (sources.isEmpty()) {
            throw new IllegalStateException("No .java sources found in " + srcDir);
        }

        compileJava(sources, classesDir);
        createJar(classesDir, jarPath);

        AnalysisEnv.initJarOnly(jarPath.toString(), true);

        List<CallSite> cipherGetInstanceCallSites = findCipherGetInstanceCallSites();

        Files.createDirectories(outDir);

        int idx = 0;
        for (CallSite cs : cipherGetInstanceCallSites) {
            idx++;
            String key = safeFileKey(cs.getCaller(), cs.getCallee(), cs.getPos());
            Path dotPath = outDir.resolve(idx + "_" + key + ".dot");
            Path pngPath = outDir.resolve(idx + "_" + key + ".png");

            ValueTarget callsiteArgTarget = new CallsiteArgTarget(
                    cs,
                    cs.getCallee(),
                    List.of(0),
                    false,
                    Optional.empty()
            );

            ValueFlowResultTree tree = ValueFlowRunner.run(callsiteArgTarget, new NoopChecker());
            String dot = ValueFlowTreeToDot.toDot(tree);
            Files.writeString(dotPath, dot, StandardCharsets.UTF_8);

            try {
                Graphviz.fromString(dot).render(Format.PNG).toFile(pngPath.toFile());
            } catch (GraphvizException e) {
            }

            // Verification: build an equivalent STMT_LOCAL target for arg0 local (if arg0 is a Local).
            try {
                AbstractInvokeExpr ie = cs.getInvokeStmt().getInvokeExpr();
                Value arg0 = ie.getArg(0);
                if (arg0 instanceof JavaLocal jl) {
                    JavaSootMethod callerM = AnalysisEnv.view().getMethod(cs.getCaller()).get();
                    List<sootup.core.jimple.common.stmt.Stmt> stmts = callerM.getBody().getStmts();
                    int stmtIndex = -1;
                    for (int i = 0; i < stmts.size(); i++) {
                        if (stmts.get(i).equivTo(cs.getInvokeStmt())) {
                            stmtIndex = i;
                            break;
                        }
                    }
                    if (stmtIndex >= 0) {
                        CallSite pseudo = new CallSite(cs.getCaller(), cs.getCaller(), cs.getInvokeStmt().getPositionInfo(), stmts.get(stmtIndex));
                        ValueTarget stmtLocalTarget = new StmtLocalTarget(
                                pseudo,
                                cs.getCaller(),
                                stmtIndex,
                                jl.getName(),
                                Optional.empty(),
                                Optional.empty()
                        );
                        ValueFlowResultTree tree2 = ValueFlowRunner.run(stmtLocalTarget, new NoopChecker());
                        Object v1 = tree.getResolved().isEmpty() ? null : tree.getResolved().get(0).concreteValues()[0];
                        Object v2 = tree2.getResolved().isEmpty() ? null : tree2.getResolved().get(0).concreteValues()[0];
                        if (!Objects.equals(String.valueOf(v1), String.valueOf(v2))) {
                            System.err.println("VERIFY_MISMATCH " + key + " callsiteArg=" + v1 + " stmtLocal=" + v2);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
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

    private static void compileJava(List<Path> sources, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available. Run with a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(sources);

        List<String> options = new ArrayList<>();
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
        Files.createDirectories(jarPath.getParent());
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

    private static List<CallSite> findCipherGetInstanceCallSites() {
        List<CallSite> res = new ArrayList<>();
        Map<MethodSignature, MethodInfo> map = CallRelation.getApplicationMethodAndapiMethodMap();
        for (MethodInfo mi : map.values()) {
            for (CallSite cs : mi.getCallSites()) {
                MethodSignature callee = cs.getCallee();
                String decl = callee.getDeclClassType().getFullyQualifiedName();
                String name = callee.getSubSignature().getName();
                if ("javax.crypto.Cipher".equals(decl) && "getInstance".equals(name)) {
                    res.add(cs);
                }
            }
        }
        res.sort(Comparator.comparing(a -> a.getCaller().toString()));
        return res;
    }

    private static String safeFileKey(MethodSignature caller, MethodSignature callee, sootup.core.jimple.basic.StmtPositionInfo pos) {
        String posStr = (pos == null) ? "noPos" : String.valueOf(pos.getStmtPosition());
        String raw = caller.getDeclClassType().getClassName() + "_" + caller.getSubSignature().getName()
                + "__" + callee.getDeclClassType().getClassName() + "_" + callee.getSubSignature().getName()
                + "__" + posStr;
        return raw.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
    }

    private static void cleanupOldOutputs(Path outDir) throws IOException {
        try (var s = Files.list(outDir)) {
            s.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".dot") || n.endsWith(".png");
                    })
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}

