package local.aeion.forum;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Feature 001: the smallest environment that must serve — a real session secret and nothing else.
 *
 * The database key is absent, so the application selects database-less boot; and an unknown key is
 * present, to prove that a variable the forum never asked for is ignored: the start must succeed,
 * neither the log nor a page may carry it, and the refusal messages of other settings must not
 * absorb it.
 */
public class LivenessSecretResource implements QuarkusTestResourceLifecycleManager {

    static final String SECRET = "liveness-suite-secret-0123456789abcdef";
    static final String UNKNOWN_KEY = "AGORA_UNKNOWN";
    static final String UNKNOWN_VALUE = "unknown-env-value-not-rendered";

    @Override
    public Map<String, String> start() {
        return Map.of(
                "AGORA_SESSION_SECRET", SECRET,
                UNKNOWN_KEY, UNKNOWN_VALUE);
    }

    @Override
    public void stop() {
    }
}
