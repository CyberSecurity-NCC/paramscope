package org.paramscope.rule;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CipherGetInstance_Algo_String_Rule {

    public static boolean check(String transformation) throws NoSuchPaddingException, NoSuchAlgorithmException {

        Cipher cipher = Cipher.getInstance(transformation);

        if (transformation == null || transformation.isEmpty()) {
            return false;
        }

        String normalizedTransformation = transformation.toUpperCase();
        String[] parts = normalizedTransformation.split("/");
        if (parts.length > 3) return false;

        String algorithm = parts[0];
        String mode = (parts.length >= 2) ? parts[1] : "";
        String padding = (parts.length >= 3) ? parts[2] : "";

        Set<String> allowedAlgorithms = new HashSet<>(Arrays.asList(
                "RSA", "AES",
                "PBEWITHHMACSHA224ANDAES_128", "PBEWITHHMACSHA256ANDAES_128",
                "PBEWITHHMACSHA384ANDAES_128", "PBEWITHHMACSHA512ANDAES_128",
                "PBEWITHHMACSHA224ANDAES_256", "PBEWITHHMACSHA256ANDAES_256",
                "PBEWITHHMACSHA384ANDAES_256", "PBEWITHHMACSHA512ANDAES_256"
        ));

        if (!allowedAlgorithms.contains(algorithm)) return false;

        if ("RSA".equals(algorithm)) {
            return validateRSA(mode, padding);
        } else if ("AES".equals(algorithm)) {
            return validateAES(mode, padding);
        } else if (algorithm.startsWith("PBEWITH")) {
            return validatePBE(mode, padding);
        }
        return false;
    }

    private static boolean validateRSA(String mode, String padding) {
        if (!mode.isEmpty() && !"ECB".equals(mode)) return false;

        Set<String> ALLOWED_PADDINGS = new HashSet<>(Arrays.asList(
                "OAEPWITHMD5ANDMGF1PADDING", "OAEPWITHSHA-1ANDMGF1PADDING",
                "OAEPWITHSHA-224ANDMGF1PADDING", "OAEPWITHSHA-256ANDMGF1PADDING",
                "OAEPWITHSHA-384ANDMGF1PADDING", "OAEPWITHSHA-512ANDMGF1PADDING"));

        if (mode.isEmpty()) {
            return padding.isEmpty();
        } else {
            return ALLOWED_PADDINGS.contains(padding);
        }
    }

    private static boolean validateAES(String mode, String padding) {
        Set<String> allowedModes = new HashSet<>(Arrays.asList(
                "GCM", "CTR", "CTS", "CFB", "OFB", "CBC"
        ));

        if ("CBC".equals(mode)) {
            return "PKCS5PADDING".equals(padding);
        } else {
            return allowedModes.contains(mode) && "NOPADDING".equals(padding);
        }
    }

    private static boolean validatePBE(String mode, String padding) {
        return "PKCS5PADDING".equals(padding);
    }

}
