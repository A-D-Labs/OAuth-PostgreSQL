package com.template.OAuth.ratelimit;

import io.github.bucket4j.Bucket;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Default {@link RateLimitStore}: buckets live in the in-process Caffeine-backed
 * {@link CacheManager} configured in {@code RateLimitingConfig}. This is the only
 * store active unless a Redis-backed alternative is enabled by profile (ADR-0008),
 * and it carries no per-request network latency.
 */
@Component
public class CaffeineRateLimitStore implements RateLimitStore {

    private final CacheManager cacheManager;

    public CaffeineRateLimitStore(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public Bucket resolveBucket(@NonNull String storeName, @NonNull String key,
            @NonNull Supplier<Bucket> bucketSupplier) {
        Cache cache = cacheManager.getCache(storeName);
        if (cache == null) {
            throw new IllegalStateException("Cache not found: " + storeName);
        }

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            return (Bucket) wrapper.get();
        }

        Bucket bucket = bucketSupplier.get();
        cache.put(key, bucket);
        return bucket;
    }
}
