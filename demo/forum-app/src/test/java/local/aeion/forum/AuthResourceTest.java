package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/**
 * Features 004 and 005 over HTTP: register, log in, ask who I am, log out, and get locked out.
 *
 * Sessions are cookies here, not bearer tokens, so the test uses the real cookie jar and checks
 * the flags a browser depends on: HttpOnly, Path=/, SameSite=Strict.
 */
@QuarkusTest
@WithTestResource(QuarkusTestDatabase.class)
class AuthResourceTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Test
    void registersThenLogsInAndAnswersWhoAmI() {
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"alice\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("username", equalTo("alice"))
                .body("role", anyOf(equalTo("owner"), equalTo("member")));

        io.restassured.response.ExtractableResponse<io.restassured.response.Response> login =
                given().contentType(ContentType.JSON)
                        .body("{\"username\":\"alice\",\"password\":\"" + PASSWORD + "\"}")
                        .when().post("/api/auth/login")
                        .then().statusCode(200)
                        .body("username", equalTo("alice"))
                        .body("expires_ns", notNullValue())
                        .body("csrf", notNullValue())
                        .cookie(AuthService.COOKIE)
                        .extract();
        String cookie = login.cookie(AuthService.COOKIE);
        String csrf = login.path("csrf");

        given().cookie(AuthService.COOKIE, cookie)
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("username", equalTo("alice"));

        // Logout is a mutation: the session must present its CSRF token (feature 006).
        given().cookie(AuthService.COOKIE, cookie)
                .when().post("/api/auth/logout")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));

        given().cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", csrf)
                .when().post("/api/auth/logout")
                .then().statusCode(200)
                .body("revoked", equalTo(true));

        given().cookie(AuthService.COOKIE, cookie)
                .when().get("/api/auth/me")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Test
    void setsAHardenedCookie() {
        String name = "cookie-" + System.nanoTime();
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201);

        io.restassured.response.ExtractableResponse<Response> login = given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract();

        String header = login.header("Set-Cookie");
        String flags = header == null ? "" : header;
        org.junit.jupiter.api.Assertions.assertTrue(flags.contains("HttpOnly"), flags);
        org.junit.jupiter.api.Assertions.assertTrue(flags.contains("Path=/"), flags);
        org.junit.jupiter.api.Assertions.assertTrue(flags.toLowerCase().contains("samesite=strict"), flags);
        org.junit.jupiter.api.Assertions.assertFalse(flags.contains(PASSWORD), "the cookie is not a credential echo");
    }

    @Test
    void answers401ForAWrongPasswordAnd423AfterFive() {
        String name = "locked-" + System.nanoTime();
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201);

        for (int attempt = 1; attempt <= 5; attempt++) {
            given().contentType(ContentType.JSON)
                    .body("{\"username\":\"" + name + "\",\"password\":\"wrong-password-here\"}")
                    .when().post("/api/auth/login")
                    .then().statusCode(401)
                    .body("error", equalTo("invalid_credentials"));
        }
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(423)
                .body("error", equalTo("account_locked"));
    }

    @Test
    void refusesABodyBeyondTheSixteenKilobyteCap() {
        String tooBig = "{\"username\":\"bulk\",\"password\":\"" + "x".repeat(20000) + "\"}";
        given().contentType(ContentType.JSON)
                .body(tooBig)
                .when().post("/api/auth/register")
                .then().statusCode(413);
    }

    @Test
    void refusesAWeakPasswordAndAnUnknownAccount() {
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"weakling\",\"password\":\"short\"}")
                .when().post("/api/auth/register")
                .then().statusCode(400)
                .body("error", equalTo("weak_password"));

        given().contentType(ContentType.JSON)
                .body("{\"username\":\"ghost\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(401)
                .body("error", equalTo("invalid_credentials"));
    }

    // ---- Feature 005: rotation on login and sign-out-everywhere, over the real HTTP surface ----

    /** Register a handle this suite's shared database has not met and return it. */
    private static String freshHandle(String prefix) {
        String name = prefix + System.nanoTime();
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201);
        return name;
    }

    private static io.restassured.response.ExtractableResponse<Response> login(String username) {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract();
    }

    @Test
    void aLoginPresentingItsCookieRotatesTheSessionAndRevokesOnlyThatOne() {
        String name = freshHandle("rotate");
        io.restassured.response.ExtractableResponse<Response> first = login(name);
        io.restassured.response.ExtractableResponse<Response> other = login(name);
        String firstCookie = first.cookie(AuthService.COOKIE);
        String otherCookie = other.cookie(AuthService.COOKIE);
        String firstCsrf = first.path("csrf");
        org.junit.jupiter.api.Assertions.assertNotEquals(firstCookie, otherCookie,
                "two logins must not share one session identifier");

        io.restassured.response.ExtractableResponse<Response> rotated =
                given().contentType(ContentType.JSON)
                        .cookie(AuthService.COOKIE, firstCookie)
                        .header("X-CSRF-Token", firstCsrf)
                        .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                        .when().post("/api/auth/login")
                        .then().statusCode(200)
                        .body("csrf", notNullValue())
                        .extract();

        String rotatedCookie = rotated.cookie(AuthService.COOKIE);
        String rotatedCsrf = rotated.path("csrf");
        org.junit.jupiter.api.Assertions.assertNotNull(rotatedCookie, "the rotated login sets a cookie");
        org.junit.jupiter.api.Assertions.assertNotEquals(rotatedCookie, firstCookie,
                "the presented identifier is rotated on login");
        org.junit.jupiter.api.Assertions.assertNotEquals(rotatedCsrf, firstCsrf,
                "the rotated session carries a new CSRF token");
        org.junit.jupiter.api.Assertions.assertTrue(rotatedCookie.contains("."),
                "the rotated cookie keeps the signed shape");

        given().cookie(AuthService.COOKIE, rotatedCookie)
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("username", equalTo(name));

        given().cookie(AuthService.COOKIE, firstCookie)
                .when().get("/api/auth/me")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));

        given().cookie(AuthService.COOKIE, otherCookie)
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("username", equalTo(name));

        // The pre-rotation CSRF token must not authorize the rotated cookie's mutations.
        given().cookie(AuthService.COOKIE, rotatedCookie)
                .header("X-CSRF-Token", firstCsrf)
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/logout-all")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));
        given().cookie(AuthService.COOKIE, rotatedCookie)
                .when().get("/api/auth/me")
                .then().statusCode(200);
    }

    @Test
    void logoutAllAnswersItsRefusalsAndThenRevokesEverySessionOfTheAccount() {
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/logout-all")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));

        String name = freshHandle("everywhere");
        io.restassured.response.ExtractableResponse<Response> first = login(name);
        io.restassured.response.ExtractableResponse<Response> sibling = login(name);
        String firstCookie = first.cookie(AuthService.COOKIE);
        String siblingCookie = sibling.cookie(AuthService.COOKIE);

        given().cookie(AuthService.COOKIE, firstCookie)
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/logout-all")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));

        given().cookie(AuthService.COOKIE, firstCookie)
                .when().get("/api/auth/me")
                .then().statusCode(200); // a CSRF refusal must not have revoked the session

        given().cookie(AuthService.COOKIE, firstCookie)
                .header("X-CSRF-Token", first.path("csrf"))
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/logout-all")
                .then().statusCode(200);

        for (String cookie : new String[] {firstCookie, siblingCookie}) {
            given().cookie(AuthService.COOKIE, cookie)
                    .when().get("/api/auth/me")
                    .then().statusCode(401)
                    .body("error", equalTo("unauthorized"));
        }

        // A login after the sign-out-everywhere issues a fresh identifier that authenticates.
        io.restassured.response.ExtractableResponse<Response> again = login(name);
        org.junit.jupiter.api.Assertions.assertNotEquals(again.cookie(AuthService.COOKIE), firstCookie);
        given().cookie(AuthService.COOKIE, again.cookie(AuthService.COOKIE))
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("username", equalTo(name));
    }

    @Test
    void logoutWithoutALiveSessionKeepsItsRevokedFalseAnswer() {
        // The pre-005 contract is preserved: no live session to revoke is not a refusal.
        given().when().post("/api/auth/logout")
                .then().statusCode(200)
                .body("revoked", equalTo(false));

        // A value no verified key signed is unusable, so it revokes nothing and says so.
        given().cookie(AuthService.COOKIE, "forged-cookie.forged-signature")
                .when().post("/api/auth/logout")
                .then().statusCode(200)
                .body("revoked", equalTo(false));

        // A real session, logged out once: the second attempt reports that nothing was left to revoke.
        String name = freshHandle("refuse");
        io.restassured.response.ExtractableResponse<Response> opened = login(name);
        String cookie = opened.cookie(AuthService.COOKIE);
        given().cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", opened.path("csrf"))
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/logout")
                .then().statusCode(200)
                .body("revoked", equalTo(true));
        given().cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", opened.path("csrf"))
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/logout")
                .then().statusCode(200)
                .body("revoked", equalTo(false));
    }

    /**
     * The cookie contract this suite's plain-HTTP transport can observe: the three constant
     * attributes on the issued session cookie, the signed shape, and no Secure — that flag belongs
     * to an HTTPS request alone (SessionCookieTest proves the pair, the acceptance proves 401s).
     */
    @Test
    void theIssuedSessionCookieStaysWithoutSecureOverPlainHttp() {
        String name = freshHandle("plainhttp");
        String header = login(name).header("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(header, "login must set a cookie");

        String pair = header.split(";")[0];
        org.junit.jupiter.api.Assertions.assertTrue(pair.startsWith(AuthService.COOKIE + "="), header);
        org.junit.jupiter.api.Assertions.assertTrue(
                pair.substring(AuthService.COOKIE.length() + 1).matches("[A-Za-z0-9_\\-+/=]+\\.[A-Za-z0-9_\\-+/=]+"),
                "the cookie must keep the signed <token>.<hmac> shape, got: " + header);

        String attributes = header.toLowerCase();
        org.junit.jupiter.api.Assertions.assertTrue(attributes.contains("httponly"), header);
        org.junit.jupiter.api.Assertions.assertTrue(attributes.contains("path=/"), header);
        org.junit.jupiter.api.Assertions.assertTrue(attributes.contains("samesite=strict"), header);
        org.junit.jupiter.api.Assertions.assertTrue(attributes.contains("max-age=" + AuthService.SESSION_SECONDS),
                "an unconfigured deployment issues the documented 900-second cookie: " + header);
        org.junit.jupiter.api.Assertions.assertFalse(attributes.contains("secure"),
                "Secure is issued over HTTPS only, got: " + header);
    }
}
