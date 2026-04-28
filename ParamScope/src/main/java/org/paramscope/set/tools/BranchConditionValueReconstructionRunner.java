package org.paramscope.set.tools;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.set.reconstruct.BranchConditionReconstructionResult;
import org.paramscope.set.reconstruct.ConditionReconstructor;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Phase-2 runner: reconstruct Local values used in a branch condition stmt.
 *
 * <p>Current demo targets motivationExample_valueFlow/Branch_if.java (if (x.equals("12")) ...).</p>
 *
 * <p>Outputs under: motivationExample_valueFlow/test/</p>
 */
public final class BranchConditionValueReconstructionRunner {

    private BranchConditionValueReconstructionRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path wd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path repoRoot = Files.exists(wd.resolve("motivationExample_valueFlow"))
                ? wd
                : (wd.getParent() == null ? wd : wd.getParent());
        Path srcDir = repoRoot.resolve("motivationExample_valueFlow");
        Path outDir = srcDir.resolve("test");
        Path buildDir = outDir.resolve("build_branch_condition_values");
        Path classesDir = buildDir.resolve("classes");
        Path jarPath = buildDir.resolve("motivationExample_valueFlow_branch_condition_values.jar");

        Files.createDirectories(outDir);
        Files.createDirectories(classesDir);

        List<Path> sources = listJavaSources(srcDir);
        if (sources.isEmpty()) {
            throw new IllegalStateException("No .java sources found in " + srcDir);
        }
        compileJavaWithDebug(sources, classesDir);
        createJar(classesDir, jarPath);

        AnalysisEnv.initJarOnly(jarPath.toString(), true);

        MethodSignature ms = findBranchIfMain();
        Objects.requireNonNull(ms, "methodSignature");
        JavaSootMethod m = AnalysisEnv.view().getMethod(ms).orElseThrow();
        List<Stmt> stmts = m.getBody().getStmts();

        int ifIndex = findIfEquals12Index(stmts);
        BranchConditionReconstructionResult res = ConditionReconstructor.reconstructBranchConditionLocals(ms, ifIndex);

        StringBuilder sb = new StringBuilder();
        sb.append("method=").append(ms).append("\n");
        sb.append("branchStmtIndex=").append(ifIndex).append("\n");
        sb.append("branchStmt=").append(res.branchStmtText()).append("\n");
        sb.append("locals=").append(res.extractedLocalNames()).append("\n\n");
        for (var e : res.reconstructedLocals().entrySet()) {
            sb.append("local=").append(e.getKey()).append("\n");
            sb.append("  seedStmtIndex=").append(e.getValue().seedStmtIndex()).append("\n");
            sb.append("  concrete=").append(String.valueOf(e.getValue().concreteValue())).append("\n");
            if (e.getValue().diagnostics() != null && !e.getValue().diagnostics().isEmpty()) {
                sb.append("  diagnostics=").append(String.join("; ", e.getValue().diagnostics())).append("\n");
            }
            sb.append("\n");
        }

        Files.writeString(outDir.resolve("Branch_if.condition_values.txt"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static MethodSignature findBranchIfMain() {
        // Find by simple name. This avoids hardcoding SootUp signature string format.
        return AnalysisEnv.view().getClasses().stream()
                .filter(c -> Objects.equals(c.getType().getClassName(), "Branch_if"))
                .flatMap(c -> c.getMethods().stream())
                .map(m -> m.getSignature())
                .filter(sig -> Objects.equals(sig.getSubSignature().getName(), "main"))
                .min(Comparator.comparing(MethodSignature::toString))
                .orElseThrow(() -> new IllegalStateException("Branch_if.main not found"));
    }

    private static int findIfEquals12Index(List<Stmt> stmts) {
        for (int i = 0; i < stmts.size(); i++) {
            String s = stmts.get(i).toString();
            if (s.contains("equals") && s.contains("\"12\"")) {
                return i;
            }
        }
        throw new IllegalStateException("if (x.equals(\"12\")) stmt not found in Jimple");
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
}

