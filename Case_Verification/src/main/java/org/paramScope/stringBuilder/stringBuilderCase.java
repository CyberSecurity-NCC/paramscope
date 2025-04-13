package main.java.org.paramScope.stringBuilder;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;

public class stringBuilderCase {

    // case pattern from cn.shiguangji.time.apk
    // This is a Positive case of "MessageDigest" algorithm name.
    //   Cryptoguard: False Negative.
    //   CrySL: False Negative.
    public static void main(String[] args) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("DE");
        stringBuilder.append("S/CB");
        stringBuilder.append("C/NoP");
        stringBuilder.append("adding");

        try {
            System.out.println("[stringBuilderCase] cipherAlgo: " + stringBuilder.toString());
            Cipher cipher = Cipher.getInstance(stringBuilder.toString());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }
}
