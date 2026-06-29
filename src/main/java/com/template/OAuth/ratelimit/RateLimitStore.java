package com.template.OAuth.ratelimit;

import io.github.bucket4j.Bucket;

import java.util.function.Supplier;

/**
 * Abstraction over the storage that holds rate-limit buckets.
 *
 * <p>The default implementation ({@link CaffeineRateLimitStore}) keeps buckets in an
 * in-process Caffeine cache, preserving today's behaviour exactly. A Redis-backed
 * implementation can be swapped in behind this interface without touching
 * {@code RateLimitService} or any caller (ADR-0008).
 */
public interface RateLimitStore {

    /**
     * Return the bucket stored under {@code key} in the named store, creating and
     * persisting it via {@code bucketSupplier} if absent.
     *
     * @param storeName logical store/namespace (e.g. {@code "ipCache"})
     * @param key       bucket key within the store
     * @param bucketSupplier factory invoked only when no bucket exists yet
     * @return the existing or newly created bucket
     */
    Bucket resolveBucket(String storeName, String key, Supplier<Bucket> bucketSupplier);
}
