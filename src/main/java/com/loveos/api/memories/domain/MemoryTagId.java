package com.loveos.api.memories.domain;

import java.io.Serializable;
import java.util.UUID;

public record MemoryTagId(UUID memoryId, UUID tagId) implements Serializable {}