package main.java.org.paramScope.arraysFill;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

public class arraysFillCase {

    // case pattern from com.myntra.android.apk
    // This is a Positive case of "SecretKeySpec" key bytes
    // Cryptoguard: FALSE negative
    // CrySL:       positive

    public static void main(String[] args) {
        byte[] keyBytes = new byte[16];
        Arrays.fill(keyBytes, (byte) 0);

        System.out.println("[arraysFillCase] constant keyBytes: " + Arrays.toString(keyBytes));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        System.out.println(keySpec.toString());
    }

}
