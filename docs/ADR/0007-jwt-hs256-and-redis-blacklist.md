# ADR 0007 — JWT HS256 + Redis token blacklist



- Status: Accepted

- Date: 2026-06-18

- Context owner: auth-gateway + bff-ops

- Related ADRs: [0004](0004-single-db-vs-database-per-service.md) (schema `auth`)



## Context



PayPulse нужен API auth для Spring Cloud Gateway и resource servers (`bff-ops`, downstream services) с:



- access JWT + refresh rotation / reuse detection;

- logout и logout-all до истечения TTL access token;

- SSE из браузера (`EventSource`), который **не** умеет ставить `Authorization` header.



Варианты подписи / сессий:



- **RS256 / ES256** + JWKS + key rotation — production-grade, больше moving parts.

- **HS256** shared secret — просто для monorepo Compose demo.

- **Opaque sessions только в Redis** — gateway stateful на каждый request lookup.



Без blacklist access JWT остаётся валидным до `exp` после logout — неприемлемо для S1.5 hardening.



## Decision



1. **Issuance / validation**: `auth-gateway` выдаёт **HS256** access JWT через `jjwt` (`JwtService`, `SIG.HS256`); secret из конфига (`JwtProperties` / env).

2. **Refresh**: строки в Postgres schema `auth` (`liquibase/changelog/005-auth-schema.xml`) — family, fingerprint, rotation; reuse detection invalidates family.

3. **Revocation**: optional **Redis blacklist** по `jti`, ключ `blacklist:{jti}`, TTL = оставшееся время жизни access token.

   - Service: `TokenBlacklistService` (`@ConditionalOnProperty` `paypulse.auth.token-blacklist-enabled=true`).

   - Decoder wrap: `BlacklistingReactiveJwtDecoder` вокруг `NimbusReactiveJwtDecoder`.

   - Docker Compose: blacklist **enabled**; local default может быть `false`.

4. **BFF**: `bff-ops` — resource server с тем же HS256 secret (`SecurityConfiguration` / `MacAlgorithm.HS256`).

5. **SSE**: `SseTokenWebFilter` принимает `?token=` (query) потому что `EventSource` не шлёт Bearer; filter поднимает SecurityContext. Live routes auth в BFF; gateway пропускает `/api/live/**` pattern согласно routing notes.



Не внедряем JWKS endpoint и asymmetric keys в MVP.



## Consequences



### Positive



- Один shared secret в Compose env — быстрый E2E login → API → SSE.

- Logout-all / revoke работает до `exp` при включённом Redis.

- Refresh rotation + fingerprint дают reuse detection без полного session store для access tokens.

- Согласовано с demo scripts (`docs/demo/auth.http`).



### Negative / accepted limitations



- HS256: любой verifier должен знать тот же secret (gateway, BFF); компрометация secret = forge tokens.

- Нет per-service key rotation / JWKS caching story.

- Blacklist best-effort: зависит от Redis availability; при `enabled=false` revoke access до `exp` не работает.

- `?token=` в query для SSE может утечь в access logs / Referer — accepted for demo; prefer short-lived access JWT.

- Не полный server-side session store: нет «list all sessions» кроме refresh rows + blacklist JTIs.



## Alternatives considered



1. **RS256 + JWKS** — preferred for production; deferred (keystore, rotation job, gateway JWK fetch).

2. **Opaque access tokens in Redis only** — rejected как default: ломает простой resource-server JWT filter и увеличивает Redis на каждый request.

3. **No blacklist** (ждать `exp`) — rejected после logout-all / hardening requirements (S1.5).

4. **Cookie httpOnly session for SPA** — possible later; текущий SPA хранит bearer и refresh flow явно для portfolio clarity.



## Code pointers



| Area | Path |

|------|------|

| JWT issue / HS256 | `auth-gateway/src/main/kotlin/.../auth/JwtService.kt` |

| Properties | `auth-gateway/.../config/JwtProperties.kt` |

| Blacklist | `auth-gateway/.../auth/TokenBlacklistService.kt` |

| Decoder wrap | `auth-gateway/.../config/BlacklistingReactiveJwtDecoder.kt` |

| Auth schema | `liquibase/changelog/005-auth-schema.xml` |

| BFF resource server | `bff-ops/.../config/SecurityConfiguration.kt` |

| SSE query token | `bff-ops/.../config/SseTokenWebFilter.kt`, test `SseTokenWebFilterTest.kt` |

| UI auth | `ops-dashboard-ui/src/hooks/useAuth.ts`, `stores/authStore.ts`, `api/authClient.ts` |



## See also / Revisit



- [ADR 0004](0004-single-db-vs-database-per-service.md) — `auth` schema на общем Postgres.

- [ADR 0006](0006-analytics-split-superset-vs-react.md) — Ops UI login path.

- Demo: [`docs/demo/auth.http`](../demo/auth.http).



**Revisit triggers**



- External IdP / multi-service verification → migrate to **RS256 + JWKS** (or OIDC).

- Cookie-based BFF-for-frontend → reconsider query-string SSE token.

- Hard requirement list/revoke sessions centrally → enrich refresh store UI + always-on blacklist.

- Secret sprawl across many verifiers → asymmetric keys become cheaper than distributing HS256.
