package com.template.OAuth.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CaffeineRateLimitStoreTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private Cache.ValueWrapper valueWrapper;

    private CaffeineRateLimitStore store;

    private Bucket testBucket;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        store = new CaffeineRateLimitStore(cacheManager);
        testBucket = Bucket.builder()
                .addLimit(Bandwidth.simple(10, java.time.Duration.ofMinutes(1)))
                .build();
    }

    @Test
    void resolveBucket_createsAndStoresWhenAbsent() {
        when(cacheManager.getCache("ipCache")).thenReturn(cache);
        when(cache.get("127.0.0.1")).thenReturn(null);

        Bucket bucket = store.resolveBucket("ipCache", "127.0.0.1", () -> testBucket);

        assertSame(testBucket, bucket);
        verify(cache, times(1)).get("127.0.0.1");
        verify(cache, times(1)).put("127.0.0.1", testBucket);
    }

    @Test
    void resolveBucket_returnsExistingWithoutRecreating() {
        when(cacheManager.getCache("ipCache")).thenReturn(cache);
        when(cache.get("127.0.0.1")).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testBucket);

        Bucket bucket = store.resolveBucket("ipCache", "127.0.0.1",
                () -> fail("supplier must not run when bucket already cached"));

        assertSame(testBucket, bucket);
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void resolveBucket_throwsWhenCacheMissing() {
        when(cacheManager.getCache("missing")).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> store.resolveBucket("missing", "k", () -> testBucket));
    }
}
