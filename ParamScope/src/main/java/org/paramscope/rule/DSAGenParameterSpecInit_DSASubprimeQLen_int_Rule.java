package org.paramscope.rule;

public class DSAGenParameterSpecInit_DSASubprimeQLen_int_Rule {
    public static boolean check(Object obj) {
        assert obj instanceof Integer || obj instanceof Byte;
        if (obj instanceof Integer) {
            return checkSecirity((Integer) obj);
        } else {
            return checkSecirity(((Byte) obj).intValue());
        }
    }

    public static boolean checkSecirity(int subPrimeQLen) {
        return subPrimeQLen == 256;            // from DSAGenParameterSpec.crysl
    }
}
