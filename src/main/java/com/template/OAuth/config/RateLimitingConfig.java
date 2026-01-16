package com.template.OAuth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.Objects;

@Configuration
@EnableCaching
public class RateLimitingConfig {

    private final AppProperties appProperties;

    public RateLimitingConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("ipCache", "userCache", "endpointCache");
        cacheManager.setCaffeine(Objects.requireNonNull(Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(10000)
                .recordStats()));
        return cacheManager;
    }

    /**
     * Creates a bucket for general API rate limiting
     */
    @Bean
    public Bucket apiTokenBucket() {
        int limit = appProperties.getSecurity().getRateLimiting().getDefaultLimit();
        Bandwidth limitBandwidth = Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limitBandwidth).build();
    }

    /**
     * Creates a bucket configuration for authentication endpoints
     */
    @Bean
    public Bandwidth authRateLimit() {
        int limit = appProperties.getSecurity().getRateLimiting().getAuthLimit();
        return Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofMinutes(1)));
    }

    /**
     * Creates a bucket configuration for sensitive operations
     */
    @Bean
    public Bandwidth sensitiveOperationsLimit() {
        int limit = appProperties.getSecurity().getRateLimiting().getSensitiveLimit();
        return Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofMinutes(1)));
    }
}