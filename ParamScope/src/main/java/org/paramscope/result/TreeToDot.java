package org.paramscope.result;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizException;
import org.paramscope.analysis.slice.Slicing;
import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.data.APIList;
import org.paramscope.slice.IntraResult;
import org.paramscope.slice.IntraResultNode;
import org.paramscope.slice.IntraResultTree;
import org.paramscope.slice.OneResult;
import sootup.core.jimple.common.stmt.Stmt;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class TreeToDot {

    IntraResultTree intraResultTree;
    StringBuilder dot;

    public TreeToDot(IntraResultTree intraResultTree) {
        this.intraResultTree = intraResultTree;
        this.toDOT();
    }

    public void toDOT() {
        if (intraResultTree == null || intraResultTree.getRoot() == null) {
            return;
        }

        dot = new StringBuilder();
        dot.append("digraph Tree {\n");
        dot.append("    labelloc=a\n");
        dot.append("    style=filled\n");
        dot.append("    color=grey90\n");
        dot.append("    node [shape=box, style=filled, fillcolor=white, color=grey50]\n");
        dot.append("    fontsize=15\n");
        dot.append("\n");

        Queue<IntraResultNode> queue = new LinkedList<>();
        queue.add(intraResultTree.getRoot());

        while (!queue.isEmpty()) {
            IntraResultNode node = queue.poll();
            IntraResult2Dot nodeDOT = new IntraResult2Dot(node.getIntraResult());
            dot.append(nodeDOT.getDOT());
            for (CallSite callSite : node.getCallerResults().keySet()) {
                IntraResultNode callerNode = node.getCallerResults().get(callSite);
                IntraResult2Dot callerNodeDOT = new IntraResult2Dot(callerNode.getIntraResult());
                dot.append("\n");
                dot.append("    ").append(callerNodeDOT.startHash).append(":s -> ").append(nodeDOT.lastHash).append(":n\n\n");
                queue.add(callerNode);
            }
        }

        dot.append("\n");
        dot.append("    label = \"Results:[ ");

        Iterator<OneResult> iterator = intraResultTree.getSolvedResults().keySet().iterator();
        while (iterator.hasNext()) {
            OneResult oneResult = iterator.next();
            dot.append(" [");
            Object[] oneResultInstances = intraResultTree.getSolvedResults().get(oneResult);
            for (Object obj : oneResultInstances) {
                if (obj == null) {
                    dot.append("\\\"").append("null").append("\\\"");
                    if (oneResult.getNullReason() != null) {
                        dot.append("(\\\"").append(oneResult.getNullReason()).append("\\\")");
                    }
                } else {
                    dot.append("\\\"").append(obj).append("\\\"");
                    if (intraResultTree.getArrayInfo().containsKey(oneResult)) {
                        dot.append("(").append(intraResultTree.getArrayInfo().get(oneResult)).append(")");
                    }
                    checkNotNullRandomizedArray(dot);
                }
            }
            dot.append(intraResultTree.getResultSecurity().get(oneResult));
            dot.append(" ]");
            if (iterator.hasNext()) {
                dot.append(", ");
            }
        }
        dot.append("]\"\n");
        dot.append("\n}");
    }

    private void checkNotNullRandomizedArray(StringBuilder dot) {
        // There are still some implementation issues with Array Randomization Check.
        // For temporary, here we check the result text.
        APIParamInfo apiParamInfo = intraResultTree.getRoot().getIntraResult().getApiParamInfo();

        if ((dot.toString().contains("Not SecureRandomized Array(Constant array/Credential in String/Insecure PRNG)") && dot.toString().contains("<java.security.SecureRandom: java.util.stream.IntStream ints()>"))
                || (dot.toString().contains("Not SecureRandomized Array(Constant array/Credential in String/Insecure PRNG)") && dot.toString().contains("<java.security.SecureRandom: byte[] generateSeed"))) {
            Slicing.setInsecurityFlagFalse();
            int start = dot.indexOf("Not SecureRandomized Array(Constant array/Credential in String/Insecure PRNG)");
            dot.replace(start, start + "Not SecureRandomized Array(Constant array/Credential in String/Insecure PRNG)".length(), "(SecureRandomized array)");
        }

        if (APIList.getTrackArrayApiParamInfoList().contains(apiParamInfo)) {
            if ((dot.toString().contains("<java.security.SecureRandom: void nextBytes(byte[])>") && dot.toString().contains("new java.security.SecureRandom"))
                    || (dot.toString().contains("<java.util.Random: void nextBytes(byte[])>") && dot.toString().contains("new java.security.SecureRandom"))
                    || (dot.toString().contains("<java.security.SecureRandom: byte[] generateSeed") && dot.toString().contains("new java.security.SecureRandom"))) {
                Slicing.setInsecurityFlagFalse();
                dot.append("(SecureRandomized array)");
            } else if (dot.toString().contains("<java.util.Random: void nextBytes(byte[])>") && dot.toString().contains("new java.util.Random")) {
                Slicing.setInSecurityFlagTrue();
                dot.append("(InsecureRandomized array)");
            }
        }

        // if result passed via String.
        if (APIList.getTrackStringInCredentialsApiParamInfoList().contains(apiParamInfo)) {
            if (dot.toString().contains("<java.security.SecureRandom: java.util.stream.IntStream ints()>") && dot.toString().contains(apiParamInfo.getMethodSignatureString())) {
                int secureRandomizationIndex = dot.indexOf("<java.security.SecureRandom: java.util.stream.IntStream ints()>");
                int apiIndex = dot.indexOf(apiParamInfo.getMethodSignatureString());
                if (dot.subSequence(apiIndex, secureRandomizationIndex).toString().contains("<java.lang.String: java.lang.String valueOf(java.lang.Object)>")) {
                    Slicing.setInSecurityFlagTrue();
                    int start = dot.indexOf("(SecureRandomized array)");
                    dot.replace(start, start + "(SecureRandomized array)".length(), "(SecureRandomized array, but credentials in String)");
                }
            }
        }

        if (APIList.getTrackLongApiParamInfoList().contains(apiParamInfo)) {
            if (dot.toString().contains("<java.security.SecureRandom: long nextLong()>")) {
                Slicing.setInsecurityFlagFalse();
                dot.append("(SecureRandomized Long)");
            }
        }
    }

    public void save(String filePath) {

        File file = new File(filePath);
        File pngFile = new File(filePath + ".png");
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(dot.toString());
            System.out.println("File written to " + filePath);
        } catch (IOException e) {
            System.err.println("Error writing file: " + e.getMessage());
        }

        try {
            if (dot.length() <= 500000) {
                Graphviz.fromFile(file).render(Format.PNG).toFile(pngFile);
            } else {
                System.out.println("[INFO] too large graph");
            }
        } catch (IOException e) {
            System.err.println("Error writing file: " + e.getMessage());
        } catch (GraphvizException e) {
            System.out.println("[INFO] GraphvizException: may caused by complex string with special characters.");
        } catch (OutOfMemoryError e) {
            System.out.println("[INFO] OutOfMemoryError: too large graph, stop rendering current png.");
        }
    }
}


class IntraResult2Dot {
    String startHash;
    String lastHash;
    String methodSignature;
    StringBuilder intraResultDOT;
    IntraResult intraResult;

    public IntraResult2Dot(IntraResult intraResult) {
        this.methodSignature = intraResult.getCallSite().getCaller().toString();
        this.intraResultDOT = new StringBuilder();
        this.intraResult = intraResult;
        this.build();
    }

    private void build() {

        intraResultDOT.append("    subgraph cluster_").append(methodSignature.hashCode() & 0x7FFFFFFF).append(" {\n");
        intraResultDOT.append("        label = \"").append(methodSignature).append("\"\n");

        List<Stmt> stmts = intraResult.getResultStmts();

        for (Stmt stmt : intraResult.getResultStmts()) {
            appendStmtBox(intraResultDOT, stmt);
        }
        intraResultDOT.append("\n");
        for (int i = intraResult.getResultStmts().size() - 1; i >= 1; i--) {
            intraResultDOT.append("        ").append(stmts.get(i).hashCode() & 0x7FFFFFFF).append(":s -> ").append(stmts.get(i - 1).hashCode() & 0x7FFFFFFF).append(":n \n");
        }
        this.startHash = String.valueOf(stmts.get(0).hashCode() & 0x7FFFFFFF);
        this.lastHash = String.valueOf(stmts.get(stmts.size() - 1).hashCode() & 0x7FFFFFFF);
        intraResultDOT.append("    }\n");

    }

    private void appendStmtBox(StringBuilder stringBuilder, Stmt stmt) {
        stringBuilder.append("        ").append(stmt.hashCode() & 0x7FFFFFFF).append("[label=\"").append(stmt.toString().replace("\"", "\\\"")).append("\"]\n");
    }

    public String getDOT() {
        return intraResultDOT.toString();
    }

}