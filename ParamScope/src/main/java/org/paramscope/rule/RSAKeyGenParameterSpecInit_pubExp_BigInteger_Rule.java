package org.paramscope.rule;

import java.math.BigInteger;
import java.security.spec.RSAKeyGenParameterSpec;

public class RSAKeyGenParameterSpecInit_pubExp_BigInteger_Rule {
    public static boolean check(BigInteger pubExp) {
        return pubExp.equals(RSAKeyGenParameterSpec.F4);
    }
}
