package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Feature 001: a setting nobody reads changes nothing.
 *
 * The application reads the keys it documents and ignores the rest of the environment. A value
 * that arrives under a name the application does not know is not an error, is not announced and
 * is not rendered: a deployment with a spare variable is still a deployment that starts.
 */
@QuarkusTest
@WithTestResource(QuarkusTestDatabase.class)
@TestProfile(UnknownSettingTest.UnknownSetting.class)
class UnknownSettingTest {

    /** A key and a value that nothing in the application reads. */
    static final String UNKNOWN_KEY = "agora.unknown.setting";
    static final String UNKNOWN_VALUE = "a-value-nobody-reads";

    public static class UnknownSetting implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(UNKNOWN_KEY, UNKNOWN_VALUE);
        }
    }

    @Test
    void theApplicationStartsAndSaysNothingAboutIt() {
        String body = given().when().get("/healthz").then().statusCode(200).extract().asString();
        assertFalse(body.contains(UNKNOWN_VALUE), body);
        assertFalse(body.contains(UNKNOWN_KEY), body);
    }

    @Test
    void theForumRoutesAreUnaffectedByIt() {
        given().when().get("/api/settings").then().statusCode(200);
    }
}
