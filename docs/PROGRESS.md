# LoveOS Spring Boot Progress Tracker

Last updated: 2026-09-18

Update this file when work starts or finishes. Keep evidence links or commands in the Notes column rather than marking work complete based only on code existence.

## Overall status

| Phase | Status | Progress | Notes |
|---|---|---:|---|
| Phase 0 — Foundation hardening | ✅ Complete | 9/9 | 31 tests pass; Flyway/PostgreSQL/package gate verified |
| Phase 1 — Couple lifecycle | 🔄 Verification | 9/9 | Automated gates pass; two-client manual smoke test pending |
| Phase 2 — Story/onboarding persistence | ✅ Complete | 5/5 | Story and authenticated durable-URL upload boundary are implemented |
| Phase 3 — Media and Memories | ✅ Complete | 6/6 | Media, Memories, albums, tags, On This Day, hydration, and tests implemented |
| Phase 4 — Calendar and Home | ✅ Complete | 4/4 | Home composition, frontend service boundaries, hydration, and package gate verified |
| Phase 5 — Messaging and Realtime | 🔄 Verification | 6/6 | Implementation and automated tests pass; package and physical two-device gates remain |
| Phase 6 — Product completion | 🔄 In progress | 3/6 | Lifecycle/export, preferences/device registration, and Timeline implemented; external-provider features fail closed |
| Phase 7 — Daily connection | 🔄 Verification | 5/5 | Implementation and 145-test backend suite pass; content/visual/package/device gates remain |
| Phase 8 — Repair signal | 🔄 Verification | 5/5 | Implementation and 155-test backend suite pass; safety/visual/package/device gates remain |
| Phase 9 — Reciprocal Memory notes | 🔄 Verification | 5/5 | Implementation and 162-test backend suite pass; product/safety/visual/package/device gates remain |

## Completed baseline

- [x] Java 21 and Maven Spring Boot project established.
- [x] PostgreSQL is the only database dependency.
- [x] Persistent local PostgreSQL development profile (`pgdev`) works.
- [x] Embedded PostgreSQL is excluded from deployment artifacts.
- [x] Auth signup/login/refresh/logout implemented.
- [x] Email verification/resend implemented.
- [x] Forgot/reset password implemented.
- [x] Current-user endpoint implemented.
- [x] Access JWT plus rotated, hashed refresh-token model implemented.
- [x] Request ID, success envelope, error envelope and global exception handling implemented.
- [x] Auth manually smoke-tested end to end against PostgreSQL.
- [x] PostgreSQL persistence verified across JVM/database restart.
- [x] Bruno Auth and error collections exist.
- [x] Frontend HTTP client and Auth/Pairing/Story/Memories/Chat/Media adapters exist.
- [x] Node backend declared retired for future work.

## Phase 0 — Foundation hardening

- [x] Auth unit tests (`AuthService`, `JwtService`, token hashing and JWT filter).
- [x] PostgreSQL repository/integration test using genuine PostgreSQL 16.2.
- [x] MockMvc contract tests for every Auth endpoint and error envelope.
- [x] Flyway baseline and versioned migrations.
- [x] Rate limiting for Auth and Pairing attack surfaces.
- [x] Liveness/readiness split with database readiness.
- [x] Environment-driven production configuration and validated settings.
- [x] PostgreSQL-only database policy.
- [x] Architecture/package-boundary tests and Maven package build gate.

### Phase 0 evidence

- Maven test: 31 tests, 0 failures, 0 errors as of 2026-09-18.
- Maven package: passing; deployable `loveos-api.jar` produced.
- Artifact inspection: Flyway and `V1__auth_baseline.sql` included; embedded PostgreSQL excluded.
- Flyway integration: empty PostgreSQL 16.2 migrated to V1 before Hibernate validation.
- PostgreSQL Auth integration: signup/login round-trip passed against migrated PostgreSQL.
- Contract coverage: all nine Auth endpoints plus validation/malformed/error envelopes.
- Security coverage: JWT claims/expiry/tampering/issuer, filter authorities and refresh reuse revocation.
- Operational controls: fixed-window Auth/Pairing rate limits, `Retry-After`, `/health`, `/health/ready`, graceful shutdown and request completion logs.
- Auth manual smoke test: 17 scenarios previously passed.
- PostgreSQL 16.2 persistence: verified with login after full restart.
- Configuration validation: nested `@Valid` added and short JWT secret confirmed to fail during binding.

## Phase 1 — Couple lifecycle

- [x] Define Java Couple/Profile/Invite API contracts.
- [x] Add Couple, CoupleMember and Invite migrations/entities.
- [x] Implement profile get/update.
- [x] Implement invite create/cancel.
- [x] Implement invite redeem/confirm.
- [x] Implement shared-space get/update.
- [x] Add pairing-status polling endpoint.
- [x] Add race, expiry, self-pairing and cross-user security tests.
- [x] Integrate Pairing frontend adapter and server-state hydration.

### Phase 1 evidence

- Flyway V2 migrated an empty PostgreSQL 16.2 database before Hibernate validation.
- Pairing integration tests cover persisted profile/space state, complete two-account connection, expiry, self-pairing, ownership/partner forgery, one-couple enforcement and simultaneous invite claims.
- MockMvc tests cover invite, space, confirm and validation response contracts.
- Inviter waiting state polls `/v1/pairing/space` and hydrates relationship/space stores without visual changes.
- Targeted Phase 1 gate: 11 tests, 0 failures, 0 errors.
- Full Maven package gate: 40 tests, 0 failures, 0 errors; deployable JAR produced.
- Frontend editor diagnostics pass; CLI typecheck is unavailable until frontend dependencies are installed (`tsc` is missing).

### Phase 1 remaining manual verification

- [ ] Pair two physical clients/accounts, restart both applications and the backend, and confirm both recover `connected` state from `/v1/pairing/space`.
- This manual exit check is tracked separately and does not block beginning Phase 2 implementation; it must pass before a production release.

## Phase 2 — Story and onboarding persistence

- [x] Define Story contract and migration.
- [x] Implement Story get/idempotent replace.
- [x] Implement moments, date precision and key dates.
- [x] Integrate profile/story media uploads.
- [x] Restore pairing, space and story state at frontend bootstrap.

### Phase 2 evidence

- Flyway V3 migrated an empty PostgreSQL 16.2 database before Hibernate validation.
- Story uses a Pairing-owned `CoupleAccess` boundary rather than importing Pairing repositories or entities.
- PostgreSQL tests cover pending-founder access, exact replacement/idempotency, role-relative birthdays, no-couple authorization, invalid calendar dates and cascade deletion.
- MockMvc tests cover empty GET, complete frontend PUT shape and nested validation errors.
- Frontend Story adapter now supports GET; root hydration restores Pairing/Space before Story without visual component changes.
- Full Maven package gate: 49 tests, 0 failures, 0 errors; deployable JAR produced.
- Artifact inspection confirms V3 and Story are packaged and embedded PostgreSQL is excluded.
- Frontend CLI typecheck remains unavailable until dependencies are installed; editor errors are dependency-resolution fallout from the missing `node_modules`.

### Phase 2 manual verification

- [ ] Verify profile and Story local-photo selection/upload on a physical Expo client after the separately planned `expo-image-picker` dependency is activated.
- The backend upload boundary and frontend pre-save upload adapters are complete. The existing visual `PhotoPicker` intentionally remains a stub until its planned UI dependency is approved; component code was not changed.

## Phase 3 — Media and Memories

- [x] Implement media storage-provider abstraction and upload security.
- [x] Implement Memory CRUD/favorite/search/soft delete.
- [x] Implement ordered photos, tags and albums.
- [x] Implement starter albums and On This Day.
- [x] Integrate existing frontend adapter and startup hydration; defer inactive UI update/delete methods.
- [x] Test local upload atomicity/path security and cross-couple authorization; define retry cleanup.

### Phase 3 evidence

- Flyway V5 creates couple-scoped Memories, ordered photos, case-insensitive tags, albums, and junction tables before Hibernate validation.
- PostgreSQL tests cover photo/tag replacement semantics, cursor and On This Day filtering, favorite/search/soft-delete behavior, starter albums, idempotent album links, and cross-couple isolation.
- Pairing confirmation publishes a transaction-bound event that creates starter albums; connected couples predating V5 are backfilled idempotently when albums are read.
- Frontend HTTP adapters upload local profile, Story, and Memory photos before durable records are saved. Root hydration now restores Memories after Pairing and Story without visual component changes.
- Local media writes use temporary files plus atomic move; production uses the S3-compatible provider and AWS credential chain. Failed local writes remove temporary files; successful but unattached uploads remain valid objects and may be retried by URL. Production orphan reclamation requires storage metadata and a retention job.
- Full Maven package gate: 75 tests, 0 failures, 0 errors. The deployable JAR contains V5 and `MemoriesController` and excludes embedded PostgreSQL.
- Frontend editor diagnostics are clean. CLI typecheck remains unavailable because the checked-in `node_modules/typescript` directory is empty and `tsc` is not installed.

## Phase 4 — Calendar and Home

- [x] Implement Calendar event CRUD and recurrence.
- [x] Implement merged upcoming occasions.
- [x] Implement Home composition endpoint.
- [x] Add frontend Calendar/Home adapters and hydration.

### Phase 4 evidence

- Flyway V4 migrates `calendar_events` before Hibernate validates the schema.
- Calendar CRUD is couple-scoped and deletion is soft; cross-couple IDs return `NOT_FOUND`.
- Upcoming merges Story key dates with active Calendar events, wraps annual recurrence, skips past one-off events, and resolves viewer-relative birthday labels through Pairing's `CoupleAccess` boundary.
- MockMvc covers create, validation, delete, and upcoming response contracts.
- PostgreSQL integration covers CRUD, range listing, soft deletion, cross-couple isolation, and annual recurrence.
- Home composes Pairing, Story, Memories, and Calendar through feature-owned application APIs; it owns no persistence and imports no cross-feature repositories or entities.
- `GET /v1/home` returns shared-space identity, viewer greeting, days together, relationship pulse, memory/place/trip statistics, upcoming occasions, and six recent memories.
- Home unit, MockMvc contract, real-PostgreSQL composition, and architecture tests pass.
- Frontend Calendar and Home mock/HTTP adapters plus Zustand stores are implemented. Authenticated bootstrap restores Calendar and Home after couple-scoped Story and Memories without changing visual components.
- Full Maven package gate: 79 tests, 0 failures, 0 errors; deployable JAR contains Home, V5, and no embedded PostgreSQL.
- Frontend editor diagnostics for the new boundaries are clean except dependency-library diagnostics from the missing TypeScript installation. CLI typecheck was attempted and remains blocked because `tsc` is unavailable.

### Before release or physical-device acceptance

- [ ] Set `EXPO_PUBLIC_API_URL` to the development computer's LAN address on physical devices; `localhost` points back to the phone.
- [ ] Install/repair frontend dependencies so `npm run typecheck` and Jest can run; the current local TypeScript package directory is empty.
- [ ] Complete the tracked two-client pairing restart/recovery smoke test.
- [ ] Activate the separately planned image-picker dependency before manually testing profile, Story, and Memory local-photo selection.
- These are verification/environment gates, not blockers for implementing the Phase 4 backend and service adapters.

## Phase 5 — Messaging and Realtime

- [x] Implement message list/send/idempotency/replies/media.
- [x] Implement reactions, pins, search and soft delete.
- [x] Implement delivery/read receipts and per-viewer DTOs.
- [x] Implement authenticated Spring WebSocket and heartbeat.
- [x] Implement typing/presence/message/status/reaction/pin events.
- [x] Integrate reconnect, HTTP catch-up, stable retry keys, read receipts, typing and live updates in the existing frontend Chat boundary.

### Phase 5 evidence

- Flyway V6 owns couple-scoped messages, reactions, and recipient receipts with sender/reply foreign keys, soft deletion, cursor indexes, and a unique `(couple_id, client_id)` idempotency boundary.
- All message lookups derive couple scope from the authenticated user. Cross-couple IDs and attempts to delete a partner's message return `NOT_FOUND`.
- PostgreSQL tests cover relative author DTOs, idempotent and concurrent retries, replies, media, cursor paging, case-insensitive search, reactions, pins, delivery/read transitions, sender-only soft deletion, and cross-couple isolation.
- `/ws?token=...` verifies the short-lived access JWT and connected-couple context before upgrade. The in-process hub isolates fan-out by couple/user and has protocol ping/pong cleanup.
- HTTP remains the durable source of truth. Committed sends, reactions, pins, delivery, and read transitions notify sockets; typing and presence remain ephemeral.
- Frontend Chat keeps its existing visual components. The adapter uploads media before send, reuses `clientId` on retry, reconnects with backoff/jitter, catches up over HTTP, marks received messages read, deduplicates live messages, and applies reaction/pin updates in place.
- Complete workspace test runner: 128 passed, 0 failed. New focused controller, PostgreSQL, handshake, handler, hub, and architecture tests pass.
- Editor diagnostics for all changed frontend Chat files are clean. CLI typecheck remains blocked because `tsc` is unavailable.

### Phase 5 remaining verification

- [ ] Restore Maven 3.9.11 to the terminal PATH and run the final `mvn package`/JAR inspection; the full Java test suite passes through the editor runner, but the current shell cannot resolve `mvn`.
- [ ] On two paired physical clients, verify live send/status/typing/reaction/pin, disconnect one client, reconnect it, and confirm HTTP catch-up has no duplicates.

## Phase 6 — Product completion

- [x] Implement export, pause/reactivate, unpair grace, and scheduled account-deletion semantics.
- [x] Add notification preferences, quiet hours, timezone, and device-token registration.
- [ ] Configure push provider delivery and deep links.
- [ ] Add OAuth/account linking.
- [x] Build Timeline as a Story/Memories/Calendar projection.
- [ ] Define AI privacy/moderation/budget policy, then implement daily questions.
- [ ] Review measured production load before extracting any service.

### Phase 6 evidence

- Flyway V7 adds paused/unpair-grace state, notification preferences, device registrations, deletion scheduling, and account tombstone state.
- Shared reads remain available while paused; all feature writes using Pairing's update boundary are rejected until reactivation. Unpair pauses immediately, permits either member to cancel for seven days, then archives without deleting shared history.
- Account deletion has a 30-day cancellation window. Execution anonymizes the account and revokes credentials/devices while retaining couple-owned records for the remaining member.
- Authenticated account export returns user-visible profile and all couple-owned Story, Calendar, Memories, albums, messages, reactions, and receipts without password or token material.
- Timeline is a read-only keyset-paginated SQL projection and owns no tables. Frontend Timeline, account, notification, and lifecycle adapters/stores/hydration were added without visual component changes.
- Complete editor test runner: 137 passed, 0 failed, including Phase 6 PostgreSQL, controller-contract, lifecycle, paused-history, and architecture coverage. Maven package and frontend CLI commands remain for the repaired laptop environment.
- OAuth verification, push delivery, and AI generation are intentionally unavailable until the requirements in `PHASE6_EXTERNAL_REQUIREMENTS.md` are supplied.

## Phase 7 — Daily connection

- [x] Add durable one-question-per-couple-per-day assignment and answer storage.
- [x] Enforce the reciprocity gate: each member sees their own answer, but neither sees the partner answer until both submit.
- [x] Convert completed exchanges into exactly one shared Memory.
- [x] Add typed frontend HTTP/mock service and state hydration without changing visual components.
- [x] Add PostgreSQL, concurrency, authorization, contract, and architecture tests.

### Phase 7 evidence

- Flyway V8 owns a non-clinical static catalogue, prompt-snapshotted daily assignments, and one answer per member.
- Lazy assignment uses PostgreSQL conflict handling, so simultaneous opens converge on one couple/day row.
- Answer completion serializes on the assignment row. Partner answer text/timestamp remain absent until both answer.
- Completion creates one `Daily Questions` Memory; retries and later answer edits update that Memory instead of duplicating it.
- Connected-couple and writable-state checks prevent unpaired, pending, paused, or archived writes. Couple identity always comes from the JWT principal.
- Frontend HTTP/mock adapters, typed results, Zustand state, and authenticated startup hydration are present; no existing visual component was changed.
- Complete backend editor test runner: 145 passed, 0 failed, including simultaneous assignment and answer completion. Changed Java and TypeScript diagnostics are clean.
- Frontend test discovery remains unavailable on this laptop because dependencies are incomplete; Maven package remains unavailable because `mvn` is absent from PATH.

### Before Phase 7 production release

- [x] Implement frontend auth/pairing route guards tracked as `LOV-005`; physical deep-link/device verification remains pending.
- [ ] Complete the existing two-physical-client pairing restart/recovery check before evaluating a two-person daily loop.
- [ ] Product/content review the starter question catalogue in Flyway V8. It intentionally avoids clinical advice, conflict escalation, sexuality, money, health, and other sensitive categories.
- [ ] Confirm the couple's daily boundary. Implementation starts with UTC so both partners always receive the same prompt; a future couple-timezone decision must migrate assignments rather than reinterpret old dates.
- [ ] Decide whether completed answer exchanges should remain automatic Memories. Phase 7 uses this documented product-roadmap default and tags them `Daily Questions`.
- [ ] Approve visual placement and copy before activating the existing Chat `prompt` concept. Phase 7 initially changes service/state boundaries only, preserving the standing no-visual-component constraint.
- AI is not required. Adaptive selection and generated questions remain disabled under the Phase 6 privacy/provider requirements.

## Phase 8 — Repair signal

- [x] Persist one active, expiring repair signal per couple.
- [x] Let either partner send the same non-verbal `I'm ready when you are` signal and converge to mutual readiness.
- [x] Enforce connected/writable couple scope, sender cancellation, expiry, idempotency, and concurrency safety.
- [x] Add typed frontend HTTP/mock service, store, and hydration without changing visual components.
- [x] Add PostgreSQL, authorization, contract, concurrency, and architecture tests.

### Phase 8 evidence

- Flyway V9 owns the signal lifecycle and a partial unique index permits at most one `OPEN` or `MUTUAL` signal per couple.
- Same-sender retries return the existing signal; simultaneous partner sends converge on one row and mutual readiness.
- Signals expire quietly after 24 hours. Only the sender may cancel, and cancellation remains available while paused even though new sends are blocked.
- REST DTOs expose no couple ID, sender identity, read/open state, response-time metric, negative response, location, or activity data.
- Frontend HTTP/mock adapters, Zustand state, and startup hydration were added without changing visual components or adding push/realtime nudges.
- Complete backend editor test runner: 155 passed, 0 failed. Phase 8 focused PostgreSQL, concurrency, authorization, contract, and architecture coverage passes; changed-file diagnostics are clean.
- Frontend test discovery and CLI gates remain unavailable because dependencies are incomplete; Maven packaging remains unavailable because `mvn` is absent from PATH.

### Before Phase 8 production release

- [ ] Test the concept with couples in the intended wedge; the product research explicitly says the case for `LOV-017` depends on whether it is used rather than ignored in the real moment.
- [ ] Complete a coercive-control and interpersonal-safety review. The signal must never expose read/open status, response time, location, activity, streaks, compliance language, or reminders that let one partner pressure the other.
- [ ] Approve placement and exact copy before adding a Home/Chat control. Phase 8 initially exposes service/state boundaries only under the standing no-visual-component constraint.
- [ ] Decide whether 24-hour expiry is appropriate after testing. The first contract uses quiet expiry with no negative state and allows sender cancellation at any time.
- [x] Complete deny-by-default root and route-layout guards. Two-physical-client pairing/restart verification remains pending.
- Push delivery is intentionally excluded until provider credentials, neutral lock-screen copy, quiet-hour behavior, and abuse safeguards are approved.

## Phase 9 — Reciprocal Memory notes

- [x] Add one private note per couple member per active Memory without replacing the existing shared `note` field.
- [x] Reveal the partner note only after both members have submitted non-blank notes.
- [x] Support idempotent replacement and privacy-preserving withdrawal of the viewer's own note.
- [x] Add typed frontend HTTP/mock service and store updates without changing visual components.
- [x] Add PostgreSQL, reciprocity, authorization, concurrency, contract, and architecture tests.

### Phase 9 evidence

- Flyway V10 adds `memory_private_notes` with a `(memory_id, user_id)` primary key and cascading Memory/user ownership; the existing shared `memories.note` column remains unchanged.
- Memory-row locking serializes concurrent submissions. Each viewer sees their own note immediately and the partner note only when both rows exist.
- PUT replaces the caller's note idempotently. DELETE is idempotent, removes only the caller's note, and re-masks the partner note.
- Private-note DTOs expose no author name, submission timestamp, typing state, reminder, or response-delay information. Cross-couple IDs return `NOT_FOUND`.
- Account export includes the requesting user's private-note rows only, preventing an unrevealed partner note from leaking through export.
- Existing Memories HTTP/mock adapters and Zustand state carry the reciprocal fields and update/withdraw operations; no visual component changed.
- Complete backend editor test runner: 162 passed, 0 failed. Focused PostgreSQL, simultaneous-submission, masking, withdrawal, export isolation, authorization, validation, contract, and architecture tests pass; changed-file diagnostics are clean.
- Frontend test discovery and CLI gates remain unavailable because dependencies are incomplete; Maven packaging remains unavailable because `mvn` is absent from PATH.

### Before Phase 9 production release

- [ ] Validate `LOV-014` with real couples; product confidence is explicitly speculative, so usage and emotional response must be measured before broad activation.
- [ ] Approve exact private/reveal/withdrawal copy and visual placement. Phase 9 initially changes only service/state boundaries.
- [ ] Complete an interpersonal-safety review. Do not expose typing presence, reminders, submission timestamps, response delays, or language that pressures the second partner to participate.
- [ ] Confirm withdrawal semantics after a note was previously revealed. The first contract re-masks the partner note after withdrawal, but cannot make already-read content unseen.
- [ ] Confirm retention/export policy for private notes during unpair and account deletion. Existing shared-history preservation remains unchanged until that policy is approved.
- [x] Complete deny-by-default root and route-layout guards. Two-physical-client verification, including simultaneous submission and withdrawal/re-mask behavior, remains pending.
- The existing shared `Memory.note` is preserved for backward compatibility and Daily Question Memory generation; private reciprocal notes use a separate table and contract.

## Frontend integration checklist

- [ ] Set `EXPO_PUBLIC_API_URL` per environment.
- [ ] Wire approved secure storage and persist refresh token only.
- [x] Hydrate tokens before authenticated routing.
- [x] Add session/bootstrap states: hydrating, unauthenticated, unverified, unpaired, pending, connected, and recoverable offline.
- [x] Add root and route-layout guards.
- [x] Restore data in order: tokens → `/v1/auth/me` → pairing/space → story → app.
- [ ] Keep uploads, pagination, token refresh, retry and error mapping in adapters.
- [ ] Configure `EXPO_PUBLIC_WS_URL` for each deployed Realtime environment (development falls back to the API host's `/ws`).
- [ ] Add verification/reset/pairing deep-link handling.
- [ ] Test emulator/simulator and physical-device URLs; physical devices cannot use the computer's `localhost`.
- [ ] Add frontend/backend contract tests.

## Release-readiness workstreams

### Deferred environment and manual gates

- [ ] Install Maven 3.9.x and run the final clean test/package gate. This is explicitly set aside until Maven is available on PATH.
- [ ] Restore frontend dependencies and run TypeScript, lint, and Jest. This is explicitly set aside; editor diagnostics may still report missing dependency modules.
- [ ] Run the complete two-physical-client acceptance suite. This is set aside except where required by the product pilot or provider verification.
- [ ] Approve final visual placement and copy for Daily Questions, Repair Signal, and reciprocal Memory notes. No feature UI activation occurs before approval.
- [ ] Inspect the production JAR and an empty PostgreSQL migration from V1 through V10. This is set aside until Maven is restored.

### Route guards (`LOV-005`) — implemented

- [x] Replace the hardcoded Welcome redirect with an authoritative route decision.
- [x] Recover the principal through persisted refresh-token hydration and `/v1/auth/me` before pairing hydration.
- [x] Deny protected routes during unresolved bootstrap and prevent protected-content flashes.
- [x] Enforce auth, onboarding, and app route-group prerequisites independently.
- [x] Route unauthenticated, unverified, unpaired, inviting, pending, connected, paused, and archived states.
- [x] Preserve sessions during transient network failure and expose a retry action rather than treating offline as logout.
- [x] Clear all user/couple server state when token revocation, expiry, reset, or logout removes the session.
- [x] Add a pure route-policy matrix test covering prerequisite redirects and allowed groups.
- Frontend CLI/Jest execution remains blocked by the deferred dependency repair gate; changed guard/auth/hydration files have no code-specific editor diagnostics.

### Product and interpersonal-safety pilot — planned

- [ ] Recruit 5–8 consenting adult couples, including varied relationship lengths and at least one cross-timezone pair.
- [ ] Run separate private onboarding, a one-to-two-week diary pilot, and separate exit interviews for Daily Questions, Repair Signal, and reciprocal Memory notes.
- [ ] Collect only privacy-minimized feature use, comprehension, usability, pressure, and self-reported impact; never collect answer/note contents, response rankings, activity, or streaks.
- [ ] Stop a feature immediately for retaliation, coercive monitoring, severe distress, or dangerous privacy misunderstanding.
- [ ] Require at least 90% visibility/expiry/withdrawal comprehension, no severe safety incident, no recurring coercion pattern, and evidence of usefulness before activation.
- [ ] Produce an explicit ship/revise/stop finding for each tested feature.

### Worldwide date consistency — implemented

- [x] Add canonical Java and TypeScript date utilities: UTC `Instant` for absolute events, timezone-free `LocalDate` for calendar days, and `LocalTime` plus IANA `ZoneId` for user-local schedules.
- [x] Keep Daily Questions on one UTC shared boundary until a couple-level timezone and migration policy are explicitly approved.
- [x] Implement quiet-hour evaluation across midnight and DST using the recipient's IANA timezone; never persist a fixed UTC offset as a timezone.
- [x] Add UTC-midnight, international-date-line, leap-day, recurrence, overnight-window, DST gap/overlap, malformed-date, and ISO round-trip tests with fixed clocks.
- [ ] Confirm current defaults through pilot/product review: 24-hour quiet Repair Signal expiry, withdrawal re-mask, seven-day reversible unpair, 30-day reversible deletion, shared-history retention, and caller-private export isolation.

#### DateUtils evidence

- Backend `DateUtils` centralizes strict calendar-date/instant/local-time parsing, IANA timezone validation, UTC day selection, ISO output, elapsed calendar days, annual recurrence, and recipient-local quiet-window evaluation.
- Calendar, Daily Questions, Home, Story, Memories, Pairing, Timeline, and notification preferences now delegate date-policy-sensitive behavior to the canonical backend utility.
- Frontend calendar dates are validated and compared as calendar components/UTC epoch days rather than local-midnight elapsed milliseconds, avoiding DST and international-date-line drift.
- Frontend Story formatting, Home day counts/recurrence, Chat instant grouping, and Repair Signal expiry parsing use the canonical utility without changing visual components.
- Leap-day annual recurrence is consistent across backend and frontend: February 29 is observed on February 28 in non-leap years.
- Complete editor test runner after implementation: 170 passed, 0 failed. Changed-file diagnostics are clean. Maven package and frontend CLI commands remain under the already deferred environment gates.

### External providers — planned in approved order

- [ ] Deep links first: approve an HTTPS domain and strict allowlist for verification, reset, pairing, Chat, lifecycle, Daily Question, Repair Signal, and Memory routes; route every link through the new guards.
- [ ] Google/Apple OAuth second: acquire client IDs/signing configuration and implement issuer, audience, signature, expiry, nonce, stable-subject, collision, link, and safe-unlink checks. Never auto-link by email.
- [ ] Expo Push third: add a provider boundary and transactional outbox, honor preferences/timezone/quiet hours, bound retries, deactivate invalid tokens, and redact sensitive payloads.
- [ ] Keep direct APNs/FCM and Repair Signal push outside initial scope. Repair Signal delivery requires a successful safety pilot and explicit abuse review.

## Current next action

Route guards and worldwide `DateUtils` are implemented. Lifecycle defaults, the 5–8-couple safety pilot, deep links, Google/Apple OAuth, Expo Push, and all deferred environment/manual gates remain pending detailed discussion or verification.

## Change log

| Date | Change |
|---|---|
| 2026-09-18 | Created roadmap, feature register and progress tracker. Recorded Spring Boot modular-monolith decision and retired Node backend. |
| 2026-09-18 | Completed Phase 0: 31 automated tests, Flyway V1 baseline, PostgreSQL integration coverage, Auth contracts, rate limiting, readiness, graceful shutdown, request logs and architecture rules. |
| 2026-09-18 | Completed Phase 1 implementation: Flyway V2 Couple lifecycle, transactional pairing, status polling, frontend hydration and PostgreSQL race/security coverage. Exit smoke test remains. |
| 2026-09-18 | Started Phase 2 Story/onboarding persistence. Phase 1's manual two-client restart/recovery check remains explicitly tracked as a pre-release gate. |
| 2026-09-18 | Implemented Phase 2 Story core: Flyway V3, exact PUT replacement, moments/key dates, role-relative birthdays, startup hydration and 49-test package gate. Media upload backend remains pending. |
| 2026-09-18 | Started Phase 3 Media and Memories. Media is first because it closes the explicitly tracked Phase 2 profile/Story photo-upload dependency. |
| 2026-09-18 | Implemented Phase 3 Media: authenticated multipart upload, couple authorization, signature/type/size validation, random keys, local storage adapter and public object retrieval. Full package gate: 54 tests pass. |
| 2026-09-18 | Started Phase 4 Calendar and Home at user direction. Phase 3 Memories remains explicitly open; Calendar can proceed independently, while final Home composition depends on Memories. |
| 2026-09-18 | Implemented Calendar and merged upcoming occasions with Flyway V4, couple-scoped CRUD, soft deletion, annual recurrence, Story key-date merging, and a 64-test package gate. |
| 2026-09-18 | Returned to finish pre-Phase-4 work: implemented Flyway V5 Memories, full shared-memory API, ordered photos, tags, albums, starter creation, On This Day, frontend hydration, and storage lifecycle tests. Further Phase 4 work paused pending command. |
| 2026-09-18 | Phase 4 explicitly resumed. Recorded physical-device URL, dependency repair, two-client recovery, and image-picker checks as pre-release prerequisites rather than implementation blockers. |
| 2026-09-18 | Completed Phase 4: Home read composition, feature application boundaries, Calendar/Home frontend adapters and hydration, and a 79-test package gate. No visual frontend components were changed. |
| 2026-09-18 | Started Phase 5 Messaging and Realtime. Durable, couple-scoped, idempotent HTTP messaging is first; WebSocket remains a notification channel rather than the source of truth. |
| 2026-09-18 | Completed Phase 5 implementation: Flyway V6, durable REST messaging, receipts, per-viewer DTOs, authenticated WebSocket hub/events/heartbeat, frontend retry/catch-up/live updates, and passing automated tests. Maven packaging and physical two-client acceptance remain verification gates. |
| 2026-09-18 | Started Phase 6 at user direction. Timeline, lifecycle safety, export, preferences, and provider-independent boundaries proceed locally; OAuth, push delivery, and AI generation must remain disabled until credentials and privacy policy exist. |
| 2026-09-18 | Implemented Phase 6 local core: Flyway V7, pause/reactivate, reversible unpair and deletion schedules, privacy export, notification preferences/device registration, read-only Timeline, frontend non-visual boundaries, and focused PostgreSQL tests. |
| 2026-09-18 | Started Phase 7 Daily connection: durable static-catalogue questions, reciprocity-gated answers, and completed-exchange Memories. Recorded catalogue, day-boundary, auto-Memory, and visual approvals as pre-release decisions rather than implementation blockers. |
| 2026-09-18 | Completed Phase 7 implementation: Flyway V8, race-safe daily assignment, masked reciprocal answers, exactly-once Memory automation, frontend non-visual integration, and 145 passing backend tests. Production content/visual and environment gates remain. |
| 2026-09-18 | Started Phase 8 Repair signal. Recorded user validation, coercive-control review, visual placement/copy, 24-hour expiry, route-guard/device, and push-safety decisions as pre-release requirements. |
| 2026-09-18 | Completed Phase 8 implementation: Flyway V9, idempotent and concurrency-safe mutual signaling, sender cancellation, quiet expiry, non-visual frontend hydration, and 155 passing backend tests. Safety, visual, package, frontend CLI, and physical-device gates remain. |
| 2026-09-18 | Started Phase 9 Reciprocal Memory notes (`LOV-014`). Recorded real-couple validation, interpersonal-safety, copy/placement, withdrawal, retention/export, route-guard, and two-device decisions as production gates. |
| 2026-09-18 | Completed Phase 9 implementation: Flyway V10, private-until-reciprocal Memory notes, idempotent replacement, withdrawal/re-mask, privacy-scoped export, frontend non-visual integration, and 162 passing backend tests. |
| 2026-09-18 | Implemented `LOV-005` route guards: token/principal/pairing bootstrap, auth/onboarding/app layout enforcement, offline retry, session-state cleanup, and route-policy tests. Recorded the safety pilot, worldwide DateUtils, lifecycle decisions, deep links, OAuth, Expo Push, and deferred environment/manual gates as planned work. |
| 2026-09-18 | Implemented canonical Java/TypeScript `DateUtils`, migrated date-policy-sensitive boundaries, aligned leap-day recurrence, added IANA/DST/date-line/strict-parsing coverage, and passed the complete 170-test editor suite. Other planned workstreams remain pending discussion. |
