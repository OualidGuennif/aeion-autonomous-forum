// Spec: knowledge/components/request-validation.md
package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(QuarkusTestDatabase.class)
class RequestValidationTest {

    @Test
    void allRoutesRejectUnknownOrWrongTypeFieldsWithoutEchoingValues() {
        for (String route : List.of("register", "login")) {
            String response = given().contentType(ContentType.JSON)
                    .body("{\"username\":123,\"password\":\"PRIVATE_VALUE\",\"extra\":true}")
                    .post("/api/auth/" + route).then().statusCode(422)
                    .body("error", equalTo("invalid_request"))
                    .body("fields", hasItem("username"))
                    .body("fields", hasItem("extra"))
                    .extract().asString();
            assertFalse(response.contains("PRIVATE_VALUE"));
            assertFalse(response.contains("123"));
        }
        given().contentType(ContentType.JSON).body("{\"title\":true,\"body\":\"valid body\"}")
                .post("/api/threads").then().statusCode(422).body("fields", hasItem("title"));
    }

    @Test
    void requiredFieldsAreReportedOnceAndInStableOrder() {
        given().contentType(ContentType.JSON).body("{\"username\":null}")
                .post("/api/auth/login").then().statusCode(422)
                .body("fields", equalTo(List.of("password", "username")));
        for (String value : List.of("false", "[]", "{}", "42")) {
            given().contentType(ContentType.JSON)
                    .body("{\"username\":" + value + ",\"password\":\"fixture-password\"}")
                    .post("/api/auth/login").then().statusCode(422)
                    .body("fields", equalTo(List.of("username")));
        }
    }

    @Test
    void malformedRootsAndAmbiguousJsonAreRefused() {
        for (String body : List.of("", "null", "[]", "true", "7", "{", "{} {}",
                "{\"username\":\"one\",\"username\":\"two\",\"password\":\"fixture-password\"}")) {
            given().contentType(ContentType.JSON).body(body)
                    .post("/api/auth/login").then().statusCode(422)
                    .body("error", equalTo("invalid_request"))
                    .body("fields", equalTo(List.of("body")));
        }
    }

    @Test
    void bodyLimitIncludesTheBoundary() {
        String prefix = "{\"username\":\"absent\",\"password\":\"";
        String suffix = "\"}";
        String atLimit = prefix + "x".repeat(16384 - prefix.length() - suffix.length()) + suffix;
        given().contentType(ContentType.JSON).body(atLimit)
                .post("/api/auth/login").then().statusCode(401);
        given().contentType(ContentType.JSON).body(atLimit + " ")
                .post("/api/auth/login").then().statusCode(413);
        given().contentType(ContentType.JSON).body("x".repeat(16385))
                .post("/api/auth/logout").then().statusCode(413);
    }

    @Test
    void validationRefusalsKeepSecurityHeaders() {
        given().contentType(ContentType.JSON).body("{}")
                .post("/api/auth/login").then().statusCode(422)
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer");
    }

    @Test
    void frameworkMediaTypeRefusalsRemainUnchanged() {
        given().contentType(ContentType.TEXT).body("not-json")
                .post("/api/auth/login").then().statusCode(415);
    }
}
