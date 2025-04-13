package org.paramscope.slice;

import org.paramscope.call.CallSite;
import org.paramscope.reflection.ReflectionObject2;

import java.util.ArrayList;
import java.util.List;

public class OneResult {
    List<IntraResult> intraResults;
    List<CallSite> callRelations;
    ReflectionObject2[] reflectionObject;
    String nullReason;
    String securityInfo;
    ArrayList<String> runningExceptions;

    public OneResult() {
        this.intraResults = new ArrayList<>();
        this.callRelations = new ArrayList<>();
        this.runningExceptions = new ArrayList<>();
    }

    public OneResult(OneResult oneResult) {
        this.intraResults = new ArrayList<>(oneResult.getIntraResults());
        this.callRelations = new ArrayList<>(oneResult.getCallRelations());
        this.runningExceptions = new ArrayList<>();
    }

    public List<IntraResult> getIntraResults() {
        return intraResults;
    }

    public List<CallSite> getCallRelations() {
        return callRelations;
    }

    public String getNullReason() {
        return nullReason;
    }

    public void setNullReason(String nullReason) {
        this.nullReason = nullReason;
    }

    public ArrayList<String> getRunningExceptions() {
        return runningExceptions;
    }

    public String getSecurityInfo() {
        return securityInfo;
    }

    public void setSecurityInfo(String securityInfo) {
        this.securityInfo = securityInfo;
    }

    public void setReflectionObject(ReflectionObject2[] reflectionObject) {
        this.reflectionObject = reflectionObject;
    }
}
