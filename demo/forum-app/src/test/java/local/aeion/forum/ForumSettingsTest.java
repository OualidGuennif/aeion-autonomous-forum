package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Feature 001: the typed configuration, its bounds and the refusals it makes.
 *
 * A refusal has to say which key is wrong without saying what was typed: the message is read by
 * an operator with a log in front of them, and a value that leaks into it is a value that leaks.
 */
class ForumSettingsTest {

    @Test
    void anAbsentDatabaseMeansTheDatabaseBackedFeaturesAreOff() {
        assertFalse(ForumSettings.of(null, null, null, null).databaseEnabled(),
                "a deployment that says nothing about its database gets the skeleton alone");
        assertFalse(ForumSettings.of("false", null, null, null).databaseEnabled());
        assertFalse(ForumSettings.of("  ", null, null, null).databaseEnabled());
        assertTrue(ForumSettings.of("true", null, null, null).databaseEnabled());
        assertTrue(ForumSettings.of("TRUE", null, null, null).databaseEnabled());
    }

    @Test
    void theDefaultsAreTheDocumentedCeilings() {
        ForumSettings settings = ForumSettings.of(null, null, null, null);
        assertEquals(16384, settings.requestMaxBytes());
        assertEquals(4000, settings.textMaxCodepoints());
        assertEquals(32768, settings.renderedMaxBytes());
        assertEquals(ZoneId.of("Europe/Paris"), settings.businessZone());
    }

    @Test
    void aValueInsideItsRangeIsKept() {
        ForumSettings settings = ForumSettings.of("true", "1024", "512", "2048");
        assertTrue(settings.databaseEnabled());
        assertEquals(1024, settings.requestMaxBytes());
        assertEquals(512, settings.textMaxCodepoints());
        assertEquals(2048, settings.renderedMaxBytes());
    }

    @Test
    void aValueOutsideItsRangeRefusesTheStartAndNamesTheKey() {
        assertRefuses("16385", ForumSettings.REQUEST_MAX_BYTES, settings -> settings.requestMaxBytes());
        assertRefuses("-1", ForumSettings.REQUEST_MAX_BYTES, settings -> settings.requestMaxBytes());
        assertRefuses("4001", ForumSettings.TEXT_MAX_CODEPOINTS, settings -> settings.textMaxCodepoints());
        assertRefuses("-1", ForumSettings.TEXT_MAX_CODEPOINTS, settings -> settings.textMaxCodepoints());
        assertRefuses("32769", ForumSettings.RENDERED_MAX_BYTES, settings -> settings.renderedMaxBytes());
        assertRefuses("-1", ForumSettings.RENDERED_MAX_BYTES, settings -> settings.renderedMaxBytes());
    }

    @Test
    void aValueThatIsNotANumberRefusesTheStartAndNamesTheKey() {
        assertRefuses("many", ForumSettings.REQUEST_MAX_BYTES, settings -> settings.requestMaxBytes());
        assertRefuses("16.5", ForumSettings.REQUEST_MAX_BYTES, settings -> settings.requestMaxBytes());
        assertRefuses("1e3", ForumSettings.TEXT_MAX_CODEPOINTS, settings -> settings.textMaxCodepoints());
    }

    @Test
    void anEmptyValueIsAnAbsentValueAndKeepsTheDefault() {
        ForumSettings settings = ForumSettings.of(null, "", " ", null);
        assertEquals(16384, settings.requestMaxBytes());
        assertEquals(4000, settings.textMaxCodepoints());
    }

    @Test
    void aDatabaseFlagThatIsNotABooleanRefusesTheStart() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> ForumSettings.of("perhaps", null, null, null).databaseEnabled());
        assertTrue(refusal.getMessage().contains(ForumSettings.DATABASE_ENABLED), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("perhaps"), "the refused value must not be written down");
    }

    @Test
    void selfCheckRefusesTheSameWayTheAccessorsDo() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> ForumSettings.of(null, "16385", null, null).selfCheck());
        assertTrue(refusal.getMessage().contains(ForumSettings.REQUEST_MAX_BYTES), refusal.getMessage());
    }

    /** A refused setting names its key, its range and never the value that was refused. */
    private static void assertRefuses(String value, String key, Consumer<ForumSettings> read) {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> read.accept(ForumSettings.of(null, value, value, value)), value);
        assertTrue(refusal.getMessage().contains(key), refusal.getMessage());
        assertFalse(refusal.getMessage().contains(value), "the refused value must not be written down");
    }

    @Test
    void theDocumentedKeysAreTheOnesTheBriefNames() {
        assertEquals(List.of("AGORA_DATABASE_ENABLED", "AGORA_REQUEST_MAX_BYTES",
                        "AGORA_TEXT_MAX_CODEPOINTS", "AGORA_RENDERED_MAX_BYTES"),
                List.of(ForumSettings.DATABASE_ENABLED, ForumSettings.REQUEST_MAX_BYTES,
                        ForumSettings.TEXT_MAX_CODEPOINTS, ForumSettings.RENDERED_MAX_BYTES));
    }
}
