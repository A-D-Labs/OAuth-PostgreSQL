package com.template.OAuth.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the Redis-backed rate-limit proxy manager. Loaded ONLY when
 * {@code app.security.rate-limiting.store=redis} (ADR-0008); in every other profile —
 * including {@code test} (ADR-0003) — these beans are absent and no Redis connection is
 * ever opened, so the suite runs with no Redis reachable.
 *
 * <p>Connection settings are read from the standard Spring {@code spring.data.redis.*}
 * properties already present in {@code application.yaml}.
 */
@Configuration
@ConditionalOnProperty(name = "app.security.rate-limiting.store", havingValue = "redis")
public class RedisRateLimitConfig {

    /** Bucket TTL in Redis: long enough to outlive a full refill window, then reclaimed. */
    private static final Duration BUCKET_TTL = Duration.ofHours(1);

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean ssl) {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withSsl(ssl);
        if (password != null && !password.isBlank()) {
            uri.withPassword(password.toCharArray());
        }
        return RedisClient.create(uri.build());
    }

    @Bean
    public ProxyManager<byte[]> rateLimitProxyManager(RedisClient rateLimitRedisClient) {
        StatefulRedisConnection<byte[], byte[]> connection =
                rateLimitRedisClient.connect(ByteArrayCodec.INSTANCE);
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(BUCKET_TTL))
                .build();
    }
}
