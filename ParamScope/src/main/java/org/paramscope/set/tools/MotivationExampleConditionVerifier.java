package org.paramscope.set.tools;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.set.reconstruct.BranchConditionReconstructionResult;
import org.paramscope.set.reconstruct.ConditionReconstructor;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
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
 * Verify branch-condition local reconstruction on motivationExample/motivationExample.java.
 *
 * <p>Outputs all intermediate files under: motivationExample/test/</p>
 */
public final class MotivationExampleConditionVerifier {

    private MotivationExampleConditionVerifier() {
    }

    public static void main(String[] args) throws Exception {
        Path wd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path repoRoot = Files.exists(wd.resolve("motivationExample"))
                ? wd
                : (wd.getParent() == null ? wd : wd.getParent());

        Path srcDir = repoRoot.resolve("motivationExample");
        Path outDir = srcDir.resolve("test");
        Path buildDir = outDir.resolve("build_motivation_condition_verify");
        Path classesDir = buildDir.resolve("classes");
        Path jarPath = outDir.resolve("motivationExample_condition_verify.jar");

        Files.createDirectories(outDir);
        Files.createDirectories(classesDir);

        List<Path> sources = listJavaSources(srcDir);
        if (sources.isEmpty()) {
            throw new IllegalStateException("No .java sources found in " + srcDir);
        }
        compileJavaWithDebug(sources, classesDir);
        createJar(classesDir, jarPath);

        AnalysisEnv.initJarOnly(jarPath.toString(), true);

        JavaSootClass clazz = AnalysisEnv.view().getClasses().stream()
                .filter(c -> Objects.equals(c.getType().getClassName(), "motivationExample"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("motivationExample class not found"));

        JavaSootMethod main = clazz.getMethods().stream()
                .filter(m -> Objects.equals(m.getSignature().getSubSignature().getName(), "main"))
                .min(Comparator.comparing(m -> m.getSignature().toString()))
                .orElseThrow(() -> new IllegalStateException("motivationExample.main not found"));

        MethodSignature ms = main.getSignature();
        List<Stmt> stmts = main.getBody().getStmts();

        // Dump indexed jimple for manual cross-check.
        Files.writeString(outDir.resolve("motivationExample_main.jimple.indexed.txt"), dumpIndexed(stmts), StandardCharsets.UTF_8);

        int randCallIndex = findFirstIndexContains(stmts, "nextBoolean");
        int randIfIndex = (randCallIndex >= 0) ? findFirstIfIndexAfter(stmts, randCallIndex) : -1;

        List<Integer> ifIndices = findAllIfIndices(stmts);
        StringBuilder report = new StringBuilder();
        report.append("MotivationExampleConditionVerifier report\n");
        report.append("method=").append(ms).append("\n");
        report.append("stmtCount=").append(stmts.size()).append("\n");
        report.append("ifCount=").append(ifIndices.size()).append("\n");
        report.append("randCallIndex=").append(randCallIndex).append("\n");
        report.append("randIfIndex=").append(randIfIndex).append("\n\n");

        for (int ifIdx : ifIndices) {
            Stmt ifStmt = stmts.get(ifIdx);
            report.append("== IF @ ").append(ifIdx).append(" ==\n");
            report.append("ifStmt=").append(String.valueOf(ifStmt)).append("\n");

            if (ifIdx == randIfIndex) {
                // For Random.nextBoolean branch, run multiple reconstructions to see if both true/false appear.
                int runs = 30;
                Map<String, Set<String>> observed = new LinkedHashMap<>();
                for (int r = 0; r < runs; r++) {
                    BranchConditionReconstructionResult res = ConditionReconstructor.reconstructDirectConditionLocalsSeedAtDef(ms, ifIdx);
                    for (var e : res.reconstructedLocals().entrySet()) {
                        observed.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>())
                                .add(String.valueOf(e.getValue().concreteValue()));
                    }
                }
                report.append("mode=multi_run_random\n");
                report.append("runs=").append(runs).append("\n");
                for (var e : observed.entrySet()) {
                    report.append("  local=").append(e.getKey()).append(" observed=").append(e.getValue()).append("\n");
                }
                report.append("\n");
                continue;
            }

            BranchConditionReconstructionResult res = ConditionReconstructor.reconstructDirectConditionLocalsSeedAtDef(ms, ifIdx);
            report.append("locals=").append(res.extractedLocalNames()).append("\n");
            for (var e : res.reconstructedLocals().entrySet()) {
                report.append("  ").append(e.getKey())
                        .append(" @seed=").append(e.getValue().seedStmtIndex())
                        .append(" => ").append(String.valueOf(e.getValue().concreteValue()))
                        .append("\n");
            }
            report.append("\n");
        }

        Files.writeString(outDir.resolve("motivationExample_main.condition_values.report.txt"), report.toString(), StandardCharsets.UTF_8);
    }

    private static int findFirstIndexContains(List<Stmt> stmts, String token) {
        for (int i = 0; i < stmts.size(); i++) {
            if (String.valueOf(stmts.get(i)).contains(token)) return i;
        }
        return -1;
    }

    private static int findFirstIfIndexAfter(List<Stmt> stmts, int afterIndexInclusive) {
        for (int i = Math.max(0, afterIndexInclusive); i < stmts.size(); i++) {
            if (String.valueOf(stmts.get(i)).startsWith("if ")) return i;
        }
        return -1;
    }

    private static List<Integer> findAllIfIndices(List<Stmt> stmts) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < stmts.size(); i++) {
            if (String.valueOf(stmts.get(i)).startsWith("if ")) out.add(i);
        }
        return out;
    }

    private static String dumpIndexed(List<Stmt> stmts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stmts.size(); i++) {
            sb.append(i).append(": ").append(stmts.get(i)).append("\n");
        }
        return sb.toString();
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

