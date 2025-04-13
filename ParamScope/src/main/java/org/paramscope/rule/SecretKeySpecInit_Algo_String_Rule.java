package org.paramscope.rule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SecretKeySpecInit_Algo_String_Rule {
    private static final Set<String> SECURE_ALGORITHMS = new HashSet<>(Arrays.asList(
            "AES", "CHAHA20", "HMACSHA256", "HMACSHA384", "HMACSHA512", "HMACSHA3-256", "HMACSHA3-512"
    ));

    public static boolean check(String algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return false;
        }

        String normalized = algorithm.toUpperCase();
        return SECURE_ALGORITHMS.contains(normalized);
    }

}
