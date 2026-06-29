package com.template.OAuth.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Redis-backed {@link RateLimitStore}: bucket state lives in Redis so multiple
 * application replicas share one set of rate-limit counters. Activated only when
 * {@code app.security.rate-limiting.store=redis} (ADR-0008); otherwise
 * {@link CaffeineRateLimitStore} is used and Redis is never contacted.
 *
 * <p>Buckets are proxied through bucket4j's {@link ProxyManager}; the manager itself
 * does the get-or-create against Redis atomically, so this store only has to derive a
 * stable Redis key and hand over the configuration. Redis is consulted only on the
 * rate-limit filter path, never on the JWT validation fast-path.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limiting.store", havingValue = "redis")
public class RedisRateLimitStore implements RateLimitStore {

    private final ProxyManager<byte[]> proxyManager;

    public RedisRateLimitStore(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public Bucket resolveBucket(@NonNull String storeName, @NonNull String key,
            @NonNull Supplier<BucketConfiguration> configSupplier) {
        byte[] redisKey = redisKey(storeName, key);
        return proxyManager.builder().build(redisKey, configSupplier);
    }

    private byte[] redisKey(String storeName, String key) {
        return ("rate-limit:" + storeName + ":" + key).getBytes(StandardCharsets.UTF_8);
    }
}
