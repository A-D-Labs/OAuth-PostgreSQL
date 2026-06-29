package com.template.OAuth.ratelimit;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the {@code app.security.rate-limiting.store} property selects exactly one
 * {@link RateLimitStore} bean — Caffeine by default, Redis only when explicitly opted in.
 * Uses {@link ApplicationContextRunner} so no real CacheManager / Redis is needed; the
 * Redis proxy manager is stubbed, proving the wiring without any Redis reachable
 * (ADR-0008).
 */
class RateLimitStoreSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(CacheManager.class, () -> mock(CacheManager.class))
            .withBean("rateLimitProxyManager", ProxyManager.class, () -> mock(ProxyManager.class))
            .withUserConfiguration(CaffeineRateLimitStore.class, RedisRateLimitStore.class);

    @Test
    void defaultsToCaffeineStore() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CaffeineRateLimitStore.class);
            assertThat(context).doesNotHaveBean(RedisRateLimitStore.class);
            assertThat(context.getBean(RateLimitStore.class)).isInstanceOf(CaffeineRateLimitStore.class);
        });
    }

    @Test
    void caffeineStoreWhenExplicitlySelected() {
        runner.withPropertyValues("app.security.rate-limiting.store=caffeine").run(context -> {
            assertThat(context).hasSingleBean(CaffeineRateLimitStore.class);
            assertThat(context).doesNotHaveBean(RedisRateLimitStore.class);
        });
    }

    @Test
    void redisStoreWhenSelected() {
        runner.withPropertyValues("app.security.rate-limiting.store=redis").run(context -> {
            assertThat(context).hasSingleBean(RedisRateLimitStore.class);
            assertThat(context).doesNotHaveBean(CaffeineRateLimitStore.class);
            assertThat(context.getBean(RateLimitStore.class)).isInstanceOf(RedisRateLimitStore.class);
        });
    }
}
