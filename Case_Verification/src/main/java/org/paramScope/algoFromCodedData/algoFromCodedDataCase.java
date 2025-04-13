package main.java.org.paramScope.algoFromCodedData;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;

public class algoFromCodedDataCase {
    // case pattern from com.ebay.mobile.apk
    // This is a Positive case of "Cipher" algorithm name
    // Cryptoguard: FALSE negative
    // CrySL:       FALSE negetive
    public static void main(String[] args) {
        String cipherAlgo = Data.a(668);
        System.out.println("[algoFromCodedDataCase] constant cipherAlgo: " + cipherAlgo);
        try {
            Cipher.getInstance(cipherAlgo);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }
}

class Data {

    // We did not further analyze the data compression and data extraction methods used by the app, only implemented a simplified class with similar methods here.
    private static final String compressedData = new String("............................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................L020:UVD/HFE/SNFV1Sdgglqj...................................................................................................................................................................................................................................................................................................................");
    public static String a(int offset) {
        int HEADER_LENGTH = 5;
        String header = compressedData.substring(offset, offset + HEADER_LENGTH);
        int targetLength;
        targetLength = Integer.parseInt(header.substring(1, 4));

        return decode(compressedData.substring(offset + HEADER_LENGTH, offset + HEADER_LENGTH + targetLength));
    }

    public static String decode(String data) {
        int shift = -3;
        StringBuilder result = new StringBuilder();
        for (char c : data.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                char ch = (char) (((c - 'A' + shift) % 26) + 'A');
                result.append(ch);
            } else if (c >= 'a' && c <= 'z') {
                char ch = (char) (((c - 'a' + shift) % 26) + 'a');
                result.append(ch);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}