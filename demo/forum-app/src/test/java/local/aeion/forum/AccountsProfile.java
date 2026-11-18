package local.aeion.forum;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Feature 004: a profile of its own gives the account HTTP suite a fresh disposable database, so
 * its first registration is deterministically the owner instead of inheriting another suite's rows.
 */
public class AccountsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("AGORA_SESSION_SECRET", "ephemeral-suite-secret-0123456789abcdef");
    }
}
