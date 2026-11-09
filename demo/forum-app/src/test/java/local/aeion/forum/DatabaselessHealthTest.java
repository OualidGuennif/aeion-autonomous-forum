// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Feature 001's first acceptance check on a real boot: a minimal deployment answers liveness and
 * never reaches for a database.
 *
 * The profile switches database-backed features off in both names the application can ask for, and
 * points the datasource at a path that cannot exist, so this suite fails the moment anything reads a
 * connection while starting — or while serving. A route that tried would answer 500 out of a driver
 * that cannot open the file, which is what makes the refusals below evidence. What is left is the
 * contract everything else probes: one status field, no release, no runtime, no setting value — and
 * no trace of a key nobody documented.
 */
@QuarkusTest
@TestProfile(DatabaselessHealthTest.Databaseless.class)
class DatabaselessHealthTest {

    /** A value no route may produce: proof that an undocumented key is ignored, not echoed. */
    private static final String UNDOCUMENTED_SENTINEL = "SENTINEL_UNDOCUMENTED_KEY";

    /** This boot's secret: a value that must stay out of every body and every log line. */
    private static final String SECRET = "databaseless-health-secret-0123456789abcdef";

    public static class Databaseless implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    // Both names, because a @QuarkusTest run asks for the test-profiled one and that
                    // is the name application.properties turns on for the suites that do use a DB.
                    "agora.database.enabled", "false",
                    "%test.agora.database.enabled", "false",
                    "agora.session.secret", SECRET,
                    // An unreachable datasource: neither liveness nor a refusal may notice, because
                    // noticing would mean a connection this deployment never stated.
                    "quarkus.datasource.jdbc.url", "jdbc:sqlite:/agora-no-such-directory/health.db",
                    "agora.database.connection.url", "",
                    "agora.undocumented.probe", UNDOCUMENTED_SENTINEL);
        }
    }

    @Inject
    ForumSettings settings;

    /**
     * Every route that stores or reads a row: settings, a setting, threads, the session of the
     * cookie it is given, and the migration ledger. All of them live under the data surface the
     * gate covers, so a leak of the shipped development database would show up here first.
     */
    private static final List<String> DATA_ROUTES = List.of(
            "/api/settings",
            "/api/settings/forum.name",
            "/api/threads",
            "/api/auth/me",
            "/api/schema");

    @Test
    void healthAnswersWithNothingButLiveness() {
        String body = given()
                .when().get("/healthz")
                .then().statusCode(200)
                .extract().asString();
        assertEquals("{\"status\":\"ok\"}", body);
    }

    @Test
    void everyDataRouteRefusesWithoutTouchingADatabaseNobodyStated() {
        for (String route : DATA_ROUTES) {
            String body = given()
                    .when().get(route)
                    // 503 is the refusal of an unconfigured deployment; 500 would be the datasource
                    // being asked anyway, and that is the leak this asserts against.
                    .then().statusCode(503)
                    .extract().asString();
            assertEquals("{\"error\":\"database_not_configured\"}", body, route);
            assertFalse(body.contains(SECRET), route + " must carry no setting value");
            assertFalse(body.contains(UNDOCUMENTED_SENTINEL), route);
        }
    }

    @Test
    void aWriteIsRefusedBeforeItsBodyIsRead() {
        // The gate runs ahead of the request filter and the handler, so a database-less deployment
        // never buffers, parses or stores a body it has nowhere to put.
        given()
                .contentType("application/json")
                .body("{\"title\":\"Agora now\",\"body\":\"Written without a database\"}")
                .when().post("/api/threads")
                .then().statusCode(503)
                .extract().asString();

        given()
                .contentType("application/json")
                .body("{\"value\":\"Agora\"}")
                .when().put("/api/settings/forum.name")
                .then().statusCode(503);

        // Liveness keeps answering through all of it: the probe is not a data route.
        given().when().get("/healthz").then().statusCode(200);
    }

    @Test
    void noSettingValueIsRenderedAndAnUndocumentedKeyIsIgnored() {
        String health = given().when().get("/healthz").then().statusCode(200).extract().asString();
        String release = given().when().get("/__release").then().statusCode(200).extract().asString();
        assertFalse(health.contains(SECRET), "liveness must carry no setting value");
        assertFalse(release.contains(SECRET), release);
        assertFalse(health.contains(UNDOCUMENTED_SENTINEL), health);
        assertFalse(release.contains(UNDOCUMENTED_SENTINEL), release);
    }

    @Test
    void theBoundedSettingsKeepTheirDefaultsWhenNoDatabaseIsConfigured() {
        assertFalse(settings.databaseEnabled());
        assertEquals(16384, settings.requestMaxBytes());
        assertEquals(4000, settings.textMaxCodepoints());
        assertEquals(32768, settings.renderedMaxBytes());
        assertEquals("Europe/Paris", settings.businessZone().getId());
    }
}
