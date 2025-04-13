package main.java.org.paramScope.algoFromList;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class algoFromListCase {
    // case patten from instagram.photo.video.downloader.repost.insta.apk
    // This is a Positive case of "SecretKeySpec" algorithm
    // Cryptoguard: False Negative
    // CrySL:       Positive

    private static final List<SignatureAlgorithm> PREFERRED_ALGS = new ArrayList<>();
    static {
        SignatureAlgorithm algo = new SignatureAlgorithm("DES");
        SignatureAlgorithm algo2 = new SignatureAlgorithm("AES");
        PREFERRED_ALGS.add(algo);
        PREFERRED_ALGS.add(algo2);
    }

    public static void main(String[] args) {
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[16];
        random.nextBytes(keyBytes);

        Iterator<SignatureAlgorithm> iterator = PREFERRED_ALGS.iterator();
        SignatureAlgorithm hmac_algo = iterator.next();
        System.out.println("[algoFromListCase] algo: " + hmac_algo.getJcaName());
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, hmac_algo.getJcaName());
    }
}

class SignatureAlgorithm {
    String jcaName;

    public SignatureAlgorithm(String jcaName) {
        this.jcaName = jcaName;
    }

    public String getJcaName(){
        return jcaName;
    }
}
