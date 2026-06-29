# Redis for rate-limit state only — sessions stay in PostgreSQL

**Status:** accepted (2026-06-25) · robustness Tier 3a.

Rate-limit state is in-memory per-instance, which blocks running more than one replica (each instance has its own counters). This ADR introduces Redis as an **optional, profile-activated** backing store for rate-limit buckets — and deliberately scopes Redis to that role only.

## Decision

- A `RateLimitStore` abstraction sits behind `RateLimitService`. **Default: Caffeine in-memory** (zero new infra, unchanged latency, the `test` profile stays in-memory per ADR-0003 so the suite runs on the headless box). A **`redis` profile/property** swaps in a Redis-backed bucket store (`bucket4j-redis`, already in the pom).
- **Session / refresh-token state does NOT move to Redis** (Option B rejected). Sessions must be durable (a Redis flush or restart must not log everyone out) and queryable for "list my sessions" (ADR-0007) — both are natural in PostgreSQL and awkward/fragile in Redis. Keeping sessions in Postgres also avoids putting Redis on any auth-critical path.
- Redis is therefore a horizontal-scale convenience for rate limiting, not a correctness dependency. Single-instance and dev/test run exactly as before with no Redis reachable.

## Performance

Default profile is unchanged (in-process Caffeine). Redis, when enabled, is consulted only by the rate-limit filter (already off the JWT validation fast-path), so the stateless auth path keeps its latency. This satisfies the operator's no-regression condition.

## Consequences

- Operators can scale to >1 replica with consistent rate limiting by enabling the `redis` store + pointing at a Redis instance.
- No Redis dependency for auth correctness; sessions remain Postgres-durable (ADR-0007).
- The existing `spring-boot-starter-data-redis` + `bucket4j-redis` scaffolding is now actually wired behind the abstraction.

## Enabling Redis for a multi-replica deploy

The store is selected by one property, `app.security.rate-limiting.store` (env `RATE_LIMIT_STORE`), defaulting to `caffeine`. To run more than one replica with shared throttling:

1. Set `RATE_LIMIT_STORE=redis` on **every** replica. This activates `RedisRateLimitStore` and `RedisRateLimitConfig`; `CaffeineRateLimitStore` is switched off by the same condition (so exactly one `RateLimitStore` bean exists).
2. Point the standard Spring Redis properties at your instance — `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` (already wired in `application.yaml` under `spring.data.redis.*`).
3. Leave `RATE_LIMIT_STORE` unset (or `caffeine`) for single-instance and for `dev`/`test` — those run in-process with no Redis reachable (ADR-0003). The test suite never sets it, so CI needs no Redis.

Bucket keys are namespaced `rate-limit:<store>:<key>` in Redis and expire one hour after last write (a full refill window), so abandoned keys are reclaimed automatically. Only the rate-limit filter path touches Redis; the JWT validation fast-path is untouched.
