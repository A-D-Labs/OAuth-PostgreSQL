package com.template.OAuth.service;

import com.template.OAuth.ratelimit.RateLimitStore;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class RateLimitService {

    private final RateLimitStore rateLimitStore;
    private final Bandwidth authRateLimit;
    private final Bandwidth sensitiveOperationsLimit;

    @Autowired
    public RateLimitService(RateLimitStore rateLimitStore,
            Bandwidth authRateLimit,
            Bandwidth sensitiveOperationsLimit) {
        this.rateLimitStore = rateLimitStore;
        this.authRateLimit = authRateLimit;
        this.sensitiveOperationsLimit = sensitiveOperationsLimit;
    }

    /**
     * Get or create a rate limiter bucket for an IP address with authentication
     * limit
     */
    public Bucket resolveBucketForAuthRequest(@NonNull String ipAddress) {
        return rateLimitStore.resolveBucket("ipCache", ipAddress, () -> BucketConfiguration.builder()
                .addLimit(authRateLimit)
                .build());
    }

    /**
     * Get or create a rate limiter bucket for an IP address with sensitive
     * operation limit
     */
    public Bucket resolveBucketForSensitiveOperation(@NonNull String ipAddress) {
        return rateLimitStore.resolveBucket("ipCache", ipAddress + ":sensitive", () -> BucketConfiguration.builder()
                .addLimit(sensitiveOperationsLimit)
                .build());
    }

    /**
     * Get or create a rate limiter bucket for a specific user
     */
    public Bucket resolveBucketForUser(@NonNull String username) {
        return rateLimitStore.resolveBucket("userCache", username, () -> BucketConfiguration.builder()
                .addLimit(authRateLimit)
                .build());
    }

    /**
     * Get or create a rate limiter bucket for a specific endpoint
     */
    public Bucket resolveBucketForEndpoint(@NonNull String endpoint) {
        return rateLimitStore.resolveBucket("endpointCache", Objects.requireNonNull(endpoint),
                () -> BucketConfiguration.builder()
                        .addLimit(authRateLimit)
                        .build());
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