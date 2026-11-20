package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Feature 005: the session lifetimes are typed and bounded, and every refusal names only the key.
 *
 * Absent keys take the documented 900-second default, so an unconfigured deployment keeps the
 * behaviour the cookie has always had; a value outside the range, or not an integer at all, stops
 * the boot without repeating the configured value.
 */
class SessionLifetimesTest {

    @Test
    void absentKeysTakeTheDocumentedDefault() {
        SessionLifetimes lifetimes = SessionLifetimes.defaults();
        assertEquals(AuthService.SESSION_SECONDS, lifetimes.idleSeconds());
        assertEquals(AuthService.SESSION_SECONDS, lifetimes.absoluteSeconds());
        assertEquals(900, lifetimes.idleSeconds());
        assertEquals(900, lifetimes.absoluteSeconds());
    }

    @Test
    void blankValuesCountAsAbsentKeysToo() {
        SessionLifetimes lifetimes = SessionLifetimes.of("  ", "  ");
        assertEquals(AuthService.SESSION_SECONDS, lifetimes.idleSeconds());
        assertEquals(AuthService.SESSION_SECONDS, lifetimes.absoluteSeconds());
    }

    @Test
    void eachLifetimeIsBounded() {
        assertEquals(1, SessionLifetimes.of("1", "1").idleSeconds());
        assertEquals(1, SessionLifetimes.of("1", "1").absoluteSeconds());
        assertEquals(SessionLifetimes.MAX_SECONDS, SessionLifetimes.of(
                String.valueOf(SessionLifetimes.MAX_SECONDS), null).idleSeconds());
    }

    @Test
    void eachLifetimeRefusesNamingOnlyItsKey() {
        for (String[] attempt : new String[][] {
                {SessionLifetimes.IDLE_KEY, "not-a-number"},
                {SessionLifetimes.IDLE_KEY, "-2"},
                {SessionLifetimes.IDLE_KEY, "2592001"},
                {SessionLifetimes.ABSOLUTE_KEY, "soon"},
                {SessionLifetimes.ABSOLUTE_KEY, "-3"},
                {SessionLifetimes.ABSOLUTE_KEY, "2592001"}}) {
            String key = attempt[0];
            String raw = attempt[1];
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> oneKey(key, raw), key + ": " + raw);
            assertTrue(refusal.getMessage().startsWith("setting_out_of_bounds"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains(key), refusal.getMessage());
            assertFalse(refusal.getMessage().contains(raw), "a refusal never repeats the value");
        }
    }

    /** Construct lifetimes with exactly one raw value set, the way each scenario demands. */
    private static SessionLifetimes oneKey(String key, String raw) {
        return SessionLifetimes.of(
                SessionLifetimes.IDLE_KEY.equals(key) ? raw : null,
                SessionLifetimes.ABSOLUTE_KEY.equals(key) ? raw : null);
    }
}
