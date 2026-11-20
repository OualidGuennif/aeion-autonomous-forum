package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Feature 006 on a database-less boot: the CSRF bootstrap is an authenticated, database-backed
 * endpoint, so it must answer the documented refusals without ever opening a driver — a 401 for a
 * request that names no usable session, and the feature-001 503 for a verifiable one.
 */
@QuarkusTest
@TestProfile(DatabaselessProfile.class)
class CsrfBootstrapDatabaselessTest {

    @Inject
    SessionSecret secret;

    @Test
    void aRequestWithNoUsableSessionAnswers401WithoutTouchingTheDatabase() {
        given().when().get("/api/auth/csrf")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));

        given().cookie(AuthService.COOKIE, "A".repeat(43) + "." + "B".repeat(43))
                .when().get("/api/auth/csrf")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Test
    void aVerifiableCookieMeetsTheDatabaselessRefusal() {
        // The value verifies, so the bootstrap asks the database for the session row it names.
        String verifiable = secret.sign("a-token-no-row-of-this-boot-carries");
        given().cookie(AuthService.COOKIE, verifiable)
                .when().get("/api/auth/csrf")
                .then().statusCode(503)
                .body("error", equalTo("database_disabled"));
    }
}
