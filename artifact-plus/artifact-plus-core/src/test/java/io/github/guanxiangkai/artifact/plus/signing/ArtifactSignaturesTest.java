package io.github.guanxiangkai.artifact.plus.signing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactSignaturesTest {

    @TempDir
    Path directory;

    private Path artifact;
    private Path signature;
    private Path privateKey;
    private Path publicKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair keyPair = generator.generateKeyPair();
        artifact = directory.resolve("service.jar");
        signature = directory.resolve("service.jar.sig");
        privateKey = directory.resolve("private.der");
        publicKey = directory.resolve("public.pem");
        Files.writeString(artifact, "artifact content");
        Files.write(privateKey, keyPair.getPrivate().getEncoded());
        Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
    }

    @Test
    void signsAndVerifiesRawArtifactBytes() throws IOException {
        assertDoesNotThrow(() -> ArtifactSignatures.sign(artifact, signature, privateKey, publicKey));
        assertDoesNotThrow(() -> ArtifactSignatures.verify(artifact, signature, publicKey));
        assertFalse(Files.readAllBytes(signature).length == 0);
    }

    @Test
    void acceptsPrivatePemAndPublicDer() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair keyPair = generator.generateKeyPair();
        Files.writeString(privateKey, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        Files.write(publicKey, keyPair.getPublic().getEncoded());

        ArtifactSignatures.sign(artifact, signature, privateKey, publicKey);
        assertDoesNotThrow(() -> ArtifactSignatures.verify(artifact, signature, publicKey));
    }

    @Test
    void rejectsTamperedTruncatedAndAppendedArtifactsOrSignatures() throws Exception {
        ArtifactSignatures.sign(artifact, signature, privateKey, publicKey);
        Files.writeString(artifact, "tampered");
        assertThrows(GeneralSecurityException.class, () -> ArtifactSignatures.verify(artifact, signature, publicKey));

        Files.writeString(artifact, "artifact content");
        Files.write(signature, new byte[0]);
        assertThrows(IOException.class, () -> ArtifactSignatures.verify(artifact, signature, publicKey));

        ArtifactSignatures.sign(artifact, signature, privateKey, publicKey);
        Files.writeString(signature, "extra", java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> ArtifactSignatures.verify(artifact, signature, publicKey));
    }

    @Test
    void rejectsWrongKeyAndUnsafePathReuse() throws Exception {
        ArtifactSignatures.sign(artifact, signature, privateKey, publicKey);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        Path wrongPublicKey = directory.resolve("wrong-public.der");
        Files.write(wrongPublicKey, generator.generateKeyPair().getPublic().getEncoded());
        assertThrows(GeneralSecurityException.class, () -> ArtifactSignatures.verify(artifact, signature, wrongPublicKey));
        assertThrows(IOException.class, () -> ArtifactSignatures.sign(artifact, privateKey, privateKey, publicKey));

        Files.write(privateKey, new byte[] {1, 2, 3});
        assertThrows(GeneralSecurityException.class, () -> ArtifactSignatures.sign(artifact, signature, privateKey, publicKey));
        assertFalse(Files.exists(signature));
    }

    @Test
    void rejectsRsaKeysShorterThan3072Bits() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair shortKeyPair = generator.generateKeyPair();
        Path shortPrivateKey = directory.resolve("short-private.der");
        Path shortPublicKey = directory.resolve("short-public.der");
        Files.write(shortPrivateKey, shortKeyPair.getPrivate().getEncoded());
        Files.write(shortPublicKey, shortKeyPair.getPublic().getEncoded());

        assertThrows(GeneralSecurityException.class,
                () -> ArtifactSignatures.sign(artifact, signature, shortPrivateKey, shortPublicKey));
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }
}
