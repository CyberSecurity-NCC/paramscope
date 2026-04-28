package org.paramscope.valueflow.target;

import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import sootup.core.signatures.MethodSignature;

import java.util.List;
import java.util.Optional;

public record CallsiteArgTarget(
        CallSite callSite,
        MethodSignature calleeSignature,
        List<Integer> argIndices,
        boolean trackBase,
        Optional<APIParamInfo> legacyApiParamInfo
) implements ValueTarget {

    public CallsiteArgTarget {
        if (argIndices == null) {
            throw new IllegalArgumentException("argIndices is null");
        }
        if (legacyApiParamInfo == null) {
            legacyApiParamInfo = Optional.empty();
        }
    }

    @Override
    public ValueTargetKind kind() {
        return ValueTargetKind.CALLSITE_ARGS;
    }
}

