package org.paramscope.rule;

public class DSAGenParameterSpecInit_DSAprimePLen_int_Rule {
    public static boolean check(Object obj) {
        assert obj instanceof Integer || obj instanceof Byte;
        if (obj instanceof Integer) {
            return checkSecirity((Integer) obj);
        } else {
            return checkSecirity(((Byte) obj).intValue());
        }
    }

    public static boolean checkSecirity(int primeLen) {
        return primeLen == 3072;            // from DSAGenParameterSpec.crysl
    }
}
