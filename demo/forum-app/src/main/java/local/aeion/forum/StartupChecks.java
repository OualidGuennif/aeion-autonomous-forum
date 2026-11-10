package local.aeion.forum;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Feature 001: what the application checks before it serves anything.
 *
 * A required setting that is validated lazily refuses the first request instead of the start, which
 * is the wrong moment: a half-configured deployment would be reachable, and a probe would call it
 * alive. This observer builds the validated beans during startup, so a missing or short secret, or
 * a bounded value outside its range, stops the process — and every refusal names only the key.
 */
@ApplicationScoped
public class StartupChecks {

    @Inject
    AgoraSettings settings;

    @Inject
    SessionSecret secret;

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent event) {
        // The first read of the settings bean constructs it and runs the allowlist validation:
        // an invalid database flag, cap or zone refuses here, before the server is reachable.
        // The business timezone is then pinned, so the deployment never inherits the host clock zone.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(settings.businessTimeZone()));
        secret.selfCheck();
        log.info("settings validated: session secret and bounded settings present and usable");
    }
}
