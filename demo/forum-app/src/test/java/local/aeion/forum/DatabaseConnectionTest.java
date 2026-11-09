package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Feature 001: the database connection settings of a deployment that enables the database.
 *
 * With the database off nothing is required: that is the skeleton. With the database on, a
 * connection must be configured, and the refusal names the key that is missing.
 */
class DatabaseConnectionTest {

    @Test
    void aDatabaseLessDeploymentNeedsNoConnection() {
        assertDoesNotThrow(() -> DatabaseConnection.of(null, null, null, null).selfCheck(false));
    }

    @Test
    void aDatabaseWithoutAConnectionRefusesTheStartAndNamesTheKey() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> DatabaseConnection.of(null, null, null, null).selfCheck(true));
        assertTrue(refusal.getMessage().contains(DatabaseConnection.URL), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("jdbc:"), refusal.getMessage());
    }

    @Test
    void theTransitionalFileIsAConnection() {
        assertDoesNotThrow(() -> DatabaseConnection.of(null, null, null, "/tmp/agora.db").selfCheck(true));
    }

    @Test
    void aPostgreSqlUrlNeedsItsCredentials() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> DatabaseConnection.of("jdbc:postgresql://db/forum", null, null, null).selfCheck(true));
        assertTrue(refusal.getMessage().contains(DatabaseConnection.USER), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(DatabaseConnection.PASSWORD), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("db/forum"), "the refused value must not be written down");

        assertDoesNotThrow(() -> DatabaseConnection
                .of("jdbc:postgresql://db/forum", "forum", "secret", null).selfCheck(true));
    }

    @Test
    void aUrlThatIsNotAJdbcUrlIsRefused() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> DatabaseConnection.of("db/forum", "forum", "secret", null).selfCheck(true));
        assertTrue(refusal.getMessage().contains(DatabaseConnection.URL), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("db/forum"), "the refused value must not be written down");
    }
}
