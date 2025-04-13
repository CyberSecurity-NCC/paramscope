package org.paramscope.result;

import com.google.gson.annotations.Expose;
import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.reflection.GetClassFromType2;
import org.paramscope.slice.IntraResultTree;

import java.util.Arrays;

public class ResultEntry {
    @Expose
    int entryID;
    @Expose
    APIParamInfo apiInfo;
    @Expose
    CallSite callsite;
    @Expose
    String filePath;

    @Expose
    int paramNums;
    @Expose
    int instanceNums;
    @Expose(serialize = false)
    Class<?>[] paramDataTypes;
    @Expose
    String[] paramDataTypesStr;
    @Expose
    InstanceInfo[] paramInstances;

    public ResultEntry(APIParamInfo apiInfo, CallSite callSite, IntraResultTree intraResultTree, int entryID) {
        this.entryID = entryID;
        this.apiInfo = apiInfo;
        this.callsite = callSite;
        this.paramNums = apiInfo.getParamPosList().size();
        this.instanceNums = intraResultTree.getResults().size();
        this.filePath = intraResultTree.getFilePath();

        this.paramDataTypes = new Class[this.paramNums];
        this.paramDataTypesStr = new String[this.paramNums];
        for (int i = 0; i < paramNums; i++) {
            this.paramDataTypes[i] = GetClassFromType2.get(apiInfo.getMethodSignature().getParameterTypes().get(apiInfo.getParamPosList().get(i)));
            this.paramDataTypesStr[i] = this.paramDataTypes[i].getName();
        }

        this.paramInstances = new InstanceInfo[instanceNums];
        for (int i = 0; i < instanceNums; i++) {
            this.paramInstances[i] = new InstanceInfo(intraResultTree.getSolvedResults().get(intraResultTree.getResults().get(i)));
            if (this.containsArray() && intraResultTree.getArrayInfo().containsKey(intraResultTree.getResults().get(i))) {
                this.paramInstances[i].setArrayInfo(intraResultTree.getArrayInfo().get(intraResultTree.getResults().get(i)));
            }
            if (intraResultTree.getNullReason().containsKey(intraResultTree.getResults().get(i))) {
                this.paramInstances[i].setNullReason(intraResultTree.getNullReason().get(intraResultTree.getResults().get(i)));
            }
            if (!intraResultTree.getResults().get(i).getRunningExceptions().isEmpty()) {
                this.paramInstances[i].setRunningExceptions(intraResultTree.getResults().get(i).getRunningExceptions());
            }
            if (intraResultTree.getResults().get(i).getSecurityInfo() != null) {
                this.paramInstances[i].setSecurityInfo(intraResultTree.getResults().get(i).getSecurityInfo());
            }
        }
    }

    private boolean containsArray() {
        for (Class<?> c : paramDataTypes) {
            if (c.isArray()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ResultEntry{" +
                "entryID=" + entryID +
                ", apiInfo=" + apiInfo +
                ", callsite=" + callsite +
                ", paramNums=" + paramNums +
                ", instanceNums=" + instanceNums +
                ", paramDataTypes=" + Arrays.toString(paramDataTypes) +
                ", paramInstances=" + Arrays.toString(paramInstances) +
                '}';
    }
}
