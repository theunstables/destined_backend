# LoveOS Feature Register

Last updated: 2026-09-18

This is the product and backend feature inventory for the Spring Boot implementation. The Node backend is retired and is not an implementation target or runtime dependency.

## Status legend

- ✅ Complete and verified
- 🔄 In progress
- ⬜ Planned
- ⏸ Deferred pending product or infrastructure decisions

## Features

| Area | Capability | Backend | Frontend | Dependencies / notes |
|---|---|---:|---:|---|
| Platform | Success/error envelopes, request IDs, validation and exception handling | ✅ | ✅ | Shared foundation |
| Platform | PostgreSQL configuration and persistent local PostgreSQL development profile | ✅ | N/A | PostgreSQL is the only database |
| Platform | Automated tests and build gates | ✅ | ✅ | Complete workspace runner passes; Phase 5 Maven package rerun pending PATH repair |
| Platform | Flyway migrations | ✅ | N/A | V1–V10; Hibernate validates after migration |
| Platform | Rate limiting | ✅ | N/A | In-memory single-instance limiter; replace with shared limiter when scaling out |
| Platform | Separate liveness/readiness and observability | ✅ | N/A | PostgreSQL readiness, graceful shutdown and request completion logs |
| Auth | Signup, login, refresh and logout | ✅ | Adapter ready | End-to-end backend verified |
| Auth | Email verification and resend | ✅ | Adapter ready | Console mail in development |
| Auth | Forgot/reset password | ✅ | Adapter ready | Reset revokes sessions |
| Auth | Current-user endpoint | ✅ | Adapter ready | Used during app bootstrap |
| Auth | OAuth/account linking | ⏸ | UI controls deferred | Requires provider credentials and linking policy |
| Auth | Device/session management | ⏸ | ⬜ | Later account feature |
| Couple | User profile | ✅ | ✅ | Name, nickname, pronouns, birthday and photo |
| Couple | Invite create/cancel/redeem/confirm | ✅ | ✅ | PostgreSQL locks and constraints protect races |
| Couple | Pairing status polling | ✅ | ✅ | Waiting screen polls; WebSocket is not required |
| Couple | Shared space | ✅ | ✅ | Name, short name, cover style and server hydration |
| Story | Story get/replace | ✅ | ✅ | Idempotent whole-story replacement and startup hydration |
| Story | Moments and date precision | ✅ | ✅ | Met, first date, became us, first memory |
| Story | Important dates | ✅ | ✅ | Role-relative birthdays; feeds Calendar and Home |
| Media | Image/audio/video upload | ✅ | ✅ | Atomic local and S3-compatible production providers |
| Memories | List/get/create/favorite/search | ✅ | ✅ | Couple-scoped authorization and startup hydration |
| Memories | Update and soft delete | ✅ | Existing inactive controls unchanged | Backend contract complete |
| Memories | Ordered photos, tags and albums | ✅ | Adapter-ready | Starter albums created after pairing |
| Memories | On This Day | ✅ | Adapter-ready | Month/day projection |
| Memories | Reciprocal private notes | ✅ | Adapter/store ready | Partner note masked until both submit; withdrawal re-masks; no timing metadata |
| Calendar | Event CRUD and annual recurrence | ✅ | ✅ | Couple-scoped adapter/store/hydration |
| Calendar | Upcoming occasions | ✅ | ✅ | Combines Story key dates and calendar events |
| Home | Composite dashboard | ✅ | ✅ | Read model over Couple, Story, Calendar and Memories |
| Chat | Message list/send/retry/reply | ✅ | ✅ | Connected-couple only; stable retry idempotency |
| Chat | Reactions, pins, search and soft delete | ✅ | ✅ | Couple-scoped; live updates replace existing bubbles |
| Chat | Delivery and read receipts | ✅ | ✅ | Bulk read acknowledgement and per-viewer status |
| Realtime | Authenticated WebSocket and heartbeat | ✅ | ✅ | Colocated with Chat initially |
| Realtime | Typing, presence and message/status/update events | ✅ | ✅ | Redis/pub-sub required only for multiple instances |
| Profile | Export, pause/reactivate, unpair grace and account deletion | ✅ | Adapter/store ready | Account tombstoning preserves couple-owned history |
| Notifications | Preferences, quiet hours and device registration | ✅ | Adapter/store ready | Provider-independent state only |
| Notifications | Push delivery and deep links | ⏸ | Not configured | Requires Expo/FCM/APNs credentials and URL policy |
| Timeline | Story, Memories and Calendar projection | ✅ | Adapter/store ready | Read-only; owns no persistence or visual route yet |
| Connection | Daily Question assignment and reciprocity gate | ✅ | Adapter/store ready | Static catalogue; partner answer hidden until both answer |
| Connection | Completed questions become Memories | ✅ | Hydrated through existing Memories | Exactly one tagged Memory per completed daily exchange |
| Connection | Repair Signal | ✅ | Adapter/store ready | One quiet 24-hour signal; symmetric mutual readiness; sender cancellation; no read telemetry |
| AI | Adaptive/generated questions and drafted notes | ⏸ | Placeholder cards exist | Privacy, retention, moderation and cost policy required |
| Security | End-to-end encryption | ⏸ | N/A | Requires a separate product/security architecture |

## Dependency order

```text
Platform → Auth → Couple/Pairing
                    ├─ Story ────────┬─ Calendar ─┐
                    ├─ Media ────────┼─ Memories ─┼─ Home
                    └─ Media + Couple┴─ Chat + Realtime
```

## Product boundaries

- The authenticated user ID always comes from the verified access token, never a request body or path supplied by the client.
- Shared content is authorized by couple membership on every query.
- Controllers translate HTTP; services own business rules; repositories and JPA entities remain internal to their feature.
- REST DTOs are stable contracts. JPA entities never cross the wire.
- PostgreSQL is the only database. Embedded PostgreSQL is development-only and excluded from deployable artifacts.
