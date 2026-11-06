package local.aeion.forum;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: liveness is one fact and nothing else.
 *
 * The health body says only that the process answers. It carries no release, host, runtime or
 * setting: a probe must not become a disclosure channel, and an orchestrator only needs the status.
 * The endpoint deliberately touches no database, so it stays truthful while a datasource is down.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ForumHealth {

    @ConfigProperty(name = "aeion.release", defaultValue = "unreleased")
    String release;

    @GET
    @Path("healthz")
    public Map<String, Object> health() {
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
