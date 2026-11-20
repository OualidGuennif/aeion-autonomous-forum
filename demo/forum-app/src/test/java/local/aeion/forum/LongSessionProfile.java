package local.aeion.forum;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Feature 005: a deployment that names a longer session than the 900-second default.
 *
 * The keys are pinned exactly as an operator would pin them in the environment, so the suite proves
 * the mapping from those keys to the cookie a browser is handed, and not only the parsed value.
 */
public class LongSessionProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "AGORA_SESSION_SECRET", "ephemeral-suite-secret-0123456789abcdef",
                "AGORA_SESSION_IDLE_SECONDS", "7200",
                "AGORA_SESSION_ABSOLUTE_SECONDS", "7200");
    }
}
