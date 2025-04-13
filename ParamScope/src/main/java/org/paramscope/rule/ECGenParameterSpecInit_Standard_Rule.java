package org.paramscope.rule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ECGenParameterSpecInit_Standard_Rule {
    private static final Set<String> SECURE_STANDARDS = new HashSet<>(Arrays.asList(
            "BRAINPOOLP224R1", "1.3.36.3.3.2.8.1.1.5",
            "BRAINPOOLP256R1", "1.3.36.3.3.2.8.1.1.7",
            "BRAINPOOLP320R1", "1.3.36.3.3.2.8.1.1.9",
            "BRAINPOOLP384R1", "1.3.36.3.3.2.8.1.1.11",
            "BRAINPOOLP512R1", "1.3.36.3.3.2.8.1.1.13",
            "SECP224R1", "NISTP224", "1.3.132.0.33",
            "SECP256R1", "NISTP256", "X9.62PRIME256V1", "PRIME256V1", "1.2.840.10045.3.1.7",
            "SECP384R1", "NISTP384", "X9.62PRIME384V1", "PRIME384V1", "1.3.132.0.34",
            "SECP521R1", "NISTP521", "X9.62PRIME521V1", "PRIME521V1", "1.3.132.0.35",          // from ECGenParameterSpec.crysl
            "SECP256K1", "1.3.132.0.10",
            "SECT233K1", "SECT233R1", "NISTK233", "NISTB233", "1.3.132.0.26", "1.3.132.0.27",
            "SECT283K1", "SECT283R1", "NISTK283", "NISTB283", "1.3.132.0.16", "1.3.132.0.17",
            "SECT409K1", "SECT409R1", "NISTK409", "NISTB409", "1.3.132.0.36", "1.3.132.0.37",
            "SECT571K1", "SECT571R1", "NISTK571", "NISTB571", "1.3.132.0.38", "1.3.132.0.39",
            "X25519", "1.3.101.110",
            "X448", "1.3.101.111",
            "ED25519", "1.3.101.112",
            "ED448", "1.3.101.113"
    ));

    public static boolean check(String algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return false;
        }

        String normalized = algorithm.toUpperCase().replace("_", "");
        normalized = normalized.replace("-", "");
        normalized = normalized.replace(" ", "");

        return SECURE_STANDARDS.contains(normalized);
    }
}
