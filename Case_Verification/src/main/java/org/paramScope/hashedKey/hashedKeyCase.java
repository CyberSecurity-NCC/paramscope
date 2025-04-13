package main.java.org.paramScope.hashedKey;

import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class hashedKeyCase {
    // case pattern from com.shopping.limerode.apk
    // This is a Positive case of "SecretKeySpec" key bytes
    // Cryptoguard: FALSE Negative
    // CrySL:       Positive

    public static void main(String[] args) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = messageDigest.digest();
            a(new byte[10], keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static void a(byte[] somethingElse, byte[] keyBytes) {
        System.out.println("[hashedKeyCase] keyBytes: " + Arrays.toString(keyBytes));
        // empty value leads to constant hashed keyBytes.
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
    }
}
