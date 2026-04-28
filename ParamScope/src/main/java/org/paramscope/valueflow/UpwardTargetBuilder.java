package org.paramscope.valueflow;

import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.slice.IntraResult;
import org.paramscope.valueflow.target.CallsiteArgTarget;
import org.paramscope.valueflow.target.CallsiteBaseTarget;
import org.paramscope.valueflow.target.ValueTarget;

import java.util.List;
import java.util.Optional;

final class UpwardTargetBuilder {

    private UpwardTargetBuilder() {
    }

    static ValueTarget from(IntraResult intraResult, CallSite callerOfCallerCallSite) {
        List<Integer> paramIndices = intraResult.getTracingParamRefs()
                .stream()
                .map(ref -> ref.parameterRef().getIndex())
                .distinct()
                .toList();

        boolean needTrackBase = intraResult.getThisOfCallerNeedsTracking();

        Optional<APIParamInfo> legacy = Optional.empty();
        if (intraResult.getApiParamInfo() != null) {
            legacy = Optional.of(intraResult.getApiParamInfo());
        }

        if (!paramIndices.isEmpty()) {
            return new CallsiteArgTarget(
                    callerOfCallerCallSite,
                    callerOfCallerCallSite.getCallee(),
                    paramIndices,
                    needTrackBase,
                    legacy
            );
        }

        if (needTrackBase) {
            return new CallsiteBaseTarget(
                    callerOfCallerCallSite,
                    callerOfCallerCallSite.getCallee(),
                    legacy
            );
        }

        // Fallback: track nothing but keep the callsite; this will usually terminate tracking.
        return new CallsiteArgTarget(
                callerOfCallerCallSite,
                callerOfCallerCallSite.getCallee(),
                List.of(),
                false,
                legacy
        );
    }
}

