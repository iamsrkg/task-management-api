# Secure Multi-Tenant Task Management API

[![CI](https://github.com/iamsrkg/task-management-api/actions/workflows/ci.yml/badge.svg)](https://github.com/iamsrkg/task-management-api/actions/workflows/ci.yml)

A REST API for projects and tasks where every user sees only their own data. It's built to show the patterns a real multi-tenant backend needs:
- stateless JWT auth with role-based access
- tenant-scoped queries
- optimistic locking for concurrent edits
- rate limiting
- one consistent error format

**Stack:** Java 21 · Spring Boot 4 · Spring Security 7 · Spring Data JPA / Hibernate · PostgreSQL 15 · JJWT · Docker · JUnit 5 + MockMvc · GitHub Actions

---

## Design decisions

| Concern | Decision | Why |
|---|---|---|
| **Tenant isolation** | The tenant filter is part of the query (`WHERE id = ? AND (owner = ? OR assignee = ?)`) | Another tenant's task returns **404**, the same as a task that doesn't exist, so the API never confirms another tenant's data exists. It also avoids fetch-then-check bugs. |
| **Permissions** | Owners edit and delete. Assignees can see a task and change its status. | Visible but not permitted is an honest **403**. |
| **Concurrent edits** | JPA `@Version` + a required `version` on `PUT` | A stale write gets **409 Conflict** instead of silently overwriting someone else's change. |
| **Auth** | Stateless JWT (HS256). BCrypt password hashing. RBAC via `@PreAuthorize`. | Invalid, expired or tampered tokens get **401**. Wrong credentials get **401** with the same message for unknown email and wrong password (no account enumeration). |
| **Rate limiting** | Token bucket per client IP that runs *before* authentication | Rejected requests never reach BCrypt or the database. Responses carry **429** + `Retry-After` and `X-RateLimit-Remaining`. |
| **Errors** | One `{success, message, data}` envelope from controllers *and* security filters | Clients parse errors one way. Unexpected exceptions are logged server-side and never leaked. |
| **Input safety** | Sort fields are whitelisted and page size is capped. DTOs are used everywhere. | No arbitrary property access. Entities (and password hashes) never reach the wire. |
| **Traceability** | `X-Request-Id` on every response and in every log line (MDC) | One search follows a request end to end. |
| **Config** | Secrets come from environment variables | The dev defaults in `application.properties` are for local use only. |

## Run it

**With Docker** (API + PostgreSQL):
```bash
git clone https://github.com/iamsrkg/task-management-api.git
cd task-management-api
docker compose up --build
```
The API waits for PostgreSQL to be healthy, then listens on `http://localhost:8080`. Check it with `GET /actuator/health`.

For anything beyond local development, copy `.env.example` to `.env` and set `JWT_SECRET` (`openssl rand -base64 32`) and `ADMIN_PASSWORD`.

**Tests** (no database needed, they use in-memory H2):
```bash
./mvnw test
```

## API

All endpoints except auth and health need `Authorization: Bearer <token>`.

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/auth/register` | `{email, password}` → `{token}` |
| `POST` | `/api/auth/login` | `{email, password}` → `{token}`. 401 on bad credentials |
| `GET` | `/api/users/me` | Current user (never includes the password hash) |
| `GET` | `/api/admin/users` | **ADMIN only**. 403 for other roles |
| `POST` | `/api/projects` | `{name, description?}` |
| `GET` | `/api/projects` | Your projects |
| `POST` | `/api/tasks` | `{title, projectId, description?, status?, assigneeId?}`. The project must be yours |
| `GET` | `/api/tasks?status=&page=&size=&sort=` | Tasks you own or are assigned. `sort` ∈ `createdAt, updatedAt, title, status` (`,asc`/`,desc`). `size` ≤ 100 |
| `GET` | `/api/tasks/{id}` | 404 if it isn't visible to you |
| `PUT` | `/api/tasks/{id}` | Owner only. **Requires `version`**, 409 if stale |
| `PATCH` | `/api/tasks/{id}/status?status=DONE` | Owner or assignee |
| `DELETE` | `/api/tasks/{id}` | Owner only |
| `GET` | `/actuator/health` | Public health check |

The first run seeds an admin (`admin@example.com` / `admin123` locally, or set `ADMIN_EMAIL` / `ADMIN_PASSWORD`).

### Example: optimistic locking
```http
PUT /api/tasks/6f1c…    {"title": "Cache FX rates", "projectId": "…", "version": 3}

HTTP/1.1 409 Conflict
{"success": false, "message": "Conflict: This record was updated by another user or process. Please refresh and try again.", "data": null}
```

## Tests

`ApiIntegrationTests` exercises the real HTTP contract through MockMvc:
- **Auth:** 401 without a token, with a tampered or garbage token, and with a wrong password. 403 for a user calling the admin API.
- **Tenant isolation:** another tenant's task returns 404 on read, update and delete. You can't add tasks to someone else's project. Listings are scoped.
- **Optimistic locking:** a stale version returns 409, and a missing version returns 400.
- **Validation:** field errors, malformed JSON, unknown sort fields and non-UUID ids all return 400.
- **Leaks:** no response ever contains a password hash.
- **Ops:** public health check, and `X-Request-Id` generated or echoed.

`RateLimitFilterTest` covers the token bucket (429 + `Retry-After`, with separate buckets per client).

## Not in scope (and what I'd do next)
- **Schema migrations:** it uses `ddl-auto=update` for simplicity. Production would use Flyway.
- **Distributed rate limiting:** buckets are in memory per instance. Multiple replicas need Redis or gateway-level limits.
- **Refresh tokens and revocation:** access tokens are short-lived (1h by default) with no revocation list.
