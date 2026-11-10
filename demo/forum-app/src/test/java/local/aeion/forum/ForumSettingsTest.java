package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Feature 001: the typed allowlist, validated without a running application.
 *
 * A refusal must name only the key and its rule, never the offending value, and never look like a
 * configuration echo: each refusal test asserts exactly that.
 */
class ForumSettingsTest {

    @Test
    void defaultsWhenNothingIsProvided() {
        ForumSettings settings = ForumSettings.of("", "", "", "", "");
        assertEquals(16384, settings.requestMaxBytes());
        assertEquals(4000, settings.textMaxCodepoints());
        assertEquals(32768, settings.renderedMaxBytes());
        assertFalse(settings.databaseEnabled(), "absent AGORA_DATABASE_ENABLED means database-less boot");
        assertEquals(ZoneId.of("Europe/Paris"), settings.businessZone());
        settings.selfCheck();
    }

    @Test
    void acceptsTheDocumentedBoundaries() {
        ForumSettings low = ForumSettings.of("1", "1", "1", "true", "UTC");
        assertEquals(1, low.requestMaxBytes());
        assertEquals(1, low.textMaxCodepoints());
        assertEquals(1, low.renderedMaxBytes());
        assertTrue(low.databaseEnabled());
        assertEquals(ZoneId.of("UTC"), low.businessZone());

        ForumSettings high = ForumSettings.of("16384", "4000", "32768", "false", "Asia/Tokyo");
        assertEquals(16384, high.requestMaxBytes());
        assertEquals(4000, high.textMaxCodepoints());
        assertEquals(32768, high.renderedMaxBytes());
        assertFalse(high.databaseEnabled());
        assertEquals(ZoneId.of("Asia/Tokyo"), high.businessZone());
    }

    @Test
    void readsTheCommonBooleanSpellings() {
        for (String truthy : new String[] {"true", "TRUE", "1", "yes", "on"}) {
            assertTrue(ForumSettings.of("16384", "4000", "32768", truthy, "UTC").databaseEnabled(), truthy);
        }
        for (String falsy : new String[] {"false", "FALSE", "0", "no", "off", " "}) {
            assertFalse(ForumSettings.of("16384", "4000", "32768", falsy, "UTC").databaseEnabled(), falsy);
        }
    }

    @Test
    void refusesNumbersOutsideTheirRangeWithoutEchoingTheValue() {
        for (String bad : new String[] {"-1", "0", "16385", "999999"}) {
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> ForumSettings.of(bad, "4000", "32768", "false", "UTC"), bad);
            assertTrue(refusal.getMessage().startsWith("settings_invalid"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains("AGORA_REQUEST_MAX_BYTES"), refusal.getMessage());
            assertFalse(refusal.getMessage().contains(bad), "the refusal must not echo the value: " + bad);
        }
    }

    @Test
    void refusesNonIntegersAndOutOfRangeTextAndRenderedCaps() {
        String ten = "ten";
        IllegalStateException text = assertThrows(IllegalStateException.class,
                () -> ForumSettings.of("16384", ten, "32768", "false", "UTC"));
        assertTrue(text.getMessage().startsWith("settings_invalid"), text.getMessage());
        assertTrue(text.getMessage().contains("AGORA_TEXT_MAX_CODEPOINTS"), text.getMessage());
        assertFalse(text.getMessage().contains(ten), text.getMessage());

        IllegalStateException rendered = assertThrows(IllegalStateException.class,
                () -> ForumSettings.of("16384", "4000", "32769", "false", "UTC"));
        assertTrue(rendered.getMessage().contains("AGORA_RENDERED_MAX_BYTES"), rendered.getMessage());
        assertFalse(rendered.getMessage().contains("32769"), rendered.getMessage());
    }

    @Test
    void refusesAnUnreadableDatabaseFlagAndAnUnknownTimeZone() {
        String badFlag = "maybe";
        IllegalStateException flag = assertThrows(IllegalStateException.class,
                () -> ForumSettings.of("16384", "4000", "32768", badFlag, "UTC"));
        assertTrue(flag.getMessage().contains("AGORA_DATABASE_ENABLED"), flag.getMessage());
        assertFalse(flag.getMessage().contains(badFlag), flag.getMessage());

        IllegalStateException zone = assertThrows(IllegalStateException.class,
                () -> ForumSettings.of("16384", "4000", "32768", "false", "Mars/Olympus"));
        assertTrue(zone.getMessage().contains("agora.business.timezone"), zone.getMessage());
        assertFalse(zone.getMessage().contains("Mars"), zone.getMessage());
    }
}
