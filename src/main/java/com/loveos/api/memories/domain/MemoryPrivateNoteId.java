package com.loveos.api.memories.domain;

import java.io.Serializable;
import java.util.UUID;

public record MemoryPrivateNoteId(UUID memoryId, UUID userId) implements Serializable {}
