# ADR 0007 — JWT HS256 + Redis blacklist токенов

- Статус: Принят
- Дата: 2026-06-18
- Владелец контекста: auth-gateway + bff-ops
- Связанные ADR: [0004](0004-single-db-vs-database-per-service.md) (schema `auth`)

## Контекст

PayPulse нужен API auth для Spring Cloud Gateway и resource servers (`bff-ops`, downstream) с:

- access JWT + refresh rotation / reuse detection;
- logout и logout-all до истечения TTL access token;
- SSE из браузера (`EventSource`), который **не** умеет ставить `Authorization` header.

Варианты подписи / сессий:

- **RS256 / ES256** + JWKS + key rotation — production-grade, больше moving parts;
- **HS256** shared secret — просто для monorepo Compose demo;
- **Opaque sessions только в Redis** — gateway stateful на каждый request lookup.

Без blacklist access JWT остаётся валидным до `exp` после logout — неприемлемо для demo hardening (logout / logout-all).

## Решение

1. **Выдача / валидация**: `auth-gateway` выдаёт **HS256** access JWT через `jjwt` (`JwtService`, `SIG.HS256`); secret из конфига (`JwtProperties` / env).
2. **Refresh**: строки в Postgres schema `auth` (`liquibase/changelog/005-auth-schema.xml`) — family, fingerprint, rotation; reuse detection инвалидирует family.
3. **Revocation**: опциональный **Redis blacklist** по `jti`, ключ `blacklist:{jti}`, TTL = оставшееся время жизни access token.
   - Сервис: `TokenBlacklistService` (`@ConditionalOnProperty` `paypulse.auth.token-blacklist-enabled=true`).
   - Decoder wrap: `BlacklistingReactiveJwtDecoder` вокруг `NimbusReactiveJwtDecoder`.
   - Docker Compose: blacklist **включён**; локальный default может быть `false`.
4. **BFF**: `bff-ops` — resource server с тем же HS256 secret (`SecurityConfiguration` / `MacAlgorithm.HS256`).
5. **SSE**: `SseTokenWebFilter` принимает `?token=` (query), потому что `EventSource` не шлёт Bearer; filter поднимает SecurityContext. Gateway пропускает `/api/live/**` согласно routing.

JWKS endpoint и asymmetric keys в MVP **не** внедряем.

## Последствия

### Плюсы

- Один shared secret в Compose env — быстрый E2E login → API → SSE.
- Logout-all / revoke работает до `exp` при включённом Redis.
- Refresh rotation + fingerprint дают reuse detection без полного session store для access tokens.
- Согласовано с demo scripts (`docs/demo/auth.http`).

### Минусы / принятые ограничения

- HS256: любой verifier должен знать тот же secret; компрометация secret = forge tokens.
- Нет per-service key rotation / JWKS caching story.
- Blacklist best-effort: зависит от Redis; при `enabled=false` revoke access до `exp` не работает.
- `?token=` в query для SSE может утечь в access logs / Referer — accepted for demo; предпочтителен short-lived access JWT.
- Не полный server-side session store: нет «list all sessions» кроме refresh rows + blacklist JTI.

## Альтернативы

1. **RS256 + JWKS** — предпочтительно для production; отложено (keystore, rotation job, gateway JWK fetch).
2. **Opaque access tokens только в Redis** — отклонено как default: ломает простой resource-server JWT filter и увеличивает Redis на каждый request.
3. **Без blacklist** (ждать `exp`) — отклонено после требований logout-all / hardening.
4. **Cookie httpOnly session для SPA** — возможно позже; сейчас SPA хранит bearer и refresh явно для ясности портфолио.

## Указатели в коде

| Область | Путь |
|---------|------|
| JWT issue / HS256 | `auth-gateway/src/main/kotlin/.../auth/JwtService.kt` |
| Properties | `auth-gateway/.../config/JwtProperties.kt` |
| Blacklist | `auth-gateway/.../auth/TokenBlacklistService.kt` |
| Decoder wrap | `auth-gateway/.../config/BlacklistingReactiveJwtDecoder.kt` |
| Auth schema | `liquibase/changelog/005-auth-schema.xml` |
| BFF resource server | `bff-ops/.../config/SecurityConfiguration.kt` |
| SSE query token | `bff-ops/.../config/SseTokenWebFilter.kt`, тест `SseTokenWebFilterTest.kt` |
| UI auth | `ops-dashboard-ui/src/hooks/useAuth.ts`, `stores/authStore.ts`, `api/authClient.ts` |

## См. также / когда пересмотреть

- [ADR 0004](0004-single-db-vs-database-per-service.md) — schema `auth` на общем Postgres.
- [ADR 0006](0006-analytics-split-superset-vs-react.md) — login path Ops UI.
- Demo: [`docs/demo/auth.http`](../demo/auth.http).

**Триггеры пересмотра**

- External IdP / multi-service verification → миграция на **RS256 + JWKS** (или OIDC).
- Cookie-based BFF-for-frontend → пересмотреть query-string SSE token.
- Жёсткое требование list/revoke sessions централизованно → расширить refresh store UI + always-on blacklist.
- Secret sprawl между многими verifiers → asymmetric keys дешевле, чем раздача HS256.
