package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Feature 001: the health endpoint is liveness.
 *
 * A probe asks whether the process is up. It gets one field, and it gets nothing that describes
 * the build, the host or the configuration: a health response is read by whoever can reach the
 * port, and anything it carries is information that port gives away.
 */
@QuarkusTest
@WithTestResource(QuarkusTestDatabase.class)
class ForumHealthTest {

    @Test
    void theHealthBodyIsExactlyOneField() throws Exception {
        String body = given().when().get("/healthz").then().statusCode(200).extract().asString();
        JsonNode node = new ObjectMapper().readTree(body);
        assertEquals(1, node.size(), "the health body carries one field and nothing else: " + body);
        assertEquals("ok", node.get("status").asText(), body);
    }

    @Test
    void theHealthBodyNamesNoBuildOrHostDetail() throws Exception {
        String body = given().when().get("/healthz").then().statusCode(200).extract().asString();
        assertFalse(body.contains("release"), body);
        assertFalse(body.contains("runtime"), body);
        assertFalse(body.contains("127.0.0.1"), body);
        assertFalse(body.contains("agora"), body);
    }

    @Test
    void theHealthEndpointCarriesNoConfigurationValue() throws Exception {
        String body = given().when().get("/healthz").then().statusCode(200).extract().asString();
        assertFalse(body.contains("AGORA_"), body);
        assertFalse(body.contains("secret"), body);
        assertTrue(body.startsWith("{"), body);
    }
}
