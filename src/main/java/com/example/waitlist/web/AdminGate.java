package com.example.waitlist.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;

import com.example.waitlist.config.AdminProperties;
import com.example.waitlist.service.RegistrationException.AdminDisabled;
import com.example.waitlist.service.RegistrationException.AdminUnauthorized;

/**
 * Minimal gate for the records endpoint: a shared secret in the {@code X-Admin-Key} header. Deliberately not Spring
 * Security (identity integrations are out of scope). Fail-closed: with no key configured the endpoint is disabled.
 * Compared in constant time so response timing does not leak the key.
 */
@Component
public class AdminGate {

    private final byte[] expected;

    public AdminGate(AdminProperties props) {
        String key = props.apiKey();
        this.expected = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @throws AdminDisabled when no key is configured; @throws AdminUnauthorized when the key is missing or wrong
     */
    public void check(String presentedKey) {
        if (expected.length == 0) throw new AdminDisabled();
        byte[] given = presentedKey == null ? new byte[0] : presentedKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, given)) throw new AdminUnauthorized();
    }
}
