package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 005: the two lifetimes of a server-side session, typed and bounded like every setting.
 *
 * The idle timeout measures inactivity: how long the row may go without an authenticated request
 * before it stops answering. The absolute lifetime measures age: how long the row may live at all
 * from its issuance, whatever the activity, and it is also the {@code Max-Age} the issued cookie
 * carries, so a configured lifetime reaches the browser instead of dying at a hard-coded value.
 * Both default to {@code AuthService.SESSION_SECONDS}, so an unconfigured deployment behaves
 * exactly as it did, and a deployment that sets either key names a row's real deadline.
 *
 * Every value is parsed here instead of by the framework, so a non-numeric or out-of-range value
 * refuses startup with a message that names only the key: configured values never enter a log, an
 * error text or a page.
 */
@ApplicationScoped
public class SessionLifetimes {

    static final String IDLE_KEY = "AGORA_SESSION_IDLE_SECONDS";
    static final String ABSOLUTE_KEY = "AGORA_SESSION_ABSOLUTE_SECONDS";
    static final int MIN_SECONDS = 1;
    /** Thirty days: no remember-me survives past the absolute lifetime a deployment configured. */
    static final int MAX_SECONDS = 2_592_000;

    private final int idleSeconds;
    private final int absoluteSeconds;

    @Inject
    public SessionLifetimes(
            @ConfigProperty(name = "agora.session.idle-seconds") Optional<String> idle,
            @ConfigProperty(name = "agora.session.absolute-seconds") Optional<String> absolute) {
        this(idle.orElse(null), absolute.orElse(null));
    }

    /** Direct construction with the raw values, for a test that pins one documented key at a time. */
    SessionLifetimes(String idle, String absolute) {
        this.idleSeconds = parseBound(idle, IDLE_KEY);
        this.absoluteSeconds = parseBound(absolute, ABSOLUTE_KEY);
    }

    /** One deployment-shaped instance with both keys pinned; the way a test builds one. */
    public static SessionLifetimes of(String idle, String absolute) {
        return new SessionLifetimes(idle, absolute);
    }

    /** Both keys absent: the unconfigured deployment, two 900-second lifetimes. */
    public static SessionLifetimes defaults() {
        return new SessionLifetimes((String) null, (String) null);
    }

    /** Idle seconds without an authenticated request before a session stops answering. */
    public int idleSeconds() {
        return idleSeconds;
    }

    /** Absolute seconds from issuance before a session stops answering, activity or not. */
    public int absoluteSeconds() {
        return absoluteSeconds;
    }

    /**
     * Force the bean to be built now, before the server serves: construction parses and bounds both
     * keys, so an invalid value refuses startup instead of the first login. The bounds are checked
     * again here, so this method keeps refusing even if a value ever arrived another way.
     */
    public void selfCheck() {
        if (idleSeconds < MIN_SECONDS || idleSeconds > MAX_SECONDS
                || absoluteSeconds < MIN_SECONDS || absoluteSeconds > MAX_SECONDS) {
            throw new IllegalStateException(
                    "setting_out_of_bounds: " + IDLE_KEY + "/" + ABSOLUTE_KEY);
        }
    }

    private static int parseBound(String raw, String key) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return AuthService.SESSION_SECONDS;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // The failing value stays out of the refusal: the message names the key, nothing else.
            throw new IllegalStateException(rangeMessage(key));
        }
        if (parsed < MIN_SECONDS || parsed > MAX_SECONDS) {
            throw new IllegalStateException(rangeMessage(key));
        }
        return parsed;
    }

    private static String rangeMessage(String key) {
        return "setting_out_of_bounds: " + key + " must be an integer between "
                + MIN_SECONDS + " and " + MAX_SECONDS;
    }
}
