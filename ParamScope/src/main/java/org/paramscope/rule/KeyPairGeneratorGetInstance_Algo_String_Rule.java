package org.paramscope.rule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class KeyPairGeneratorGetInstance_Algo_String_Rule {
    private static final Set<String> SECURE_ALGORITHMS = new HashSet<>(Arrays.asList(
            "RSA", "EC", "DSA", "DIFFIEHELLMAN", "DH"
    ));

    public static boolean check(String algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return false;
        }

        String normalized = algorithm.toUpperCase();

        return SECURE_ALGORITHMS.contains(normalized);
    }
}
