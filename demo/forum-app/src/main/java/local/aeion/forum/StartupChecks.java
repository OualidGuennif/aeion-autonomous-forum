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
 * alive. This observer builds the validated beans during startup, so a placeholder secret, a missing
 * key or a bound outside its range stops the process, and each refusal names only the key it is
 * about — never the value an operator put there.
 */
@ApplicationScoped
public class StartupChecks {

    @Inject
    SessionSecret secret;

    @Inject
    ForumSettings settings;

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent event) {
        // The order is the contract: the key that stops a deployment from signing sessions is named
        // before the key that only stops it from storing rows, and the second question is never asked
        // of a deployment that already failed the first.
        secret.selfCheck();
        settings.selfCheck();
        settings.requireConnectionStated();
        log.infof("settings validated: %d documented keys in range, session secret present and usable, "
                        + "database-backed features %s",
                ForumSettings.DOCUMENTED_KEYS.size(),
                settings.databaseEnabled() ? "enabled" : "disabled");
    }
}
