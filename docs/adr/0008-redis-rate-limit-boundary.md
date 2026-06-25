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

- Operators can scale to >1 replica with consistent rate limiting by enabling the `redis` profile + pointing at a Redis instance.
- No Redis dependency for auth correctness; sessions remain Postgres-durable (ADR-0007).
- The existing `spring-boot-starter-data-redis` + `bucket4j-redis` scaffolding is now actually wired behind the abstraction.
