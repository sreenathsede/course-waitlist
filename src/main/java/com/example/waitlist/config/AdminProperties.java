package com.example.waitlist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Shared secret protecting the records endpoint ({@code PUT /api/learners/{id}/completed-courses}).
 * Supplied through the {@code ADMIN_API_KEY} environment variable, never committed. Blank = endpoint disabled.
 *
 * @param apiKey expected value of the {@code X-Admin-Key} header
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(@DefaultValue("") String apiKey) {
}
