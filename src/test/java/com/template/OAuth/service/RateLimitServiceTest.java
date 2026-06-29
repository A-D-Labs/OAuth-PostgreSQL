package com.template.OAuth.service;

import com.template.OAuth.ratelimit.RateLimitStore;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {

    @Mock
    private RateLimitStore rateLimitStore;

    @Mock
    private Bandwidth authRateLimit;

    @Mock
    private Bandwidth sensitiveOperationsLimit;

    @InjectMocks
    private RateLimitService rateLimitService;

    private Bucket testBucket;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testBucket = Bucket.builder()
                .addLimit(Bandwidth.simple(10, java.time.Duration.ofMinutes(1)))
                .build();

        // Default: the store invokes the supplier and returns the created bucket.
        when(rateLimitStore.resolveBucket(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Supplier<Bucket> supplier = invocation.getArgument(2);
                    return supplier.get();
                });
    }

    @Test
    void testResolveBucketForAuthRequest_delegatesToStoreWithIpKey() {
        Bucket bucket = rateLimitService.resolveBucketForAuthRequest("127.0.0.1");

        assertNotNull(bucket);
        verify(rateLimitStore, times(1)).resolveBucket(eq("ipCache"), eq("127.0.0.1"), any());
    }

    @Test
    void testResolveBucketForAuthRequest_returnsBucketFromStore() {
        when(rateLimitStore.resolveBucket(eq("ipCache"), eq("127.0.0.1"), any()))
                .thenReturn(testBucket);

        Bucket bucket = rateLimitService.resolveBucketForAuthRequest("127.0.0.1");

        assertSame(testBucket, bucket);
    }

    @Test
    void testResolveBucketForSensitiveOperation_usesSensitiveKeySuffix() {
        Bucket bucket = rateLimitService.resolveBucketForSensitiveOperation("127.0.0.1");

        assertNotNull(bucket);
        verify(rateLimitStore, times(1)).resolveBucket(eq("ipCache"), eq("127.0.0.1:sensitive"), any());
    }

    @Test
    void testResolveBucketForUser_usesUserCache() {
        Bucket bucket = rateLimitService.resolveBucketForUser("alice");

        assertNotNull(bucket);
        verify(rateLimitStore, times(1)).resolveBucket(eq("userCache"), eq("alice"), any());
    }

    @Test
    void testResolveBucketForEndpoint_usesEndpointCache() {
        Bucket bucket = rateLimitService.resolveBucketForEndpoint("/api/login");

        assertNotNull(bucket);
        verify(rateLimitStore, times(1)).resolveBucket(eq("endpointCache"), eq("/api/login"), any());
    }

    @Test
    void testCheckRateLimit_Available() {
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(10, io.github.bucket4j.Refill.greedy(10, java.time.Duration.ofMinutes(1))))
                .build();

        long remainingTokens = rateLimitService.checkRateLimit(bucket, 1);

        assertTrue(remainingTokens >= 0);
    }

    @Test
    void testCheckRateLimit_Exhausted() {
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(1, io.github.bucket4j.Refill.greedy(1, java.time.Duration.ofMinutes(1))))
                .build();

        bucket.tryConsume(1);

        long remainingTokens = rateLimitService.checkRateLimit(bucket, 1);

        assertEquals(-1, remainingTokens);
    }
}
