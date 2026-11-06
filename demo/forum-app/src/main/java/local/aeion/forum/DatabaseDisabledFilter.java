package local.aeion.forum;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Feature 001: a database-less deployment has no database-backed API.
 *
 * The mode exists so liveness can answer with nothing but a session secret. Requests to the forum
 * API are refused before any resource can resolve an entity manager or a connection, and the
 * refusal says only that the feature is unavailable in this mode. The health endpoint is not
 * under {@code /api} and is therefore never gated.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 2)
public class DatabaseDisabledFilter implements ContainerRequestFilter {

    @Inject
    AgoraSettings settings;

    @Override
    public void filter(ContainerRequestContext request) {
        if (settings.databaseEnabled()) {
            return;
        }
        String path = request.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.equals("api") || path.startsWith("api/")) {
            request.abortWith(Response.status(503)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(Map.of("error", "database_disabled"))
                    .build());
        }
    }
}
