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
 * alive. This observer builds the validated beans during startup, so a placeholder secret or a
 * missing key stops the process, and the refusal names only the key.
 *
 * The order matters. The session secret is checked first, so a deployment that lost it is told
 * about that key alone, whatever else it may also be missing.
 */
@ApplicationScoped
public class StartupChecks {

    @Inject
    SessionSecret secret;

    @Inject
    ForumSettings settings;

    @Inject
    DatabaseConnection connection;

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent event) {
        secret.selfCheck();
        settings.selfCheck();
        connection.selfCheck(settings.databaseEnabled());
        log.info("settings validated: session secret present and usable");
    }
}
