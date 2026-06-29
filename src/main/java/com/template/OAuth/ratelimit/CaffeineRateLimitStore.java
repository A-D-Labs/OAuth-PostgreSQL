package com.template.OAuth.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Default {@link RateLimitStore}: buckets live in the in-process Caffeine-backed
 * {@link CacheManager} configured in {@code RateLimitingConfig}. Active unless
 * {@code app.security.rate-limiting.store=redis} selects {@link RedisRateLimitStore}
 * (ADR-0008). Carries no per-request network latency.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limiting.store", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineRateLimitStore implements RateLimitStore {

    private final CacheManager cacheManager;

    public CaffeineRateLimitStore(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public Bucket resolveBucket(@NonNull String storeName, @NonNull String key,
            @NonNull Supplier<BucketConfiguration> configSupplier) {
        Cache cache = cacheManager.getCache(storeName);
        if (cache == null) {
            throw new IllegalStateException("Cache not found: " + storeName);
        }

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            return (Bucket) wrapper.get();
        }

        Bucket bucket = buildLocalBucket(configSupplier.get());
        cache.put(key, bucket);
        return bucket;
    }

    private Bucket buildLocalBucket(BucketConfiguration configuration) {
        LocalBucketBuilder builder = Bucket.builder();
        for (Bandwidth bandwidth : configuration.getBandwidths()) {
            builder.addLimit(bandwidth);
        }
        return builder.build();
    }
}
