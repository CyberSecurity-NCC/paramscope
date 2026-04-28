package org.paramscope.valueflow.compat;

import org.paramscope.api.APIParamInfo;
import org.paramscope.call.CallSite;
import org.paramscope.valueflow.target.CallsiteArgTarget;
import org.paramscope.valueflow.target.ValueTarget;

import java.util.Optional;

public final class ApiParamInfoTargetAdapter {

    private ApiParamInfoTargetAdapter() {
    }

    public static ValueTarget forCallsite(APIParamInfo apiParamInfo, CallSite callSite) {
        return new CallsiteArgTarget(
                callSite,
                apiParamInfo.getMethodSignature(),
                apiParamInfo.getParamPosList(),
                false,
                Optional.of(apiParamInfo)
        );
    }
}

