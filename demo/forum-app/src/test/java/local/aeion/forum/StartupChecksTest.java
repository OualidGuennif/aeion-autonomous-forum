package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Feature 001's startup order: the secret is checked first, then the bounds, then the database
 * plane, then the business timezone — every refusal names its key and nothing else, so removing
 * one setting produces exactly one named key and no value.
 */
class StartupChecksTest {

    private static final String SECRET = "startup-suite-secret-0123456789abcdefghijkl";

    private StartupChecks checks(String secretRaw, String boundRaw, String flagRaw, String zone) {
        StartupChecks checks = new StartupChecks();
        checks.secret = SessionSecret.of(secretRaw);
        checks.bounds = new ForumBounds(
                Optional.ofNullable(boundRaw), Optional.empty(), Optional.empty(), true);
        checks.database = new DatabaseMode(
                Optional.ofNullable(flagRaw),
                flagRaw == null ? Optional.empty() : Optional.of("jdbc:postgresql://host/db"),
                flagRaw == null ? Optional.empty() : Optional.of("user"),
                flagRaw == null ? Optional.empty() : Optional.of("password"),
                true);
        checks.businessZone = Optional.ofNullable(zone);
        return checks;
    }

    @Test
    void aFullyTypedDeploymentVerifies() {
        checks(SECRET, null, null, "Europe/Paris").verify();
    }

    @Test
    void aMissingSecretIsTheFirstRefusalAndNamesOnlyItsKey() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                this::refusedSetup);
        assertTrue(refusal.getMessage().startsWith("session_secret_missing"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("AGORA_SESSION_SECRET"), refusal.getMessage());
        // The other checks' keys do not leak into the secret refusal.
        assertTrue(!refusal.getMessage().contains("AGORA_REQUEST_MAX_BYTES"), refusal.getMessage());
        assertTrue(!refusal.getMessage().contains("AGORA_DATABASE_ENABLED"), refusal.getMessage());
        assertTrue(!refusal.getMessage().contains("business"), refusal.getMessage());
    }

    private void refusedSetup() {
        // A deployment whose only mistake is the missing secret: everything else is valid default.
        StartupChecks checks = new StartupChecks();
        checks.secret = SessionSecret.of(null);
        checks.bounds = new ForumBounds(
                Optional.empty(), Optional.empty(), Optional.empty(), true);
        checks.database = new DatabaseMode(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true);
        checks.businessZone = Optional.of("Europe/Paris");
        checks.verify();
    }

    @Test
    void anInvalidBoundRefusesNamingHigherKeysOnly() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> checks(SECRET, "99999999999", null, "Europe/Paris").verify());
        assertTrue(refusal.getMessage().startsWith("bound_invalid"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("AGORA_REQUEST_MAX_BYTES"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("between 1 and 16384"), refusal.getMessage());
        assertTrue(!refusal.getMessage().contains("99999999999"), "the value must not be echoed");
        assertTrue(!refusal.getMessage().contains("AGORA_DATABASE_ENABLED"), refusal.getMessage());
    }

    @Test
    void aBrokenDatabaseFlagRefusesAfterTheBounds() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> checks(SECRET, null, "maybe", "Europe/Paris").verify());
        assertTrue(refusal.getMessage().startsWith("database_flag_invalid"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("AGORA_DATABASE_ENABLED"), refusal.getMessage());
        assertTrue(!refusal.getMessage().contains("maybe"), "the value must not be echoed");
    }

    @Test
    void anAbsentBusinessTimezoneRefusesNamingTheProperty() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> checks(SECRET, null, null, null).verify());
        assertTrue(refusal.getMessage().startsWith("business_timezone_invalid"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("agora.time.business-zone"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("Europe/Paris"), refusal.getMessage());
    }

    @Test
    void aForeignBusinessTimezoneRefuses() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> checks(SECRET, null, null, "UTC").verify());
        assertTrue(refusal.getMessage().startsWith("business_timezone_invalid"), refusal.getMessage());
        assertTrue(!refusal.getMessage().contains("UTC"), "the value must not be echoed");
    }
}
