package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Feature 001: every allowlisted key is parsed once, bounded, and refused without echoing its
 * value. These tests are the unit-level half of the startup contract the protected check restarts
 * the packaged application for.
 */
class AgoraSettingsTest {

    @Test
    void defaultsAreTheDocumentedCeilingsAndNoDatabase() {
        AgoraSettings settings = AgoraSettings.of(false, 16384, 4000, 32768);
        assertFalse(settings.databaseEnabled());
        assertEquals(16384, settings.requestMaxBytes());
        assertEquals(4000, settings.textMaxCodepoints());
        assertEquals(32768, settings.renderedMaxBytes());
        assertEquals("Europe/Paris", settings.businessZone().getId());
    }

    @Test
    void acceptsBothEndsOfEveryDocumentedRange() {
        AgoraSettings smallest = AgoraSettings.of(true, 1, 1, 1);
        assertTrue(smallest.databaseEnabled());
        assertEquals(1, smallest.requestMaxBytes());
        assertEquals(1, smallest.textMaxCodepoints());
        assertEquals(1, smallest.renderedMaxBytes());

        AgoraSettings largest = new AgoraSettings("TRUE", "16384", "4000", "32768");
        assertTrue(largest.databaseEnabled());
    }

    @Test
    void refusesANonIntegerBoundWithoutEchoingTheValue() {
        IllegalStateException refusal = refused("false", "not-a-number", "4000", "32768");
        assertEquals("invalid_setting: AGORA_REQUEST_MAX_BYTES must be an integer within its permitted range",
                refusal.getMessage());
        assertFalse(refusal.getMessage().contains("not-a-number"));
    }

    @Test
    void refusesEveryBoundOutsideItsRange() {
        assertRefused("false", "0", "4000", "32768", "AGORA_REQUEST_MAX_BYTES");
        assertRefused("false", "16385", "4000", "32768", "AGORA_REQUEST_MAX_BYTES");
        assertRefused("false", "16384", "0", "32768", "AGORA_TEXT_MAX_CODEPOINTS");
        assertRefused("false", "16384", "4001", "32768", "AGORA_TEXT_MAX_CODEPOINTS");
        assertRefused("false", "16384", "4000", "0", "AGORA_RENDERED_MAX_BYTES");
        assertRefused("false", "16384", "4000", "32769", "AGORA_RENDERED_MAX_BYTES");
        assertRefused("false", "999999999999", "4000", "32768", "AGORA_REQUEST_MAX_BYTES");
    }

    @Test
    void refusesAnUnclearDatabaseSwitchWithoutEchoingIt() {
        IllegalStateException refusal = refused("sometimes", "16384", "4000", "32768");
        assertEquals("invalid_setting: AGORA_DATABASE_ENABLED must be true or false", refusal.getMessage());
        assertFalse(refusal.getMessage().contains("sometimes"));
    }

    private static IllegalStateException refused(String database, String request, String text, String rendered) {
        return assertThrows(IllegalStateException.class,
                () -> new AgoraSettings(database, request, text, rendered));
    }

    private static void assertRefused(String database, String request, String text, String rendered, String key) {
        assertEquals("invalid_setting: " + key + " must be an integer within its permitted range",
                refused(database, request, text, rendered).getMessage());
    }
}
