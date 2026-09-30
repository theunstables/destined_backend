package com.loveos.api.memories.domain;

import java.io.Serializable;
import java.util.UUID;

public record AlbumMemoryId(UUID albumId, UUID memoryId) implements Serializable {}