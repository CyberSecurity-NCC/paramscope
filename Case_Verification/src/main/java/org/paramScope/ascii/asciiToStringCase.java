package main.java.org.paramScope.ascii;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;

public class asciiToStringCase {
    // case pattern from tencent turingSDK
    // This is a Positive case of "MessageDigest" algorithm name
    // Cryptoguard: FALSE positive
    // CrySL:       FALSE positive

    public static void main(String[] args) {

        String cryptoAlgo = asciiToString("524334");   // MD5
        System.out.println("[asciiToStringCase] cryptoAlgo1: " + cryptoAlgo);

        try {
            Cipher cipher = Cipher.getInstance(cryptoAlgo);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }

    }


    public static String asciiToString(String hexString){
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hexString.length(); i += 2) {
            String str = hexString.substring(i, i + 2);
            char ch = (char) Integer.parseInt(str, 16);
            output.append(ch);
        }
        return output.toString();
    }
}
