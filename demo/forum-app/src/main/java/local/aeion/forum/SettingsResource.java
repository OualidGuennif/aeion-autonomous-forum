package local.aeion.forum;

import jakarta.inject.Inject;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Feature 001: the skeleton answers, and the settings it reads at boot are readable and writable.
 *
 * A missing setting is a 404, never an empty object with defaults invented on the way out: the
 * caller must know the difference between "not configured" and "configured to nothing". Values are
 * bounded so a single row cannot become a blob. A database-less boot answers 503 here too: the
 * stored settings are a database-backed feature, and the runtime configuration keys are not rows.
 */
@Path("/api/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsResource {

    private static final int MAX_NAME = 120;
    private static final int MAX_VALUE = 4000;

    @Inject
    Instance<EntityManager> entities;

    @Inject
    AgoraSettings settings;

    @Inject
    ForumClock clock;

    private EntityManager em() {
        if (!settings.databaseEnabled() || entities.isUnsatisfied()) {
            throw new IllegalStateException("database_disabled");
        }
        return entities.get();
    }

    @GET
    public Object list() {
        Response offline = databaseless();
        if (offline != null) {
            return offline;
        }
        return em().createQuery("select s from Setting s order by s.name", Setting.class)
                .getResultList()
                .stream()
                .map(SettingsResource::view)
                .toList();
    }

    @GET
    @Path("{name}")
    public Response read(@PathParam("name") String name) {
        Response offline = databaseless();
        if (offline != null) {
            return offline;
        }
        Setting setting = em().find(Setting.class, name);
        if (setting == null) {
            return Response.status(404).entity(Map.of("error", "unknown_setting")).build();
        }
        return Response.ok(view(setting)).build();
    }

    @PUT
    @Path("{name}")
    @Transactional
    public Response write(@PathParam("name") String name, Map<String, String> body) {
        Response offline = databaseless();
        if (offline != null) {
            return offline;
        }
        String value = body == null ? null : body.get("value");
        if (name == null || name.isBlank() || name.length() > MAX_NAME) {
            return Response.status(400).entity(Map.of("error", "invalid_name")).build();
        }
        if (value == null || value.isBlank() || value.length() > MAX_VALUE) {
            return Response.status(400).entity(Map.of("error", "invalid_value")).build();
        }
        long now = clock.nowNs();
        Setting setting = em().find(Setting.class, name);
        if (setting == null) {
            setting = new Setting(name, value, now);
            em().persist(setting);
        } else {
            setting.value = value;
            setting.updatedNs = now;
        }
        Map<String, Object> payload = view(setting);
        return Response.ok(payload).build();
    }

    /** The database-less refusal: 503 when the deployment has no database to read or write. */
    private Response databaseless() {
        if (settings.databaseEnabled() && !entities.isUnsatisfied()) {
            return null;
        }
        return Response.status(503).entity(Map.of("error", "database_disabled")).build();
    }

    /** The one wire shape of a setting: GET and PUT must not disagree on a field name. */
    private static Map<String, Object> view(Setting setting) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", setting.name);
        payload.put("value", setting.value);
        payload.put("updated_ns", setting.updatedNs);
        return payload;
    }
}
