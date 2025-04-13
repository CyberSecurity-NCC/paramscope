package org.paramscope.rule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MacGetInstance_Algo_String_Rule {
    private static final Set<String> SECURE_ALGORITHMS = new HashSet<>(Arrays.asList(
            "HMACSHA256", "HMACSHA384", "HMACSHA512",
            "PBEWITHHMACSHA224", "PBEWITHHMACSHA256", "PBEWITHHMACSHA384", "PBEWITHHMACSHA512",
            "HMACPBESHA1", "PBEWITHHMACSHA1"            // The two from Mac.crysl
    ));

    public static boolean check(String algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return false;
        }

        String normalized = algorithm.toUpperCase().replaceAll("[-_ ]", "");

        return SECURE_ALGORITHMS.contains(normalized);
    }
}
