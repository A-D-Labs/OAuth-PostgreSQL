package com.template.OAuth.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CaffeineRateLimitStoreTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private Cache.ValueWrapper valueWrapper;

    private CaffeineRateLimitStore store;

    private Supplier<BucketConfiguration> config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        store = new CaffeineRateLimitStore(cacheManager);
        config = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(10, java.time.Duration.ofMinutes(1)))
                .build();
    }

    @Test
    void resolveBucket_buildsLocalBucketAndStoresWhenAbsent() {
        when(cacheManager.getCache("ipCache")).thenReturn(cache);
        when(cache.get("127.0.0.1")).thenReturn(null);

        Bucket bucket = store.resolveBucket("ipCache", "127.0.0.1", config);

        // A real local bucket built from the configuration (10-token capacity).
        assertNotNull(bucket);
        assertEquals(10, bucket.getAvailableTokens());
        verify(cache, times(1)).get("127.0.0.1");
        verify(cache, times(1)).put(eq("127.0.0.1"), any(Bucket.class));
    }

    @Test
    void resolveBucket_returnsExistingWithoutRecreating() {
        Bucket existing = Bucket.builder()
                .addLimit(Bandwidth.simple(5, java.time.Duration.ofMinutes(1)))
                .build();
        when(cacheManager.getCache("ipCache")).thenReturn(cache);
        when(cache.get("127.0.0.1")).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(existing);

        Bucket bucket = store.resolveBucket("ipCache", "127.0.0.1",
                () -> fail("config supplier must not run when bucket already cached"));

        assertSame(existing, bucket);
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void resolveBucket_throwsWhenCacheMissing() {
        when(cacheManager.getCache("missing")).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> store.resolveBucket("missing", "k", config));
    }
}
