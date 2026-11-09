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
 * Feature 001: a deployment without a database serves liveness, and says so for the rest.
 *
 * The forum's own routes need the forum's tables. When the database-backed features are off,
 * those routes answer 503 with a stable code instead of failing somewhere deeper with an error
 * that says nothing about the cause. The probe keeps working, because {@code /healthz} is not one
 * of these routes.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 2)
public class DatabaseModeFilter implements ContainerRequestFilter {

    @Inject
    ForumSettings settings;

    @Override
    public void filter(ContainerRequestContext request) {
        if (settings.databaseEnabled()) {
            return;
        }
        String path = request.getUriInfo().getPath();
        if (path == null) {
            return;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.equals("api") && !path.startsWith("api/")) {
            return;
        }
        request.abortWith(Response.status(503).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "database_disabled")).build());
    }
}
