package local.aeion.forum;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Feature 001: the health endpoint is liveness and nothing else.
 *
 * {@code GET /healthz} answers one fixed body, {@code {"status":"ok"}}, once the process can serve.
 * It does not touch the database in any mode, and it carries no release, runtime or configuration
 * detail: a probe is asked whether to leave the pod alone, not what the pod looks like. Settings
 * and environment stay out of every response for the same reason they stay out of the logs.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ForumHealth {

    @GET
    @Path("healthz")
    public Map<String, Object> health() {
        return payload("ok");
    }

    @GET
    @Path("__release")
    public Map<String, Object> release() {
        return payload("serving");
    }

    /** The one reply shape of this resource: a status and nothing else. */
    private static Map<String, Object> payload(String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        return body;
    }
}
