package org.paramscope.rule;

public class DHGenParameterSpecInit_DHExpSize_int_Rule {
    public static boolean check(Object obj) {
        assert obj instanceof Integer || obj instanceof Byte;
        if (obj instanceof Integer) {
            return checkSecirity((Integer) obj);
        } else {
            return checkSecirity(((Byte) obj).intValue());
        }
    }

    public static boolean checkSecirity(int DHExpSize) {
        return DHExpSize >= 224;
    }
}
