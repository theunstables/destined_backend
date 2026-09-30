package com.loveos.api.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loveos.media.s3")
public record S3MediaProperties(
    String bucket, String region, String endpoint, String publicBaseUrl) {}