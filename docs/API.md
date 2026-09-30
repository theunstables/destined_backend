# LoveOS Spring API

Base URL: `{PUBLIC_API_URL}/v1`; local default: `http://localhost:4000/v1`.

Authenticated requests send `Authorization: Bearer <accessToken>`. Pairing routes require a verified account. Successful JSON responses use `{ "data": ... }`; errors use `{ "error": { "code": "...", "message": "...", "issues": [...] }, "requestId": "..." }`. A `204` response has no body.

## Pairing — `/pairing`

| Method | Path | Request | Response |
|---|---|---|---|
| `GET` | `/profile` | — | `Profile` |
| `PUT` | `/profile` | `Profile` | `Profile` |
| `POST` | `/invites` | no body | `201 Invite` |
| `POST` | `/invites/cancel` | `{ "code": "ABC234" }` | `204` |
| `POST` | `/redeem` | `{ "code": "ABC234" }` | `Partner` |
| `POST` | `/confirm` | `{ "partnerId": "uuid" }` | `Partner` |
| `GET` | `/space` | — | `Space` |
| `PATCH` | `/space` | partial space metadata | `Space` |
| `GET` | `/lifecycle` | — | `Lifecycle` |
| `POST` | `/lifecycle/pause` | no body | `Lifecycle` |
| `POST` | `/lifecycle/reactivate` | no body | `Lifecycle` |
| `POST` | `/lifecycle/unpair` | no body | `Lifecycle` |
| `POST` | `/lifecycle/unpair/cancel` | no body | `Lifecycle` |

### Profile

```json
{
  "name": "Alex",
  "nickname": "Al",
  "pronouns": "they/them",
  "birthday": "1997-04-12",
  "photoUri": "https://cdn.example/alex.jpg"
}
```

`name` is required. Optional blank fields are stored as absent. Birthdays are calendar dates, not instants. Photos must already be remote HTTP(S) URLs; the frontend media adapter uploads local files first.

### Invite and partner

```json
{ "code": "ABC234", "expiresAt": "2026-09-19T00:00:00Z" }
```

```json
{ "id": "uuid", "name": "Alex", "photoUri": "https://cdn.example/alex.jpg" }
```

Codes are six unambiguous uppercase characters and expire after 24 hours. Input normalization ignores case and display separators. Redeeming only reserves the invitation and returns its creator; confirming commits the relationship. The server derives both users and the couple from the signed principal and reserved invite, never from a caller-supplied couple ID.

### Space and status polling

```json
{
  "coupleId": "uuid",
  "name": "Our Place",
  "shortName": "Us",
  "coverStyle": "dawn",
  "status": "connected",
  "partner": { "id": "uuid", "name": "Alex" }
}
```

`GET /pairing/space` always succeeds for an authenticated, verified account. `status` is one of:

- `none`: no membership or reserved invite.
- `inviting`: the caller issued an active invite.
- `pending`: an invite has been redeemed but not confirmed.
- `connected`: both members are committed to the couple.
- `paused`: members retain read access but shared writes are disabled.
- `archived`: the seven-day unpair grace period completed; history is retained read-only.

The inviter waiting screen polls this endpoint every two seconds. HTTP remains the source of truth; Realtime may replace the polling trigger later.

`PATCH /pairing/space` accepts any subset of `name`, `shortName`, and `coverStyle` (`dawn`, `dusk`, or `night`).

Lifecycle changes are idempotent. Pausing blocks writes through every
couple-scoped feature update boundary. Requesting unpair also pauses immediately
and starts a seven-day grace period; either member may cancel. Reactivation is
allowed after a plain pause or after cancelling unpair. Expired requests archive
the space without deleting shared content.

```json
{
  "status": "paused",
  "sharedWritesAllowed": false,
  "unpairPending": true,
  "unpairRequestedByMe": true,
  "pausedAt": "2026-09-18T12:00:00Z",
  "unpairRequestedAt": "2026-09-18T12:00:00Z",
  "unpairExpiresAt": "2026-09-25T12:00:00Z"
}
```

## Pairing errors

| Code | HTTP | Meaning |
|---|---:|---|
| `CODE_INVALID` | 404 | Unknown, cancelled, used, or unreserved invitation |
| `CODE_EXPIRED` | 410 | Invitation is past its expiry |
| `CANNOT_PAIR_WITH_SELF` | 409 | Caller submitted their own invitation |
| `ALREADY_PAIRED` | 409 | Caller already has a pending or connected couple |
| `NO_COUPLE` | 403 | Shared-space operation requires membership |
| `VALIDATION_ERROR` | 422 | Parsed request failed field validation |
| `RATE_LIMITED` | 429 | Invite/redeem/confirm request exceeded its limit |

## Persistence and concurrency

Flyway migration `V2__couple_lifecycle.sql` owns the `couples`, `couple_members`, and `invites` tables. PostgreSQL constraints enforce one couple membership per user, unique codes, and one active invitation per couple. User and invite rows are locked during claims and confirmation, so simultaneous redeemers cannot create a three-person couple. Confirmation atomically creates membership, connects the couple, and accepts the invite.

## Story — `/story`

| Method | Path | Request | Response |
|---|---|---|---|
| `GET` | `/story` | — | `Story` |
| `PUT` | `/story` | complete `Story` | server `Story` |

Both routes require a verified account with couple membership. A founder may
write while an invitation is still pending; Story does not require the couple
to be connected. The couple ID and role come only from the authenticated user's
membership.

```json
{
  "met": { "value": "2020-01-01", "precision": "monthYear" },
  "firstDate": {
    "date": "2020-07-04",
    "location": "Rome",
    "note": "Our first date",
    "photoUri": "https://cdn.example/first-date.jpg"
  },
  "becameUs": { "date": "2020-09-12", "note": "We made it official" },
  "firstMemory": { "date": "2020-10-01", "note": "Our first trip" },
  "keyDates": {
    "anniversary": "2020-09-12",
    "yourBirthday": "1990-02-03",
    "partnerBirthday": "1991-04-05",
    "firstDate": "2020-07-04",
    "firstMeeting": "2020-01-01"
  }
}
```

All top-level fields are optional and `{}` is valid. Dates are calendar days in
`YYYY-MM-DD` format. Precision is `exact`, `monthYear`, or `yearOnly`.
Locations are limited to 120 characters, notes to 2,000, and photos must be
remote HTTP(S) URLs. Local photos are uploaded by the frontend media adapter
before Story is saved.

`PUT` is a complete replacement: omitted moments and key dates are removed, so
retries converge to exactly one story. When explicit values are absent,
`met.value` is mirrored to `firstMeeting` and `firstDate.date` is mirrored to
the `firstDate` key date. Birthdays are stored as founder/member facts and
returned as `yourBirthday`/`partnerBirthday` relative to the caller.

`GET` before the first save returns `{ "keyDates": {} }`. Flyway migration
`V3__story_onboarding.sql` owns `stories`, `story_moments`, and `key_dates`.

## Media — `/media`

`POST /media` requires a verified account with couple membership. Send
`multipart/form-data` with exactly one file part named `file`. The default size
limit is 25 MB.

```json
{
  "url": "http://localhost:4000/media/68be67bd-b861-4677-b66f-937d9c52ed90.jpg",
  "contentType": "image/jpeg",
  "bytes": 184204
}
```

The response status is `201`. Accepted content types are JPEG, PNG, WebP, HEIC,
MP4, QuickTime MOV, M4A/MP4 audio, MP3, WebM audio, and Ogg audio. The server
checks both the declared type and the file signature, discards the client
filename, and stores the object under a random UUID key.

The returned object URL is outside the `/v1` namespace and can be fetched
without an Authorization header so native image and media controls can render
it. Keys are unguessable; responses include strict content type, CSP, and
`nosniff` headers. Errors are `INVALID_REQUEST`, `UNSUPPORTED_MEDIA_TYPE`,
`PAYLOAD_TOO_LARGE`, or `NO_COUPLE` in the standard error envelope.

Development uses atomic local-file storage. `MEDIA_DRIVER=s3` selects the
production S3-compatible adapter and returns URLs under
`MEDIA_S3_PUBLIC_BASE_URL`; credentials come from the AWS SDK provider chain.

## Calendar — `/calendar`

All routes require a verified account with couple membership. IDs are always
resolved within the caller's couple scope.

| Method | Path | Request | Response |
|---|---|---|---|
| `GET` | `/calendar?from=YYYY-MM-DD&to=YYYY-MM-DD&limit=100` | — | `Event[]` |
| `POST` | `/calendar` | complete event | `201 Event` |
| `GET` | `/calendar/{id}` | — | `Event` |
| `PATCH` | `/calendar/{id}` | partial event | `Event` |
| `DELETE` | `/calendar/{id}` | — | `204` |
| `GET` | `/calendar/upcoming?withinDays=365&limit=10` | — | `ComingUp[]` |

```json
{
  "title": "Date night",
  "date": "2026-10-01",
  "startsAt": "2026-10-01T18:00:00Z",
  "endsAt": "2026-10-01T21:00:00Z",
  "location": "Rome",
  "notes": "Dinner reservation",
  "kind": "dateNight",
  "repeatsAnnually": false,
  "reminderMinutesBefore": 60
}
```

Kinds are `anniversary`, `birthday`, `dateNight`, `trip`, `reminder`, and
`custom`. Titles are limited to 160 characters, notes to 2,000, and reminders
to 0–43,200 minutes. `endsAt` cannot precede `startsAt`. Deletion is soft, and
all reads exclude deleted events.

`upcoming` merges active Calendar events with Story key dates. Annual dates use
their next occurrence, one-off past events are excluded, and results are sorted
by days remaining. Birthday labels are relative to the viewer and use the
partner's nickname/display name when available.

Flyway migration `V4__calendar_events.sql` owns `calendar_events`.

## Home — `/home`

`GET /home` requires a verified account with couple membership and returns the
read-only dashboard composition. Home owns no tables: it reads Pairing, Story,
Memories, and Calendar through their feature application APIs.

The response includes `coupleName`, viewer-relative `greetingName`, shared
`space`, nullable `daysTogether`, `stats`, merged `comingUp`, up to six
`recentMemories`, and the relationship `pulse`. The standard success envelope
wraps the object in `data`.

```json
{
  "coupleName": "Alex & Sam",
  "greetingName": "Alex",
  "space": { "name": "Our Space", "shortName": "OS", "coverStyle": "warm" },
  "daysTogether": 365,
  "stats": [
    { "key": "memories", "icon": "images", "value": "12", "label": "Memories", "unit": "" }
  ],
  "comingUp": [],
  "recentMemories": [],
  "pulse": { "label": "Relationship pulse", "value": "365", "unit": "days", "caption": "since we first met." }
}
```

## Memories — `/memories`

All routes require a verified account with couple membership. Every lookup is
scoped by the authenticated caller's couple; a valid ID from another couple is
reported as `NOT_FOUND`.

| Method | Path | Result |
|---|---|---|
| `GET` | `/memories` | paginated `Memory[]` plus `meta.nextCursor` |
| `POST` | `/memories` | `201 Memory` |
| `GET` | `/memories/search?q=...` | matching `Memory[]` |
| `GET` | `/memories/{id}` | `Memory` |
| `PATCH` | `/memories/{id}` | updated `Memory` |
| `PUT` | `/memories/{id}/private-note` | `{ "note": "..." }` → updated `Memory` |
| `DELETE` | `/memories/{id}/private-note` | updated `Memory` with the viewer's note withdrawn |
| `POST` | `/memories/{id}/favorite` | server-toggled `Memory` |
| `DELETE` | `/memories/{id}` | `204`, soft deletion |

List parameters are `cursor`, `limit` (1–100, default 50), `tag`, `albumId`,
`favorite`, and `onDay` (`MM-DD`). Results sort by memory date descending and
then ID descending. Cursors are opaque IDs returned by the preceding page.
Search is case-insensitive across title, caption, note, location, and tags.

```json
{
  "title": "Rome",
  "date": "2024-06-12",
  "caption": "A perfect afternoon",
  "location": "Rome",
  "note": "Our private note",
  "photoUri": "https://api.example/media/cover.jpg",
  "photos": ["https://api.example/media/second.jpg"],
  "tags": ["Trips", "Us"],
  "favorite": true,
  "albumIds": ["uuid"]
}
```

`title` is limited to 200 characters, caption to 500, location to 160, note to
4,000, tags to 20 values of 40 characters, photos to 20 unique HTTP(S) URLs,
and album links to 10. Position zero is the cover returned as `photoUri`.
Photo and tag arrays on PATCH replace those collections when supplied. Tags
are case-insensitive per couple and retain the first entered display spelling.

The existing `note` remains a shared couple field. Reciprocal private notes are
separate: each member may store one non-blank note of up to 4,000 characters.
`myPrivateNote` is visible to its author immediately; `partnerPrivateNote` is
`null` and `reciprocalNotesRevealed` is false until both members have submitted.
The API exposes no submission timestamps or response-delay information. PUT is
idempotent and replaces only the caller's note. DELETE withdraws only the
caller's note and re-masks the partner note; previously read text cannot be made
unseen. Private-note writes use the connected/writable couple boundary and
cross-couple Memory IDs return `NOT_FOUND`.

### Albums

| Method | Path | Result |
|---|---|---|
| `GET` | `/memories/albums` | `Album[]` |
| `POST` | `/memories/albums` | `201 Album` |
| `GET` | `/memories/albums/{id-or-systemKey}` | `Album` |
| `PUT` | `/memories/albums/{id}/memories/{memoryId}` | `204` |
| `DELETE` | `/memories/albums/{id}/memories/{memoryId}` | `204` |

Connected couples receive `us`, `trips`, `dates`, `birthdays`, and
`littlethings` starter albums. Creation happens when pairing confirms, with an
idempotent read-time backfill for couples connected before this migration.
Album membership is idempotent and both sides are couple-scoped.

Flyway migration `V5__memories.sql` owns Memories, photos, tags, albums, and
their junction tables. `V10__reciprocal_memory_notes.sql` owns member-private
notes without changing the shared `memories.note` contract.

## Messaging — `/chat`

All routes require a verified account in a connected couple. Couple and viewer
identity always come from the access-token principal; no request accepts either
ID. Message and reaction `authorId` values are viewer-relative: `me` or
`partner`.

| Method | Path | Result |
|---|---|---|
| `GET` | `/chat/messages?cursor&limit=50` | chronological `Message[]` plus `meta.nextCursor` |
| `GET` | `/chat/messages/pinned` | up to 50 pinned `Message[]` |
| `GET` | `/chat/messages/search?q=...&limit=50` | case-insensitive body matches |
| `POST` | `/chat/messages` | `201 Message`; duplicate `clientId` returns `200` and the original |
| `POST` | `/chat/messages/{id}/reactions` | reaction-toggled `Message` |
| `POST` | `/chat/messages/{id}/pin` | pin-toggled `Message` |
| `POST` | `/chat/read` | `{ messageIds }` changed through the marker |
| `DELETE` | `/chat/messages/{id}` | `204`; sender-only soft deletion |

Kinds are `text`, `photo`, `voice`, and `video`. Text requires a nonblank body;
other kinds require an HTTP(S) `mediaUri`. Bodies are limited to 4,000
characters, media URLs to 2,048, voice/video duration to one hour, emoji to
eight characters, and optional client-generated idempotency keys to 8–64
characters. A reply target must be an active message in the same couple.

Flyway migration `V6__messaging.sql` owns `messages`, `message_reactions`, and
`message_receipts`.

## Account privacy — `/account`

| Method | Path | Result |
|---|---|---|
| `GET` | `/account/export` | generated JSON export of personal and couple-owned data |
| `GET` | `/account/deletion` | current deletion schedule |
| `POST` | `/account/deletion` | create or restart a 30-day deletion grace period |
| `DELETE` | `/account/deletion` | cancel a pending deletion |

Exports omit password hashes, access/refresh tokens, verification/reset tokens,
and registered device tokens. They include the caller's visible profile and the
shared Story, Calendar, Memories, albums, messages, reactions, and receipts for
their couple.

Deletion remains reversible for 30 days. When due, a scheduled transaction
anonymizes personal profile/login fields, revokes refresh and recovery tokens,
and deactivates devices. It retains the user tombstone, membership, and shared
content so deleting one partner's account cannot destroy the other partner's
history.

## Notifications — `/notifications`

| Method | Path | Request | Result |
|---|---|---|---|
| `GET` | `/preferences` | — | current preferences; defaults are created lazily |
| `PUT` | `/preferences` | complete preferences | updated preferences |
| `POST` | `/devices` | `{ token, platform }` | `201` registered device |
| `DELETE` | `/devices` | `{ token }` | `204`, idempotent deactivation |

Preferences contain `enabled`, `messages`, `occasions`, `memories`, optional
paired `quietStart`/`quietEnd` local times, and a validated IANA `timezone`.
Platforms are `EXPO`, `IOS`, `ANDROID`, and `WEB`. Registration is an
idempotent ownership transfer for a unique token. These endpoints persist
provider-independent state only; they do not claim that push delivery is active.

## Timeline — `/timeline`

`GET /timeline?cursor&limit=30` returns newest-first `TimelineItem[]` and
`meta.nextCursor`. Limit is 1–100 and cursors are opaque. Item types are
`memory`, `calendar`, `story`, and `occasion`.

```json
{
  "id": "uuid",
  "type": "memory",
  "date": "2026-09-18",
  "title": "Rome",
  "subtitle": "A perfect afternoon",
  "mediaUrl": "https://cdn.example/photo.jpg"
}
```

Timeline owns no persistence. It is a read-only, couple-scoped, keyset-paginated
projection over active Memories and Calendar events plus Story moments and key
dates.

## Daily Question — `/daily-question`

Both routes require a verified account in a connected, writable couple.
Assignments use a shared UTC calendar day so both partners always receive the
same prompt.

| Method | Path | Request | Result |
|---|---|---|---|
| `GET` | `/daily-question` | — | today's `DailyQuestion` |
| `PUT` | `/daily-question/answer` | `{ "answer": "..." }` | updated `DailyQuestion` |

```json
{
  "id": "uuid",
  "date": "2026-09-18",
  "prompt": "What small thing made you smile today?",
  "status": "waiting",
  "partnerHasAnswered": true,
  "myAnswer": "Coffee together.",
  "partnerAnswer": null,
  "answeredAt": "2026-09-18T12:00:00Z",
  "partnerAnsweredAt": null,
  "memoryId": null
}
```

Status is `unanswered`, `waiting`, or `complete`. Each member can replace their
own answer, limited to 1,500 characters. `partnerHasAnswered` may support a
neutral waiting state, but `partnerAnswer` and `partnerAnsweredAt` remain absent
until both members answer. Completion creates exactly one shared Memory tagged
`Daily Questions`; later answer edits update that Memory instead of duplicating
it. Prompt assignment and answer completion are concurrency-safe.

Flyway migration `V8__daily_questions.sql` owns the static catalogue, daily
assignments, and answers. Prompt text is snapshotted into each assignment.
Generated or adaptive questions are not enabled.

## Repair Signal — `/repair-signal`

The signal is available only to members of a connected couple. It deliberately
has no message body, dismissal response, read/open receipt, response-time field,
streak, reminder, or location/activity information.

| Method | Path | Request | Result |
|---|---|---|---|
| `GET` | `/repair-signal` | — | active `RepairSignal` or `null` |
| `POST` | `/repair-signal` | — | created, existing, or mutually completed `RepairSignal` |
| `DELETE` | `/repair-signal` | — | `204 No Content` |

```json
{
  "id": "uuid",
  "status": "open",
  "sentByMe": true,
  "mutual": false,
  "createdAt": "2026-09-18T12:00:00Z",
  "partnerSignaledAt": null,
  "expiresAt": "2026-09-19T12:00:00Z"
}
```

The first send creates an `open` signal. A retry by that sender is idempotent;
the other member sending the same signal changes it to `mutual`. Only the
original sender may cancel. Signals expire quietly after 24 hours, and elapsed
signals are omitted rather than surfaced as a negative state. Sending is
blocked while the couple is paused, but sender cancellation remains available.
Flyway migration `V9__repair_signals.sql` owns persistence and the one-active-
signal-per-couple constraint.

## Realtime — `/ws`

Connect with the short-lived access token as `/ws?token=<accessToken>`.
The handshake rejects invalid/unverified users and users without a connected
partner before establishing the socket. Refresh tokens are never accepted.

Client frames:

- `{ "type": "typing", "isTyping": true }`
- `{ "type": "read", "upToMessageId": "uuid" }`
- `{ "type": "ping" }`

Server frames use `message`, `typing`, `status`, `reaction`, `pin`, `presence`,
or `pong`. Protocol ping/pong runs every 30 seconds and removes stale sockets.
The socket is a notification channel only: HTTP remains authoritative for
writes and reconnect catch-up. The current hub is in-process; horizontal scale
requires replacing its publish boundary with shared pub/sub.
