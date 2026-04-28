package org.paramscope.valueflow.target;

import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import sootup.core.signatures.MethodSignature;

import java.util.List;
import java.util.Optional;

public record CallsiteBaseTarget(
        CallSite callSite,
        MethodSignature calleeSignature,
        Optional<APIParamInfo> legacyApiParamInfo
) implements ValueTarget {

    public CallsiteBaseTarget {
        if (legacyApiParamInfo == null) {
            legacyApiParamInfo = Optional.empty();
        }
    }

    @Override
    public ValueTargetKind kind() {
        return ValueTargetKind.CALLSITE_BASE;
    }

    @Override
    public List<Integer> argIndices() {
        return List.of();
    }

    @Override
    public boolean trackBase() {
        return true;
    }
}

