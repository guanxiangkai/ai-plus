package io.github.guanxiangkai.web.plus.license.runtime;

import io.github.guanxiangkai.web.plus.license.LicenseException;
import io.github.guanxiangkai.web.plus.license.properties.LicenseProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 禁止重定向、限制响应大小和完整请求时长的 HTTPS 租约客户端。 */
final class HttpsLicenseClient implements LicenseLeaseClient {
    private static final int MAXIMUM_TOKEN_BYTES = 64 * 1024;
    private final LicenseProperties properties;
    private final HttpClient client;

    HttpsLicenseClient(LicenseProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public String fetch(String nonce) throws IOException, InterruptedException {
        String body = "product=" + encode(properties.product()) + "&subject=" + encode(properties.subject())
                + "&instance=" + encode(properties.instance()) + "&nonce=" + encode(nonce);
        HttpRequest request = HttpRequest.newBuilder(properties.leaseEndpoint())
                .timeout(properties.requestTimeout()).header("Authorization", "Bearer " + credential())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/jwt")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var pending = client.sendAsync(request, HttpsLicenseClient::responseBody);
        HttpResponse<byte[]> response;
        try {
            response = pending.get(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException failure) {
            pending.cancel(true);
            throw new IOException("授权服务请求失败");
        } catch (ExecutionException failure) {
            pending.cancel(true);
            rethrowResponseFailure(failure);
            throw new AssertionError("已重新抛出响应失败");
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            throw interrupted;
        }
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /** 在读取响应体前确定拒绝、暂时不可用和响应格式，避免截断响应掩盖授权撤销。 */
    private static HttpResponse.BodySubscriber<byte[]> responseBody(HttpResponse.ResponseInfo response) {
        if (response.statusCode() >= 500 && response.statusCode() <= 599) {
            throw new CompletionException(new IOException("授权服务暂时不可用"));
        }
        if (response.statusCode() != 200) throw new LicenseException("授权服务拒绝续租");
        if (!response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim()
                .equalsIgnoreCase("application/jwt")) {
            throw new LicenseException("授权服务响应格式不符");
        }
        return new BoundedBody();
    }

    /** 保留异步 HTTP 客户端包装前的授权或暂时故障类别。 */
    private static void rethrowResponseFailure(ExecutionException failure) throws IOException {
        Throwable cause = failure;
        while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof LicenseException rejected) throw rejected;
        if (cause instanceof IOException unavailable) throw unavailable;
        throw new IOException("授权服务请求失败");
    }

    private String credential() throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(properties.credentialFile())) { bytes = input.readNBytes(8193); }
        String value = new String(bytes, StandardCharsets.US_ASCII).strip();
        if (bytes.length > 8192 || value.isEmpty() || !value.matches("[A-Za-z0-9._~+/=-]+")) {
            throw new LicenseException("授权凭据文件格式不符");
        }
        return value;
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    @Override public void close() { client.shutdownNow(); }

    /** 读取过程中限制真实字节数，不信任 Content-Length。 */
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAXIMUM_TOKEN_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new LicenseException("授权响应超过大小限制"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { body.completeExceptionally(failure); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
