package org.paramscope.slice;

import org.paramscope.call.CallSite;

import java.util.HashMap;

public class IntraResultNode {
    IntraResult intraResult;
    HashMap<CallSite, IntraResultNode> callerResults;

    public IntraResultNode(IntraResult intraResult) {
        this.intraResult = intraResult;
        this.callerResults = new HashMap<>();
    }

    public IntraResult getIntraResult() {
        return intraResult;
    }

    public HashMap<CallSite, IntraResultNode> getCallerResults() {
        return callerResults;
    }
}
