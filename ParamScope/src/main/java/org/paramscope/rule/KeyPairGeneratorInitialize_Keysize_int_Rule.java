package org.paramscope.rule;

public class KeyPairGeneratorInitialize_Keysize_int_Rule {
    public static boolean check(Object obj, String algo) {
        assert obj instanceof Integer || obj instanceof Byte;
        if (obj instanceof Integer) {
            return checkSecirity((Integer) obj, algo);
        } else {
            return checkSecirity(((Byte) obj).intValue(), algo);
        }
    }

    public static boolean checkSecirity(int keysize, String algo) {
        return switch (algo.toUpperCase()) {
            case "RSA", "DSA", "DIFFIEHELLMAN", "DH" -> keysize >= 2048;
            case "EC" -> keysize >= 256;
            default -> false;
        };
    }
}
