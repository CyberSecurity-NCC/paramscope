package org.paramscope.rule;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SignatureGetInstance_Algo_String_Rule {
    private static final Set<String> SECURE_ALGORITHMS = new HashSet<>(Arrays.asList(
            "SHA256WITHRSA", "SHA384WITHRSA", "SHA512WITHRSA",
            "SHA3256WITHRSA", "SHA3384WITHRSA", "SHA3-512WITHRSA",
            "RSASSAPSS",
            "SHA256WITHECDSA", "SHA384WITHECDSA", "SHA512WITHECDSA",
            "SHA3256WITHECDSA", "SHA3384WITHECDSA", "SHA3-512WITHECDSA",
            "ED25519", "ED448",
            "SHA256WITHDSA", "SHA384WITHDSA", "SHA512WITHDSA",
            "SHA3256WITHDSA", "SHA3384WITHDSA", "SHA3-512WITHDSA"
    ));

    public static boolean check(String algorithm) {
        if (algorithm == null) return false;
        String normalized = algorithm.toUpperCase().replace("-", "");
        return SECURE_ALGORITHMS.contains(normalized);
    }
}
