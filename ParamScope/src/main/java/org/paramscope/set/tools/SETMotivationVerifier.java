package org.paramscope.set.tools;

import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.exec.Limits;
import org.paramscope.set.exec.SETBuilder;
import org.paramscope.set.exec.TraversalStrategy;
import org.paramscope.set.state.ProgramPoint;
import sootup.core.cache.provider.LRUCacheProvider;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.bytecode.inputlocation.JrtFileSystemAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Automated acceptance checks for Phase-1 motivation examples.
 *
 * Writes report ONLY under: motivationExample/test
 */
public final class SETMotivationVerifier {
    private static final Path OUTPUT_ROOT = Paths.get("motivationExample", "test").toAbsolutePath();
    private static final Path INPUT_JAR = OUTPUT_ROOT.resolve("motivationExample.jar");

    public static void main(String[] args) {
        JavaView view = buildJarView(INPUT_JAR);
        Limits limits = Limits.defaults();
        SETBuilder builder = new SETBuilder(TraversalStrategy.BFS, limits);

        List<CheckResult> results = new ArrayList<>();
        results.add(checkTC01(view, builder));
        results.add(checkTC02(view, builder));
        results.add(checkTC03(view, builder));
        results.add(checkTC04(view, builder));
        results.add(checkTC05(view, builder));

        writeReport(results);
    }

    private static CheckResult checkTC01(JavaView view, SETBuilder builder) {
        String id = "TC01_AssignArithmetic.target";
        ExecutionGraph g = run(view, builder, "TC01_AssignArithmetic", "target");
        boolean pass = g.activeStates().isEmpty() && g.terminalStates().size() == 1;
        return new CheckResult(id, pass, summary(g, "Expect 1 terminal return, no active states."));
    }

    private static CheckResult checkTC02(JavaView view, SETBuilder builder) {
        String id = "TC02_IfTrivialUnsatPrune.target";
        ExecutionGraph g = run(view, builder, "TC02_IfTrivialUnsatPrune", "target");
        // Expect exactly two terminal returns (then + inner-else), and no contradictory PCs survived.
        boolean pass = g.terminalStates().size() == 2 && g.activeStates().isEmpty();
        return new CheckResult(id, pass, summary(g, "Expect inner-then pruned; 2 terminal returns, no active."));
    }

    private static CheckResult checkTC03(JavaView view, SETBuilder builder) {
        String id = "TC03_StringConcatBranch.target";
        ExecutionGraph g = run(view, builder, "TC03_StringConcatBranch", "target");
        boolean pass = g.terminalStates().size() == 2 && g.activeStates().isEmpty();
        return new CheckResult(id, pass, summary(g, "Expect 2 terminal returns for flag true/false."));
    }

    private static CheckResult checkTC04(JavaView view, SETBuilder builder) {
        String id = "TC04_MultiBranchStatesAtCallsite.target";
        ExecutionGraph g = run(view, builder, "TC04_MultiBranchStatesAtCallsite", "target");
        boolean hasMultiAtPoint = g.statesAtPoint().values().stream().anyMatch(l -> l.size() > 1);
        boolean pass = hasMultiAtPoint && g.activeStates().isEmpty() && g.terminalStates().size() >= 1;
        return new CheckResult(id, pass, summary(g, "Expect >1 state reaching at least one program point."));
    }

    private static CheckResult checkTC05(JavaView view, SETBuilder builder) {
        String id = "TC05_DepthAndReturnTermination.target";
        ExecutionGraph g = run(view, builder, "TC05_DepthAndReturnTermination", "target");
        boolean pass = g.terminalStates().size() >= 3 && g.activeStates().isEmpty();
        return new CheckResult(id, pass, summary(g, "Expect multiple terminal returns (>=3)."));
    }

    private static ExecutionGraph run(JavaView view, SETBuilder builder, String className, String methodName) {
        JavaSootMethod m = findMethod(view, className, methodName)
                .orElseThrow(() -> new IllegalStateException("Method not found: " + className + "." + methodName));
        return builder.build(m);
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

    private static String summary(ExecutionGraph g, String expectation) {
        long multiPoints = g.statesAtPoint().values().stream().filter(l -> l.size() > 1).count();
        return String.join("\n",
                expectation,
                "states=" + g.states().size(),
                "edges=" + g.edges().values().stream().mapToInt(List::size).sum(),
                "terminal=" + g.terminalStates().size(),
                "active=" + g.activeStates().size(),
                "multiStatePoints=" + multiPoints
        );
    }

    private static void writeReport(List<CheckResult> results) {
        File outDir = OUTPUT_ROOT.toFile();
        if (!outDir.exists()) outDir.mkdirs();
        File out = OUTPUT_ROOT.resolve("phase1_acceptance_report.txt").toFile();

        int pass = 0;
        try (FileWriter w = new FileWriter(out)) {
            w.write("Phase1 SET Acceptance Report\n");
            w.write("OutputRoot: " + OUTPUT_ROOT + "\n");
            w.write("InputJar: " + INPUT_JAR + "\n\n");
            for (CheckResult r : results) {
                if (r.pass) pass++;
                w.write((r.pass ? "[PASS] " : "[FAIL] ") + r.caseId + "\n");
                w.write(r.details + "\n\n");
            }
            w.write("Summary: " + pass + "/" + results.size() + " passed\n");
        } catch (IOException e) {
            throw new RuntimeException("Failed writing report: " + out, e);
        }
    }

    private record CheckResult(String caseId, boolean pass, String details) {}
}

