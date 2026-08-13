package io.github.guanxiangkai.redis.plus.ratelimit.spi;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registry for named rate-limit algorithms.
 */
public class RateLimitAlgorithmRegistry {

    private final Map<String, RateLimitAlgorithm> algorithms;

    public RateLimitAlgorithmRegistry(Collection<RateLimitAlgorithm> algorithms) {
        Map<String, RateLimitAlgorithm> registered = new LinkedHashMap<>();
        if (algorithms != null) {
            for (RateLimitAlgorithm algorithm : algorithms) {
                String name = normalize(algorithm.algorithmName());
                RateLimitAlgorithm previous = registered.putIfAbsent(name, algorithm);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate rate-limit algorithm: " + name);
                }
            }
        }
        this.algorithms = Map.copyOf(registered);
    }

    public RateLimitAlgorithm getRequired(String algorithmName) {
        RateLimitAlgorithm algorithm = algorithms.get(normalize(algorithmName));
        if (algorithm == null) {
            throw new IllegalArgumentException("Unknown rate-limit algorithm: " + algorithmName);
        }
        return algorithm;
    }

    private static String normalize(String algorithmName) {
        if (algorithmName == null || algorithmName.isBlank()) {
            throw new IllegalArgumentException("algorithmName must not be blank");
        }
        return algorithmName.trim().toUpperCase(Locale.ROOT);
    }
}
