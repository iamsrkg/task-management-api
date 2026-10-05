# Secure Multi-Tenant Task Management API

[![CI](https://github.com/iamsrkg/task-management-api/actions/workflows/ci.yml/badge.svg)](https://github.com/iamsrkg/task-management-api/actions/workflows/ci.yml)

A REST API for projects and tasks where every user sees only their own data. It's built to show the patterns a real multi-tenant backend needs:
- stateless JWT auth with role-based access
- tenant-scoped queries
- optimistic locking for concurrent edits
- rate limiting
- one consistent error format

**Stack:** Java 21 · Spring Boot 4 · Spring Security 7 · Spring Data JPA / Hibernate · PostgreSQL 15 · JJWT · springdoc-openapi (Swagger UI) · Docker · JUnit 5 + MockMvc · GitHub Actions

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

## Try it in your browser

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/iamsrkg/task-management-api?quickstart=1)

One click starts the real service (Spring Boot + PostgreSQL) in your own GitHub account. After about 2–3 minutes, a browser tab opens on **Swagger UI**:
1. Call `POST /api/auth/login` with `{"email": "admin@example.com", "password": "admin123"}`, or register your own user.
2. Copy the `token`, click **Authorize** and paste it.
3. Try the rules above: create a project and a task, then edit the task twice with the same `version` (the second call gets **409**), or register a second user and read the first user's task (**404**).

## Run it

**With Docker** (API + PostgreSQL):
```bash
git clone https://github.com/iamsrkg/task-management-api.git
cd task-management-api
docker compose up --build
```
The API waits for PostgreSQL to be healthy, then listens on `http://localhost:8080`, which opens Swagger UI. Check health with `GET /actuator/health`.

For anything beyond local development, copy `.env.example` to `.env` and set `JWT_SECRET` (`openssl rand -base64 32`) and `ADMIN_PASSWORD`.

**Tests** (no database needed, they use in-memory H2):
```bash
./mvnw test
```

## Deploy (Render + Neon, free tier)

1. Create a free PostgreSQL database on [Neon](https://neon.tech). From its connection string `postgresql://USER:PASSWORD@HOST/DB?sslmode=require`, note the host, database, user and password.
2. On [Render](https://render.com), go to **New → Blueprint**, pick this repo, and Render reads `render.yaml`. When prompted, set:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://HOST/DB?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` = the Neon credentials
3. Render generates `JWT_SECRET` and `ADMIN_PASSWORD`, builds the Dockerfile and health-checks `/actuator/health`.

Free instances sleep after 15 minutes idle. The first request afterwards takes about 30–60 s while the JVM starts.

## API

All endpoints except auth, health and the docs need `Authorization: Bearer <token>`.

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
| `GET` | `/swagger-ui.html` | Public. Interactive docs for everything above (`/` redirects here) |
| `GET` | `/v3/api-docs` | Public. The OpenAPI 3 spec as JSON |

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
