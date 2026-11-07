package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Feature 001 over HTTP with lowered caps: the configuration is read, not hardcoded.
 *
 * A body above the configured byte cap is refused with 413 while a body exactly at the cap reaches
 * the handler and is answered by its business refusal (401 for absent credentials). The configured
 * text cap counts codepoints, not UTF-16 units: six astral codepoints are six for the cap.
 */
@QuarkusTest
@WithTestResource(ConfiguredCapsResource.class)
class ConfiguredCapsBootTest {

    private static String loginBody(int bodySize) {
        String prefix = "{\"username\":\"absent\",\"password\":\"";
        String suffix = "\"}";
        int filler = bodySize - prefix.length() - suffix.length();
        return prefix + "x".repeat(filler) + suffix;
    }

    @Test
    void theConfiguredByteCapIsTheBoundaryBetweenPassingAndRefusing() {
        String atCap = loginBody((int) ConfiguredCapsResource.REQUEST_CAP);
        given().contentType(ContentType.JSON).body(atCap)
                .post("/api/auth/login").then().statusCode(401);
        given().contentType(ContentType.JSON).body(atCap + "x")
                .post("/api/auth/login").then().statusCode(413)
                .body("error", org.hamcrest.Matchers.equalTo("request_too_large"));
    }

    @Test
    void theConfiguredTextCapCountsCodepointsNotUtf16Units() {
        // Six astral codepoints are six UTF-16 units twice over: a unit counter would refuse them
        // (12 > 10), a codepoint counter accepts them.
        String sixAstral = "\uD83D\uDE00".repeat(6);
        given().contentType(ContentType.JSON).body("{\"value\":\"" + sixAstral + "\"}")
                .put("/api/settings/forum.name").then().statusCode(200);

        // Eleven codepoints are above the configured cap, whatever their units.
        String elevenAstral = "\uD83D\uDE00".repeat(11);
        given().contentType(ContentType.JSON).body("{\"value\":\"" + elevenAstral + "\"}")
                .put("/api/settings/forum.name").then().statusCode(400)
                .body("error", equalTo("invalid_value"));
    }

    @Test
    void livenessIsUnchangedByAShorterCap() {
        String body = given().when().get("/healthz").then().statusCode(200).extract().asString();
        assertEquals("{\"status\":\"ok\"}", body);
    }
}
