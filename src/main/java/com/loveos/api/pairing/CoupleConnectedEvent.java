package com.loveos.api.pairing;

import java.util.UUID;

/** Published inside the pairing transaction when a couple becomes connected. */
public record CoupleConnectedEvent(UUID coupleId) {}