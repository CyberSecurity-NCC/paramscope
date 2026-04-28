package org.paramscope.valueflow;

import org.paramscope.slice.OneResult;

import java.util.List;

public record ValueFlowResolvedPath(
        OneResult path,
        Object[] concreteValues,
        List<String> diagnostics,
        String securityInfo
) {
}

