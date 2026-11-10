package local.aeion.forum;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Feature 002: runs the migrations at boot and reports what the database carries.
 *
 * The engine lives in {@link Migrations} so a test can apply the same files to an ephemeral
 * database without CDI. This class only schedules it and exposes the ledger at {@code /api/schema}:
 * a deployment can ask the running application what it applied, and {@code pending} must be zero.
 * A database-less boot (feature 001) runs nothing and reports 503 instead of a database error.
 */
@ApplicationScoped
@Path("/api/schema")
@Produces(MediaType.APPLICATION_JSON)
public class MigrationRunner {

    @Inject
    Instance<DataSource> dataSources;

    @Inject
    AgoraSettings settings;

    @Inject
    ForumClock clock;

    @ConfigProperty(name = "aeion.migrations.location", defaultValue = "db/migration")
    String location;

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent event) {
        if (!databaseAvailable()) {
            log.info("database-less boot: migrations are not run");
            return;
        }
        List<String> appliedNow = migrate();
        if (!appliedNow.isEmpty()) {
            log.infof("applied migrations %s", appliedNow);
        }
    }

    /** Apply every pending migration; returns the versions applied now. */
    public synchronized List<String> migrate() {
        return Migrations.apply(source(), location, clock);
    }

    @GET
    public Object status() {
        if (!databaseAvailable()) {
            return Response.status(503).entity(Map.of("error", "database_disabled")).build();
        }
        List<Map<String, Object>> rows = Migrations.applied(source());
        int expected = Migrations.index(location).size();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applied", rows);
        body.put("expected", expected);
        body.put("pending", Math.max(0, expected - rows.size()));
        return body;
    }

    /** The setting is the authority; an absent datasource bean is only a secondary refusal. */
    private boolean databaseAvailable() {
        return settings.databaseEnabled() && !dataSources.isUnsatisfied();
    }

    private DataSource source() {
        if (!databaseAvailable()) {
            throw new IllegalStateException("database_disabled");
        }
        return dataSources.get();
    }
}
