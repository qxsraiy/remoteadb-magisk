import java.math.BigInteger;
import java.nio.file.*;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class Probe {
    public static void main(String[] a) throws Exception {
        String pem = new String(Files.readAllBytes(Paths.get(a[0]))).replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        java.security.interfaces.RSAPrivateCrtKey crt = (java.security.interfaces.RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        BigInteger n = crt.getModulus(), e = crt.getPublicExponent();
        byte[] pub = Base64.getDecoder().decode(new String(Files.readAllBytes(Paths.get(a[1]))).trim().split("\\s+")[0]);
        byte[] nb = le(n, 256);
        BigInteger rr = BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.valueOf(2), n);
        byte[] rb = le(rr, 256);
        System.out.println("pub bytes=" + pub.length);
        System.out.println("n at offset=" + indexOf(pub, nb) + "  rr at offset=" + indexOf(pub, rb));
        System.out.println("n0inv real=" + Integer.toHexString(r32(pub, 0)));
        BigInteger calcN0 = BigInteger.ONE.shiftLeft(32).subtract(n.modInverse(BigInteger.ONE.shiftLeft(32)));
        System.out.println("n0inv calc=" + Long.toHexString(calcN0.longValue()));
        System.out.println("tail=" + hex(pub, 512, 12));
    }
    static int indexOf(byte[] h, byte[] n) {
        outer: for (int i = 0; i + n.length <= h.length; i++) { for (int j = 0; j < n.length; j++) if (h[i+j] != n[j]) continue outer; return i; }
        return -1;
    }
    static int r32(byte[] b, int o) { return (b[o]&0xFF) | ((b[o+1]&0xFF)<<8) | ((b[o+2]&0xFF)<<16) | ((b[o+3]&0xFF)<<24); }
    static byte[] le(BigInteger v, int len) { byte[] raw = v.toByteArray(), o = new byte[len]; for (int i=0;i<len;i++){int idx=raw.length-1-i; o[i]= idx>=0?raw[idx]:0;} return o; }
    static String hex(byte[] b,int o,int l){StringBuilder s=new StringBuilder();for(int i=0;i<l;i++)s.append(String.format("%02x",b[o+i]));return s.toString();}
}
