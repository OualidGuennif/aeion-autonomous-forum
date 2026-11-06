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
 * alive. This observer builds the validated beans during startup, so a placeholder secret, a
 * missing key or an out-of-range bound stops the process, and the refusal names only the key.
 */
@ApplicationScoped
public class StartupChecks {

    @Inject
    SessionSecret secret;

    @Inject
    AgoraSettings settings;

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent event) {
        secret.selfCheck();
        settings.selfCheck();
        // Never log the values: only the fact that every allowlisted setting passed its check.
        log.info("settings validated");
    }
}
