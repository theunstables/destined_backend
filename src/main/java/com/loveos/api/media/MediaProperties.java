package com.loveos.api.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "loveos.media")
public record MediaProperties(
    @NotBlank String driver,
    Path localDir,
    @Positive long maxBytes) {}