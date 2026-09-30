# Spring Boot Architecture and Delivery Roadmap

Last updated: 2026-09-18

## Decision

LoveOS will be built as **one Spring Boot modular monolith with one PostgreSQL database**. We will not create an independent Spring Boot application for each feature at this stage.

The Node backend is retired. It may be consulted only as historical product/contract evidence until the corresponding Java documentation is complete. No new implementation work belongs there.

## Why one application

Auth, Pairing, Story, Memories, Calendar and Home share the same user/couple authorization model and several transactional workflows. Splitting them now would introduce network failures, duplicated security, distributed transactions, tracing, multiple deployments and harder local development without providing useful isolation or independent scale.

Features remain separated by package so a measured future need can justify extraction.

```text
com.loveos.api
├── auth
├── account
├── couple
├── story
├── media
├── memories
├── calendar
├── home
├── messaging
├── notifications
├── timeline
├── dailyquestions
├── config
└── core
```

Each feature should contain its own controller, service, domain, repository and DTO packages. A feature must not import another feature's repositories or entities. Cross-feature work uses an exposed application-service interface or a post-commit domain event.

## Deployment boundary assessment

| Module | Separate application now? | Long-term guidance |
|---|---:|---|
| Auth | No | Extract only for multiple products, SSO or a dedicated identity team |
| Couple/Pairing | No | Keep near authorization and membership rules |
| Story | No | Small and tightly connected to Calendar/Home |
| Memories | No | Reassess only for independent search/storage scale |
| Calendar | No | Depends on Couple and Story |
| Home | No | Aggregator/read model, not a data owner |
| Media | No | Use object storage through an adapter; do not build a file-server microservice |
| Chat + Realtime | No, but first candidate | Extract for independently measured message/socket scale |
| Daily Questions + Repair Signal | No | Small couple-scoped connection loops; keep beside membership rules |
| Notifications | Later worker candidate | Provider retries and outages are naturally asynchronous |
| AI | Later isolated worker/service | Isolate cost, privacy and provider failures |

Before Chat can be extracted, it needs its own schema/database, stable JWT verification, Redis/pub-sub or a broker, idempotent events and distributed tracing. Extraction is triggered by measured scaling, team ownership, release cadence, availability or regulatory needs—not by feature count.

## Delivery phases

### Phase 0 — Harden the foundation

1. Freeze Auth with unit, repository, PostgreSQL integration and MockMvc contract tests.
2. Cover response envelopes, error codes, validation, refresh rotation/reuse and authorization gates.
3. Introduce Flyway and convert the current schema into a versioned baseline.
4. Keep Hibernate at `ddl-auto: validate`; never use `update` in a real environment.
5. Add public-endpoint rate limiting.
6. Separate liveness from PostgreSQL readiness.
7. Add structured request logging, graceful shutdown and CI/build gates.
8. Add architecture tests enforcing controller → service → repository and feature package boundaries.
9. Maintain API, architecture, errors and integration documentation in the Java project.

**Exit criterion:** repeatable Maven test/package succeeds and Auth contract regressions fail automatically.

### Phase 1 — Couple lifecycle

**Status: implementation complete; exit verification pending (2026-09-18).**
Implemented as the `pairing` feature package with Flyway V2, PostgreSQL
locking/constraints, HTTP status polling and frontend store hydration. A manual
two-client restart/recovery smoke test remains. Starter albums stay in Phase 3
so Pairing does not own Memories persistence before that module exists.

1. Profile get/update.
2. Invite create/cancel/redeem/confirm.
3. Couple membership, partner lookup and status.
4. Shared-space get/update.
5. Verified-email, no-self-pairing and one-active-couple enforcement.
6. Transactional confirmation and concurrency tests.
7. Pairing-status polling for the inviter waiting screen.
8. Starter-album creation through a Memories service interface or post-commit event.
9. Integrate Pairing adapter and hydrate relationship/space state.

**Exit criterion:** two accounts can verify, pair from separate clients, restart, and recover the connected state.

### Phase 2 — Story and onboarding persistence

**Status: core implementation complete; Media dependency pending (started
2026-09-18).** Flyway V3, exact Story replacement, moments, key dates,
role-relative birthdays and frontend hydration are implemented. Story obtains
scope through Pairing's `CoupleAccess` application boundary and permits pending
founders. Profile/Story photo upload awaits the Phase 3 Media backend. Phase 1's
manual two-client restart/recovery smoke test remains a pre-release gate.

1. Story get and idempotent replace.
2. Story moments, date precision and role-relative important dates.
3. Profile/story media upload before durable URL persistence.
4. Restore token → current user → pairing/space → story during frontend bootstrap.
5. Permit Story entry with a couple before every connected-only feature is available.

**Exit criterion:** onboarding survives an application restart and Home can derive relationship dates.

### Phase 3 — Media and Memories

**Status: complete (2026-09-18).** Media is the durable-URL boundary used by
profile, Story, and Memories. Memories is couple-scoped and provides ordered
photos, normalized tags, albums, starter collections, soft deletion, cursor
pagination, search, favorites, and On This Day.

1. Storage-provider interface; development storage and production S3-compatible storage.
2. Content-type, size and ownership validation.
3. Memory CRUD, favorite, search and soft delete.
4. Ordered photos, tags, albums, starter albums and On This Day.
5. Extend frontend update/delete contracts only when activating those controls.
6. Define cleanup/retry behavior when upload succeeds but database persistence fails.

**Exit criterion:** both partners see the same durable memories and media after restarts.

### Phase 4 — Calendar and Home

**Status: complete (2026-09-18).** Calendar CRUD, annual recurrence, soft
deletion, Story-key-date upcoming projection, Home composition, and non-visual
frontend service/hydration boundaries are implemented.

1. ✅ Calendar event CRUD and recurrence.
2. ✅ Merge Story key dates and Calendar events into upcoming occasions.
3. ✅ Implement Home as a read-only composition service over feature application APIs.
4. ✅ Add frontend Calendar and Home service adapters and hydration.

**Exit criterion:** one Home request returns durable shared-space, days-together, upcoming and recent-memory data.

Verified by unit, contract, architecture, and real-PostgreSQL composition tests;
the full package gate passes 79 tests.

### Phase 5 — Messaging and Realtime

**Status: implementation complete; verification in progress (2026-09-18).**
Durable PostgreSQL/HTTP messaging, authenticated WebSocket delivery, frontend
reconnect/catch-up, and automated tests are implemented. Maven packaging and a
physical two-client acceptance run remain.

1. ✅ Message list/send with client idempotency keys.
2. ✅ Replies, media, reactions, pins, search and soft delete.
3. ✅ Delivery/read receipts and per-viewer DTO mapping.
4. ✅ Authenticated Spring WebSocket upgrade and couple context.
5. ✅ Typing, presence, status, reaction and pin events.
6. ✅ Heartbeat, reconnect, HTTP catch-up and automated multi-connection tests.

HTTP is the durable source of truth. WebSocket only delivers live events.

**Exit criterion:** two devices exchange messages without duplicates and recover correctly after disconnection.

### Phase 6 — Product completion

**Status: in progress (started 2026-09-18).** Safe local lifecycle, export,
preferences, and Timeline contracts are active. Provider-dependent OAuth/push
and policy-dependent AI remain fail-closed until configured.

1. Export, pause/unpair and deletion semantics.
2. Push notifications, preferences, quiet hours and deep links.
3. OAuth/account linking.
4. Timeline projection.
5. AI/daily questions after privacy, retention, moderation and budget decisions.
6. Reassess service extraction using production measurements.

Implemented locally: items 1 and 4, plus the provider-independent preference,
quiet-hour, timezone, and device-registration portion of item 2. Account
deletion tombstones personal credentials after a 30-day grace period instead
of cascading shared history. Unpair archives after seven days and likewise
retains shared history. Timeline owns no table and uses a keyset-paginated
union projection over feature-owned data.

OAuth token verification, push delivery, deep-link routing, and AI generation
must not be enabled with placeholders. Their credentials and policy gates are
listed in `PHASE6_EXTERNAL_REQUIREMENTS.md`.

### Phase 7 — Daily connection

**Status: implementation complete; verification in progress (2026-09-18).** Phase 7 implements
the product roadmap's next backend-dependent loop without activating AI.

1. Assign exactly one catalogue question to each connected couple per UTC day.
2. Store one idempotently replaceable answer per member.
3. Reveal the partner answer only when both members have answered.
4. Create exactly one tagged shared Memory when reciprocity completes.
5. Expose typed frontend HTTP/mock and state boundaries without visual changes.

Assignment is lazy and race-safe through PostgreSQL `ON CONFLICT`. Answer and
Memory completion serialize on a pessimistic lock. The prompt is snapshotted so
later catalogue edits cannot rewrite history. Generated or adaptive questions
remain outside this phase and fail closed under the Phase 6 AI policy gate.

### Phase 8 — Repair signal

**Status: implementation complete; verification in progress (2026-09-18).**
Phase 8 implements the provider-independent `LOV-017` connection loop with a
safety-minimized contract.

1. Persist at most one active signal per couple through a PostgreSQL partial unique index.
2. Make sender retries idempotent and converge a partner send to mutual readiness.
3. Permit only the original sender to cancel, including while the couple is paused.
4. Expire signals quietly after 24 hours without a dismissal or negative state.
5. Hydrate typed frontend service/state boundaries without adding visual controls.

No free text, read/open receipt, response-time exposure, streak, reminder,
location/activity data, push, or realtime event is part of this phase. Product
validation, coercive-control review, exact copy/placement approval, route guards,
and physical two-client verification remain production gates.

### Phase 9 — Reciprocal Memory notes

**Status: implementation complete; verification in progress (2026-09-18).**
Phase 9 implements proposed feature `LOV-014` without replacing the established
shared Memory-note contract.

1. Store at most one private note per Memory and member in a feature-owned table.
2. Return the caller's note immediately while masking the partner note until both exist.
3. Serialize writes on the Memory row so simultaneous submissions converge safely.
4. Permit idempotent replacement and withdrawal; withdrawal re-masks the partner note.
5. Project the fields through existing frontend Memories adapters and state without visual changes.

The API does not expose note timestamps, typing state, reminders, or response
delay. Account export includes only the requesting user's private-note rows;
broader unpair/deletion retention policy requires explicit product approval.

## Release verification

Every phase must pass:

- Maven clean test/package
- PostgreSQL migration and integration tests
- Success/error contract tests
- Unauthenticated, unverified, unpaired and cross-couple security tests
- Frontend TypeScript, ESLint and Jest
- Cold-start, refresh, logout and offline/retry tests
- PostgreSQL persistence across JVM restart
- Physical-device API URL, deep-link, background/resume and media checks

Production packages must exclude embedded PostgreSQL. Production runs with no development profile, uses environment variables/secrets, requires encrypted PostgreSQL connectivity and validates the schema before serving traffic.
