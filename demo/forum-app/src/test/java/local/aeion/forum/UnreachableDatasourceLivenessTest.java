package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

/**
 * Feature 001: liveness is independent of the datasource.
 *
 * A deployment with an invalid, unreachable datasource still starts and answers liveness, because
 * nothing in a database-less boot opens a connection: the migrations, the outbox drain and the
 * database-backed endpoints are all refused on the configuration flag before a driver is touched.
 */
@QuarkusTest
@TestProfile(UnreachableDatasourceProfile.class)
class UnreachableDatasourceLivenessTest {

    @Test
    void livenessAnswersDespiteABrokenDatasource() {
        given()
                .when().get("/healthz")
                .then().statusCode(200)
                .body("status", equalTo("ok"));
        String body = given()
                .when().get("/healthz")
                .then().statusCode(200)
                .extract().asString();
        org.junit.jupiter.api.Assertions.assertEquals("{\"status\":\"ok\"}", body);
    }

    @Test
    void databaseBackedEndpointsStillRefuseWithoutTouchingTheDriver() {
        given()
                .when().get("/api/settings")
                .then().statusCode(503).body("error", equalTo("database_disabled"));
        given()
                .when().get("/api/schema")
                .then().statusCode(503).body("error", equalTo("database_disabled"));
    }
}
