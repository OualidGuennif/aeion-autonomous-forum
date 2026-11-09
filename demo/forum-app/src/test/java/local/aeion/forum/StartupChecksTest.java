package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Feature 001: what the startup check does with a deployment that is not configured.
 *
 * The observer is the last gate before the application serves anything, so what it refuses is
 * what a deployment learns about itself: the key that is wrong, and nothing else.
 */
class StartupChecksTest {

    private static final String GOOD_SECRET = "a-real-secret-with-32-bytes-at-least";

    @Test
    void anInvalidBoundStopsTheStartupAndNamesTheKey() {
        StartupChecks checks = new StartupChecks();
        checks.secret = SessionSecret.of(GOOD_SECRET);
        checks.settings = ForumSettings.of(null, "16385", null, null);
        checks.connection = DatabaseConnection.of(null, null, null, null);
        checks.log = Logger.getLogger(StartupChecksTest.class);

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> checks.onStart(null));
        assertTrue(refusal.getMessage().contains(ForumSettings.REQUEST_MAX_BYTES), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("16385"), "the refused value must not be written down");
    }

    @Test
    void aConfiguredDeploymentPassesTheStartup() {
        StartupChecks checks = new StartupChecks();
        checks.secret = SessionSecret.of(GOOD_SECRET);
        checks.settings = ForumSettings.of(null, null, null, null);
        checks.connection = DatabaseConnection.of(null, null, null, null);
        checks.log = Logger.getLogger(StartupChecksTest.class);

        checks.onStart(null);
    }
}
