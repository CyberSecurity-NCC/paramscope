package org.paramscope.analysis.slice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.paramscope.analysis.AnalysisEnv;
import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.call.MethodInfo;
import org.paramscope.data.APIList;
import org.paramscope.data.CallRelation;
import org.paramscope.result.ResultEntry;
import org.paramscope.result.ResultJson;
import org.paramscope.result.TreeToDot;
import org.paramscope.result.InstanceInfo;
import org.paramscope.result.adapters.InstanceSerializer;
import org.paramscope.result.adapters.MethodSignatureTypeAdapter;
import org.paramscope.result.adapters.StmtPositionInfoTypeAdapter;
import org.paramscope.slice.IntraResult;
import org.paramscope.slice.IntraResultNode;
import org.paramscope.slice.IntraResultTree;
import org.paramscope.slice.IntraSlicing;
import org.paramscope.slice.PrunedStmtGraph;
import org.paramscope.slice.PathConstrainedStmtGraph;
import org.paramscope.set.exec.ExecutionGraph;
import org.paramscope.set.exec.Limits;
import org.paramscope.set.exec.SETBuilder;
import org.paramscope.set.exec.TraversalStrategy;
import org.paramscope.set.exec.post.BranchPruner;
import org.paramscope.set.exec.post.CfgBranchPruneReducer;
import org.paramscope.set.exec.post.PathExtractor;
import sootup.core.jimple.basic.StmtPositionInfo;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.graph.StmtGraph;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootMethod;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

public class Slicing {

    private static final HashMap<MethodSignature, String> keyPairGenerator_algo = new HashMap<>();
    private static MethodSignature currentCaller;
    private static boolean inSecurityFlag;

    public static void runSlicing2() {

        ResultJson resultJson = new ResultJson();
        ResultJson insecureResultJson = new ResultJson();
        int ID = 1;

        // 遍历API列表的所有目标API
        for (APIParamInfo apiParamInfo : APIList.getAllApiParamInfoList()) {
            if (apiParamInfo.getMethodSignature() != null) {
                // 从 applicationMethodAndapiMethodMap （该表记录了目标API和所有app方法之间的调用关系）中查询每个目标API的调用点
                MethodSignature calleeMS = apiParamInfo.getMethodSignature();
                MethodInfo calleeMI = CallRelation.getApplicationMethodAndapiMethodMap().get(calleeMS);

                for (CallSite callSite : calleeMI.getCallSites()) {
                    setInsecurityFlagFalse();
                    System.out.println("[INFO] process Callsite:");
                    System.out.println("    [caller]:" + callSite.getCaller().toString());
                    System.out.println("    [callee]:" + callSite.getCallee().toString());
                    try {
                        currentCaller = callSite.getCaller();
                        if (APIList.getKeyPairGeneratorGetInstance_Algo_String().contains(apiParamInfo)) {
                            keyPairGenerator_algo.put(callSite.getCaller(), "");
                        }

                        boolean pathSensitive = Boolean.parseBoolean(System.getProperty("paramscope.slice.pathSensitive", "false"));
                        List<IntraResultNode> roots = pathSensitive
                                ? buildIntraResultTreesPathSensitive(apiParamInfo, callSite, new ArrayList<>(), false)
                                : List.of(buildIntraResultTree2(apiParamInfo, callSite, new ArrayList<>(), false));

                        StringBuilder result_subDir = new StringBuilder();
                        result_subDir.append(safePathSegment(apiParamInfo.getMethodSignature().toString())).append("_paramList_");
                        ListIterator<Integer> paramListIterator = apiParamInfo.getParamPosList().listIterator();
                        while (paramListIterator.hasNext()) {
                            result_subDir.append(paramListIterator.next());
                            if (paramListIterator.hasNext()) {
                                result_subDir.append("_");
                            }
                        }
                        String baseFilePath = "./" + "result_" + AnalysisEnv.getFileName() + "/" + result_subDir + "/" + (callSite.hashCode() & 0x7FFFFFFF);

                        if (!pathSensitive) {
                            IntraResultTree resultTree = new IntraResultTree(roots.get(0));
                            resultTree.resolveResults();
                            resultTree.setFilePath(baseFilePath);
                            new TreeToDot(resultTree).save(baseFilePath);

                            ResultEntry resultEntry = new ResultEntry(apiParamInfo, callSite, resultTree, ID++);
                            resultJson.addResult(resultEntry);
                            if (inSecurityFlag) insecureResultJson.addResult(resultEntry);
                        } else {
                            ArrayList<InstanceInfo> mergedInstances = new ArrayList<>();
                            boolean anyInsecure = false;
                            for (int pi = 0; pi < roots.size(); pi++) {
                                IntraResultTree resultTree = new IntraResultTree(roots.get(pi));
                                resultTree.resolveResults();

                                String pathFilePath = baseFilePath + "_path" + pi;
                                resultTree.setFilePath(pathFilePath);
                                new TreeToDot(resultTree).save(pathFilePath);

                                mergedInstances.addAll(buildInstanceInfos(resultTree));
                                for (var oneResult : resultTree.getResults()) {
                                    if (isPotentialVulnerabilitySecurityInfo(oneResult.getSecurityInfo())) {
                                        anyInsecure = true;
                                        break;
                                    }
                                }
                            }

                            ResultEntry resultEntry = new ResultEntry(
                                    apiParamInfo,
                                    callSite,
                                    baseFilePath,
                                    mergedInstances.toArray(new InstanceInfo[0]),
                                    ID++
                            );
                            resultJson.addResult(resultEntry);
                            if (anyInsecure) insecureResultJson.addResult(resultEntry);
                        }
                    } catch (StackOverflowError e) {
                        System.out.println("[INFO] caught StackOverflow Error at \"" + callSite + "\", may caused by excessive call depth");
                    }
                }
            }
        }

        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .registerTypeAdapter(MethodSignature.class, new MethodSignatureTypeAdapter())
                .registerTypeAdapter(StmtPositionInfo.class, new StmtPositionInfoTypeAdapter())
                .registerTypeAdapter(Object.class, new InstanceSerializer())
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        String json = gson.toJson(resultJson);
        String insecureResultJsonString = gson.toJson(insecureResultJson);

        String directoryPath = "./" + "result_" + AnalysisEnv.getFileName();
        Path directory = Paths.get(directoryPath);

        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                System.out.println(directoryPath + " directory creation failed");
            }
        }
        try (FileWriter file = new FileWriter("./" + "result_" + AnalysisEnv.getFileName() + "/result.json")) {
            file.write(json);
            file.flush();
        } catch (IOException e) {
            System.out.println("No results to write. The apk may be obfuscated, packed or not contain crypto-related API");
        }
        try (FileWriter file = new FileWriter("./" + "result_" + AnalysisEnv.getFileName() + "/result_potentialVulnerability.json")) {
            file.write(insecureResultJsonString);
            file.flush();
        } catch (IOException e) {
            System.out.println("No insecure results to write. No insecure Results found");
        }
    }

    public static IntraResultNode buildIntraResultTree2(APIParamInfo apiParamInfo, CallSite callSite, List<JStaticFieldRef> trackingStaticFields, boolean trackBaseOfTheMethod) {
        MethodSignature callerMs = java.util.Objects.requireNonNull(callSite.getCaller(), "callerMethodSignature");
        JavaSootMethod callerSM = AnalysisEnv.view().getMethod(callerMs).get();
        // 方法内程序切片，切片结果主要记录在其中的intraResult属性中
        StmtGraph<?> baseGraph = callerSM.getBody().getStmtGraph();
        int ifCount = 0;
        for (var s : callerSM.getBody().getStmts()) {
            if (s instanceof sootup.core.jimple.common.stmt.JIfStmt) ifCount++;
        }
        int setSkipIfThreshold = Integer.parseInt(System.getProperty("paramscope.set.skipIfThreshold", "20"));

        boolean enableSetPrunedCfg = Boolean.parseBoolean(System.getProperty("paramscope.set.pruneCfg", "true"))
                && ifCount <= setSkipIfThreshold;
        StmtGraph<?> graphForSlicing = baseGraph;
        if (enableSetPrunedCfg) {
            Limits limits = Limits.defaults();
            SETBuilder builder = new SETBuilder(TraversalStrategy.BFS, limits);
            ExecutionGraph raw = builder.build(callerSM);
            var blocked = CfgBranchPruneReducer.computeBlockedEdges(callerSM, baseGraph, raw);
            graphForSlicing = new PrunedStmtGraph(baseGraph, blocked);
        }

        IntraSlicing intraSlicing = new IntraSlicing(callerSM, graphForSlicing, callSite, apiParamInfo, trackingStaticFields, trackBaseOfTheMethod);
        IntraResult intraResult = intraSlicing.getIntraResult2();

        IntraResultNode treeNode = new IntraResultNode(intraResult);
        // intraResult.needsTracking() 判断是否需要进行跨方法分析
        if (intraResult.needsTracking()) {
            MethodInfo callerMI = CallRelation.getApplicationMethodAndapiMethodMap().get(callSite.getCaller());
            // 如果该方法是main方法，则不存在调用者了
            if (!callerMI.getIsMain()) {
                // 对该方法的每个调用者都会进行跨方法分析
                for (CallSite callerOfCallerCallSite : callerMI.getCallSites()) {
                    // 关注的目标API为被调用者方法
                    APIParamInfo apiParamInfoCaller = new APIParamInfo(callerOfCallerCallSite.getCallee().getDeclClassType().getClassName(), callerOfCallerCallSite.getCallee().getSubSignature().getName(), intraResult.getTracingParamRefs().stream().map(value -> value.parameterRef().getIndex()).toList());
                    apiParamInfoCaller.setMethodSignature(callerOfCallerCallSite.getCallee());
                    // 递归，构成树状IR图
                    IntraResultNode intraResultNode = buildIntraResultTree2(apiParamInfoCaller, callerOfCallerCallSite, intraResult.getTracingStaticFieldRefs(), intraResult.getThisOfCallerNeedsTracking());
                    treeNode.getCallerResults().put(callerOfCallerCallSite, intraResultNode);
                }
            }
        }
        return treeNode;

    }

    /**
     * Path-sensitive slicing (方案A): extract pruned SET paths to the callsite, then slice once per path by constraining CFG.
     *
     * <p>This is intra-only (only the current caller method is path-constrained). Interprocedural expansion remains unchanged.</p>
     */
    public static List<IntraResultNode> buildIntraResultTreesPathSensitive(APIParamInfo apiParamInfo,
                                                                          CallSite callSite,
                                                                          List<JStaticFieldRef> trackingStaticFields,
                                                                          boolean trackBaseOfTheMethod) {
        MethodSignature callerMs = java.util.Objects.requireNonNull(callSite.getCaller(), "callerMethodSignature");
        JavaSootMethod callerSM = AnalysisEnv.view().getMethod(callerMs).get();
        StmtGraph<?> baseGraph = callerSM.getBody().getStmtGraph();

        int ifCount = 0;
        for (var s : callerSM.getBody().getStmts()) {
            if (s instanceof sootup.core.jimple.common.stmt.JIfStmt) ifCount++;
        }
        int setSkipIfThreshold = Integer.parseInt(System.getProperty("paramscope.set.skipIfThreshold", "20"));
        if (ifCount > setSkipIfThreshold) {
            // Hard gate: skip SET build/pruning and path-sensitive slicing for complex methods.
            return List.of(buildIntraResultTree2(apiParamInfo, callSite, trackingStaticFields, trackBaseOfTheMethod));
        }
        int ifThreshold = Integer.parseInt(System.getProperty("paramscope.slice.pathSensitive.ifThreshold", "10"));
        if (ifCount >= ifThreshold) {
            return List.of(buildIntraResultTree2(apiParamInfo, callSite, trackingStaticFields, trackBaseOfTheMethod));
        }

        Limits limits = Limits.defaults();
        SETBuilder builder = new SETBuilder(TraversalStrategy.BFS, limits);
        ExecutionGraph raw = builder.build(callerSM);
        ExecutionGraph pruned1 = BranchPruner.prune(raw, BranchPruner.Options.defaults());

        var blocked = CfgBranchPruneReducer.computeBlockedEdges(callerSM, baseGraph, raw);
        int pathThreshold = Integer.parseInt(System.getProperty("paramscope.slice.pathSensitive.pathThreshold", "128"));
        // Extract up to threshold+1 so we can decide whether to enable path-sensitive slicing.
        List<PathExtractor.PathKey> paths0 = PathExtractor.extractToCallsite(pruned1, callSite, pathThreshold + 1);
        if (paths0.size() > pathThreshold) {
            return List.of(buildIntraResultTree2(apiParamInfo, callSite, trackingStaticFields, trackBaseOfTheMethod));
        }

        int maxPaths = Integer.parseInt(System.getProperty("paramscope.slice.pathSensitive.maxPaths", "8"));
        if (maxPaths > pathThreshold) maxPaths = pathThreshold;
        List<PathExtractor.PathKey> paths = (paths0.size() <= maxPaths) ? paths0 : paths0.subList(0, maxPaths);
        if (paths.isEmpty()) {
            return List.of(buildIntraResultTree2(apiParamInfo, callSite, trackingStaticFields, trackBaseOfTheMethod));
        }

        List<IntraResultNode> out = new ArrayList<>();
        List<sootup.core.jimple.common.stmt.Stmt> stmtListForIds = callerSM.getBody().getStmts();
        for (PathExtractor.PathKey pk : paths) {
            StmtGraph<?> pathGraph = new PathConstrainedStmtGraph(baseGraph, pk, blocked, stmtListForIds);
            IntraSlicing intraSlicing = new IntraSlicing(callerSM, pathGraph, callSite, apiParamInfo, trackingStaticFields, trackBaseOfTheMethod);
            IntraResult intraResult = intraSlicing.getIntraResult2();
            out.add(new IntraResultNode(intraResult));
        }
        return out;
    }

    private static List<InstanceInfo> buildInstanceInfos(IntraResultTree intraResultTree) {
        ArrayList<InstanceInfo> infos = new ArrayList<>();
        for (int i = 0; i < intraResultTree.getResults().size(); i++) {
            var oneResult = intraResultTree.getResults().get(i);
            InstanceInfo info = new InstanceInfo(intraResultTree.getSolvedResults().get(oneResult));

            if (intraResultTree.getArrayInfo().containsKey(oneResult)) {
                info.setArrayInfo(intraResultTree.getArrayInfo().get(oneResult));
            }
            if (intraResultTree.getNullReason().containsKey(oneResult)) {
                info.setNullReason(intraResultTree.getNullReason().get(oneResult));
            }
            if (oneResult.getRunningExceptions() != null && !oneResult.getRunningExceptions().isEmpty()) {
                info.setRunningExceptions(oneResult.getRunningExceptions());
            }
            if (oneResult.getSecurityInfo() != null) {
                info.setSecurityInfo(oneResult.getSecurityInfo());
            }

            infos.add(info);
        }
        return infos;
    }

    private static boolean isPotentialVulnerabilitySecurityInfo(String securityInfo) {
        if (securityInfo == null) return false;
        // Conservative: any non-secure / whitelist miss / runtime crypto init failure should be treated as potential vulnerability.
        return securityInfo.contains("Not in whitelist");
    }

    public static MethodSignature getCurrentCaller() {
        return currentCaller;
    }

    public static HashMap<MethodSignature, String> getKeyPairGenerator_algo() {
        return keyPairGenerator_algo;
    }

    public static void setInSecurityFlagTrue() {
        inSecurityFlag = true;
    }

    public static void setInsecurityFlagFalse() {
        inSecurityFlag = false;
    }

    /**
     * Make a string safe to use as a single filesystem path segment across platforms (esp. Windows).
     */
    private static String safePathSegment(String s) {
        if (s == null || s.isBlank()) return "empty";
        // Replace invalid filename characters: < > : " / \ | ? * and control chars
        String cleaned = s.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_");
        // Avoid trailing dots/spaces on Windows
        cleaned = cleaned.replaceAll("[\\s.]+$", "");
        return cleaned.isBlank() ? "empty" : cleaned;
    }
}
