package org.paramscope.rule;

public class RSAKeyGenParameterSpecInit_keySize_int_Rule {
    public static boolean check(Object obj) {
        assert obj instanceof Integer || obj instanceof Byte;
        if (obj instanceof Integer) {
            return checkSecirity((Integer) obj);
        } else {
            return checkSecirity(((Byte) obj).intValue());
        }
    }

    public static boolean checkSecirity(int keySize) {
        return keySize == 2048 || keySize == 3072 || keySize == 4096;
    }
}
