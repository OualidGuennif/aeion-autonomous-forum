package local.aeion.forum;

import jakarta.ws.rs.core.NewCookie;

/**
 * Feature 005: the writer of the {@code agora_session} attributes a login issues.
 *
 * The contract is fixed for every session the forum issues: {@code HttpOnly}, {@code Path=/} and
 * {@code SameSite=Strict}, so the identifier never reaches a script and never travels to a
 * cross-site request. {@code Secure} is the one attribute that follows the transport: it is set
 * exactly when the request itself arrived over HTTPS, and omitted when the same code answers a
 * plain-HTTP loopback deployment, where a Secure cookie could never come back.
 *
 * The scheme arrives as the request URI's own scheme, which is what Quarkus reports for the
 * connection it served; a deployment behind a TLS-terminating proxy makes that scheme HTTPS by
 * turning on Quarkus' forwarded-header handling. {@code Max-Age} arrives from the service as the
 * session's own absolute lifetime, so the browser never keeps a cookie past the row it names, and
 * an unconfigured deployment keeps issuing the documented 900 seconds.
 */
public final class SessionCookie {

    /** The scheme that alone justifies the Secure attribute. */
    static final String HTTPS = "https";

    private SessionCookie() {
    }

    /** True when this request was served over HTTPS, so its cookie may carry Secure. */
    public static boolean secure(String scheme) {
        return HTTPS.equalsIgnoreCase(scheme);
    }

    /**
     * The session cookie a login issues: hardened, Secure only over an HTTPS request, and kept by
     * the browser for exactly {@code maxAgeSeconds} — the session's absolute lifetime.
     */
    public static NewCookie issued(String value, boolean secure, int maxAgeSeconds) {
        return new NewCookie.Builder(AuthService.COOKIE)
                .value(value)
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
