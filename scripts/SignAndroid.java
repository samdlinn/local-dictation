import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

class SignAndroid {
    public static void main(String[] args) throws Exception {
        var keys = KeyStore.getInstance("PKCS12");
        try (var stream = Files.newInputStream(Path.of(args[2]))) {
            keys.load(stream, "android".toCharArray());
        }
        var certificate = (X509Certificate) keys.getCertificate("androiddebugkey");
        var signer = new ApkSigner.SignerConfig.Builder("Local Dictation alpha",
            (PrivateKey) keys.getKey("androiddebugkey", "android".toCharArray()),
            List.of(certificate)).build();
        new ApkSigner.Builder(List.of(signer))
            .setInputApk(new File(args[0]))
            .setOutputApk(new File(args[1]))
            .setMinSdkVersion(29)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setV4SigningEnabled(false)
            .setOtherSignersSignaturesPreserved(false)
            .setAlignmentPreserved(false)
            .setLibraryPageAlignmentBytes(16384)
            .build().sign();
        var verified = new ApkVerifier.Builder(new File(args[1])).build().verify();
        if (!verified.isVerified() || verified.getSignerCertificates().size() != 1 ||
            !Arrays.equals(certificate.getEncoded(), verified.getSignerCertificates().get(0).getEncoded())) {
            throw new IllegalStateException("Signed APK failed verification: " + verified.getErrors());
        }
        System.out.println("Verified APK with persistent project signing certificate (SHA-256): " +
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
    }
}
