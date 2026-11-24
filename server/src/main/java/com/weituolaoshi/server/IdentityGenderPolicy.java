package com.weituolaoshi.server;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Gender-only exception; verification, adulthood and account permissions remain mandatory. */
@Component
public class IdentityGenderPolicy {
    private final Set<String> allowedDigests;
    private final AuthSupport auth;
    private final String digestKey;
    @Autowired private JdbcTemplate db;

    public IdentityGenderPolicy(AuthSupport auth,
            @Value("${identity.gender-check-whitelist:}") String numbers,
            @Value("${identity.digest-key:}") String digestKey) {
        this.auth = auth;
        this.digestKey = digestKey;
        allowedDigests = Arrays.stream(numbers.split(","))
            .map(s -> s.trim().toUpperCase(Locale.ROOT)).filter(s -> !s.isEmpty())
            .map(s -> {
                if (!s.matches("[1-9]\\d{16}[0-9X]"))
                    throw new IllegalArgumentException("identity.gender-check-whitelist contains an invalid ID format");
                if (digestKey.isBlank()) throw new IllegalArgumentException("Whitelist requires identity.digest-key");
                return auth.hash(digestKey + ":" + s);
            }).collect(Collectors.toUnmodifiableSet());
    }

    public boolean allowsNumber(String gender, String number) {
        return allowsDigest(gender, auth.hash(digestKey + ":" + number.trim().toUpperCase(Locale.ROOT)));
    }

    public boolean allowsDigest(String gender, String digest) {
        return "F".equals(gender) || (digest != null && (allowedDigests.contains(digest) || (db != null && db.queryForObject("SELECT COUNT(*) FROM identity_test_whitelist WHERE document_digest=?",Integer.class,digest)>0)));
    }

    public String sql(String prefix) {
        if (!prefix.matches("(?:[a-zA-Z_][a-zA-Z0-9_]*\\.)?")) throw new IllegalArgumentException("Invalid SQL prefix");
        String female = prefix + "legal_gender='F'";
        String managed = prefix + "document_digest IN (SELECT document_digest FROM identity_test_whitelist)";
        if (allowedDigests.isEmpty()) return "(" + female + " OR " + managed + ")";
        // Only SHA-256 hex digests are embedded, never configured identity numbers.
        String hashes = allowedDigests.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
        return "(" + female + " OR " + managed + " OR " + prefix + "document_digest IN (" + hashes + "))";
    }
}
