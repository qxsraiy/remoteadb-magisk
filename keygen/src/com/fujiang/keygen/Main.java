package com.fujiang.keygen;

import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * 刷入模块时在被控机本地生成 adb 密钥对(不出网, 不依赖 openssl)。
 * 用法: app_process / com.fujiang.keygen.Main <输出目录>
 * 产出: <dir>/adbkey (PKCS#8 私钥, 主控端使用)  <dir>/adb_keys (adb 公钥, 本机授权用)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : ".";
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        // 私钥: PKCS#8 PEM
        byte[] pkcs8 = kp.getPrivate().getEncoded();
        String b64 = Base64.getEncoder().encodeToString(pkcs8);
        StringBuilder pem = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < b64.length(); i += 64)
            pem.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        pem.append("-----END PRIVATE KEY-----\n");
        write(new File(dir, "adbkey"), pem.toString());

        // 公钥: adb 自定义结构 base64(n0inv|n|rr|exponent, 小端)
        write(new File(dir, "adb_keys"), adbPubBase64((RSAPublicKey) kp.getPublic()) + " remoteadb@keygen\n");
        System.out.println("KEYGEN_OK");
    }

    static void write(File f, String s) throws Exception {
        FileWriter w = new FileWriter(f);
        w.write(s);
        w.close();
    }

    static String adbPubBase64(RSAPublicKey pub) {
        BigInteger n = pub.getModulus();
        BigInteger e = pub.getPublicExponent();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        // n0inv = -1/n mod 2^32
        BigInteger n0inv = r32.subtract(n.modInverse(r32));
        // rr = (2^2048)^2 mod n
        BigInteger rr = BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.valueOf(2), n);

        // 实测 adb 公钥结构(524字节, 均为小端):
        // [0..4) 模长字数(2048位=64) [4..8) n0inv [8..264) n [264..520) rr [520..524) e
        byte[] out = new byte[4 + 4 + 256 + 256 + 4];
        putLE32(out, 0, BigInteger.valueOf(64));
        putLE32(out, 4, n0inv);
        putLE(out, 8, n, 256);
        putLE(out, 264, rr, 256);
        putLE32(out, 520, e);
        return Base64.getEncoder().encodeToString(out);
    }

    static void putLE32(byte[] b, int off, BigInteger v) {
        long x = v.longValue();
        for (int i = 0; i < 4; i++) b[off + i] = (byte) ((x >>> (8 * i)) & 0xFF);
    }

    static void putLE(byte[] b, int off, BigInteger v, int len) {
        byte[] raw = v.toByteArray();
        for (int i = 0; i < len; i++) {
            int idx = raw.length - 1 - i;
            b[off + i] = idx >= 0 ? raw[idx] : 0;
        }
    }
}
