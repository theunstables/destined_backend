# LoveOS API — Java / Spring Boot

This is the only active LoveOS backend. New backend features, contracts,
migrations and documentation belong in this project.

The former Node/Express backend is retired and read-only. It may be consulted as
historical product and contract evidence while the Java documentation is being
completed, but it is not an implementation target, runtime dependency or place
for new work.

## Planning and tracking

- [Feature register](docs/FEATURES.md) — complete feature inventory and status
- [API reference](docs/API.md) — active Spring HTTP contracts
- [Architecture roadmap](docs/ARCHITECTURE_ROADMAP.md) — application-boundary decision and phased delivery plan
- [Progress tracker](docs/PROGRESS.md) — checklists, evidence and next action
- [Phase 6 external requirements](docs/PHASE6_EXTERNAL_REQUIREMENTS.md) — OAuth/push/AI gates and other-laptop verification

Update the progress tracker and its change log whenever a phase begins or an
item is verified complete.

---

## Running it

### No install, no password, no administrator rights

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=pgdev"
```

The `pgdev` profile starts a **real PostgreSQL 16** as a child process of the
JVM on port 5433, applies Flyway migrations, and prints outgoing email to the
console. Data lives under `%LOCALAPPDATA%\loveos\pgdata` and survives restarts.
The first boot takes about ninety seconds while the binaries are extracted and
`initdb` runs; later boots take a few seconds.

There is no in-memory database option. H2 was removed on purpose: a substitute
engine can make a write look successful that the real database never performed,
and that failure is much harder to notice than a missing dependency. Postgres is
the only driver on the classpath, so every profile talks to Postgres or does not
start at all.

Inspect the data with pgAdmin while the app is running — host `localhost`, port
`5433`, database `postgres`, user `postgres`, no password. The server is owned by
the JVM, so it stops when the application stops.

Emails appear in the log, so the verification and reset links are copy-pasteable:

```
──────────── EMAIL (console driver) ────────────
To      : sam@example.com
Subject : Confirm your email
http://localhost:4000/v1/auth/verify-email?token=uAv7iru…
```

### Against a PostgreSQL server you already have

```powershell
mvn spring-boot:run
```

Flyway applies every pending file under `db/migration` before Hibernate starts.

Required environment — the application refuses to start without them, which is
deliberate:

| Variable | Notes |
|---|---|
| `JWT_SECRET` | ≥ 32 characters. No default, by design: a shipped default is a forged token. |
| `DATABASE_PASSWORD` | No default. |
| `DATABASE_URL` | defaults to `jdbc:postgresql://localhost:5432/loveos` |
| `MAIL_DRIVER` | `console` or `smtp` |
| `CORS_ORIGINS` | comma-separated allow-list |
| `MEDIA_LOCAL_DIR` | local-development object directory; defaults to `~/.loveos/media` |
| `MEDIA_MAX_BYTES` | upload limit in bytes; defaults to 25 MB |

---

## Deploying

**The database is not part of the application.** In production PostgreSQL is a
separate, managed thing — RDS, Cloud SQL, or its own container — with its own
backups, its own upgrade schedule and its own failure modes. The `pgdev` profile
starting a server inside the JVM is a workaround for a laptop that cannot install
one, and it is the only place that arrangement is acceptable. A deployed process
that owns its own database cannot be restarted without downtime, cannot scale to
two instances, and loses everything if the container is replaced.

**Run with no profile active.** The base configuration at the top of
`application.yml` is the production configuration; it reads every value from the
environment. Both `pgdev` and `postgres` are development profiles and both carry a
published JWT secret, so activating either in production would sign real tokens
with a string printed in this repository.

### Configuration comes from the environment — not yml, not `-D`

| | |
|---|---|
| **Environment variables** ✅ | Never enter git, never enter the artifact, injected by the platform. The same jar promotes dev → staging → prod untouched, which is what makes a staging test meaningful. |
| **`application.yml`** ❌ | A secret committed once is in the history permanently, and rotating it becomes a code change and a redeploy. It also forces a different build per environment. |
| **VM args `-Dspring.datasource.password=…`** ❌ | The full command line of a process is readable by any user on the host (`ps`, Task Manager, `/proc/<pid>/cmdline`) and is captured in crash dumps and container inspect output. |

### Full environment

| Variable | Required | Notes |
|---|---|---|
| `DATABASE_URL` | ✅ | `jdbc:postgresql://host:5432/loveos?sslmode=require` |
| `DATABASE_USER` | ✅ | Owns only the `loveos` database, not a superuser |
| `DATABASE_PASSWORD` | ✅ | From the platform's secret store |
| `JWT_SECRET` | ✅ | ≥ 32 random characters. Rotating it logs everyone out — intentionally |
| `PORT` | | defaults to `4000` |
| `DB_POOL_MAX` | | defaults to `10`; must stay below the server's `max_connections` |
| `MAIL_DRIVER` | ✅ | `smtp` in production. `console` only prints to the log |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASSWORD` | ✅ | with `SMTP_AUTH=true`, `SMTP_TLS=true` |
| `MAIL_FROM` | | a verified sender on your domain |
| `PUBLIC_API_URL` | ✅ | the externally reachable base URL — this is what verification and reset links are built from, so a wrong value produces emails nobody can act on |
| `CORS_ORIGINS` | ✅ | explicit allow-list; never `*` |
| `MEDIA_DRIVER` | ✅ | `local` for development or `s3` for production object storage |
| `MEDIA_LOCAL_DIR` | when local | persistent directory outside the application artifact |
| `MEDIA_MAX_BYTES` | | defaults to 25 MB |
| `MEDIA_S3_BUCKET` | when S3 | private-write object bucket |
| `MEDIA_S3_REGION` | when S3 | defaults to `us-east-1` |
| `MEDIA_S3_ENDPOINT` | | optional S3-compatible endpoint; omit for AWS |
| `MEDIA_S3_PUBLIC_BASE_URL` | when S3 | CDN or public read base URL |

The S3 adapter obtains credentials from the AWS SDK default provider chain
(workload identity/instance role in production; environment credentials only
when the hosting platform requires them). Credentials are never application
properties and never enter the JAR.
| `LOG_LEVEL` | | `INFO`. `DEBUG` will log SQL parameters |

**`sslmode=require` is not optional.** The PostgreSQL JDBC driver does not
encrypt by default, so without it the password and every row crossing the network
travel in plaintext.

**Leave `JPA_DDL_AUTO` at `validate`.** Hibernate then checks the tables against
the entities and refuses to start on a mismatch, naming the column. `update`
would quietly add a column to make the mismatch go away, leaving the Flyway
history and real schema disagreeing with nobody aware of it — and it can neither drop
nor migrate data. Schema changes are applied by running the versioned SQL before
the new version starts.

### Order of a release

1. Add and review the next immutable Flyway migration
2. Start the new version — Flyway migrates, then Hibernate validates
3. If migration or validation fails, startup stops before serving a request

Keeping schema changes backward-compatible for one release — add columns, do not
rename or drop them until nothing reads them — lets steps 1 and 2 overlap without
the old version breaking.

---

## How it is arranged

```
core/     ErrorCode, AppException, the response envelopes, request-id plumbing
config/   AppProperties, SecurityConfig, the JSON authentication entry point
auth/     domain · repo · dto · JwtService · AuthService · AuthController
pairing/  domain · repo · dto · PairingService · PairingController
story/    domain · repo · dto · StoryService · StoryController
media/    StorageProvider · Local/S3 providers · MediaService · MediaController
memories/ shared archive · albums/tags · reciprocal private-note gate
calendar/ domain · repo · dto · CalendarService · CalendarController
home/     dto · HomeService composition · HomeController
messaging/domain · repo · dto · MessagingService · MessagingController
realtime/ authenticated WebSocket handler · couple/user hub · heartbeat
account/  privacy export · deletion grace and tombstoning
notifications/ preferences · quiet hours · device registration
timeline/ read-only Story/Calendar/Memories projection
dailyquestions/ static daily assignment · reciprocity gate · Memory completion
repairsignal/ quiet expiring signal · symmetric mutual readiness · cancellation
infra/    Mailer
```

Three rules hold the shape together:

1. **`AuthService` imports nothing from the web layer.** No `HttpServletRequest`,
   no `ResponseEntity`. It is testable without a servlet container, and it cannot
   be tempted into reading a header to decide a business question.
2. **Controllers contain no rules.** They translate HTTP into a call and a result
   back into HTTP. A conditional appearing in one means logic has leaked upward.
3. **Entities never cross the wire.** Responses are records that name their
   fields, so `passwordHash` cannot escape because somebody added a getter.

---

## The security decisions, and why

**Two tokens, not one.** A 15-minute access JWT that no one has to look up, and a
30-day opaque refresh token that is a database row and can therefore be revoked.
A stateless token that lasts thirty days cannot be withdrawn; a stateful one
checked on every request is a database read per request. This splits the
difference: statelessness where it is read constantly, revocability where it
matters.

**Refresh tokens are stored as SHA-256 digests.** A refresh token *is* the
account for thirty days. Treating it like a password means a leak of the sessions
table yields nothing that can be replayed. No salt — the token is 256 random
bits, and salts exist to defeat precomputation against low-entropy inputs.

**Rotation with reuse detection.** Every refresh mints a new token and marks the
old one superseded. Presenting a superseded token means two parties hold it, and
nothing here can tell which one is the thief — so every session for that user is
revoked. One person is inconvenienced; the alternative is letting an attacker
refresh indefinitely.

**`INVALID_CREDENTIALS` for both a wrong password and an unknown account, in the
same amount of time.** A missing account still pays for a bcrypt comparison
against a dummy hash. Otherwise response time answers "does this person have an
account?" — which for a couples app is a genuinely sensitive question. The dummy
hash is generated from the injected encoder at startup rather than pasted in as a
literal, so its cost factor cannot drift away from the real one.

**`/password/forgot` always returns `202`.** Not `404` for an unknown address.
A different answer turns the endpoint into a membership oracle: ask it about a
thousand addresses and learn which belong to users.

**`userId` is never a parameter.** It comes from the signed token, always. If a
caller could name the user, every handler would need an ownership check and one
would eventually be missed — that is broken access control (OWASP A01) waiting
to happen. Removing it from the signature makes the class of bug unreachable.

**Deny by default.** The filter chain names the public routes and ends with
`anyRequest().hasAuthority(ROLE_VERIFIED)`. A route added later and forgotten is
a locked door, not an open one.

**Errors are a closed set.** `ErrorCode` carries its own HTTP status; the
`message` is for humans reading logs and is never rendered by the client.
`GlobalExceptionHandler` owns every error response, so a stack trace cannot reach
the wire.

**Flyway writes; Hibernate validates.** Schema changes are immutable, ordered
migrations under [src/main/resources/db/migration](src/main/resources/db/migration).
Hibernate checks the result with `ddl-auto: validate` and never alters it.

---

## Gotchas worth knowing

**Spring Boot 4 ships Jackson 3.** `ObjectMapper` is `tools.jackson.databind`,
not `com.fasterxml.jackson.databind`. Annotations stayed at
`com.fasterxml.jackson.annotation`, so `@JsonInclude` is unchanged — which makes
the split easy to miss. `WRITE_DATES_AS_TIMESTAMPS` moved from
`SerializationFeature` to `DateTimeFeature`, so it is configured under
`spring.jackson.datetime`, and putting it under `spring.jackson.serialization`
fails at startup rather than being ignored.

**`open-in-view` is off.** Anything lazy must be fetched inside a service method.
The default holds a database connection through JSON serialisation, which is how
a connection pool gets exhausted under load.

**Verification status is baked into the access token.** After a user verifies,
their *existing* access token still says `emailVerified: false` until it is
refreshed — at most 15 minutes. That is inherent to stateless tokens; the client
already refreshes on `403`.

---

## Verified behaviour

Exercised end to end against PostgreSQL:

| | |
|---|---|
| `POST /v1/auth/signup` | `201` + session, verification email sent |
| duplicate signup | `409 EMAIL_ALREADY_EXISTS` |
| malformed signup | `422 VALIDATION_ERROR` with per-field issues |
| `POST /v1/auth/login` wrong password | `401 INVALID_CREDENTIALS` |
| `POST /v1/auth/login` unknown account | `401 INVALID_CREDENTIALS`, same shape |
| `GET /v1/auth/me` | `200` + user |
| `GET /v1/auth/me` without a token | `401 UNAUTHORIZED` |
| bad bearer token | `401 TOKEN_INVALID` |
| unverified user on a gated route | `403 EMAIL_NOT_VERIFIED` |
| `POST /v1/auth/refresh` | `200`, token rotated |
| replaying a rotated token | `401 TOKEN_INVALID`, all sessions revoked |
| `GET /v1/auth/verify-email` | `200`, `emailVerified: true` |
| resend after verifying | `409 EMAIL_ALREADY_VERIFIED` |
| `POST /v1/auth/password/forgot` | `202 { accepted: true }`, known or not |
| `POST /v1/auth/password/reset` | `200 { reset: true }`, all sessions revoked |
| replaying a reset token | `401 TOKEN_INVALID` |
| `POST /v1/auth/logout` | `204` |
| `PUT /v1/pairing/profile` | `200` + durable profile |
| `POST /v1/pairing/invites` | `201` + six-character expiring code |
| `POST /v1/pairing/redeem` | `200` + inviter profile; one concurrent claimant |
| `POST /v1/pairing/confirm` | `200` + transactional two-member connection |
| `GET /v1/pairing/space` | `200` + recoverable lifecycle status |
| `GET /v1/story` | `200` + role-relative durable Story |
| `PUT /v1/story` | `200` + idempotent whole-story replacement |
| `GET /v1/chat/messages` | `200` + cursor-paginated, viewer-relative messages |
| `POST /v1/chat/messages` | `201`, or `200` for an idempotent retry |
| `POST /v1/chat/messages/{id}/reactions` | `200` + reaction-toggled message |
| `POST /v1/chat/messages/{id}/pin` | `200` + pin-toggled message |
| `POST /v1/chat/read` | `200` + changed message IDs |
| `GET /ws?token=...` | authenticated WebSocket upgrade for live notifications |

---

## Not yet ported

Only the deferred product-completion features remain planned. Media, Memories,
Calendar, Home, Messaging, and Realtime are implemented. The retired Node
project is historical evidence only; active contracts are documented here.
