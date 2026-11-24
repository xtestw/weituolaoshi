package com.weituolaoshi.server;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import static org.junit.jupiter.api.Assertions.*;

class IdentityGenderPolicyTest {
    private final AuthSupport auth = new AuthSupport(null);
    private static final String TEST_ID = "11010119900101123X";

    @Test void emptyWhitelistRetainsGenderRestriction() {
        IdentityGenderPolicy policy = new IdentityGenderPolicy(auth, "", "test-key");
        assertFalse(policy.allowsNumber("M", TEST_ID));
        assertTrue(policy.allowsNumber("F", TEST_ID));
    }

    @Test void matchesExactNormalizedIdentityAndRequiresDigestKey() {
        IdentityGenderPolicy policy = new IdentityGenderPolicy(auth, " " + TEST_ID.toLowerCase() + " ", "test-key");
        assertTrue(policy.allowsNumber("M", TEST_ID));
        assertFalse(policy.allowsNumber("M", "110101199001011221"));
        assertFalse(policy.allowsDigest("M", null));
        assertThrows(IllegalArgumentException.class, () -> new IdentityGenderPolicy(auth, TEST_ID, ""));
        assertThrows(IllegalArgumentException.class, () -> new IdentityGenderPolicy(auth, "*", "test-key"));
        assertFalse(new IdentityGenderPolicy(auth, "", "test-key").allowsNumber("M", TEST_ID));
    }

    @Test void whitelistSqlStillRequiresVerifiedAdultAndDoesNotChangeGender() {
        IdentityGenderPolicy policy = new IdentityGenderPolicy(auth, TEST_ID, "test-key");
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder().setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        try {
            JdbcTemplate db = new JdbcTemplate(database);
            db.execute("CREATE TABLE identity_test_whitelist (document_digest VARCHAR(64))");
            db.execute("CREATE TABLE identities (legal_gender VARCHAR(1), document_digest VARCHAR(64), adult BOOLEAN, status VARCHAR(20))");
            String digest = auth.hash("test-key:" + TEST_ID);
            db.update("INSERT INTO identities VALUES ('M',?,TRUE,'verified')", digest);
            db.update("INSERT INTO identities VALUES ('M',?,FALSE,'verified')", digest);
            db.update("INSERT INTO identities VALUES ('M',?,TRUE,'pending')", digest);
            db.update("INSERT INTO identities VALUES ('M','not-listed',TRUE,'verified')");
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM identities v WHERE v.adult=TRUE AND v.status='verified' AND " + policy.sql("v."), Integer.class));
            assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM identities WHERE legal_gender='M'", Integer.class));
        } finally { database.shutdown(); }
    }
}
