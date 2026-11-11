package local.aeion.forum;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Feature 002: the application validates the schema at boot and never migrates it.
 *
 * Migration is the operator job's task, run as the migration owner in a separate JVM; the
 * application holds a least-privilege role that cannot write schema or audit history. What it can
 * do is refuse to serve a database it does not recognise - a checksum drift, a schema newer than
 * the code, a failed migration or a pending one all stop the boot, naming only the reason. The
 * same boot refuses a configured role that is not least-privileged, so a deployment pointed at a
 * migration authority never serves through it.
 */
@ApplicationScoped
public class MigrationRunner {

    @Inject
    Instance<javax.sql.DataSource> dataSources;

    @Inject
    AgoraSettings settings;

    @Inject
    Logger log;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url", defaultValue = "")
    String datasourceUrl;

    void onStart(@Observes StartupEvent event) {
        if (!settings.databaseEnabled() || dataSources.isUnsatisfied()) {
            log.info("database-less boot: the schema is not validated");
            return;
        }
        DatabaseTls.requireVerifiedTransport(datasourceUrl);
        Migrations.validate(dataSources.get());
        AppRoleGuard.requireLeastPrivilege(dataSources.get());
        log.info("schema validated: history matches the shipped migrations, none pending");
    }
}
