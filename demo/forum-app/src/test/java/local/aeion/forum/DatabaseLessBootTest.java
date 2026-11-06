package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Feature 001: the liveness contract of a database-less deployment.
 *
 * The environment names a database that cannot exist. The health body is asserted byte for byte,
 * not field by field: the protected acceptance compares it with {@code {status: "ok"}}, so a
 * release, runtime or host field would be a regression even if every existing field still matched.
 */
@QuarkusTest
@WithTestResource(DatabaseLessTestEnvironment.class)
class DatabaseLessBootTest {

    @Test
    void applicationStartsWithAnUnreachableDatabase() {
        assertTrue(true);
    }

    @Test
    void databaseBackedApiIsUnavailableWithoutAConnection() {
        given()
                .when().get("/api/settings")
                .then().statusCode(503)
                .body("error", equalTo("database_disabled"));
    }

    @Test
    void healthIsLivenessOnly() {
        String body = given()
                .when().get("/healthz")
                .then().statusCode(200)
                .extract().asString();
        assertEquals("{\"status\":\"ok\"}", body);
    }
}
