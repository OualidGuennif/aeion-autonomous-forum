package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Feature 001: the minimal deployment, booted for real.
 *
 * Only the session secret is set, the database is not enabled, and no ephemeral database is
 * attached — yet {@code /healthz} answers and the liveness body carries nothing but the status.
 * The allowlisted defaults are read back from the running application so the properties file and
 * the typed allowlist cannot drift apart silently.
 */
@QuarkusTest
@TestProfile(DatabaseLessProfile.class)
class DatabaseLessBootTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    ForumSettings settings;

    @Test
    void aMinimalDeploymentServesLiveness() throws Exception {
        String body = given()
                .when().get("/healthz")
                .then().statusCode(200)
                .contentType(ContentType.JSON)
                .extract().asString();
        JsonNode parsed = JSON.readTree(body);
        JsonNode expected = JsonNodeFactory.instance.objectNode().put("status", "ok");
        assertEquals(expected, parsed, body);
    }

    @Test
    void theAllowlistedDefaultsAreTheBootedDefaults() {
        assertEquals(16384, settings.requestMaxBytes());
        assertEquals(4000, settings.textMaxCodepoints());
        assertEquals(32768, settings.renderedMaxBytes());
        assertEquals(ZoneId.of("Europe/Paris"), settings.businessZone());
        assertFalse(settings.databaseEnabled(), "this profile must boot database-less");
    }
}
