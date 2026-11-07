package local.aeion.forum;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Feature 001: what the application checks before it serves anything.
 *
 * A required setting that is validated lazily refuses the first request instead of the start, which
 * is the wrong moment: a half-configured deployment would be reachable, and a probe would call it
 * alive. This observer builds the validated beans during startup, so a missing or placeholder
 * secret, a bound outside its range, a database flag that is not a flag, or a database-backed
 * deployment without its connection settings stops the process — and every refusal names its key
 * and nothing else, so no log turns into a mirror of the environment.
 */
@ApplicationScoped
public class StartupChecks {

    @Inject
    SessionSecret secret;

    @Inject
    ForumBounds bounds;

    @Inject
    DatabaseMode database;

    @ConfigProperty(name = "agora.time.business-zone")
    Optional<String> businessZone;

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent event) {
        verify();
        log.info("settings validated: session secret present and usable, caps within range, "
                + (database.enabled() ? "database-backed mode enabled" : "database-less mode"));
    }

    /**
     * The whole fail-closed check, in the order an operator reads it: the secret, the caps, the
     * database plane, the business timezone. A test calls this directly to see the same refusal
     * the application produces.
     */
    void verify() {
        secret.selfCheck();
        bounds.selfCheck();
        database.selfCheck();
        checkBusinessZone();
    }

    /**
     * The business timezone is part of the typed, allowlisted configuration: the forum's clock
     * field semantics stay Europe/Paris, and any other value refuses the start.
     */
    static final String BUSINESS_ZONE = "Europe/Paris";
    static final String BUSINESS_ZONE_KEY = "agora.time.business-zone";

    void checkBusinessZone() {
        String value = businessZone == null ? null : businessZone.orElse(null);
        if (value == null || !value.strip().equals(BUSINESS_ZONE)) {
            throw new IllegalStateException(
                    "business_timezone_invalid: " + BUSINESS_ZONE_KEY + " must be " + BUSINESS_ZONE);
        }
    }
}
