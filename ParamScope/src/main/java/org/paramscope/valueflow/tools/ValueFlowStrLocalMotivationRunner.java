package org.paramscope.valueflow.tools;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizException;
import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.call.CallSite;
import org.paramscope.data.CallRelation;
import org.paramscope.valueflow.ValueFlowResultTree;
import org.paramscope.valueflow.ValueFlowRunner;
import org.paramscope.valueflow.check.NoopChecker;
import org.paramscope.valueflow.target.CallsiteArgTarget;
import org.paramscope.valueflow.target.StmtLocalTarget;
import org.paramscope.valueflow.target.ValueTarget;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
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
 * Motivation runner targeting: "str = xxx;" where str is a Local named "str".
 *
 * Output directory requirement:
 *   <repo>/motivationExample_valueFlow/test/  (dot/png + build artifacts)
 */
public final class ValueFlowStrLocalMotivationRunner {

    private ValueFlowStrLocalMotivationRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path repoRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent();
        if (repoRoot == null) {
            repoRoot = Paths.get(".").toAbsolutePath();
        }
        Path srcDir = repoRoot.resolve("motivationExample_valueFlow");
        Path outDir = srcDir.resolve("test");
        Path buildDir = outDir.resolve("build_str_local");
        Path classesDir = buildDir.resolve("classes");
        Path jarPath = buildDir.resolve("motivationExample_valueFlow_str_local.jar");

        Files.createDirectories(outDir);
        cleanupOldOutputs(outDir);
        Files.createDirectories(classesDir);

        List<Path> sources = listJavaSources(srcDir);
        if (sources.isEmpty()) {
            throw new IllegalStateException("No .java sources found in " + srcDir);
        }

        compileJavaWithDebug(sources, classesDir);
        createJar(classesDir, jarPath);

        AnalysisEnv.initJarOnly(jarPath.toString(), true);

        // Always drive by Cipher.getInstance callsites (covers all cases).
        List<CallSite> cipherGetInstanceCallSites = findCipherGetInstanceCallSites();
        writeCallsiteReport(outDir, cipherGetInstanceCallSites);

        int id = 0;
        for (CallSite cs : cipherGetInstanceCallSites) {
            MethodSignature ms = cs.getCaller();
            Optional<JavaSootMethod> mOpt = AnalysisEnv.view().getMethod(ms);
            if (mOpt.isEmpty()) continue;
            JavaSootMethod m = mOpt.get();
            if (m.getBody() == null) continue;
            List<Stmt> stmts = m.getBody().getStmts();

            int callIndex = indexOfStmt(stmts, cs.getInvokeStmt());
            if (callIndex < 0) continue;

            AbstractInvokeExpr ie = cs.getInvokeStmt().getInvokeExpr();
            if (ie.getArgCount() < 1) continue;
            Value arg0 = ie.getArg(0);
            if (!(arg0 instanceof Local l0)) {
                // If arg0 isn't a Local (e.g., constant), fall back to CALLSITE_ARGS to still cover the case.
                id++;
                ValueTarget target = new CallsiteArgTarget(
                        cs,
                        cs.getCallee(),
                        List.of(0),
                        false,
                        Optional.empty()
                );
                ValueFlowResultTree tree = ValueFlowRunner.run(target, new NoopChecker());
                String dot = ValueFlowTreeToDot.toDot(tree);

                String key = safeFileKey(ms, callIndex);
                Path dotPath = outDir.resolve(id + "_STR_" + key + ".dot");
                Path pngPath = outDir.resolve(id + "_STR_" + key + ".png");
                Files.writeString(dotPath, dot, StandardCharsets.UTF_8);
                try {
                    Graphviz.fromString(dot).render(Format.PNG).toFile(pngPath.toFile());
                } catch (GraphvizException ignored) {
                }
                continue;
            }

            int defIndex = findLatestDefOfLocal(stmts, callIndex, l0.getName());
            if (defIndex < 0) {
                // fallback: analyze at callsite itself (still may reconstruct via existing valueObjects)
                defIndex = callIndex;
            }
            int adjusted = adjustSeedForConstructorInit(stmts, defIndex, callIndex, l0.getName());
            defIndex = adjusted;

            Stmt seedStmt = stmts.get(defIndex);
            id++;
            CallSite pseudo = new CallSite(ms, ms, seedStmt.getPositionInfo(), seedStmt);
            ValueTarget target = new StmtLocalTarget(
                    pseudo,
                    ms,
                    defIndex,
                    l0.getName(),
                    Optional.empty(),
                    Optional.empty()
            );

            ValueFlowResultTree tree = ValueFlowRunner.run(target, new NoopChecker());
            String dot = ValueFlowTreeToDot.toDot(tree);

            String key = safeFileKey(ms, defIndex);
            Path dotPath = outDir.resolve(id + "_STR_" + key + ".dot");
            Path pngPath = outDir.resolve(id + "_STR_" + key + ".png");
            Files.writeString(dotPath, dot, StandardCharsets.UTF_8);
            try {
                Graphviz.fromString(dot).render(Format.PNG).toFile(pngPath.toFile());
            } catch (GraphvizException ignored) {
            }
        }
    }

    private static List<CallSite> findCipherGetInstanceCallSites() {
        List<CallSite> res = new ArrayList<>();
        Map<MethodSignature, org.paramscope.call.MethodInfo> map = CallRelation.getApplicationMethodAndapiMethodMap();
        for (org.paramscope.call.MethodInfo mi : map.values()) {
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

    private static void writeCallsiteReport(Path outDir, List<CallSite> callSites) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("cipher.getInstance callsites: ").append(callSites.size()).append("\n");
            for (CallSite cs : callSites) {
                sb.append(cs.getCaller().getDeclClassType().getClassName())
                        .append(" :: ")
                        .append(cs.getCaller())
                        .append(" @ ")
                        .append(cs.getInvokeStmt())
                        .append("\n");
            }
            Files.writeString(outDir.resolve("STR_callsite_report.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static int indexOfStmt(List<Stmt> stmts, Stmt needle) {
        for (int i = 0; i < stmts.size(); i++) {
            if (stmts.get(i).equivTo(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static int findLatestDefOfLocal(List<Stmt> stmts, int beforeIndexInclusive, String localName) {
        for (int i = Math.min(beforeIndexInclusive, stmts.size() - 1); i >= 0; i--) {
            Stmt s = stmts.get(i);
            if (s instanceof AbstractDefinitionStmt defStmt && defStmt.getDef().isPresent() && defStmt.getDef().get() instanceof Local l) {
                if (localName.equals(l.getName())) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Heuristic: if seed is "x = new T" and there is a following "specialinvoke x.<init>(...)" before the callsite,
     * move seed to that <init> stmt so that slicing can pick up constructor args / side effects.
     */
    private static int adjustSeedForConstructorInit(List<Stmt> stmts, int seedIndex, int callIndexExclusive, String localName) {
        if (seedIndex < 0 || seedIndex >= stmts.size()) return seedIndex;
        Stmt seed = stmts.get(seedIndex);
        boolean isNewDef = false;
        if (seed instanceof AbstractDefinitionStmt defStmt && defStmt.getDef().isPresent() && defStmt.getDef().get() instanceof Local l) {
            if (localName.equals(l.getName()) && String.valueOf(defStmt.getRightOp()).startsWith("new ")) {
                isNewDef = true;
            }
        }
        if (!isNewDef) return seedIndex;
        for (int i = seedIndex + 1; i < Math.min(callIndexExclusive, stmts.size()); i++) {
            String s = String.valueOf(stmts.get(i));
            if (s.contains("specialinvoke " + localName + ".<") && s.contains("<init>")) {
                return i;
            }
        }
        return seedIndex;
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

    private static void cleanupOldOutputs(Path outDir) throws IOException {
        try (var s = Files.list(outDir)) {
            s.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return (n.endsWith(".dot") || n.endsWith(".png")) && n.contains("_str_");
                    })
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    private static String safeFileKey(MethodSignature ms, int stmtIndex) {
        String raw = ms.getDeclClassType().getClassName() + "_" + ms.getSubSignature().getName() + "__stmt_" + stmtIndex;
        return raw.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
    }
}

