package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Feature 006 over HTTP: a mutation that carries a session cookie must also carry that session's
 * CSRF token in the {@code X-CSRF-Token} header, and {@code GET /api/auth/csrf} hands a live
 * session its token back.
 *
 * The refusals are checked where they bite: a missing token, another session's token, or a token
 * in the URL query string alone must answer 403 before the handler runs, so the thread, the
 * setting or the session a probe aimed at is never touched; the bootstrap never re-mints and never
 * answers for a session that is not alive.
 */
@QuarkusTest
@WithTestResource(QuarkusTestDatabase.class)
class CsrfGuardTest {

    private static final String PASSWORD = "correct-horse-battery";
    /** The forged shape the guard must refuse before anything else happens. */
    private static final String FORGED = "A".repeat(43) + "." + "B".repeat(43);

    /** Register a handle this suite's shared database has not met and return it. */
    private static String register(String prefix) {
        String name = prefix + System.nanoTime();
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201);
        return name;
    }

    /** Log an existing handle in over HTTP and keep both halves: cookie and CSRF token. */
    private static ExtractableResponse<Response> login(String username) {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract();
    }

    @Test
    void theBootstrapAnswers401WithoutACookieWithAForgedOneOrAfterRevocation() {
        given().when().get("/api/auth/csrf")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));

        given().cookie(AuthService.COOKIE, FORGED)
                .when().get("/api/auth/csrf")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));

        String name = register("bootstrap");
        ExtractableResponse<Response> opened = login(name);
        String cookie = opened.cookie(AuthService.COOKIE);

        given().cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", opened.path("csrf"))
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/logout")
                .then().statusCode(200)
                .body("revoked", equalTo(true));
        given().cookie(AuthService.COOKIE, cookie)
                .when().get("/api/auth/csrf")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));

        ExtractableResponse<Response> again = login(name);
        String second = again.cookie(AuthService.COOKIE);
        given().cookie(AuthService.COOKIE, second)
                .header("X-CSRF-Token", again.path("csrf"))
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/logout-all")
                .then().statusCode(200);
        given().cookie(AuthService.COOKIE, second)
                .when().get("/api/auth/csrf")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Test
    void theBootstrapReturnsTheLoginTokenForALiveSessionAndItAuthorizesMutations() {
        String name = register("handback");
        ExtractableResponse<Response> opened = login(name);
        String cookie = opened.cookie(AuthService.COOKIE);
        String csrf = opened.path("csrf");

        ExtractableResponse<Response> bootstrap = given().cookie(AuthService.COOKIE, cookie)
                .when().get("/api/auth/csrf")
                .then().statusCode(200)
                .body("csrf", notNullValue())
                .extract();
        assertEquals(csrf, bootstrap.path("csrf"),
                "the bootstrap re-derives the token the login payload carried, never a fresh one");

        String title = "csrf bootstrap " + System.nanoTime();
        given().cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", bootstrap.path("csrf"))
                .contentType(ContentType.JSON)
                .body("{\"title\":\"" + title + "\",\"body\":\"created with the session's token\"}")
                .when().post("/api/threads")
                .then().statusCode(201)
                .body("title", equalTo(title));

        // Reads never ask for the token, and the bootstrap itself is a GET.
        given().cookie(AuthService.COOKIE, cookie).when().get("/api/auth/me")
                .then().statusCode(200)
                .body("username", equalTo(name));
    }

    @Test
    void threadCreationRefusesMissingForeignAndQueryOnlyTokens() {
        String name = register("guard");
        ExtractableResponse<Response> first = login(name);
        ExtractableResponse<Response> sibling = login(name);
        String cookie = first.cookie(AuthService.COOKIE);
        String csrf = first.path("csrf");
        String foreign = sibling.path("csrf");
        String title = "csrf refused " + System.nanoTime();
        String body = "{\"title\":\"" + title + "\",\"body\":\"an attempt\"}";

        given().cookie(AuthService.COOKIE, cookie).contentType(ContentType.JSON).body(body)
                .when().post("/api/threads")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().cookie(AuthService.COOKIE, cookie).header("X-CSRF-Token", foreign)
                .contentType(ContentType.JSON).body(body)
                .when().post("/api/threads")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().cookie(AuthService.COOKIE, cookie).queryParam("csrf", csrf)
                .contentType(ContentType.JSON).body(body)
                .when().post("/api/threads")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().cookie(AuthService.COOKIE, cookie).queryParam("X-CSRF-Token", csrf)
                .contentType(ContentType.JSON).body(body)
                .when().post("/api/threads")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));

        List<Map<String, ?>> threads = given().when().get("/api/threads")
                .then().statusCode(200)
                .extract().jsonPath().getList("threads");
        assertFalse(threads.stream().anyMatch(thread -> thread != null && title.equals(thread.get("title"))),
                "a refused attempt must not have created the thread");

        // The valid header is the one thing that gets through.
        given().cookie(AuthService.COOKIE, cookie).header("X-CSRF-Token", csrf)
                .contentType(ContentType.JSON)
                .body("{\"title\":\"csrf allowed " + System.nanoTime() + "\",\"body\":\"the header was present\"}")
                .when().post("/api/threads")
                .then().statusCode(201);
    }

    @Test
    void settingWritesRefuseMissingForeignAndQueryOnlyTokens() {
        String name = register("guard");
        ExtractableResponse<Response> first = login(name);
        ExtractableResponse<Response> sibling = login(name);
        String cookie = first.cookie(AuthService.COOKIE);
        String csrf = first.path("csrf");
        String foreign = sibling.path("csrf");
        String setting = "csrf-probe." + System.nanoTime();

        given().cookie(AuthService.COOKIE, cookie).contentType(ContentType.JSON)
                .body("{\"value\":\"refused\"}")
                .when().put("/api/settings/" + setting)
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().cookie(AuthService.COOKIE, cookie).header("X-CSRF-Token", foreign)
                .contentType(ContentType.JSON).body("{\"value\":\"refused\"}")
                .when().put("/api/settings/" + setting)
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().cookie(AuthService.COOKIE, cookie).queryParam("csrf", csrf)
                .contentType(ContentType.JSON).body("{\"value\":\"refused\"}")
                .when().put("/api/settings/" + setting)
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().cookie(AuthService.COOKIE, cookie).queryParam("X-CSRF-Token", csrf)
                .contentType(ContentType.JSON).body("{\"value\":\"refused\"}")
                .when().put("/api/settings/" + setting)
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().when().get("/api/settings/" + setting)
                .then().statusCode(404)
                .body("error", equalTo("unknown_setting"));

        // A refused logout revokes nothing, and the valid header is the one thing that gets through.
        given().cookie(AuthService.COOKIE, cookie)
                .when().post("/api/auth/logout")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().cookie(AuthService.COOKIE, cookie)
                .when().get("/api/auth/csrf")
                .then().statusCode(200)
                .body("csrf", equalTo(csrf));
        given().cookie(AuthService.COOKIE, cookie).header("X-CSRF-Token", csrf)
                .contentType(ContentType.JSON).body("{\"value\":\"authorized\"}")
                .when().put("/api/settings/" + setting)
                .then().statusCode(200)
                .body("value", equalTo("authorized"));
    }

    @Test
    void aCookieBearingLoginRotatesOnlyWithThePresentedSessionsToken() {
        String name = register("rotate-guard");
        ExtractableResponse<Response> first = login(name);
        ExtractableResponse<Response> sibling = login(name);
        String cookie = first.cookie(AuthService.COOKIE);
        String csrf = first.path("csrf");
        String foreign = sibling.path("csrf");
        String body = "{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}";

        given().contentType(ContentType.JSON).cookie(AuthService.COOKIE, cookie).body(body)
                .when().post("/api/auth/login")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().contentType(ContentType.JSON).cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", foreign).body(body)
                .when().post("/api/auth/login")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().contentType(ContentType.JSON).cookie(AuthService.COOKIE, cookie)
                .queryParam("csrf", csrf).body(body)
                .when().post("/api/auth/login")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        // A refused login revokes nothing: the presented session is still live.
        given().cookie(AuthService.COOKIE, cookie)
                .when().get("/api/auth/csrf")
                .then().statusCode(200)
                .body("csrf", equalTo(csrf));

        ExtractableResponse<Response> rotated = given().contentType(ContentType.JSON)
                .cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", csrf)
                .body(body)
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract();
        String rotatedCookie = rotated.cookie(AuthService.COOKIE);
        assertNotEquals(rotatedCookie, cookie, "an authorized cookie-bearing login rotates");
        assertNotEquals(rotated.path("csrf"), csrf, "the rotated session carries a new token");
        given().cookie(AuthService.COOKIE, rotatedCookie)
                .when().get("/api/auth/csrf")
                .then().statusCode(200)
                .body("csrf", equalTo(rotated.path("csrf")));
    }

    @Test
    void cookieLessCredentialExchangesAndReadsStayCallableWithoutTheHeader() {
        // Register and cookie-less login are the credential exchange: no header demanded.
        String name = register("free");
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("csrf", notNullValue())
                .cookie(AuthService.COOKIE);

        String cookie = login(name).cookie(AuthService.COOKIE);
        given().cookie(AuthService.COOKIE, cookie).when().get("/api/auth/me")
                .then().statusCode(200)
                .body("username", equalTo(name));
        given().cookie(AuthService.COOKIE, cookie).when().get("/api/threads")
                .then().statusCode(200);
        given().cookie(AuthService.COOKIE, cookie).when().get("/api/settings")
                .then().statusCode(200);
        given().cookie(AuthService.COOKIE, cookie).when().get("/api/auth/csrf")
                .then().statusCode(200);
    }
}
