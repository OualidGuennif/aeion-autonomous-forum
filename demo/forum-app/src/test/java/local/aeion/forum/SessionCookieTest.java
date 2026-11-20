package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.NewCookie;
import org.junit.jupiter.api.Test;

/**
 * Feature 005: the session cookie is hardened on every request and gains Secure on HTTPS only.
 *
 * HttpOnly, Path=/ and SameSite=Strict never vary. Secure is the transport-dependent attribute: an
 * HTTPS request gets it, a plain-HTTP loopback request does not, because a Secure cookie could
 * never come back over the very connection the local deployment serves. The flag follows the
 * request's own scheme, so a TLS-terminating proxy decides the policy by forwarding that scheme.
 */
class SessionCookieTest {

    private static final String VALUE = "dG9rZW4tdmFsdWUtdGhhdC1pcy1sb25nLWVub3VnaC5zaWduYXR1cmU";

    @Test
    void theHardenedAttributesAreAlwaysThereAndSecureIsNotOverPlainHttp() {
        NewCookie cookie = SessionCookie.issued(VALUE, false, AuthService.SESSION_SECONDS);

        assertEquals(AuthService.COOKIE, cookie.getName());
        assertEquals(VALUE, cookie.getValue(), "the cookie carries the signed value unchanged");
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.isHttpOnly(), "the session identifier never reaches a script");
        assertEquals(NewCookie.SameSite.STRICT, cookie.getSameSite());
        assertEquals(AuthService.SESSION_SECONDS, cookie.getMaxAge());
        assertFalse(cookie.isSecure(), "plain HTTP must not be told to answer only over HTTPS");
        assertFalse(cookie.toString().toLowerCase().contains("secure"), cookie.toString());
    }

    @Test
    void theCookieLivesForTheSessionLifetimeItIsIssuedWith() {
        assertEquals(7200, SessionCookie.issued(VALUE, false, 7200).getMaxAge(),
                "a configured absolute lifetime reaches the browser as Max-Age");
        assertEquals(SessionLifetimes.defaults().absoluteSeconds(),
                SessionCookie.issued(VALUE, false, SessionLifetimes.defaults().absoluteSeconds())
                        .getMaxAge(),
                "an unconfigured deployment still issues the documented 900-second cookie");
    }

    @Test
    void anHttpsRequestIssuesTheSameCookieWithSecure() {
        NewCookie cookie = SessionCookie.issued(VALUE, true, AuthService.SESSION_SECONDS);

        assertTrue(cookie.isSecure(), "a session issued over HTTPS is HTTPS-only from then on");
        assertTrue(cookie.isHttpOnly(), "Secure never replaces the other hardening");
        assertEquals("/", cookie.getPath());
        assertEquals(NewCookie.SameSite.STRICT, cookie.getSameSite());
    }

    @Test
    void onlyTheHttpsSchemeCarriesSecure() {
        assertTrue(SessionCookie.secure("https"));
        assertTrue(SessionCookie.secure("HTTPS"), "the scheme is compared without case");
        assertFalse(SessionCookie.secure("http"));
        assertFalse(SessionCookie.secure(null), "an unknown scheme is no proof of transport security");
    }
}
