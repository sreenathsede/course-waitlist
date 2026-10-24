package com.example.waitlist.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Static description of the one course offering managed by this service, bound from the
 * {@code offering.*} keys in application.yml. Validated at start-up so a bad capacity fails fast.
 *
 * @param code              offering identifier
 * @param capacity          maximum enrolled learners (at least 1)
 * @param prerequisite      course code learners must have completed; blank/null means no prerequisite
 * @param lockTimeoutMillis how long a request may wait for the service lock before failing with SERVICE_BUSY (default 2000)
 */
@Validated
@ConfigurationProperties(prefix = "offering")
public record OfferingProperties(@NotBlank String code, @Min(1) int capacity, String prerequisite,
                                 @DefaultValue("2000") @Min(1) long lockTimeoutMillis) {
}
