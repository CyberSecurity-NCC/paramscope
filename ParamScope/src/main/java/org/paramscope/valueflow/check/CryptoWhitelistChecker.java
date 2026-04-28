package org.paramscope.valueflow.check;

import org.paramscope.api.APIParamInfo;
import org.paramscope.data.APIList;
import org.paramscope.rule.*;
import org.paramscope.slice.OneResult;
import org.paramscope.valueflow.target.ValueTarget;
import org.paramscope.reflection.ReflectionObject2;

import javax.crypto.NoSuchPaddingException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

public final class CryptoWhitelistChecker implements ResultChecker {

    @Override
    public CheckResult check(ValueTarget target, Object[] concreteValues, ReflectionObject2[] reflectionObjects, OneResult pathMeta) {
        Optional<APIParamInfo> legacyOpt = target.legacyApiParamInfo();
        if (legacyOpt.isEmpty()) {
            return CheckResult.info("no legacy api info; whitelist check skipped");
        }
        if (concreteValues == null || concreteValues.length == 0) {
            return CheckResult.info("no concrete value");
        }

        APIParamInfo apiParamInfo = legacyOpt.get();
        Object res = concreteValues[0];
        if (res == null) {
            return CheckResult.info("null value");
        }

        try {
            if (APIList.getMessageDigestGetInstance_Algo_String().contains(apiParamInfo)) {
                if (MessageDigestGetInstance_Algo_String_Rule.check((String) res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getCipherGetInstance_Algo_String().contains(apiParamInfo)) {
                if ("AES".equals(res)) {
                    return CheckResult.insecure("Not in whitelist value: AES (uses ECB by default)");
                }
                if (CipherGetInstance_Algo_String_Rule.check((String) res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getSecretKeySpecInit_Algo_String().contains(apiParamInfo)) {
                if (SecretKeySpecInit_Algo_String_Rule.check((String) res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getMacGetInstance_Algo_String().contains(apiParamInfo)) {
                if (MacGetInstance_Algo_String_Rule.check((String) res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getKeyPairGeneratorGetInstance_Algo_String().contains(apiParamInfo)) {
                if (KeyPairGeneratorGetInstance_Algo_String_Rule.check((String) res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getSecretKeyFactoryGetInstance_Algo_String().contains(apiParamInfo)) {
                if (SecretKeyFactoryGetInstance_Algo_String_Rule.check((String) res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getECGenParameterSpecInit_ECStandard_String().contains(apiParamInfo)) {
                if (ECGenParameterSpecInit_Standard_Rule.check((String) res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getRSAKeyGenParameterSpecInit_RSAkeySize_int().contains(apiParamInfo)) {
                if (RSAKeyGenParameterSpecInit_keySize_int_Rule.check(res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getRSAKeyGenParameterSpecInit_RSAPubExp_BigInteger().contains(apiParamInfo)) {
                if (res instanceof BigInteger bigInteger && RSAKeyGenParameterSpecInit_pubExp_BigInteger_Rule.check(bigInteger)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getDSAGenParameterSpecInit_DSAprimePLen_int().contains(apiParamInfo)) {
                if (DSAGenParameterSpecInit_DSAprimePLen_int_Rule.check(res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getDSAGenParameterSpecInit_DSASubprimeQLen_int().contains(apiParamInfo)) {
                if (DSAGenParameterSpecInit_DSASubprimeQLen_int_Rule.check(res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getDHGenParameterSpecInit_DHPrimeSize_int().contains(apiParamInfo)) {
                if (DHGenParameterSpecInit_DHPrimeSize_int_Rule.check(res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getDHGenParameterSpecInit_DHExpSize_int().contains(apiParamInfo)) {
                if (DHGenParameterSpecInit_DHExpSize_int_Rule.check(res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getPBEParameterSpecInit_Iter_int().contains(apiParamInfo)) {
                if (PBEParameterSpecInit_Iter_int_Rule.check(res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
            if (APIList.getSignatureGetInstance_Algo_String().contains(apiParamInfo)) {
                if (SignatureGetInstance_Algo_String_Rule.check((String) res)) {
                    return CheckResult.info("Secure value: " + res);
                }
                return CheckResult.insecure("Not in whitelist value: " + res);
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            return CheckResult.warning("No such algorithm/padding: " + res);
        } catch (ClassCastException e) {
            return CheckResult.warning("Unexpected value type: " + res.getClass());
        }

        return CheckResult.info("no rule matched; check skipped");
    }
}

