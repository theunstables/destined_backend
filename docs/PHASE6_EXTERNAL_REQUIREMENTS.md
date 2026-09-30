# Phase 6 external requirements and transfer checklist

Phase 6's provider-independent implementation is complete. The items below are intentionally not activated with dummy credentials or unsafe fallback behavior.

## OAuth and account linking

Before Google or Apple sign-in/linking is implemented, supply:

- Google OAuth client IDs for Android, iOS, and web, with package/bundle IDs and signing fingerprints.
- Apple Services ID, Team ID, Key ID, private signing key, redirect URI, and verified domain.
- Product decisions for verified-email collisions, provider unlinking, and the rule preventing removal of a user's final login method.
- Test identities for new-account, existing-email, link, unlink, revoked-consent, and provider-outage cases.

Provider tokens must be verified against issuer, audience, signature, expiry, and nonce. The app must not decode and trust an unverified identity token.

## Push delivery and deep links

Device registration and notification preferences are implemented; delivery is disabled until these are supplied:

- Expo access token or FCM/APNs credentials for each environment.
- Android package name, iOS bundle ID, APNs environment, and notification entitlements.
- Retry/dead-letter policy, provider receipt retention, and invalid-token cleanup policy.
- Approved deep-link scheme/universal-link domains and route allow-list.
- Quiet-hour semantics for daylight-saving changes and emergency/security notification exceptions.

Notification payloads must not contain private message text or memory captions on lock screens by default. Provider delivery should run asynchronously and must not roll back committed messages.

## AI and daily questions

Do not enable model calls until all of the following are approved:

- Explicit opt-in and a way to disable AI without losing non-AI features.
- Data categories allowed to leave LoveOS; shared content requires both partners' consent.
- Provider/model, region, training opt-out, retention period, deletion workflow, and subprocessors.
- Prompt-injection handling, moderation categories, crisis/escalation language, and age policy.
- Per-couple rate and spend limits, timeout/retry behavior, and provider-outage UX.
- Audit/log redaction rules. Prompts and private responses must not enter ordinary application logs.

Daily questions can use a reviewed static local catalogue before generative AI is approved, but that catalogue and its product/content review are separate work.

## Verification on the other laptop

No source edits are required for this checklist.

1. Install Java 21 and Maven 3.9.x; verify `java -version` and `mvn -version`.
2. From the Java project, run `mvn clean test`.
3. Run `mvn clean package` and confirm the deployable JAR contains migrations V1–V10 and does not contain embedded PostgreSQL classes.
4. Start with the `pgdev` profile, pair two accounts, create shared data, then verify pause blocks writes while reads remain available.
5. Cancel one unpair request during its grace period. For expiry testing, shorten only the test database timestamp and verify the scheduler archives rather than deletes shared rows.
6. Request/cancel account deletion and download `/v1/account/export`; confirm no password, refresh token, reset token, or device token appears.
7. From the frontend project, restore dependencies with the lockfile, then run `npm run typecheck`, `npm test`, and the configured lint command.
8. Configure `EXPO_PUBLIC_API_URL` and `EXPO_PUBLIC_WS_URL` to the laptop's LAN address for physical devices.
9. Verify two-device Pairing restart recovery and Messaging reconnect/catch-up before release.

The frontend Phase 6 work is deliberately non-visual: typed HTTP/mock adapters, stores, and startup hydration exist, but no Timeline tab or settings controls were added because visual component changes were outside the approved scope.
