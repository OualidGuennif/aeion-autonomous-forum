package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(QuarkusTestDatabase.class)
class SecurityHeadersTest {

    @Test
    void healthCarriesTheEnforcingPolicy() {
        given()
                .when().get("/healthz")
                .then().statusCode(200)
                .header("Content-Security-Policy", containsString("default-src 'none'"))
                .header("Content-Security-Policy-Report-Only", nullValue())
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer");
    }
}
