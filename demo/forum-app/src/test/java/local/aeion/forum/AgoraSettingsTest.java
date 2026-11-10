package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Feature 001: settings are typed and bounded, and every refusal names only the key.
 *
 * The refusals here are the ones a deployment sees at startup: an unknown key takes the documented
 * default, a value outside its range (or not an integer at all) stops the process, and no refusal
 * ever repeats the configured value back, because settings are never logged or rendered.
 */
class AgoraSettingsTest {

    private static final String DATABASE_URL = "jdbc:sqlite:build/test-db/settings-test.db";

    @Test
    void absentKeysTakeTheDocumentedDefaults() {
        AgoraSettings settings = allNulls();
        assertFalse(settings.databaseEnabled());
        assertFalse(settings.datasourceUrlConfigured());
        assertEquals(AgoraSettings.REQUEST_MAX_BYTES_DEFAULT, settings.requestMaxBytes());
        assertEquals(AgoraSettings.TEXT_MAX_CODEPOINTS_DEFAULT, settings.textMaxCodepoints());
        assertEquals(AgoraSettings.RENDERED_MAX_BYTES_DEFAULT, settings.renderedMaxBytes());
        assertEquals("Europe/Paris", settings.businessTimeZone().getId());
    }

    @Test
    void blankValuesCountAsAbsentKeyToo() {
        AgoraSettings settings = of("  ", " 1024 ", " 400  ", "", "", DATABASE_URL);
        assertFalse(settings.databaseEnabled());
        assertTrue(settings.datasourceUrlConfigured());
        assertEquals(1024, settings.requestMaxBytes());
        assertEquals(400, settings.textMaxCodepoints());
        assertEquals(AgoraSettings.RENDERED_MAX_BYTES_DEFAULT, settings.renderedMaxBytes());
    }

    @Test
    void theDatabaseFlagAcceptsOnlyItsTwoDocumentedValues() {
        assertTrue(of("true", null, null, null, null, DATABASE_URL).databaseEnabled());
        assertTrue(of("TRUE", null, null, null, null, DATABASE_URL).databaseEnabled());
        assertFalse(of("false", null, null, null, null, null).databaseEnabled());
    }

    @Test
    void aGarbageDatabaseFlagRefusesNamingOnlyTheKey() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> of("banana", null, null, null, null, DATABASE_URL));
        assertTrue(refusal.getMessage().startsWith("setting_out_of_bounds"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(AgoraSettings.DATABASE_ENABLED_KEY), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("banana"), "a refusal never repeats the value");
    }

    @Test
    void eachCapIsBoundedAndRefusesNamingOnlyTheKey() {
        assertEquals(16384, of(null, "16384", null, null, null, null).requestMaxBytes());
        assertEquals(4000, of(null, null, "4000", null, null, null).textMaxCodepoints());
        assertEquals(32768, of(null, null, null, "32768", null, null).renderedMaxBytes());
        for (String[] attempt : new String[][] {
                {AgoraSettings.REQUEST_MAX_BYTES_KEY, "not-a-number"},
                {AgoraSettings.REQUEST_MAX_BYTES_KEY, "0"},
                {AgoraSettings.REQUEST_MAX_BYTES_KEY, "16385"},
                {AgoraSettings.TEXT_MAX_CODEPOINTS_KEY, "4001"},
                {AgoraSettings.TEXT_MAX_CODEPOINTS_KEY, "zero"},
                {AgoraSettings.RENDERED_MAX_BYTES_KEY, "32769"},
                {AgoraSettings.RENDERED_MAX_BYTES_KEY, "-1"}}) {
            String key = attempt[0];
            String raw = attempt[1];
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> oneKey(key, raw), key + ": " + raw);
            assertTrue(refusal.getMessage().startsWith("setting_out_of_bounds"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains(key), refusal.getMessage());
            assertFalse(refusal.getMessage().contains(raw), "a refusal never repeats the value");
        }
    }

    @Test
    void aDatabaseEnabledBootWithoutConnectionSettingsRefuses() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> of("true", null, null, null, null, "   "));
        assertTrue(refusal.getMessage().startsWith("datasource_required"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(AgoraSettings.DATABASE_ENABLED_KEY), refusal.getMessage());
    }

    @Test
    void anUnknownZoneRefusesNamingTheKey() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> of(null, null, null, null, "Mars/Phobos", null));
        assertTrue(refusal.getMessage().startsWith("setting_out_of_bounds"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(AgoraSettings.TIME_ZONE_KEY), refusal.getMessage());
    }

    /** Construct settings with String-typed raws, always the package-private test constructor. */
    private static AgoraSettings of(String database, String request, String text, String rendered, String zone, String url) {
        return new AgoraSettings(database, request, text, rendered, zone, url);
    }

    /** Settings with every key absent. */
    private static AgoraSettings allNulls() {
        return of(null, null, null, null, null, null);
    }

    /** Construct settings with exactly one raw value set, the way each scenario demands. */
    private static AgoraSettings oneKey(String key, String raw) {
        return of(
                AgoraSettings.DATABASE_ENABLED_KEY.equals(key) ? raw : null,
                AgoraSettings.REQUEST_MAX_BYTES_KEY.equals(key) ? raw : null,
                AgoraSettings.TEXT_MAX_CODEPOINTS_KEY.equals(key) ? raw : null,
                AgoraSettings.RENDERED_MAX_BYTES_KEY.equals(key) ? raw : null,
                null, null);
    }
}
