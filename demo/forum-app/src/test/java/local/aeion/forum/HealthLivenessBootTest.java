package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Feature 001 over HTTP with the database gate at its default (key absent).
 *
 * The application boots with only the session secret, liveness is the whole surface of the health
 * endpoint — the fixed body, no release, no runtime, no configuration — and the unknown key set by
 * the resource appears nowhere.
 */
@QuarkusTest
@WithTestResource(LivenessSecretResource.class)
class HealthLivenessBootTest {

    @Test
    void livenessRequiresNothingButTheSecretAndCarriesNothingButStatus() {
        String body = given().when().get("/healthz")
                .then().statusCode(200)
                .extract().asString();
        assertEquals("{\"status\":\"ok\"}", body, body);
        // Build, host and configuration stay out of the liveness answer (feature 001 invariant).
        assertFalse(body.contains("release"), body);
        assertFalse(body.contains("runtime"), body);
        assertFalse(body.contains("java"), body);
        assertFalse(body.contains(LivenessSecretResource.SECRET), body);
        assertFalse(body.contains(LivenessSecretResource.UNKNOWN_VALUE), body);
    }

    @Test
    void theServingStatusOfTheSameResourceCarriesNoBuildOrHostDetail() {
        String body = given().when().get("/__release")
                .then().statusCode(200)
                .extract().asString();
        assertEquals("{\"status\":\"serving\"}", body, body);
        assertFalse(body.toLowerCase().contains("unreleased"), body);
        assertFalse(body.contains("java"), body);
    }

    @Test
    void aHealthProbeRepeatedlyAnswersWithoutTheDatabase() {
        // The database-backed endpoints need tables this boot never creates: liveness must not ask.
        given().when().get("/healthz").then().statusCode(200);
        given().when().get("/healthz").then().statusCode(200);
    }
}
