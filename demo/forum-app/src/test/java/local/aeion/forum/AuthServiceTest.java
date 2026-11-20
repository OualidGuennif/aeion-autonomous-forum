package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Features 004 and 005: an account is stored as an Argon2id hash, a session only as its SHA-256,
 * and the frozen clock proves the fifteen-minute expiry without sleeping.
 */
class AuthServiceTest {

    private EphemeralDatabase database;
    private AuthService auth;
    private ForumClock clock;

    @BeforeEach
    void open() {
        database = EphemeralDatabase.create();
        clock = database.clock();
        auth = new AuthService();
        auth.dataSource = database.dataSource();
        auth.clock = clock;
        auth.secret = SessionSecret.of("a-real-secret-with-32-bytes-at-least");
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void registersTheFirstAccountAsOwnerAndTheNextAsMember() {
        assertEquals("owner", auth.register("owner-here", "a-long-enough-password").role());
        assertEquals("member", auth.register("member-here", "a-long-enough-password").role());
    }

    @Test
    void storesAPasswordOnlyAsArgon2id() throws Exception {
        auth.register("hashed", "a-long-enough-password");
        try (var connection = database.dataSource().getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT password FROM accounts")) {
            assertTrue(rows.next());
            String stored = rows.getString(1);
            assertTrue(stored.startsWith("$argon2id$"), "the stored credential must be an Argon2id encoding");
            assertFalse(stored.contains("a-long-enough-password"), "the clear text must never be stored");
        }
    }

    @Test
    void storesTheSessionOnlyAsItsDigest() {
        auth.register("first-account", "a-long-enough-password");
        String name = registerAndGetName();
        AuthService.Issued issued = auth.login(name, "a-long-enough-password");
        assertTrue(issued.token().length() >= 40);
        assertEquals(Optional.of("member"), auth.authenticate(issued.token()).map(AuthService.Account::role));
        assertTrue(auth.authenticate("not-a-real-token").isEmpty());
        assertTrue(auth.authenticate(null).isEmpty());
    }

    @Test
    void refusesAWeakPasswordAndABadUsername() {
        assertEquals("weak_password", assertThrows(AuthService.AuthException.class,
                () -> auth.register("weakling", "short")).code());
        assertEquals("invalid_username", assertThrows(AuthService.AuthException.class,
                () -> auth.register("ab", "a-long-enough-password")).code());
        assertEquals("invalid_username", assertThrows(AuthService.AuthException.class,
                () -> auth.register("two words", "a-long-enough-password")).code());
    }

    @Test
    void refusesADuplicateUsername() {
        auth.register("twin", "a-long-enough-password");
        assertEquals("username_taken", assertThrows(AuthService.AuthException.class,
                () -> auth.register("twin", "a-long-enough-password")).code());
    }

    @Test
    void locksAfterFiveFailuresAndSaysNothingDifferent() {
        auth.register("lockable", "a-long-enough-password");
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertEquals("invalid_credentials", assertThrows(AuthService.AuthException.class,
                    () -> auth.login("lockable", "wrong-password-here")).code());
        }
        assertEquals("account_locked", assertThrows(AuthService.AuthException.class,
                () -> auth.login("lockable", "a-long-enough-password")).code());
        assertEquals("account_locked", assertThrows(AuthService.AuthException.class,
                () -> auth.login("lockable", "wrong-password-here")).code(),
                "a locked account refuses before the password check, so the status never reveals "
                        + "whether the supplied password was correct");
        assertEquals("invalid_credentials", assertThrows(AuthService.AuthException.class,
                () -> auth.login("ghost", "a-long-enough-password")).code());
    }

    @Test
    void expiresASessionAfterFifteenMinutes() {
        auth.register("timed", "a-long-enough-password");
        AuthService.Issued issued = auth.login("timed", "a-long-enough-password");
        assertTrue(auth.authenticate(issued.token()).isPresent());
        clock.advance(AuthService.SESSION_SECONDS * 1_000_000_000L - 1);
        assertTrue(auth.authenticate(issued.token()).isPresent(), "one nanosecond early is still valid");
        clock.advance(1);
        assertTrue(auth.authenticate(issued.token()).isEmpty(), "the session must be gone at the deadline");
    }

    @Test
    void refusesACookieWhoseSignatureDoesNotMatch() {
        auth.register("signed", "a-long-enough-password");
        AuthService.Issued issued = auth.login("signed", "a-long-enough-password");
        String tampered = "another-token" + issued.token().substring(issued.token().indexOf('.'));
        assertTrue(auth.authenticate(tampered).isEmpty(), "an edited cookie must not resolve");
        assertFalse(auth.logout(tampered));
        assertTrue(auth.authenticate(issued.token()).isPresent(), "the real cookie still works");
    }

    @Test
    void revokesOnLogout() {
        auth.register("leaver", "a-long-enough-password");
        AuthService.Issued issued = auth.login("leaver", "a-long-enough-password");
        assertTrue(auth.logout(issued.token()));
        assertTrue(auth.authenticate(issued.token()).isEmpty());
        assertFalse(auth.logout(issued.token()), "a second logout revokes nothing");
        assertFalse(auth.logout(null));
    }

    // ---- Feature 005: rotation, sign-out-everywhere and the two configurable lifetimes ----

    @Test
    void rotatingALoginRevokesOnlyThePresentedSession() {
        String name = "rotator-" + System.nanoTime();
        auth.register(name, "a-long-enough-password");
        AuthService.Issued presented = auth.login(name, "a-long-enough-password");
        AuthService.Issued untouched = auth.login(name, "a-long-enough-password");

        AuthService.Issued rotated = auth.login(name, "a-long-enough-password", presented.token());

        assertNotEquals(presented.token(), rotated.token(), "the identifier is rotated, not reused");
        assertNotEquals(untouched.token(), rotated.token(), "the rotation mints a fresh identifier");
        assertNotEquals(presented.csrf(), rotated.csrf(), "the rotated session carries a new CSRF token");
        assertTrue(auth.authenticate(presented.token()).isEmpty(),
                "the presented cookie is revoked, so replaying it answers nothing");
        assertEquals(Optional.of(name), auth.authenticate(untouched.token()).map(AuthService.Account::username),
                "a session that was not presented stays valid");
        assertTrue(auth.authenticate(rotated.token()).isPresent(), "the rotated cookie authenticates");
    }

    @Test
    void aPresentedCookieIsRotatedEvenWhenItIsNotALiveSession() {
        String name = "stale-" + System.nanoTime();
        auth.register(name, "a-long-enough-password");
        AuthService.Issued issued = auth.login(name, "a-long-enough-password");
        assertTrue(auth.logout(issued.token()));

        AuthService.Issued again = auth.login(name, "a-long-enough-password", issued.token());
        assertTrue(again.token() != null && !again.token().isEmpty(),
                "an HMAC-valid but revoked presented cookie does not break the new login");
        assertFalse(auth.logout(issued.token()), "revoking it a second time still changes nothing");
    }

    @Test
    void aFailedLoginNeverRevokesThePresentedSession() {
        String name = "careful-" + System.nanoTime();
        auth.register(name, "a-long-enough-password");
        AuthService.Issued issued = auth.login(name, "a-long-enough-password");

        assertEquals("invalid_credentials", assertThrows(AuthService.AuthException.class,
                () -> auth.login(name, "the-wrong-password", issued.token())).code());
        assertTrue(auth.authenticate(issued.token()).isPresent(),
                "a refused credential exchange must not revoke the cookie it presented");
        assertEquals("invalid_credentials", assertThrows(AuthService.AuthException.class,
                () -> auth.login("ghost" + System.nanoTime(), "a-long-enough-password",
                        issued.token())).code());
        assertTrue(auth.authenticate(issued.token()).isPresent(), "and neither does an unknown handle");

        // An unusable presented value is not a session, so the successful login ignores it.
        AuthService.Issued fine = auth.login(name, "a-long-enough-password", "not-a-signed-value");
        assertTrue(auth.authenticate(fine.token()).isPresent());
    }

    @Test
    void signOutEverywhereRevokesEverySessionOfTheAccountAndNoOtherAccount() {
        String mine = "everywhere-" + System.nanoTime();
        String peer = "bystander-" + System.nanoTime();
        auth.register(mine, "a-long-enough-password");
        auth.register(peer, "a-long-enough-password");
        AuthService.Issued presented = auth.login(mine, "a-long-enough-password");
        AuthService.Issued sibling = auth.login(mine, "a-long-enough-password");
        AuthService.Issued bystander = auth.login(peer, "a-long-enough-password");

        assertEquals(2, auth.logoutAll(presented.account()),
                "the two live sessions of the account are revoked");
        assertTrue(auth.authenticate(presented.token()).isEmpty());
        assertTrue(auth.authenticate(sibling.token()).isEmpty());
        assertEquals(Optional.of("member"), auth.authenticate(bystander.token()).map(AuthService.Account::role),
                "another account's session is outside logout-all");
        assertEquals(0, auth.logoutAll(presented.account()), "a second pass revokes nothing");
        assertEquals(1, auth.logoutAll(bystander.account()),
                "revoking a stranger's session list touches only that account");
    }

    @Test
    void anIdleSessionDiesAtItsDeadlineEvenThoughTheAbsoluteOneIsFarAway() {
        auth.lifetimes = SessionLifetimes.of("300", "900");
        String name = "idle-" + System.nanoTime();
        auth.register(name, "a-long-enough-password");
        AuthService.Issued issued = auth.login(name, "a-long-enough-password");
        assertTrue(auth.authenticate(issued.token()).isPresent());

        clock.advance(299_999_999_999L);
        assertTrue(auth.authenticate(issued.token()).isPresent(),
                "one nanosecond before the idle deadline the session still answers");
        // That answer was itself activity: the deadline it renewed now sits one nanosecond ahead
        // of the current instant plus the idle timeout, so one more full interval ends the session.
        clock.advance(300_000_000_000L);
        assertTrue(auth.authenticate(issued.token()).isEmpty(),
                "the idle deadline ends the session even though the absolute one is far away");
    }

    @Test
    void activitySlidesTheIdleDeadlineUpToTheAbsoluteLifetime() {
        auth.lifetimes = SessionLifetimes.of("300", "900");
        String name = "sliding-" + System.nanoTime();
        auth.register(name, "a-long-enough-password");
        AuthService.Issued issued = auth.login(name, "a-long-enough-password");

        for (int step = 0; step < 3; step++) {
            clock.advance(299_000_000_000L);
            assertTrue(auth.authenticate(issued.token()).isPresent(),
                    "activity before the idle deadline renews it (step " + step + ")");
        }
        // Three renewals have carried the row to second 897; one more interval hits the absolute
        // lifetime, which no amount of activity can move.
        clock.advance(1_000_000_000L + 3_000_000_000L);
        assertTrue(auth.authenticate(issued.token()).isEmpty(),
                "the absolute lifetime ends the session whatever the idle renewals did");
    }

    @Test
    void anIdledOutSessionThatWasNeverTouchedIsMeasuredFromItsIssuance() throws Exception {
        auth.lifetimes = SessionLifetimes.of("300", "900");
        String name = "coalesced-" + System.nanoTime();
        auth.register(name, "a-long-enough-password");
        AuthService.Issued issued = auth.login(name, "a-long-enough-password");
        // Drop the recorded activity, the shape an imported row arrives in.
        try (var connection = database.dataSource().getConnection();
             var update = connection.prepareStatement("UPDATE sessions SET last_seen_ns = NULL")) {
            update.executeUpdate();
        }
        assertTrue(auth.authenticate(issued.token()).isPresent(), "the row is measured from its issuance");
        clock.advance(300_000_000_000L);
        assertTrue(auth.authenticate(issued.token()).isEmpty(), "and the idle deadline still applies");
    }

    /**
     * The cookie a browser is handed must be able to live as long as its row: the Max-Age the
     * resource publishes is the configured absolute lifetime, 900 seconds until a deployment names
     * another, and a session issued under a longer one still authenticates past the old fixed value.
     */
    @Test
    void thePublishedCookieLifetimeFollowsTheConfiguredAbsoluteLifetime() {
        assertEquals(AuthService.SESSION_SECONDS, auth.cookieLifetimeSeconds(),
                "an unconfigured deployment keeps the documented 900 seconds");

        auth.lifetimes = SessionLifetimes.of("7200", "7200");
        String name = "longlived-" + System.nanoTime();
        auth.register(name, "a-long-enough-password");
        AuthService.Issued issued = auth.login(name, "a-long-enough-password");

        assertEquals(7200, auth.cookieLifetimeSeconds(),
                "the configured absolute lifetime is the Max-Age the browser is given");
        assertEquals(7200L * 1_000_000_000L, issued.expiresNs() - clock.nowNs(),
                "the cookie never outlives the row it names");

        clock.advance(AuthService.SESSION_SECONDS * 1_000_000_000L + 1);
        assertTrue(auth.authenticate(issued.token()).isPresent(),
                "the session lives past the fixed 900 seconds a configured deployment asked for");
    }

    private String registerAndGetName() {
        String name = "digest-" + System.nanoTime();
        auth.register(name, "a-long-enough-password");
        return name;
    }
}
