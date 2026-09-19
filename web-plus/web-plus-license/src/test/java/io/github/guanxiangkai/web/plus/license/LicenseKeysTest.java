package io.github.guanxiangkai.web.plus.license;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LicenseKeysTest {
    private static KeyPair keyPair;

    @BeforeAll
    static void createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void readsDerAndPemPublicAndPrivateKeys(@TempDir Path directory) throws Exception {
        Path publicDer = directory.resolve("license-public.der");
        Path privateDer = directory.resolve("license-private.der");
        Path publicPem = directory.resolve("license-public.pem");
        Path privatePem = directory.resolve("license-private.pem");
        Files.write(publicDer, keyPair.getPublic().getEncoded());
        Files.write(privateDer, keyPair.getPrivate().getEncoded());
        Files.writeString(publicPem, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);
        Files.writeString(privatePem, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);

        assertArrayEquals(keyPair.getPublic().getEncoded(), LicenseKeys.readPublicKey(publicDer).getEncoded());
        assertArrayEquals(keyPair.getPrivate().getEncoded(), LicenseKeys.readPrivateKey(privateDer).getEncoded());
        assertArrayEquals(keyPair.getPublic().getEncoded(), LicenseKeys.readPublicKey(publicPem).getEncoded());
        assertArrayEquals(keyPair.getPrivate().getEncoded(), LicenseKeys.readPrivateKey(privatePem).getEncoded());
    }

    @Test
    void rejectsKeyFileOverLimit(@TempDir Path directory) throws Exception {
        Path oversized = directory.resolve("oversized.der");
        Files.write(oversized, new byte[64 * 1024 + 1]);
        assertThrows(LicenseException.class, () -> LicenseKeys.readPublicKey(oversized));
    }

    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
                + "\n-----END " + label + "-----\n";
    }
}
