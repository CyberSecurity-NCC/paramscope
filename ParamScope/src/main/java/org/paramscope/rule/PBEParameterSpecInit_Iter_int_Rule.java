package org.paramscope.rule;

public class PBEParameterSpecInit_Iter_int_Rule {
    public static boolean check(Object obj) {
        assert obj instanceof Integer || obj instanceof Byte;
        if (obj instanceof Integer) {
            return checkSecirity((Integer) obj);
        } else {
            return checkSecirity(((Byte) obj).intValue());
        }
    }

    public static boolean checkSecirity(int iter) {
        return iter >= 1000;
    }
}
