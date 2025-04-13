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
import org.paramscope.result.adapters.InstanceSerializer;
import org.paramscope.result.adapters.MethodSignatureTypeAdapter;
import org.paramscope.result.adapters.StmtPositionInfoTypeAdapter;
import org.paramscope.slice.IntraResult;
import org.paramscope.slice.IntraResultNode;
import org.paramscope.slice.IntraResultTree;
import org.paramscope.slice.IntraSlicing;
import sootup.core.jimple.basic.StmtPositionInfo;
import sootup.core.jimple.common.ref.JStaticFieldRef;
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

        for (APIParamInfo apiParamInfo : APIList.getAllApiParamInfoList()) {
            if (apiParamInfo.getMethodSignature() != null) {
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

                        IntraResultNode treeNode = buildIntraResultTree2(apiParamInfo, callSite, new ArrayList<>(), false);
                        IntraResultTree resultTree = new IntraResultTree(treeNode);
                        resultTree.resolveResults();

                        StringBuilder result_subDir = new StringBuilder();
                        result_subDir.append(apiParamInfo.getMethodSignature().toString()).append("_paramList_");
                        ListIterator<Integer> paramListIterator = apiParamInfo.getParamPosList().listIterator();
                        while (paramListIterator.hasNext()) {
                            result_subDir.append(paramListIterator.next());
                            if (paramListIterator.hasNext()) {
                                result_subDir.append("_");
                            }
                        }
                        String filePath = "./" + "result_" + AnalysisEnv.getFileName() + "/" + result_subDir + "/" + (callSite.hashCode() & 0x7FFFFFFF);
                        resultTree.setFilePath(filePath);
                        TreeToDot dot = new TreeToDot(resultTree);
                        dot.save(filePath);

                        ResultEntry resultEntry = new ResultEntry(apiParamInfo, callSite, resultTree, ID++);
                        resultJson.addResult(resultEntry);
                        if (inSecurityFlag) {
                            insecureResultJson.addResult(resultEntry);
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
        JavaSootMethod callerSM = AnalysisEnv.view().getMethod(callSite.getCaller()).get();
        IntraSlicing intraSlicing = new IntraSlicing(callerSM.getBody().getStmtGraph(), callSite, apiParamInfo, trackingStaticFields, trackBaseOfTheMethod);
        IntraResult intraResult = intraSlicing.getIntraResult2();

        IntraResultNode treeNode = new IntraResultNode(intraResult);
        if (intraResult.needsTracking()) {
            MethodInfo callerMI = CallRelation.getApplicationMethodAndapiMethodMap().get(callSite.getCaller());
            if (!callerMI.getIsMain()) {
                for (CallSite callerOfCallerCallSite : callerMI.getCallSites()) {
                    APIParamInfo apiParamInfoCaller = new APIParamInfo(callerOfCallerCallSite.getCallee().getDeclClassType().getClassName(), callerOfCallerCallSite.getCallee().getSubSignature().getName(), intraResult.getTracingParamRefs().stream().map(value -> value.parameterRef().getIndex()).toList());
                    apiParamInfoCaller.setMethodSignature(callerOfCallerCallSite.getCallee());
                    IntraResultNode intraResultNode = buildIntraResultTree2(apiParamInfoCaller, callerOfCallerCallSite, intraResult.getTracingStaticFieldRefs(), intraResult.getThisOfCallerNeedsTracking());
                    treeNode.getCallerResults().put(callerOfCallerCallSite, intraResultNode);
                }
            }
        }
        return treeNode;

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
}
