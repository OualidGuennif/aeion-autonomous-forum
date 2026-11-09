package local.aeion.forum;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: the service answers, and it answers liveness.
 *
 * {@code /healthz} is what a probe calls, so it says one thing: the process is up. It names no
 * release, no runtime, no host and no setting, and it never asks the database anything — a
 * liveness check that needed a database would report a forum as dead because its database is.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ForumHealth {

    @ConfigProperty(name = "aeion.release", defaultValue = "unreleased")
    String release;

    @GET
    @Path("healthz")
    public Map<String, Object> health() {
        // Exactly one field: the frozen acceptance contract compares the whole body.
        return Map.of("status", "ok");
    }

    @GET
    @Path("__release")
    public Map<String, Object> release() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "serving");
        body.put("release", release);
        body.put("runtime", System.getProperty("java.version"));
        return body;
    }
}
