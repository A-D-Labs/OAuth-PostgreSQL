package com.template.OAuth.service;

import com.template.OAuth.ratelimit.RateLimitStore;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {

    @Mock
    private RateLimitStore rateLimitStore;

    private final Bandwidth authRateLimit =
            Bandwidth.classic(10, io.github.bucket4j.Refill.greedy(10, java.time.Duration.ofMinutes(1)));
    private final Bandwidth sensitiveOperationsLimit =
            Bandwidth.classic(3, io.github.bucket4j.Refill.greedy(3, java.time.Duration.ofMinutes(1)));

    private RateLimitService rateLimitService;

    private Bucket testBucket;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Construct manually: @InjectMocks would only inject the mocked store, leaving the
        // real Bandwidth collaborators null. We want the real bandwidths so the captured
        // BucketConfiguration carries actual capacities.
        rateLimitService = new RateLimitService(rateLimitStore, authRateLimit, sensitiveOperationsLimit);

        testBucket = Bucket.builder()
                .addLimit(Bandwidth.simple(10, java.time.Duration.ofMinutes(1)))
                .build();

        // Default: the store materialises a local bucket from the supplied configuration.
        when(rateLimitStore.resolveBucket(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Supplier<BucketConfiguration> supplier = invocation.getArgument(2);
                    BucketConfiguration config = supplier.get();
                    var builder = Bucket.builder();
                    for (Bandwidth bw : config.getBandwidths()) {
                        builder.addLimit(bw);
                    }
                    return builder.build();
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
    void testResolveBucketForAuthRequest_suppliesAuthLimitConfiguration() {
        rateLimitService.resolveBucketForAuthRequest("127.0.0.1");

        // The supplier handed to the store must carry the auth bandwidth (10 tokens).
        BucketConfiguration config = capturedConfig("ipCache", "127.0.0.1");
        assertEquals(1, config.getBandwidths().length);
        assertEquals(10, config.getBandwidths()[0].getCapacity());
    }

    @Test
    void testResolveBucketForSensitiveOperation_usesSensitiveKeyAndLimit() {
        rateLimitService.resolveBucketForSensitiveOperation("127.0.0.1");

        BucketConfiguration config = capturedConfig("ipCache", "127.0.0.1:sensitive");
        assertEquals(3, config.getBandwidths()[0].getCapacity());
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

    @SuppressWarnings("unchecked")
    private BucketConfiguration capturedConfig(String store, String key) {
        org.mockito.ArgumentCaptor<Supplier<BucketConfiguration>> captor =
                org.mockito.ArgumentCaptor.forClass(Supplier.class);
        verify(rateLimitStore).resolveBucket(eq(store), eq(key), captor.capture());
        return captor.getValue().get();
    }
}
