package com.template.OAuth.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;

import java.util.function.Supplier;

/**
 * Abstraction over the storage that holds rate-limit buckets.
 *
 * <p>The default implementation ({@link CaffeineRateLimitStore}) keeps buckets in an
 * in-process Caffeine cache, preserving today's behaviour exactly. The
 * {@link RedisRateLimitStore} implementation activates via the
 * {@code app.security.rate-limiting.store=redis} property and backs buckets with Redis
 * so several replicas share one set of counters (ADR-0008). Neither
 * {@code RateLimitService} nor any caller changes when the store is swapped.
 *
 * <p>The bucket is described by a {@link BucketConfiguration} rather than a pre-built
 * {@link Bucket}: a local Caffeine bucket and a distributed Redis-proxy bucket are
 * constructed differently, but both are built from the same configuration.
 */
public interface RateLimitStore {

    /**
     * Return the bucket stored under {@code key} in the named store, creating and
     * persisting it from {@code configSupplier} if absent.
     *
     * @param storeName logical store/namespace (e.g. {@code "ipCache"})
     * @param key       bucket key within the store
     * @param configSupplier factory for the bucket configuration, consulted only when
     *                       no bucket exists yet
     * @return the existing or newly created bucket
     */
    Bucket resolveBucket(String storeName, String key, Supplier<BucketConfiguration> configSupplier);
}
