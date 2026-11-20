import java.math.BigInteger;
import java.nio.file.*;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class SelfTest {
    public static void main(String[] a) throws Exception {
        com.fujiang.keygen.Main.main(new String[]{ a[0] });
        String pub = new String(Files.readAllBytes(Paths.get(a[0], "adb_keys"))).trim().split("\\s+")[0];
        byte[] b = Base64.getDecoder().decode(pub);
        String pem = new String(Files.readAllBytes(Paths.get(a[0], "adbkey"))).replaceAll("-----[^-]+-----","").replaceAll("\\s","");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        java.security.interfaces.RSAPrivateCrtKey crt = (java.security.interfaces.RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        BigInteger n = crt.getModulus(), e = crt.getPublicExponent();
        BigInteger n0 = BigInteger.ONE.shiftLeft(32).subtract(n.modInverse(BigInteger.ONE.shiftLeft(32)));
        BigInteger rr = BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.valueOf(2), n);
        boolean ok = b.length == 524
            && read32(b,0)==64 && read32(b,4)==n0.longValue()
            && eq(le(n,256), b, 8) && eq(le(rr,256), b, 264) && read32(b,520)==e.longValue();
        System.out.println("bytes=" + b.length + " words=" + read32(b,0) + " STRUCT_OK=" + ok);
        System.out.println("pubkey=" + pub.substring(0, 40) + "...");
    }
    static int read32(byte[] b,int o){return (b[o]&0xFF)|((b[o+1]&0xFF)<<8)|((b[o+2]&0xFF)<<16)|((b[o+3]&0xFF)<<24);}
    static boolean eq(byte[] x, byte[] y, int off){for(int i=0;i<x.length;i++) if(x[i]!=y[off+i]) return false; return true;}
    static byte[] le(BigInteger v,int len){byte[] r=v.toByteArray(),o=new byte[len];for(int i=0;i<len;i++){int k=r.length-1-i;o[i]=k>=0?r[k]:0;}return o;}
}
