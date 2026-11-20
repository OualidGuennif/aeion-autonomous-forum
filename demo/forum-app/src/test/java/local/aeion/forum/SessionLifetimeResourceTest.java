package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Feature 005 over HTTP on a deployment that configured a longer session than the default.
 *
 * A cookie a browser is handed has to be able to live as long as the row behind it: a Max-Age frozen
 * at 900 seconds would silently cap every configured lifetime, so this suite reads the attribute off
 * the wire and proves it follows {@code AGORA_SESSION_ABSOLUTE_SECONDS}. The hardened attributes
 * travel with it unchanged, and nothing here needs the clock: expiry is a {@code ForumClock} service
 * check, never a live-HTTP one.
 */
@QuarkusTest
@TestProfile(LongSessionProfile.class)
@WithTestResource(QuarkusTestDatabase.class)
class SessionLifetimeResourceTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    @Test
    void issuesTheCookieForTheConfiguredAbsoluteLifetime() {
        String name = "longsession-" + System.nanoTime();
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201);

        String header = given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().header("Set-Cookie");

        assertNotNull(header, "login must set the session cookie");
        String cookie = header.toLowerCase(Locale.ROOT);
        Matcher maxAge = MAX_AGE.matcher(cookie);
        assertTrue(maxAge.find(), "the session cookie must state how long it lives: " + header);
        assertEquals("7200", maxAge.group(1),
                "the cookie lives exactly as long as the configured absolute lifetime: " + header);

        // The fixed half of the contract is untouched by a longer lifetime.
        assertTrue(cookie.contains("httponly"), header);
        assertTrue(cookie.contains("path=/"), header);
        assertTrue(cookie.contains("samesite=strict"), header);
        assertFalse(cookie.contains("secure"), "plain HTTP still gets no Secure flag: " + header);
    }
}
