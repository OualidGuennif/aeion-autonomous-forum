// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Feature 001's settings, checked the way the application checks them: before anything is served.
 *
 * Every refusal here is the message an operator reads in a log, so each test asserts the same three
 * things — which key is at fault, what the allowed range is, and that the value somebody typed does
 * not appear. Building the bean outside CDI is what makes the refusal readable in a unit test.
 */
class ForumSettingsTest {

    /** Any stated path: the check is about a setting being stated at all, not about the file. */
    private static final String STATED_CONNECTION = "/tmp/agora-settings-test.db";

    @Test
    void absentSettingsTakeTheDocumentedDefaults() {
        ForumSettings settings = ForumSettings.of(null, null, null, null, null, null);
        assertEquals(16384, settings.requestMaxBytes());
        assertEquals(4000, settings.textMaxCodepoints());
        assertEquals(32768, settings.renderedMaxBytes());
        assertEquals(ZoneId.of("Europe/Paris"), settings.businessZone());
        assertFalse(settings.databaseEnabled(), "database-backed features are off unless turned on");
        assertFalse(settings.databaseWorkRequested());
        assertDoesNotThrow(settings::selfCheck);
        assertDoesNotThrow(settings::requireConnectionStated);
    }

    @Test
    void aDatabaseLessBootNeverAsksForAConnectionSetting() {
        for (String unstated : new String[] {null, "", "   "}) {
            ForumSettings settings = ForumSettings.of(null, null, null, null, "false", unstated);
            assertDoesNotThrow(settings::requireConnectionStated);
            assertFalse(settings.databaseWorkRequested(), "nothing is asked of a database nobody enabled");
        }
    }

    @Test
    void enabledDatabaseFeaturesRefuseAnUnstatedConnectionAndNameOnlyTheKeys() {
        for (String unstated : new String[] {null, "", "   "}) {
            ForumSettings settings = ForumSettings.of(null, null, null, null, "true", unstated);
            assertFalse(settings.databaseWorkRequested(), "features on and no connection is not a request");
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    settings::requireConnectionStated, String.valueOf(unstated));
            assertTrue(refusal.getMessage().startsWith("database_connection_missing: AEION_SQLITE_PATH"),
                    refusal.getMessage());
            assertTrue(refusal.getMessage().contains("quarkus.datasource.jdbc.url"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains("AGORA_DATABASE_ENABLED"), refusal.getMessage());
            assertFalse(refusal.getMessage().contains("="), "a refusal must not look like a value");
        }
    }

    @Test
    void aConnectionStatedEitherWayIsAcceptedAndNothingIsRefused() {
        ForumSettings byPath = ForumSettings.of(null, null, null, null, "true", STATED_CONNECTION);
        assertDoesNotThrow(byPath::requireConnectionStated);
        assertTrue(byPath.databaseWorkRequested());

        ForumSettings byDatasourceUrl = ForumSettings.of(null, null, null, null, "true", null,
                "jdbc:postgresql://127.0.0.1:5432/agora");
        assertDoesNotThrow(byDatasourceUrl::requireConnectionStated);
        assertTrue(byDatasourceUrl.databaseWorkRequested());

        // The shipped development URL is not an operator's answer, so it is still a refusal.
        ForumSettings assumed = ForumSettings.of(null, null, null, null, "true", null);
        assertThrows(IllegalStateException.class, assumed::requireConnectionStated);

        // Nor is the empty connection key expanded into a scheme with no database behind it.
        ForumSettings emptyPath = ForumSettings.of(null, null, null, null, "true", null, "jdbc:sqlite:");
        assertThrows(IllegalStateException.class, emptyPath::requireConnectionStated);
        assertFalse(emptyPath.databaseWorkRequested());
    }

    @Test
    void everyBoundedKeyRefusesZeroAndItsCeilingPlusOne() {
        for (ForumSettings.Bound bound : List.of(ForumSettings.REQUEST_MAX_BYTES,
                ForumSettings.TEXT_MAX_CODEPOINTS, ForumSettings.RENDERED_MAX_BYTES)) {
            for (int illegal : new int[] {0, -1, bound.max() + 1}) {
                IllegalStateException refusal = assertThrows(IllegalStateException.class,
                        () -> refuse(bound, Integer.toString(illegal)), bound + "=" + illegal);
                assertTrue(refusal.getMessage().startsWith("setting_out_of_range: " + bound.key()),
                        refusal.getMessage());
                assertTrue(refusal.getMessage().contains("between " + bound.min() + " and " + bound.max()),
                        refusal.getMessage());
            }
            assertDoesNotThrow(() -> refuse(bound, Integer.toString(bound.max())), bound.key() + " ceiling");
            assertDoesNotThrow(() -> refuse(bound, Integer.toString(bound.min())), bound.key() + " floor");
        }
    }

    @Test
    void anInvalidIntegerRefusesStartupAndDoesNotRepeatWhatWasTyped() {
        String typed = "SENTINEL_NOT_A_NUMBER";
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> refuse(ForumSettings.REQUEST_MAX_BYTES, typed));
        assertTrue(refusal.getMessage().startsWith("setting_not_an_integer: AGORA_REQUEST_MAX_BYTES"),
                refusal.getMessage());
        assertFalse(refusal.getMessage().contains(typed), "the refusal must not carry the value");
    }

    @Test
    void aDatabaseFlagThatIsNotABinaryChoiceRefusesStartup() {
        String typed = "maybe";
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> ForumSettings.of(null, null, null, null, typed, STATED_CONNECTION));
        assertTrue(refusal.getMessage().startsWith("setting_not_a_boolean: AGORA_DATABASE_ENABLED"),
                refusal.getMessage());
        assertFalse(refusal.getMessage().contains(typed), refusal.getMessage());
        assertTrue(ForumSettings.of(null, null, null, null, "TRUE", STATED_CONNECTION).databaseEnabled());
        assertFalse(ForumSettings.of(null, null, null, null, "false", null).databaseEnabled());
    }

    @Test
    void aTimezoneThatIsNotARegionRefusesStartup() {
        for (String unusable : new String[] {"Paris/Europe", "UTC", "+02:00", "CEST"}) {
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> ForumSettings.of(null, null, null, unusable, null, null), unusable);
            assertTrue(refusal.getMessage().startsWith("setting_invalid: AGORA_BUSINESS_TIMEZONE"),
                    refusal.getMessage());
            assertFalse(refusal.getMessage().contains(unusable), refusal.getMessage());
        }
        assertEquals(ZoneId.of("Europe/Paris"),
                ForumSettings.of(null, null, null, null, null, null).businessZone());
        assertEquals(ZoneId.of("Europe/Paris"),
                ForumSettings.of(null, null, null, " Europe/Paris ", null, null).businessZone());
    }

    @Test
    void onlyTheDocumentedKeysAreRead() {
        assertEquals(List.of("AGORA_REQUEST_MAX_BYTES", "AGORA_TEXT_MAX_CODEPOINTS",
                "AGORA_RENDERED_MAX_BYTES", "AGORA_BUSINESS_TIMEZONE", "AGORA_DATABASE_ENABLED",
                "AEION_SQLITE_PATH"), ForumSettings.DOCUMENTED_KEYS);
    }

    /** Feed one value into its own bounded key, leaving the others at their defaults. */
    private static ForumSettings refuse(ForumSettings.Bound bound, String value) {
        String request = bound.equals(ForumSettings.REQUEST_MAX_BYTES) ? value : null;
        String text = bound.equals(ForumSettings.TEXT_MAX_CODEPOINTS) ? value : null;
        String rendered = bound.equals(ForumSettings.RENDERED_MAX_BYTES) ? value : null;
        return ForumSettings.of(request, text, rendered, null, "false", null);
    }
}
