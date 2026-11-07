package local.aeion.forum;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
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
 * a database-backed deployment can ask what it applied, and {@code pending} must be zero. In the
 * database-less default (feature 001) no migration runs and the ledger honestly stays empty.
 */
@ApplicationScoped
@Path("/api/schema")
@Produces(MediaType.APPLICATION_JSON)
public class MigrationRunner {

    @Inject
    DataSource dataSource;

    @Inject
    ForumClock clock;

    @Inject
    DatabaseMode database;

    @ConfigProperty(name = "aeion.migrations.location", defaultValue = "db/migration")
    String location;

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent event) {
        if (!database.enabled()) {
            // Database-less boot (feature 001): the schema is not opened, and the migration
            // stays pending — an operator's datasource is never asked for a connection.
            log.info("database disabled: migrations are not initialized and no connection is opened");
            return;
        }
        List<String> appliedNow = migrate();
        if (!appliedNow.isEmpty()) {
            log.infof("applied migrations %s", appliedNow);
        }
    }

    /** Apply every pending migration; returns the versions applied now. */
    public synchronized List<String> migrate() {
        return Migrations.apply(dataSource, location, clock);
    }

    @GET
    public Map<String, Object> status() {
        int expected = Migrations.index(location).size();
        List<Map<String, Object>> rows;
        if (database.enabled()) {
            rows = Migrations.applied(dataSource);
        } else {
            // A database-less deployment applied nothing on purpose; the ledger stays empty.
            rows = List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applied", rows);
        body.put("expected", expected);
        body.put("pending", Math.max(0, expected - rows.size()));
        return body;
    }
}
