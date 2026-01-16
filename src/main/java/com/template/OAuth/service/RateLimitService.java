package com.template.OAuth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.Callable;

@Service
public class RateLimitService {

    private final CacheManager cacheManager;
    private final Bandwidth authRateLimit;
    private final Bandwidth sensitiveOperationsLimit;

    @Autowired
    public RateLimitService(CacheManager cacheManager,
            Bandwidth authRateLimit,
            Bandwidth sensitiveOperationsLimit) {
        this.cacheManager = cacheManager;
        this.authRateLimit = authRateLimit;
        this.sensitiveOperationsLimit = sensitiveOperationsLimit;
    }

    /**
     * Get or create a rate limiter bucket for an IP address with authentication
     * limit
     */
    public Bucket resolveBucketForAuthRequest(@NonNull String ipAddress) {
        return getBucketFromCache("ipCache", ipAddress, () -> Bucket.builder()
                .addLimit(authRateLimit)
                .build());
    }

    /**
     * Get or create a rate limiter bucket for an IP address with sensitive
     * operation limit
     */
    public Bucket resolveBucketForSensitiveOperation(@NonNull String ipAddress) {
        return getBucketFromCache("ipCache", ipAddress + ":sensitive", () -> Bucket.builder()
                .addLimit(sensitiveOperationsLimit)
                .build());
    }

    /**
     * Get or create a rate limiter bucket for a specific user
     */
    public Bucket resolveBucketForUser(@NonNull String username) {
        return getBucketFromCache("userCache", username, () -> Bucket.builder()
                .addLimit(authRateLimit)
                .build());
    }

    /**
     * Get or create a rate limiter bucket for a specific endpoint
     */
    public Bucket resolveBucketForEndpoint(@NonNull String endpoint) {
        return getBucketFromCache("endpointCache", Objects.requireNonNull(endpoint), () -> Bucket.builder()
                .addLimit(authRateLimit)
                .build());
    }

    /**
     * Helper method to get or create a bucket from cache
     */
    private Bucket getBucketFromCache(@NonNull String cacheName, @NonNull String key, Callable<Bucket> bucketSupplier) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalStateException("Cache not found: " + cacheName);
        }

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            return (Bucket) wrapper.get();
        }

        try {
            Bucket bucket = bucketSupplier.call();
            cache.put(key, bucket);
            return bucket;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create rate limit bucket", e);
        }
    }

    /**
     * Check if a request can be processed under rate limits, returning remaining
     * tokens
     * 
     * @return remaining tokens, or -1 if rate limited
     */
    public long checkRateLimit(Bucket bucket, long tokensToConsume) {
        if (bucket.tryConsume(tokensToConsume)) {
            return bucket.getAvailableTokens();
        }
        return -1;
    }
}