package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * Feature 002: the schema report at {@code /api/schema}, fed by the Flyway history.
 *
 * The route and its wire shape are unchanged from the custom-runner days: a deployment can ask the
 * running application what it applied, and {@code pending} must be zero. The application role only
 * reads here; a database-less boot (feature 001) answers 503 instead of a database error.
 */
@ApplicationScoped
@Path("/api/schema")
@Produces(MediaType.APPLICATION_JSON)
public class SchemaResource {

    @Inject
    Instance<DataSource> dataSources;

    @Inject
    Instance<Flyway> flyways;

    @Inject
    AgoraSettings settings;

    @GET
    public Object status() {
        if (!settings.databaseEnabled() || dataSources.isUnsatisfied()) {
            return Response.status(503).entity(Map.of("error", "database_disabled")).build();
        }
        List<Map<String, Object>> rows = Migrations.history(dataSources.get());
        int expected = flyways.isUnsatisfied() ? rows.size() : flyways.get().info().all().length;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applied", rows);
        body.put("expected", expected);
        body.put("pending", Math.max(0, expected - rows.size()));
        return body;
    }
}
