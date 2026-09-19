package io.github.guanxiangkai.artifact.plus.signing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * 业务制品的 RSA-PSS 原始二进制签名工具。
 *
 * <p>签名和验签均直接流式处理制品字节；验签完成后，部署端仍应使用不可变的制品文件。</p>
 */
public final class ArtifactSignatures {

    private static final String SIGNATURE_ALGORITHM = "RSASSA-PSS";
    private static final PSSParameterSpec PSS_PARAMETERS = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
    private static final int MINIMUM_RSA_BITS = 3072;
    private static final int BUFFER_SIZE = 8192;
    private static final long MAXIMUM_KEY_BYTES = 64 * 1024L;
    private static final long MAXIMUM_SIGNATURE_BYTES = 64 * 1024L;

    private ArtifactSignatures() {
    }

    /**
     * 使用 PKCS#8 私钥为制品创建原始 RSA-PSS 签名，并使用指定公钥立即确认密钥配对。
     *
     * @param artifact 待签名的普通制品文件
     * @param signature 生成的原始二进制签名路径
     * @param privateKey PKCS#8 DER 或 PEM 私钥文件
     * @param publicKey X.509 DER 或 PEM 公钥文件
     * @throws IOException 文件不可读、路径冲突、制品在签名期间变化或无法原子发布签名时抛出
     * @throws GeneralSecurityException 密钥不是至少 3072 位 RSA 密钥或签名确认失败时抛出
     */
    public static void sign(Path artifact, Path signature, Path privateKey, Path publicKey)
            throws IOException, GeneralSecurityException {
        Path artifactPath = requireExistingRegularFile(artifact, "制品");
        Path privateKeyPath = requireExistingRegularFile(privateKey, "私钥");
        Path publicKeyPath = requireExistingRegularFile(publicKey, "公钥");
        SignatureDestination destination = prepareSignatureDestination(signature);
        requireDistinct(artifactPath, privateKeyPath, "制品不能与私钥相同");
        requireDistinct(artifactPath, publicKeyPath, "制品不能与公钥相同");
        requireDistinct(privateKeyPath, publicKeyPath, "私钥不能与公钥相同");
        requireDistinct(artifactPath, destination.path(), "制品不能与签名相同");
        requireDistinct(privateKeyPath, destination.path(), "私钥不能与签名相同");
        requireDistinct(publicKeyPath, destination.path(), "公钥不能与签名相同");

        Path temporarySignature = null;
        try {
            PrivateKey parsedPrivateKey = readPrivateKey(privateKeyPath);
            PublicKey parsedPublicKey = readPublicKey(publicKeyPath);
            FileState stateBeforeSigning = FileState.read(artifactPath);
            temporarySignature = Files.createTempFile(destination.path().getParent(), ".artifact-signature-", ".tmp");

            Signature signer = newSignature();
            signer.initSign(parsedPrivateKey);
            updateFromFile(signer, artifactPath);
            byte[] signedBytes = signer.sign();
            if (signedBytes.length != signatureBytes(parsedPublicKey)) {
                throw new GeneralSecurityException("RSA-PSS 签名长度异常");
            }
            Files.write(temporarySignature, signedBytes);
            stateBeforeSigning.requireUnchanged(artifactPath);

            verifySignature(artifactPath, temporarySignature, parsedPublicKey);
            stateBeforeSigning.requireUnchanged(artifactPath);
            moveAtomically(temporarySignature, destination.path());
            temporarySignature = null;
        } catch (IOException | GeneralSecurityException exception) {
            removeSignature(destination.path());
            throw exception;
        } finally {
            if (temporarySignature != null) {
                Files.deleteIfExists(temporarySignature);
            }
        }
    }

    /**
     * 使用 X.509 公钥验证制品及其原始 RSA-PSS 签名。
     *
     * @param artifact 待验证的普通制品文件
     * @param signature 原始二进制签名文件
     * @param publicKey X.509 DER 或 PEM 公钥文件
     * @throws IOException 文件不可读、路径冲突或签名文件长度异常时抛出
     * @throws GeneralSecurityException 密钥不符合要求或签名与制品不匹配时抛出
     */
    public static void verify(Path artifact, Path signature, Path publicKey)
            throws IOException, GeneralSecurityException {
        Path artifactPath = requireExistingRegularFile(artifact, "制品");
        Path publicKeyPath = requireExistingRegularFile(publicKey, "公钥");
        SignatureDestination destination = prepareExistingSignature(signature);
        requireDistinct(artifactPath, publicKeyPath, "制品不能与公钥相同");
        requireDistinct(artifactPath, destination.path(), "制品不能与签名相同");
        requireDistinct(publicKeyPath, destination.path(), "公钥不能与签名相同");

        PublicKey parsedPublicKey = readPublicKey(publicKeyPath);
        FileState stateBeforeVerification = FileState.read(artifactPath);
        verifySignature(artifactPath, destination.path(), parsedPublicKey);
        stateBeforeVerification.requireUnchanged(artifactPath);
    }

    private static Signature newSignature() throws GeneralSecurityException {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.setParameter(PSS_PARAMETERS);
        return signature;
    }

    private static void verifySignature(Path artifact, Path signatureFile, PublicKey publicKey)
            throws IOException, GeneralSecurityException {
        long expectedLength = signatureBytes(publicKey);
        long actualLength = Files.size(signatureFile);
        if (actualLength <= 0 || actualLength > MAXIMUM_SIGNATURE_BYTES || actualLength != expectedLength) {
            throw new IOException("签名文件长度异常");
        }
        byte[] signatureBytes;
        try (InputStream input = Files.newInputStream(signatureFile)) {
            signatureBytes = input.readNBytes((int) MAXIMUM_SIGNATURE_BYTES + 1);
        }
        if (signatureBytes.length != actualLength) {
            throw new IOException("签名文件读取长度异常");
        }

        Signature verifier = newSignature();
        verifier.initVerify(publicKey);
        updateFromFile(verifier, artifact);
        if (!verifier.verify(signatureBytes)) {
            throw new GeneralSecurityException("制品签名验证失败");
        }
    }

    private static void updateFromFile(Signature signature, Path file) throws IOException, GeneralSecurityException {
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(file)) {
            for (int count; (count = input.read(buffer)) != -1;) {
                signature.update(buffer, 0, count);
            }
        }
    }

    private static PrivateKey readPrivateKey(Path path) throws IOException, GeneralSecurityException {
        byte[] encoded = readKeyBytes(path, "PRIVATE KEY");
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        requireSecureRsaKey(privateKey);
        return privateKey;
    }

    private static PublicKey readPublicKey(Path path) throws IOException, GeneralSecurityException {
        byte[] encoded = readKeyBytes(path, "PUBLIC KEY");
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        requireSecureRsaKey(publicKey);
        return publicKey;
    }

    private static byte[] readKeyBytes(Path path, String expectedPemType) throws IOException {
        long length = Files.size(path);
        if (length <= 0 || length > MAXIMUM_KEY_BYTES) {
            throw new IOException("密钥文件长度异常");
        }
        byte[] input;
        try (InputStream stream = Files.newInputStream(path)) {
            input = stream.readNBytes((int) MAXIMUM_KEY_BYTES + 1);
        }
        if (input.length != length) {
            throw new IOException("密钥文件读取长度异常");
        }
        String text = new String(input, StandardCharsets.US_ASCII);
        if (!text.startsWith("-----BEGIN ")) {
            return input;
        }
        if (text.endsWith("\r\n")) {
            text = text.substring(0, text.length() - 2);
        } else if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        String begin = "-----BEGIN " + expectedPemType + "-----";
        String end = "-----END " + expectedPemType + "-----";
        if (!text.startsWith(begin) || !text.endsWith(end)) {
            throw new IOException("PEM 密钥类型异常");
        }
        String body = text.substring(begin.length(), text.length() - end.length()).replaceAll("[\\r\\n]", "");
        if (body.isEmpty() || body.indexOf('-') >= 0 || body.indexOf(' ') >= 0 || body.indexOf('\t') >= 0) {
            throw new IOException("PEM 密钥格式异常");
        }
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException exception) {
            throw new IOException("PEM 密钥编码异常", exception);
        }
    }

    private static void requireSecureRsaKey(Object key) throws GeneralSecurityException {
        if (!(key instanceof RSAKey rsaKey) || rsaKey.getModulus().bitLength() < MINIMUM_RSA_BITS) {
            throw new GeneralSecurityException("必须使用至少 3072 位 RSA 密钥");
        }
    }

    private static long signatureBytes(PublicKey publicKey) throws GeneralSecurityException {
        requireSecureRsaKey(publicKey);
        long bytes = (((RSAKey) publicKey).getModulus().bitLength() + 7L) / 8L;
        if (bytes > MAXIMUM_SIGNATURE_BYTES) {
            throw new GeneralSecurityException("RSA 密钥长度异常");
        }
        return bytes;
    }

    private static Path requireExistingRegularFile(Path path, String name) throws IOException {
        Objects.requireNonNull(path, name + "路径不能为空");
        if (!Files.isRegularFile(path)) {
            throw new IOException(name + "必须是存在的普通文件");
        }
        return path.toRealPath();
    }

    private static SignatureDestination prepareExistingSignature(Path signature) throws IOException {
        SignatureDestination destination = prepareSignatureDestination(signature);
        if (!Files.isRegularFile(destination.path(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("签名必须是存在的普通文件");
        }
        return destination;
    }

    private static SignatureDestination prepareSignatureDestination(Path signature) throws IOException {
        Objects.requireNonNull(signature, "签名路径不能为空");
        Path absolutePath = signature.toAbsolutePath().normalize();
        Path parent = absolutePath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("签名目录不存在");
        }
        if (Files.exists(absolutePath, LinkOption.NOFOLLOW_LINKS)
                && Files.isSymbolicLink(absolutePath)) {
            throw new IOException("签名路径不能是符号链接");
        }
        Path resolvedPath = parent.toRealPath().resolve(absolutePath.getFileName());
        if (Files.exists(resolvedPath, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(resolvedPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("签名必须是普通文件");
        }
        return new SignatureDestination(resolvedPath);
    }

    private static void requireDistinct(Path first, Path second, String message) throws IOException {
        if (first.equals(second)
                || (Files.exists(second, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(first, second))) {
            throw new IOException(message);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("签名目录不支持原子移动", exception);
        }
    }

    private static void removeSignature(Path signature) throws IOException {
        if (Files.exists(signature, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(signature, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(signature))) {
            return;
        }
        Files.deleteIfExists(signature);
    }

    private record SignatureDestination(Path path) {
    }

    private record FileState(long size, Object fileKey, Object modifiedTime) {
        private static FileState read(Path file) throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new FileState(attributes.size(), attributes.fileKey(), attributes.lastModifiedTime());
        }

        private void requireUnchanged(Path file) throws IOException {
            if (!equals(read(file))) {
                throw new IOException("制品在签名或验签期间发生变化");
            }
        }
    }
}
