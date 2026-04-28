package org.paramscope.set.tools;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizException;
import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.exec.Limits;
import org.paramscope.set.exec.SETBuilder;
import org.paramscope.set.exec.TraversalStrategy;
import org.paramscope.set.exec.post.BranchPruner;
import org.paramscope.set.exec.post.ConditionNodeEliminator;
import org.paramscope.set.exec.post.StateDeduplicator;
import org.paramscope.set.util.ExecutionGraphToDot;
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
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Phase-3 runner: build raw ExecutionGraph, then apply Rule1-4 post-passes and output artifacts.
 *
 * Outputs under: motivationExample/test/
 */
public final class SETPhase3PruneRunner {

    private SETPhase3PruneRunner() {
    }

    public static void main(String[] args) throws Exception {
        String mainClassSimpleName = (args != null && args.length > 0 && args[0] != null && !args[0].isBlank())
                ? args[0].trim()
                : "motivationExample";

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

        AnalysisEnv.initJarOnly(jarPath.toString(), true);

        MethodSignature ms = AnalysisEnv.view().getClasses().stream()
                .filter(c -> c.getType().getClassName().equals(mainClassSimpleName))
                .flatMap(c -> c.getMethods().stream())
                .map(m -> m.getSignature())
                .filter(sig -> sig.getSubSignature().getName().equals("main"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find main() in class: " + mainClassSimpleName));
        java.util.Objects.requireNonNull(ms, "methodSignature");

        JavaSootMethod m = AnalysisEnv.view().getMethod(ms).orElseThrow();

        Limits limits = Limits.defaults();
        SETBuilder builder = new SETBuilder(TraversalStrategy.BFS, limits);
        ExecutionGraph raw = builder.build(m);

        ExecutionGraph pruned1 = BranchPruner.prune(raw, BranchPruner.Options.defaults());
        ExecutionGraph pruned2 = ConditionNodeEliminator.eliminate(pruned1);
        ExecutionGraph pruned3 = StateDeduplicator.dedup(pruned2);

        String prefix = "phase3_" + mainClassSimpleName + "_";
        writeDotPng(outDir, prefix + "raw", ExecutionGraphToDot.toDot(raw));
        writeDotPng(outDir, prefix + "pruned_rule1", ExecutionGraphToDot.toDot(pruned1));
        writeDotPng(outDir, prefix + "pruned_rule12", ExecutionGraphToDot.toDot(pruned2));
        writeDotPng(outDir, prefix + "pruned_rule123", ExecutionGraphToDot.toDot(pruned3));

        String report = ""
                + "method=" + ms + "\n"
                + "raw.states=" + raw.states().size() + " raw.edges=" + raw.edges().values().stream().mapToInt(List::size).sum() + "\n"
                + "r1.states=" + pruned1.states().size() + " r1.edges=" + pruned1.edges().values().stream().mapToInt(List::size).sum() + "\n"
                + "r12.states=" + pruned2.states().size() + " r12.edges=" + pruned2.edges().values().stream().mapToInt(List::size).sum() + "\n"
                + "r123.states=" + pruned3.states().size() + " r123.edges=" + pruned3.edges().values().stream().mapToInt(List::size).sum() + "\n";
        Files.writeString(outDir.resolve(prefix + "prune_report.txt"), report, StandardCharsets.UTF_8);
    }

    private static void writeDotPng(Path outDir, String baseName, String dot) throws IOException {
        Path dotPath = outDir.resolve(baseName + ".dot");
        Path pngPath = outDir.resolve(baseName + ".png");
        Files.writeString(dotPath, dot, StandardCharsets.UTF_8);
        try {
            Graphviz.fromString(dot).render(Format.PNG).toFile(pngPath.toFile());
        } catch (GraphvizException ignored) {
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

