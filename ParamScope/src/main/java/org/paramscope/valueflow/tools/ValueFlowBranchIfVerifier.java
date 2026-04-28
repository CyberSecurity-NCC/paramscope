package org.paramscope.valueflow.tools;

import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.call.CallSite;
import org.paramscope.valueflow.ValueFlowResultTree;
import org.paramscope.valueflow.ValueFlowRunner;
import org.paramscope.valueflow.check.NoopChecker;
import org.paramscope.valueflow.target.StmtLocalTarget;
import org.paramscope.valueflow.target.ValueTarget;
import sootup.core.jimple.basic.Local;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Verification for motivationExample_valueFlow/Branch_if.java:
 * reconstruct Local "x" at stmt: if (x.equals("12")) ...
 *
 * Outputs:
 * - <repo>/motivationExample_valueFlow/test/Branch_if.jimple.txt
 * - <repo>/motivationExample_valueFlow/test/Branch_if.valueflow.dot/.png
 * - <repo>/motivationExample_valueFlow/test/Branch_if.valueflow.result.txt
 */
public final class ValueFlowBranchIfVerifier {

    private ValueFlowBranchIfVerifier() {
    }

    public static void main(String[] args) throws Exception {
        Path repoRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent();
        if (repoRoot == null) {
            repoRoot = Paths.get(".").toAbsolutePath();
        }
        Path srcDir = repoRoot.resolve("motivationExample_valueFlow");
        Path outDir = srcDir.resolve("test");
        Path buildDir = outDir.resolve("build_branch_if");
        Path classesDir = buildDir.resolve("classes");
        Path jarPath = buildDir.resolve("motivationExample_valueFlow_branch_if.jar");

        Files.createDirectories(outDir);
        Files.createDirectories(classesDir);

        List<Path> sources = listJavaSources(srcDir);
        compileJavaWithDebug(sources, classesDir);
        createJar(classesDir, jarPath);

        AnalysisEnv.initJarOnly(jarPath.toString(), true);

        MethodSignature ms = findBranchIfMain();
        JavaSootMethod m = AnalysisEnv.view().getMethod(ms).orElseThrow();
        List<Stmt> stmts = m.getBody().getStmts();

        // dump "indexed jimple"
        Path jimpleOut = outDir.resolve("Branch_if.jimple.txt");
        Files.writeString(jimpleOut, dumpIndexed(stmts), StandardCharsets.UTF_8);

        int ifIndex = findIfEquals12Index(stmts);
        Stmt ifStmt = stmts.get(ifIndex);

        String xLocalName = pickXLocalName(ifStmt);
        if (xLocalName == null || xLocalName.isBlank()) {
            // fall back: try literal name "x"
            xLocalName = "x";
        }

        CallSite pseudo = new CallSite(ms, ms, ifStmt.getPositionInfo(), ifStmt);
        ValueTarget target = new StmtLocalTarget(
                pseudo,
                ms,
                ifIndex,
                xLocalName,
                Optional.empty(),
                Optional.empty()
        );

        ValueFlowResultTree tree = ValueFlowRunner.run(target, new NoopChecker());
        String dot = ValueFlowTreeToDot.toDot(tree);

        Path dotPath = outDir.resolve("Branch_if.valueflow.dot");
        Path pngPath = outDir.resolve("Branch_if.valueflow.png");
        Files.writeString(dotPath, dot, StandardCharsets.UTF_8);
        try {
            guru.nidi.graphviz.engine.Graphviz.fromString(dot)
                    .render(guru.nidi.graphviz.engine.Format.PNG)
                    .toFile(pngPath.toFile());
        } catch (guru.nidi.graphviz.engine.GraphvizException ignored) {
        }

        Object concrete = (tree.getResolved().isEmpty() || tree.getResolved().get(0).concreteValues() == null)
                ? null
                : tree.getResolved().get(0).concreteValues()[0];

        String resultTxt = ""
                + "method=" + ms + "\n"
                + "ifStmtIndex=" + ifIndex + "\n"
                + "seedLocalName=" + xLocalName + "\n"
                + "reconstructed=" + String.valueOf(concrete) + "\n";
        Files.writeString(outDir.resolve("Branch_if.valueflow.result.txt"), resultTxt, StandardCharsets.UTF_8);
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

    private static String pickXLocalName(Stmt ifStmt) {
        for (var u : ifStmt.getUses().toList()) {
            if (u instanceof Local l) {
                if ("x".equals(l.getName())) {
                    return l.getName();
                }
            }
        }
        for (var u : ifStmt.getUses().toList()) {
            if (u instanceof Local l) {
                return l.getName();
            }
        }
        return null;
    }

    private static String dumpIndexed(List<Stmt> stmts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stmts.size(); i++) {
            sb.append(i).append(": ").append(stmts.get(i)).append("\n");
        }
        return sb.toString();
    }
}

