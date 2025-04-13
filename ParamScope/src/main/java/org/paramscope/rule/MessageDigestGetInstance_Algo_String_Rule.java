package org.paramscope.rule;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MessageDigestGetInstance_Algo_String_Rule {
    private static final Set<String> SECURE_ALGORITHMS = new HashSet<>(Arrays.asList(
            "SHA256", "SHA384", "SHA512",
            "SHA3256", "SHA3384", "SHA3512"
    ));

    public static boolean check(String algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        if (algorithm == null || algorithm.isEmpty()) {
            return false;
        }

        String normalized = algorithm.toUpperCase().replace("-", "");

        return SECURE_ALGORITHMS.contains(normalized);
    }
}
