package org.paramscope.set.tools;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.set.reconstruct.BranchConditionReconstructionResult;
import org.paramscope.set.reconstruct.ConditionReconstructor;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Batch verifier for motivationExample_valueFlow/Branch_*.java.
 *
 * <p>For each Branch_* case:
 * - Locate the first branch condition (first Jimple stmt whose string starts with "if ").
 * - Reconstruct the Locals used in that condition via valueflow (STMT_LOCAL).
 * - Compare reconstructed values against the source-level if line (best-effort).
 *
 * <p>All outputs go to: motivationExample_valueFlow/test/</p>
 */
public final class BranchConditionBatchVerifier {

    private BranchConditionBatchVerifier() {
    }

    public static void main(String[] args) throws Exception {
        Path wd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path repoRoot = Files.exists(wd.resolve("motivationExample_valueFlow"))
                ? wd
                : (wd.getParent() == null ? wd : wd.getParent());
        Path srcDir = repoRoot.resolve("motivationExample_valueFlow");
        Path outDir = srcDir.resolve("test");
        Path buildDir = outDir.resolve("build_branch_condition_batch");
        Path classesDir = buildDir.resolve("classes");
        Path jarPath = buildDir.resolve("motivationExample_valueFlow_branch_condition_batch.jar");

        Files.createDirectories(outDir);
        Files.createDirectories(classesDir);

        List<Path> sources = listJavaSources(srcDir);
        if (sources.isEmpty()) {
            throw new IllegalStateException("No Branch_*.java sources found in " + srcDir);
        }
        compileJavaWithDebug(sources, classesDir);
        createJar(classesDir, jarPath);

        AnalysisEnv.initJarOnly(jarPath.toString(), true);

        List<Path> branchSources = sources.stream()
                .filter(p -> p.getFileName().toString().startsWith("Branch_"))
                .sorted()
                .toList();

        StringBuilder report = new StringBuilder();
        report.append("BranchConditionBatchVerifier report\n");
        report.append("repoRoot=").append(repoRoot).append("\n");
        report.append("branchFiles=").append(branchSources.size()).append("\n\n");

        int pass = 0, fail = 0, unknown = 0;
        for (Path srcPath : branchSources) {
            String simpleName = srcPath.getFileName().toString().replace(".java", "");
            String sourceText = Files.readString(srcPath, StandardCharsets.UTF_8);
            String ifLine = findFirstIfLine(sourceText);
            Optional<String> expectedLiteralOpt = extractExpectedLiteral(ifLine);
            boolean expectMustBeTrue = ifLine != null && ifLine.contains("must be true");

            report.append("== ").append(simpleName).append(" ==\n");
            report.append("sourceIfLine=").append(ifLine == null ? "<none>" : ifLine.trim()).append("\n");
            report.append("expectedLiteral=").append(expectedLiteralOpt.orElse("<none>")).append("\n");

            Optional<JavaSootClass> cOpt = AnalysisEnv.view().getClasses().stream()
                    .filter(c0 -> Objects.equals(c0.getType().getClassName(), simpleName))
                    .findFirst();
            if (cOpt.isEmpty()) {
                report.append("status=UNKNOWN (class not found in view)\n\n");
                unknown++;
                continue;
            }
            JavaSootClass c = cOpt.get();

            // Prefer: find the branch that guards Cipher.getInstance (security-relevant branch).
            MethodSignature chosenMs = null;
            int ifIndex = -1;
            List<Stmt> stmts = null;
            for (JavaSootMethod m : c.getMethods()) {
                List<Stmt> ss = m.getBody().getStmts();
                int callIdx = findFirstCipherGetInstanceIndex(ss);
                if (callIdx >= 0) {
                    int idx = findNearestPrecedingIfIndex(ss, callIdx);
                    if (idx >= 0) {
                        chosenMs = m.getSignature();
                        ifIndex = idx;
                        stmts = ss;
                        break;
                    }
                }
            }
            // Fallback: find the first "if " stmt in any method (e.g., Branch_if has no Cipher call).
            if (chosenMs == null || stmts == null) {
                for (JavaSootMethod m : c.getMethods()) {
                    List<Stmt> ss = m.getBody().getStmts();
                    int idx = findFirstIfIndex(ss);
                    if (idx >= 0) {
                        chosenMs = m.getSignature();
                        ifIndex = idx;
                        stmts = ss;
                        break;
                    }
                }
                if (chosenMs == null || stmts == null) {
                    report.append("status=UNKNOWN (no if stmt found in any method)\n\n");
                    unknown++;
                    continue;
                }
            }

            // Dump indexed jimple for manual cross-check.
            Path jimpleOut = outDir.resolve(simpleName + ".jimple.indexed.txt");
            Files.writeString(jimpleOut, dumpIndexed(stmts), StandardCharsets.UTF_8);

            Stmt ifStmt = stmts.get(ifIndex);
            BranchConditionReconstructionResult res = ConditionReconstructor.reconstructBranchConditionLocals(chosenMs, ifIndex);

            report.append("method=").append(chosenMs).append("\n");
            report.append("ifStmtIndex=").append(ifIndex).append("\n");
            report.append("ifStmtJimple=").append(String.valueOf(ifStmt)).append("\n");
            report.append("locals=").append(res.extractedLocalNames()).append("\n");

            boolean anyMatchedLiteral = false;
            boolean anyTrue = false;
            String expected = expectedLiteralOpt.orElse(null);
            for (var e : res.reconstructedLocals().entrySet()) {
                String local = e.getKey();
                Object concrete = e.getValue().concreteValue();
                report.append("  ").append(local).append(" @seed=").append(e.getValue().seedStmtIndex())
                        .append(" => ").append(String.valueOf(concrete)).append("\n");
                if (expected != null && concrete != null && expected.equals(String.valueOf(concrete))) {
                    anyMatchedLiteral = true;
                }
                if (Boolean.TRUE.equals(concrete)) {
                    anyTrue = true;
                }
            }

            String status;
            if (expected != null && anyMatchedLiteral) {
                status = "PASS";
                pass++;
            } else if (expectMustBeTrue && anyTrue) {
                status = "PASS";
                pass++;
            } else if (expected == null && !expectMustBeTrue) {
                status = "UNKNOWN (no expected literal extracted)";
                unknown++;
            } else {
                status = "FAIL";
                fail++;
            }
            report.append("status=").append(status).append("\n\n");
        }

        report.append("Summary: PASS=").append(pass).append(", FAIL=").append(fail).append(", UNKNOWN=").append(unknown).append("\n");
        Files.writeString(outDir.resolve("Branch_cases.condition_values.report.txt"), report.toString(), StandardCharsets.UTF_8);
    }

    private static int findFirstIfIndex(List<Stmt> stmts) {
        for (int i = 0; i < stmts.size(); i++) {
            String s = String.valueOf(stmts.get(i));
            if (s.startsWith("if ")) {
                return i;
            }
        }
        return -1;
    }

    private static int findNearestPrecedingIfIndex(List<Stmt> stmts, int beforeIndexExclusive) {
        for (int i = Math.min(beforeIndexExclusive - 1, stmts.size() - 1); i >= 0; i--) {
            String s = String.valueOf(stmts.get(i));
            if (s.startsWith("if ")) {
                return i;
            }
        }
        return -1;
    }

    private static int findFirstCipherGetInstanceIndex(List<Stmt> stmts) {
        for (int i = 0; i < stmts.size(); i++) {
            Stmt s = stmts.get(i);
            if (!s.containsInvokeExpr()) continue;
            AbstractInvokeExpr ie = s.getInvokeExpr();
            MethodSignature callee = ie.getMethodSignature();
            String decl = callee.getDeclClassType().getFullyQualifiedName();
            String name = callee.getSubSignature().getName();
            if ("javax.crypto.Cipher".equals(decl) && "getInstance".equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String findFirstIfLine(String sourceText) {
        if (sourceText == null) return null;
        for (String line : sourceText.split("\\R")) {
            if (line.contains("if (")) {
                return line;
            }
        }
        return null;
    }

    /**
     * Best-effort: extract the first string literal or integer literal appearing inside the if line.
     */
    private static Optional<String> extractExpectedLiteral(String ifLine) {
        if (ifLine == null) return Optional.empty();

        Matcher mStr = Pattern.compile("\"([^\"]*)\"").matcher(ifLine);
        if (mStr.find()) {
            return Optional.ofNullable(mStr.group(1));
        }
        Matcher mInt = Pattern.compile("\\b(\\d+)\\b").matcher(ifLine);
        if (mInt.find()) {
            return Optional.ofNullable(mInt.group(1));
        }
        return Optional.empty();
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

