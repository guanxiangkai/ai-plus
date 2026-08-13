package io.github.guanxiangkai.web.plus.web.client;

import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 外部 HTTP 调用客户端
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class ExternalHttpClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * GET 文本请求
     */
    public String getText(String url, Map<String, ?> queryParams, Duration timeout) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(url);
        if (!CollectionUtils.isEmpty(queryParams)) {
            queryParams.forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                if (value instanceof Iterable<?> iterable) {
                    uriBuilder.queryParam(key, iterable);
                    return;
                }
                if (value.getClass().isArray()) {
                    uriBuilder.queryParam(key, (Object[]) value);
                    return;
                }
                uriBuilder.queryParam(key, value);
            });
        }
        return webClientBuilder.build()
                .get()
                .uri(uriBuilder.build(true).toUri())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .block(timeout);
    }

    /**
     * GET 文本请求
     */
    public String getText(String url, Duration timeout) {
        return getText(url, Map.of(), timeout);
    }
}
