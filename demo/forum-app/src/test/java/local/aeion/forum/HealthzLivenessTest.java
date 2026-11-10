package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Feature 001's acceptance cases, on a running application that starts database-less.
 *
 * With only a session secret, liveness answers exactly {"status":"ok"} — no release, no runtime,
 * no host, no settings — and no request touches the database: migrations and the outbox drain are
 * skipped, and every database-backed endpoint answers 503 naming the state, never a value.
 */
@QuarkusTest
@TestProfile(DatabaselessProfile.class)
class HealthzLivenessTest {

    @Inject
    AgoraSettings settings;

    @Test
    void livenessAnswersOnlyTheStatus() {
        given()
                .when().get("/healthz")
                .then().statusCode(200)
                .body("status", equalTo("ok"));
        String body = given()
                .when().get("/healthz")
                .then().statusCode(200)
                .extract().asString();
        assertEquals("{\"status\":\"ok\"}", body, "liveness exposes nothing but the status");
    }

    @Test
    void theDocumentedDefaultsHoldOnADatabaseLessBoot() {
        assertEquals(false, settings.databaseEnabled());
        assertEquals(AgoraSettings.REQUEST_MAX_BYTES_DEFAULT, settings.requestMaxBytes());
        assertEquals(AgoraSettings.TEXT_MAX_CODEPOINTS_DEFAULT, settings.textMaxCodepoints());
        assertEquals(AgoraSettings.RENDERED_MAX_BYTES_DEFAULT, settings.renderedMaxBytes());
        assertEquals("Europe/Paris", settings.businessTimeZone().getId());
    }

    @Test
    void databaseBackedEndpointsRefuseCleanlyWithoutValues() {
        given()
                .when().get("/api/settings")
                .then().statusCode(503).body("error", equalTo("database_disabled"));
        given()
                .when().get("/api/threads")
                .then().statusCode(503).body("error", equalTo("database_disabled"));
        given()
                .when().get("/api/schema")
                .then().statusCode(503).body("error", equalTo("database_disabled"));
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"offline\",\"password\":\"correct-horse-battery\"}")
                .post("/api/auth/register")
                .then().statusCode(503).body("error", equalTo("database_disabled"));
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"a real title\",\"body\":\"a database-less body\"}")
                .post("/api/threads")
                .then().statusCode(503).body("error", equalTo("database_disabled"));
    }
}
