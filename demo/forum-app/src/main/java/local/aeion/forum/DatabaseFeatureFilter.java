// Spec: knowledge/components/agora-product.md
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
 * Feature 001: a deployment that stated no database never opens the one it ships for developers.
 *
 * The startup checks refuse features-on-without-a-connection, and the scheduled work asks the same
 * question before it runs. What is left is the surface a caller chooses: every route under
 * {@code /api/} stores or reads rows — settings, accounts, sessions, threads, the migration ledger —
 * so without a stated connection each of them would resolve the datasource default and quietly
 * create the shipped development file on a machine that was started to serve liveness only. Answering
 * that request is not a health check, and a half-written {@code /tmp/agora.db} is not a database the
 * operator agreed to.
 *
 * So the data surface is gated where a request can still be refused cheaply: before the body is read,
 * before authentication, and before the pool is asked for a connection. The refusal is a status code
 * and one fixed word — a 503 that quotes a path, a URL or a setting value would tell an operator
 * nothing they did not already type, and would put a credential-shaped string on a public response.
 *
 * Liveness deliberately sits outside the gate. {@code GET /healthz} and {@code GET /__release} answer
 * on a minimal boot, which is exactly the deployment this class protects: a probe must keep getting
 * its one status field while the human-facing routes say plainly that no database is configured.
 *
 * Turning database-backed features on with a stated connection changes nothing here: the gate opens,
 * and every data route behaves exactly as it did before this class existed.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 2)
public class DatabaseFeatureFilter implements ContainerRequestFilter {

    /** The whole body of the refusal: a code, never a value. */
    static final String ERROR = "database_not_configured";

    /**
     * The prefix every data route of this application lives under. Anything outside it — the health
     * probe first of all — is served whatever the deployment states about a database.
     */
    private static final String DATA_SURFACE = "/api/";

    private static final String DATA_SURFACE_ROOT = "/api";

    @Inject
    ForumSettings settings;

    @Override
    public void filter(ContainerRequestContext request) {
        if (settings.databaseWorkRequested() || !isDataRoute(request)) {
            return;
        }
        // abortWith, not an exception: the refusal carries no cause to log, and the body is this
        // deployment's answer rather than a failure inside one.
        request.abortWith(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("error", ERROR))
                .build());
    }

    /** True for the routes that store or read rows; false for liveness and the release route. */
    private static boolean isDataRoute(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (path == null) {
            return false;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.startsWith(DATA_SURFACE) || path.equals(DATA_SURFACE_ROOT);
    }
}
