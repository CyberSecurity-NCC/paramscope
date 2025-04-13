package org.paramscope.data;

import org.paramscope.api.APIParamInfo;

import java.util.ArrayList;
import java.util.List;

public class APIList {
    private static final List<APIParamInfo> API_PARAM_INFO_LIST = new ArrayList<>();
    private static final List<APIParamInfo> TRACK_BASE_API_PARAM_INFO_LIST = new ArrayList<>();

    private static final List<APIParamInfo> TRACK_ARRAY_API_PARAM_INFO_LIST = new ArrayList<>();
    private static final List<APIParamInfo> TRACK_LONG_API_PARAM_INFO_LIST = new ArrayList<>();
    private static final List<APIParamInfo> TRACK_STRING_IN_CREDENTIALS_API_PARAM_INFO_LIST = new ArrayList<>();

    private static final APIParamInfo MessageDigest_getInstance0_paramList0 = new APIParamInfo("java.security.MessageDigest", "java.security.MessageDigest getInstance(java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo MessageDigest_getInstance1_paramList0 = new APIParamInfo("java.security.MessageDigest", "java.security.MessageDigest getInstance(java.lang.String,java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo MessageDigest_getInstance2_paramList0 = new APIParamInfo("java.security.MessageDigest", "java.security.MessageDigest getInstance(java.lang.String,java.security.Provider)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo Cipher_getInstance0_paramList0 = new APIParamInfo("javax.crypto.Cipher", "javax.crypto.Cipher getInstance(java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo Cipher_getInstance1_paramList0 = new APIParamInfo("javax.crypto.Cipher", "javax.crypto.Cipher getInstance(java.lang.String,java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo Cipher_getInstance2_paramList0 = new APIParamInfo("javax.crypto.Cipher", "javax.crypto.Cipher getInstance(java.lang.String,java.security.Provider)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo SecretKeySpec_init0_paramList1 = new APIParamInfo("javax.crypto.spec.SecretKeySpec", "void <init>(byte[],java.lang.String)", new ArrayList<>(List.of(1)));
    private static final APIParamInfo SecretKeySpec_init1_paramList3 = new APIParamInfo("javax.crypto.spec.SecretKeySpec", "void <init>(byte[],int,int,java.lang.String)", new ArrayList<>(List.of(3)));

    private static final APIParamInfo Mac_getInstance0_param0 = new APIParamInfo("javax.crypto.Mac", "javax.crypto.Mac getInstance(java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo Mac_getInstance1_param0 = new APIParamInfo("javax.crypto.Mac", "javax.crypto.Mac getInstance(java.lang.String,java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo Mac_getInstance2_param0 = new APIParamInfo("javax.crypto.Mac", "javax.crypto.Mac getInstance(java.lang.String,java.security.Provider)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo KeyPairGenerator_getInstance0_param0 = new APIParamInfo("java.security.KeyPairGenerator", "java.security.KeyPairGenerator getInstance(java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo KeyPairGenerator_getInstance1_param0 = new APIParamInfo("java.security.KeyPairGenerator", "java.security.KeyPairGenerator getInstance(java.lang.String,java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo KeyPairGenerator_getInstance2_param0 = new APIParamInfo("java.security.KeyPairGenerator", "java.security.KeyPairGenerator getInstance(java.lang.String,java.security.Provider)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo SecretKeyFactory_getInstance0_param0 = new APIParamInfo("javax.crypto.SecretKeyFactory", "javax.crypto.SecretKeyFactory getInstance(java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo SecretKeyFactory_getInstance1_param0 = new APIParamInfo("javax.crypto.SecretKeyFactory", "javax.crypto.SecretKeyFactory getInstance(String algorithm,String provider)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo SecretKeyFactory_getInstance2_param0 = new APIParamInfo("javax.crypto.SecretKeyFactory", "javax.crypto.SecretKeyFactory getInstance(String algorithm,java.security.Provider)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo ECGenParameterSpec_init_paramList0 = new APIParamInfo("java.security.spec.ECGenParameterSpec", "void <init>(java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo RSAKeyGenParameterSpec_init0_paramList0 = new APIParamInfo("java.security.spec.RSAKeyGenParameterSpec", "void <init>(int,java.math.BigInteger)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo RSAKeyGenParameterSpec_init1_paramList0 = new APIParamInfo("java.security.spec.RSAKeyGenParameterSpec", "void <init>(int,java.math.BigInteger,java.security.spec.AlgorithmParameterSpec)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo RSAKeyGenParameterSpec_init0_paramList1 = new APIParamInfo("java.security.spec.RSAKeyGenParameterSpec", "void <init>(int,java.math.BigInteger)", new ArrayList<>(List.of(1)));
    private static final APIParamInfo RSAKeyGenParameterSpec_init1_paramList1 = new APIParamInfo("java.security.spec.RSAKeyGenParameterSpec", "void <init>(int,java.math.BigInteger,java.security.spec.AlgorithmParameterSpec)", new ArrayList<>(List.of(1)));
    private static final APIParamInfo DSAGenParameterSpec_init0_paramList0 = new APIParamInfo("java.security.spec.DSAGenParameterSpec", "void <init>(int,int)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo DSAGenParameterSpec_init1_paramList0 = new APIParamInfo("java.security.spec.DSAGenParameterSpec", "void <init>(int,int,int)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo DSAGenParameterSpec_init0_paramList1 = new APIParamInfo("java.security.spec.DSAGenParameterSpec", "void <init>(int,int)", new ArrayList<>(List.of(1)));
    private static final APIParamInfo DSAGenParameterSpec_init1_paramList1 = new APIParamInfo("java.security.spec.DSAGenParameterSpec", "void <init>(int,int,int)", new ArrayList<>(List.of(1)));
    private static final APIParamInfo DHGenParameterSpec_init_paramList0 = new APIParamInfo("javax.crypto.spec.DHGenParameterSpec", "void <init>(int,int)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo DHGenParameterSpec_init_paramList1 = new APIParamInfo("javax.crypto.spec.DHGenParameterSpec", "void <init>(int,int)", new ArrayList<>(List.of(1)));

    private static final APIParamInfo SecretKeySpec_init0_paramList0 = new APIParamInfo("javax.crypto.spec.SecretKeySpec", "void <init>(byte[],java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo SecretKeySpec_init1_paramList0 = new APIParamInfo("javax.crypto.spec.SecretKeySpec", "void <init>(byte[],int,int,java.lang.String)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo IvParameterSpec_init0_paramList0 = new APIParamInfo("javax.crypto.spec.IvParameterSpec", "void <init>(byte[])", new ArrayList<>(List.of(0)));
    private static final APIParamInfo IvParameterSpec_init1_paramList0 = new APIParamInfo("javax.crypto.spec.IvParameterSpec", "void <init>(byte[],int,int)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo PBEKeySpec_init0_paramList0 = new APIParamInfo("javax.crypto.spec.PBEKeySpec", "void <init>(char[])", new ArrayList<>(List.of(0)));
    private static final APIParamInfo PBEKeySpec_init1_paramList0 = new APIParamInfo("javax.crypto.spec.PBEKeySpec", "void <init>(char[],byte[],int,int)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo PBEKeySpec_init2_paramList0 = new APIParamInfo("javax.crypto.spec.PBEKeySpec", "void <init>(char[],byte[],int)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo KeyStore_load_paramList1 = new APIParamInfo("java.security.KeyStore", "void load(java.io.InputStream,char[])", new ArrayList<>(List.of(1)));
    private static final APIParamInfo KeyStore_store_paramList1_ = new APIParamInfo("java.security.KeyStore", "void store(java.io.OutputStream,char[])", new ArrayList<>(List.of(1)));
    private static final APIParamInfo KeyStore_setKeyEntry_paramList2_ = new APIParamInfo("java.security.KeyStore", "void setKeyEntry(java.lang.String,java.security.Key,char[],java.security.cert.Certificate[])", new ArrayList<>(List.of(2)));
    private static final APIParamInfo KeyStore_getKey_paramList1_ = new APIParamInfo("java.security.KeyStore", "java.security.Key getKey(java.lang.String,char[])", new ArrayList<>(List.of(1)));
    private static final APIParamInfo KeyStore_getInstance_paramList1 = new APIParamInfo("java.security.KeyStore", "java.security.KeyStore getInstance(java.io.File, char[])", new ArrayList<>(List.of(1)));

    private static final APIParamInfo PBEParamterSpec_init0_paramList0 = new APIParamInfo("javax.crypto.spec.PBEParameterSpec", "void <init>(byte[],int)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo PBEParamrerSpec_init1_paramList0 = new APIParamInfo("javax.crypto.spec.PBEParameterSpec", "void <init>(byte[],int,java.security.spec.AlgorithmParameterSpec)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo PBEKeySpec_init0_paramList1 = new APIParamInfo("javax.crypto.spec.PBEKeySpec", "void <init>(char[],byte[],int)", new ArrayList<>(List.of(1)));
    private static final APIParamInfo PBEKeySpec_init1_paramList1 = new APIParamInfo("javax.crypto.spec.PBEKeySpec", "void <init>(char[],byte[],int,int)", new ArrayList<>(List.of(1)));

    private static final APIParamInfo SecureRandom_init_paramList0 = new APIParamInfo("java.security.SecureRandom", "void <init>(byte[])", new ArrayList<>(List.of(0)));
    private static final APIParamInfo SecureRandom_setSeed0_paramList0 = new APIParamInfo("java.security.SecureRandom", "void setSeed(byte[])", new ArrayList<>(List.of(0)));
    private static final APIParamInfo SecureRandom_setSeed1_paramList0 = new APIParamInfo("java.security.SecureRandom", "void setSeed(long)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo PBEParameterSpec_init0_paramList1 = new APIParamInfo("javax.crypto.spec.PBEParameterSpec", "void <init>(byte[],int)", new ArrayList<>(List.of(1)));
    private static final APIParamInfo PBEParameterSpec_init1_paramList1 = new APIParamInfo("javax.crypto.spec.PBEParameterSpec", "void <init>(byte[],int,java.security.specAlgorithmParameterSpec)", new ArrayList<>(List.of(1)));
    private static final APIParamInfo PBEKeySpec_init0_paramList2 = new APIParamInfo("javax.crypto.spec.PBEKeySpec", "void <init>(char[],byte[],int,int)", new ArrayList<>(List.of(2)));
    private static final APIParamInfo PBEKeySpec_init1_paramList2 = new APIParamInfo("javax.crypto.spec.PBEKeySpec", "void <init>(char[],byte[],int)", new ArrayList<>(List.of(2)));

    private static final APIParamInfo KeyPairGenerator_initialize0_paramList0 = new APIParamInfo("java.security.KeyPairGenerator", "void initialize(int)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo KeyPairGenerator_initialize1_paramList0 = new APIParamInfo("java.security.KeyPairGenerator", "void initialize(int,java.security.SecureRandom)", new ArrayList<>(List.of(0)));
//    private static final APIParamInfo KeyPairGenerator_initialize2_paramList0 = new APIParamInfo("java.security.KeyPairGenerator", "void initialize(java.security.spec.AlgorithmParameterSpec)", new ArrayList<>(List.of(0)));
//    private static final APIParamInfo KeyPairGenerator_initialize3_paramList0 = new APIParamInfo("java.security.KeyPairGenerator", "void initialize(java.security.spec.AlgorithmParameterSpec,java.security.SecureRandom)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo Signature_getInstance0_paramList0 = new APIParamInfo("java.security.Signature", "java.security.Signature getInstance(java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo Signature_getInstance1_paramList0 = new APIParamInfo("java.security.Signature", "java.security.Signature getInstance(java.lang.String,java.lang.String)", new ArrayList<>(List.of(0)));
    private static final APIParamInfo Signature_getInstance2_paramList0 = new APIParamInfo("java.security.Signature", "java.security.Signature getInstance(java.lang.String,java.security.Provider)", new ArrayList<>(List.of(0)));

    private static final APIParamInfo X509EncodedKeySpec_init0_paramList0 = new APIParamInfo("java.security.spec.X509EncodedKeySpec", "void <init>(byte[])", new ArrayList<>(List.of(0)));
    private static final APIParamInfo X509EncodedKeySpec_init1_paramList0 = new APIParamInfo("java.security.spec.X509EncodedKeySpec", "void <init>(byte[],java.lang.String)", new ArrayList<>(List.of(0)));

    static {
        // java.security.MessageDigest
        API_PARAM_INFO_LIST.add(MessageDigest_getInstance0_paramList0);
        API_PARAM_INFO_LIST.add(MessageDigest_getInstance1_paramList0);
        API_PARAM_INFO_LIST.add(MessageDigest_getInstance2_paramList0);

        // javax.crypto.Cipher
        API_PARAM_INFO_LIST.add(Cipher_getInstance0_paramList0);
        API_PARAM_INFO_LIST.add(Cipher_getInstance1_paramList0);
        API_PARAM_INFO_LIST.add(Cipher_getInstance2_paramList0);

        // java.security.spec.*
        API_PARAM_INFO_LIST.add(ECGenParameterSpec_init_paramList0);
        API_PARAM_INFO_LIST.add(RSAKeyGenParameterSpec_init0_paramList0);
        API_PARAM_INFO_LIST.add(RSAKeyGenParameterSpec_init1_paramList0);
        API_PARAM_INFO_LIST.add(RSAKeyGenParameterSpec_init0_paramList1);
        API_PARAM_INFO_LIST.add(RSAKeyGenParameterSpec_init1_paramList1);
        API_PARAM_INFO_LIST.add(DSAGenParameterSpec_init0_paramList0);
        API_PARAM_INFO_LIST.add(DSAGenParameterSpec_init1_paramList0);
        API_PARAM_INFO_LIST.add(DSAGenParameterSpec_init0_paramList1);
        API_PARAM_INFO_LIST.add(DSAGenParameterSpec_init1_paramList1);
        API_PARAM_INFO_LIST.add(DHGenParameterSpec_init_paramList0);
        API_PARAM_INFO_LIST.add(DHGenParameterSpec_init_paramList1);


        // javax.crypto.spec.SecretKeySpec
        API_PARAM_INFO_LIST.add(SecretKeySpec_init0_paramList1);
        API_PARAM_INFO_LIST.add(SecretKeySpec_init1_paramList3);

        // javax.crypto.Mac
        API_PARAM_INFO_LIST.add(Mac_getInstance0_param0);
        API_PARAM_INFO_LIST.add(Mac_getInstance1_param0);
        API_PARAM_INFO_LIST.add(Mac_getInstance2_param0);

        // java.security.KeyPairGenerator
        API_PARAM_INFO_LIST.add(KeyPairGenerator_getInstance0_param0);
        API_PARAM_INFO_LIST.add(KeyPairGenerator_getInstance1_param0);
        API_PARAM_INFO_LIST.add(KeyPairGenerator_getInstance2_param0);

        // javax.crypto.SecretKeyFactory
        API_PARAM_INFO_LIST.add(SecretKeyFactory_getInstance0_param0);
        API_PARAM_INFO_LIST.add(SecretKeyFactory_getInstance1_param0);
        API_PARAM_INFO_LIST.add(SecretKeyFactory_getInstance2_param0);

        // javax.crypto.spec.SecretKeySpec
        API_PARAM_INFO_LIST.add(SecretKeySpec_init0_paramList0);
        API_PARAM_INFO_LIST.add(SecretKeySpec_init1_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(SecretKeySpec_init0_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(SecretKeySpec_init1_paramList0);
        TRACK_STRING_IN_CREDENTIALS_API_PARAM_INFO_LIST.add(SecretKeySpec_init0_paramList0);
        TRACK_STRING_IN_CREDENTIALS_API_PARAM_INFO_LIST.add(SecretKeySpec_init1_paramList0);

        // javax.crypto.spec.IvParameterSpec
        API_PARAM_INFO_LIST.add(IvParameterSpec_init0_paramList0);
        API_PARAM_INFO_LIST.add(IvParameterSpec_init1_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(IvParameterSpec_init0_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(IvParameterSpec_init1_paramList0);

        // javax.crypto.spec.PBEKeySpec
        API_PARAM_INFO_LIST.add(PBEKeySpec_init0_paramList0);
        API_PARAM_INFO_LIST.add(PBEKeySpec_init1_paramList0);
        API_PARAM_INFO_LIST.add(PBEKeySpec_init2_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(PBEKeySpec_init0_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(PBEKeySpec_init1_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(PBEKeySpec_init2_paramList0);

        // java.security.KeyStore
        API_PARAM_INFO_LIST.add(KeyStore_load_paramList1);
        API_PARAM_INFO_LIST.add(KeyStore_store_paramList1_);
        API_PARAM_INFO_LIST.add(KeyStore_setKeyEntry_paramList2_);
        API_PARAM_INFO_LIST.add(KeyStore_getKey_paramList1_);
        API_PARAM_INFO_LIST.add(KeyStore_getInstance_paramList1);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(KeyStore_load_paramList1);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(KeyStore_store_paramList1_);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(KeyStore_setKeyEntry_paramList2_);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(KeyStore_getKey_paramList1_);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(KeyStore_getInstance_paramList1);

        // javax.crypto.spec.PBEParameterSpec   Static salt
        // javax.crypto.spec.PBEKeySpec         Static salt
        API_PARAM_INFO_LIST.add(PBEParamterSpec_init0_paramList0);
        API_PARAM_INFO_LIST.add(PBEParamrerSpec_init1_paramList0);
        API_PARAM_INFO_LIST.add(PBEKeySpec_init0_paramList1);
        API_PARAM_INFO_LIST.add(PBEKeySpec_init1_paramList1);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(PBEParamterSpec_init0_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(PBEParamrerSpec_init1_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(PBEKeySpec_init0_paramList1);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(PBEKeySpec_init1_paramList1);


        // java.security.SecureRandom
        API_PARAM_INFO_LIST.add(SecureRandom_init_paramList0);
        API_PARAM_INFO_LIST.add(SecureRandom_setSeed0_paramList0);
        API_PARAM_INFO_LIST.add(SecureRandom_setSeed1_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(SecureRandom_init_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(SecureRandom_setSeed0_paramList0);
        TRACK_LONG_API_PARAM_INFO_LIST.add(SecureRandom_setSeed1_paramList0);

        // javax.crypto.spec.PBEParameterSpec
        API_PARAM_INFO_LIST.add(PBEParameterSpec_init0_paramList1);
        API_PARAM_INFO_LIST.add(PBEParameterSpec_init1_paramList1);
        API_PARAM_INFO_LIST.add(PBEKeySpec_init0_paramList2);
        API_PARAM_INFO_LIST.add(PBEKeySpec_init1_paramList2);

        // java.security.KeyPairGenerator   Key Size
        TRACK_BASE_API_PARAM_INFO_LIST.add(KeyPairGenerator_initialize0_paramList0);
        TRACK_BASE_API_PARAM_INFO_LIST.add(KeyPairGenerator_initialize1_paramList0);
//        TRACK_BASE_API_PARAM_INFO_LIST.add(KeyPairGenerator_initialize2_paramList0);
//        TRACK_BASE_API_PARAM_INFO_LIST.add(KeyPairGenerator_initialize3_paramList0);

        // java.security.Signature
        API_PARAM_INFO_LIST.add(Signature_getInstance0_paramList0);
        API_PARAM_INFO_LIST.add(Signature_getInstance1_paramList0);
        API_PARAM_INFO_LIST.add(Signature_getInstance2_paramList0);

        // java.security.spec.X509EncodedKeySpec
        API_PARAM_INFO_LIST.add(X509EncodedKeySpec_init0_paramList0);
        API_PARAM_INFO_LIST.add(X509EncodedKeySpec_init1_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(X509EncodedKeySpec_init0_paramList0);
        TRACK_ARRAY_API_PARAM_INFO_LIST.add(X509EncodedKeySpec_init1_paramList0);


        API_PARAM_INFO_LIST.addAll(TRACK_BASE_API_PARAM_INFO_LIST);
    }

    public static List<APIParamInfo> getAllApiParamInfoList() {
        return API_PARAM_INFO_LIST;
    }

    public static List<APIParamInfo> getTrackBaseApiParamInfoList() {
        return TRACK_BASE_API_PARAM_INFO_LIST;
    }

    public static List<APIParamInfo> getTrackArrayApiParamInfoList() {
        return TRACK_ARRAY_API_PARAM_INFO_LIST;
    }

    public static List<APIParamInfo> getTrackLongApiParamInfoList() {
        return TRACK_LONG_API_PARAM_INFO_LIST;
    }

    public static List<APIParamInfo> getTrackStringInCredentialsApiParamInfoList() {
        return TRACK_STRING_IN_CREDENTIALS_API_PARAM_INFO_LIST;
    }

    public static List<APIParamInfo> getMessageDigestGetInstance_Algo_String() {
        return new ArrayList<>(List.of(MessageDigest_getInstance0_paramList0, MessageDigest_getInstance1_paramList0, MessageDigest_getInstance2_paramList0));
    }

    public static List<APIParamInfo> getCipherGetInstance_Algo_String() {
        return new ArrayList<>(List.of(Cipher_getInstance0_paramList0, Cipher_getInstance1_paramList0, Cipher_getInstance2_paramList0));
    }

    public static List<APIParamInfo> getSecretKeySpecInit_Algo_String() {
        return new ArrayList<>(List.of(SecretKeySpec_init0_paramList1, SecretKeySpec_init1_paramList3));
    }

    public static List<APIParamInfo> getMacGetInstance_Algo_String() {
        return new ArrayList<>(List.of(Mac_getInstance0_param0, Mac_getInstance1_param0, Mac_getInstance2_param0));
    }

    public static List<APIParamInfo> getKeyPairGeneratorGetInstance_Algo_String() {
        return new ArrayList<>(List.of(KeyPairGenerator_getInstance0_param0, KeyPairGenerator_getInstance1_param0, KeyPairGenerator_getInstance2_param0));
    }

    public static List<APIParamInfo> getSecretKeyFactoryGetInstance_Algo_String() {
        return new ArrayList<>(List.of(SecretKeyFactory_getInstance0_param0, SecretKeyFactory_getInstance1_param0, SecretKeyFactory_getInstance2_param0));
    }

    public static List<APIParamInfo> getECGenParameterSpecInit_ECStandard_String() {
        return new ArrayList<>(List.of(ECGenParameterSpec_init_paramList0));
    }

    public static List<APIParamInfo> getRSAKeyGenParameterSpecInit_RSAkeySize_int() {
        return new ArrayList<>(List.of(RSAKeyGenParameterSpec_init0_paramList0, RSAKeyGenParameterSpec_init1_paramList0));
    }

    public static List<APIParamInfo> getRSAKeyGenParameterSpecInit_RSAPubExp_BigInteger() {
        return new ArrayList<>(List.of(RSAKeyGenParameterSpec_init0_paramList1, RSAKeyGenParameterSpec_init1_paramList1));
    }

    public static List<APIParamInfo> getDSAGenParameterSpecInit_DSAprimePLen_int() {
        return new ArrayList<>(List.of(DSAGenParameterSpec_init0_paramList0, DSAGenParameterSpec_init1_paramList0));
    }

    public static List<APIParamInfo> getDSAGenParameterSpecInit_DSASubprimeQLen_int() {
        return new ArrayList<>(List.of(DSAGenParameterSpec_init0_paramList1, DSAGenParameterSpec_init1_paramList1));
    }

    public static List<APIParamInfo> getDHGenParameterSpecInit_DHPrimeSize_int() {
        return new ArrayList<>(List.of(DHGenParameterSpec_init_paramList0));
    }

    public static List<APIParamInfo> getDHGenParameterSpecInit_DHExpSize_int() {
        return new ArrayList<>(List.of(DHGenParameterSpec_init_paramList1));
    }

    public static List<APIParamInfo> getSecretKeySpecInit_Key_bytes() {
        return new ArrayList<>(List.of(SecretKeySpec_init0_paramList0, SecretKeySpec_init1_paramList0));
    }

    public static List<APIParamInfo> getIvParameterSpecInit_Iv_bytes() {
        return new ArrayList<>(List.of(IvParameterSpec_init0_paramList0, IvParameterSpec_init1_paramList0));
    }

    public static List<APIParamInfo> getPBEKeySpecInit_PBEKey_chars() {
        return new ArrayList<>(List.of(PBEKeySpec_init0_paramList0, PBEKeySpec_init1_paramList0));
    }

    public static List<APIParamInfo> getKeyStore_Key_chars() {
        return new ArrayList<>(List.of(KeyStore_load_paramList1, KeyStore_store_paramList1_, KeyStore_setKeyEntry_paramList2_, KeyStore_getKey_paramList1_, KeyStore_getInstance_paramList1));
    }

    public static List<APIParamInfo> getPBEParameterSpecInit_Salt_bytes() {
        return new ArrayList<>(List.of(PBEParamterSpec_init0_paramList0, PBEParamrerSpec_init1_paramList0));
    }

    public static List<APIParamInfo> getPBEKeySpecInit_Salt_bytes() {
        return new ArrayList<>(List.of(PBEKeySpec_init0_paramList1, PBEKeySpec_init1_paramList1));
    }

    public static List<APIParamInfo> getSecureRandomInit_Seed_bytesAndLong() {
        return new ArrayList<>(List.of(SecureRandom_init_paramList0, SecureRandom_setSeed0_paramList0, SecureRandom_setSeed1_paramList0));
    }

    public static List<APIParamInfo> getPBEParameterSpecInit_Iter_int() {
        return new ArrayList<>(List.of(PBEParameterSpec_init0_paramList1, PBEParameterSpec_init1_paramList1, PBEKeySpec_init0_paramList2, PBEKeySpec_init1_paramList2));
    }

    public static List<APIParamInfo> getKeyPairGeneratorInitialize_KeySize_int() {
//        return new ArrayList<>(List.of(KeyPairGenerator_initialize0_paramList0, KeyPairGenerator_initialize1_paramList0, KeyPairGenerator_initialize2_paramList0, KeyPairGenerator_initialize3_paramList0));
        return new ArrayList<>(List.of(KeyPairGenerator_initialize0_paramList0, KeyPairGenerator_initialize1_paramList0));
    }

    public static List<APIParamInfo> getSignatureGetInstance_Algo_String() {
        return new ArrayList<>(List.of(Signature_getInstance0_paramList0, Signature_getInstance1_paramList0, Signature_getInstance2_paramList0));
    }

    public static List<APIParamInfo> getX509EncodedKeySpecInit_Key_bytes() {
        return new ArrayList<>(List.of(X509EncodedKeySpec_init0_paramList0, X509EncodedKeySpec_init1_paramList0));
    }
}
