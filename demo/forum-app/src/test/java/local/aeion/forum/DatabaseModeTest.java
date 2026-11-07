package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Feature 001's database gate: an absent flag means database-less, a real boolean is read exactly,
 * and an enabled database-backed deployment must name all three PostgreSQL connection settings.
 * Every refusal names its key and nothing else.
 */
class DatabaseModeTest {

    private DatabaseMode mode(String enabled, String url, String user, String password) {
        return new DatabaseMode(
                Optional.ofNullable(enabled),
                Optional.ofNullable(url),
                Optional.ofNullable(user),
                Optional.ofNullable(password),
                true);
    }

    @Test
    void anAbsentFlagMeansDatabaseLess() {
        DatabaseMode absent = mode(null, null, null, null);
        absent.selfCheck();
        assertFalse(absent.enabled(), "the default must be database-less boot");
    }

    @Test
    void falseMeansDatabaseLessEvenWithPostgreSqlSettings() {
        DatabaseMode off = mode("false", "jdbc:postgresql://host/db", "user", "password");
        off.selfCheck();
        assertFalse(off.enabled(), "unrequested settings are ignored, not second-guessed");
    }

    @Test
    void trueMeansDatabaseBacked() {
        DatabaseMode on = mode("true", "jdbc:postgresql://host/db", "user", "password");
        on.selfCheck();
        assertTrue(on.enabled());
    }

    @Test
    void theFlagIsReadWithoutCasingOrPadding() {
        assertTrue(mode("TRUE ", "jdbc:postgresql://host/db", "user", "password").enabled());
        assertFalse(mode(" False", null, null, null).enabled());
    }

    @Test
    void aFlagThatIsNotAFlagRefusesNamingOnlyTheKey() {
        for (String notAFlag : new String[] {"on", "1", "yes"}) {
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    mode(notAFlag, "jdbc:postgresql://host/db", "user", "password")::selfCheck,
                    notAFlag);
            assertTrue(refusal.getMessage().startsWith("database_flag_invalid"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains("AGORA_DATABASE_ENABLED"), refusal.getMessage());
            assertFalse(refusal.getMessage().contains(notAFlag), "the value must not be echoed");
        }
    }

    @Test
    void aBlankFlagRefusesWithoutBecomingDatabaseLess() {
        // A blank provisioning is a mistake, not a silent database-less boot: an absent key is the
        // documented default, a present unreadable one refuses.
        for (String blank : new String[] {"", "  "}) {
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    mode(blank, "jdbc:postgresql://host/db", "user", "password")::selfCheck,
                    "[" + blank + "]");
            assertTrue(refusal.getMessage().startsWith("database_flag_invalid"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains("AGORA_DATABASE_ENABLED"), refusal.getMessage());
        }
    }

    @Test
    void aDatabaseBackedDeploymentWithoutItsUrlRefusesNamingTheUrl() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                mode("true", null, "user", "password")::selfCheck);
        assertTrue(refusal.getMessage().startsWith("database_setting_missing"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("AGORA_DATABASE_URL"), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("AGORA_DATABASE_USER"), refusal.getMessage());
    }

    @Test
    void aDatasetThatIsBlankCountsWithoutAValue() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                mode("true", "   ", "user", "password")::selfCheck);
        assertTrue(refusal.getMessage().contains("AGORA_DATABASE_URL"), refusal.getMessage());
    }

    @Test
    void aDatabaseBackedDeploymentWithoutItsUserOrPasswordRefusesNamingThatKey() {
        IllegalStateException noUser = assertThrows(IllegalStateException.class,
                mode("true", "jdbc:postgresql://host/db", null, "password")::selfCheck);
        assertTrue(noUser.getMessage().contains("AGORA_DATABASE_USER"), noUser.getMessage());
        assertFalse(noUser.getMessage().contains("AGORA_DATABASE_PASSWORD"), noUser.getMessage());

        IllegalStateException noPassword = assertThrows(IllegalStateException.class,
                mode("true", "jdbc:postgresql://host/db", "user", null)::selfCheck);
        assertTrue(noPassword.getMessage().contains("AGORA_DATABASE_PASSWORD"), noPassword.getMessage());
        assertFalse(noPassword.getMessage().contains("AGORA_DATABASE_USER"), noPassword.getMessage());
    }
}
