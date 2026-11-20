import java.math.BigInteger;
import java.nio.file.*;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class Verify {
    public static void main(String[] a) throws Exception {
        String pem = new String(Files.readAllBytes(Paths.get(a[0])));
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        java.security.PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        // 由私钥导出公钥参数
        java.security.interfaces.RSAPrivateCrtKey crt = (java.security.interfaces.RSAPrivateCrtKey) priv;
        BigInteger n = crt.getModulus(), e = crt.getPublicExponent();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.modInverse(r32));
        BigInteger rr = BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.valueOf(2), n);
        byte[] out = new byte[520];
        long x = n0inv.longValue();
        for (int i = 0; i < 4; i++) out[i] = (byte)((x >>> (8*i)) & 0xFF);
        putLE(out, 4, n, 256); putLE(out, 260, rr, 256);
        long ex = e.longValue();
        for (int i = 0; i < 4; i++) out[516 + i] = (byte)((ex >>> (8*i)) & 0xFF);
        String mine = Base64.getEncoder().encodeToString(out);
        String real = new String(Files.readAllBytes(Paths.get(a[1]))).trim().split("\\s+")[0];
        System.out.println("mine len=" + mine.length() + " real len=" + real.length());
        System.out.println("MATCH=" + mine.equals(real));
        if (!mine.equals(real)) { System.out.println("mine=" + mine.substring(0,80)); System.out.println("real=" + real.substring(0,80)); }
    }
    static void putLE(byte[] b, int off, BigInteger v, int len) {
        byte[] raw = v.toByteArray();
        for (int i = 0; i < len; i++) { int idx = raw.length - 1 - i; b[off+i] = idx >= 0 ? raw[idx] : 0; }
    }
}
