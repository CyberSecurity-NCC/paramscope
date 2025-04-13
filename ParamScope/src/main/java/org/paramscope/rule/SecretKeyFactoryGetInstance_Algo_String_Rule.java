package org.paramscope.rule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SecretKeyFactoryGetInstance_Algo_String_Rule {
    //    private static final Set<String> SECURE_ALGORITHMS = new HashSet<>(Arrays.asList(
//            "PBKDF2WITHHMACSHA512", "PBKDF2WITHHMACSHA384", "PBKDF2WITHHMACSHA256", "PBKDF2WITHHMACSHA224",
//            "PBEWITHHMACSHA512ANDAES_128","PBEWITHHMACSHA384ANDAES_128", "PBEWITHHMACSHA224ANDAES_128",
//            "PBEWITHHMACSHA256ANDAES_128","PBEWITHHMACSHA224ANDAES_256","PBEWITHHMACSHA256ANDAES_256",
//            "PBEWITHHMACSHA384ANDAES_256", "PBEWITHHMACSHA512ANDAES_256"     // from SecretKeyFactory.crysl
//    ));
    private static final Set<String> SECURE_ALGORITHMS = new HashSet<>(Arrays.asList(
            "PBKDF2WITHHMACSHA512", "PBKDF2WITHHMACSHA384", "PBKDF2WITHHMACSHA256", "PBKDF2WITHHMACSHA224",
            "PBEWITHHMACSHA512ANDAES128", "PBEWITHHMACSHA384ANDAES128", "PBEWITHHMACSHA224ANDAES128",
            "PBEWITHHMACSHA256ANDAES128", "PBEWITHHMACSHA224ANDAES256", "PBEWITHHMACSHA256ANDAES256",
            "PBEWITHHMACSHA384ANDAES256", "PBEWITHHMACSHA512ANDAES256"          // from SecretKeyFactory.crysl
    ));

    public static boolean check(String algorithm) {
//        if (algorithm == null || algorithm.isEmpty()) {
//            return false;
//        }
//
//        String normalized = algorithm.toUpperCase();
//        return SECURE_ALGORITHMS.contains(normalized);
        if (algorithm == null || algorithm.isEmpty()) {
            return false;
        }

        String normalized = algorithm.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        return SECURE_ALGORITHMS.contains(normalized);
    }
}
