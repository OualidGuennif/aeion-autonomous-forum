package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Feature 001: the skeleton boots with only a session secret and no database at all.
 *
 * The profile points the datasource at a path that a connection would create, so the test can
 * prove that none was made: the file is still absent after the application answered. That is the
 * difference between "the health endpoint does not ask the database" and "the health endpoint did
 * not manage to ask the database".
 */
@QuarkusTest
@TestProfile(DatabaseLessBootTest.DatabaseLess.class)
class DatabaseLessBootTest {

    /** The file a connection would create. Its absence is the proof that nothing connected. */
    static final Path UNREACHABLE = Path.of(System.getProperty("java.io.tmpdir"),
            "agora-no-connection-" + System.nanoTime() + ".db");

    public static class DatabaseLess implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    // The deployment says nothing about its database: the database-backed features
                    // are off, and nothing may connect.
                    "agora.database.enabled", "false",
                    "quarkus.datasource.jdbc.url", "jdbc:sqlite:" + UNREACHABLE,
                    // The application refuses to start without a real secret; a test gets a fixed one.
                    "agora.session.secret", "ephemeral-database-less-secret-0123456789");
        }
    }

    @Test
    void livenessAnswersWithoutADatabase() {
        String body = given().when().get("/healthz").then().statusCode(200).extract().asString();
        org.junit.jupiter.api.Assertions.assertEquals("{\"status\":\"ok\"}", body);
    }

    @Test
    void noConnectionWasOpened() {
        assertFalse(Files.exists(UNREACHABLE),
                "the application must not open a database connection when the database is off");
    }

    @Test
    void theForumRoutesRefuseCleanly() {
        given().when().get("/api/settings").then().statusCode(503)
                .body("error", equalTo("database_disabled"));
    }
}
