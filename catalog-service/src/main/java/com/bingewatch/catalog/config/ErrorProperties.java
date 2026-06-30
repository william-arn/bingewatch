package com.bingewatch.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bingewatch.errors")
public record ErrorProperties(String baseUri) {
}
