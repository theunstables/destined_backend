package com.loveos.api.auth;

import java.util.UUID;

/**
 * The authenticated caller, as the rest of the application sees them.
 *
 * <p>Deliberately NOT the {@code User} entity. A principal is held for the life of
 * a request and read all over the codebase; if it were an entity it would be
 * detached, lazily broken, and one careless getter away from serialising a
 * password hash. This carries the three facts a handler actually needs.
 *
 * <p>THE RULE THAT MAKES AUTHORISATION WORK: {@code userId} comes from the signed
 * token and from nowhere else. No endpoint may take a user id from a path, a
 * query or a body to decide *whose* data to return. That single constraint is
 * what makes IDOR — OWASP A01 — structurally impossible rather than a thing each
 * handler has to remember.
 */
public record AuthenticatedUser(UUID userId, String email, boolean emailVerified) {}
