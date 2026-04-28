package org.paramscope.valueflow.target;

import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import sootup.core.signatures.MethodSignature;

import java.util.List;
import java.util.Optional;

public interface ValueTarget {

    ValueTargetKind kind();

    CallSite callSite();

    MethodSignature calleeSignature();

    /**
     * For CALLSITE_ARGS, this is the argument indices to reconstruct, in the desired output order.
     * For other kinds, this is an empty list.
     */
    List<Integer> argIndices();

    /**
     * Whether the invoke base/receiver should be tracked.
     */
    boolean trackBase();

    /**
     * Optional legacy API metadata, used by compatibility checkers (e.g. crypto whitelist rules).
     */
    Optional<APIParamInfo> legacyApiParamInfo();
}

