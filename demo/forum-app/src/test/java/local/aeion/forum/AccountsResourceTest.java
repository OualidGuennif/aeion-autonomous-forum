// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Feature 004 over HTTP: normalized registration, the owner-only disable endpoint with CSRF, and a
 * disabled account's refusal at login. The owner is the first account of this profile's fresh
 * database, so its authority is real rather than assumed.
 */
@QuarkusTest
@TestProfile(AccountsProfile.class)
@WithTestResource(QuarkusTestDatabase.class)
class AccountsResourceTest {

    private static final String PASSWORD = "correct-horse-battery";

    private static String ownerName;
    private static String ownerCookie;
    private static String ownerCsrf;

    @BeforeEach
    void ensureOwner() {
        if (ownerCookie != null) {
            return;
        }
        String name = "ow" + System.nanoTime();
        ExtractableResponse<Response> created = given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201).extract();
        assertEquals("owner", created.path("role"),
                "the first account of a fresh forum must be the owner");
        assertNotNull(created.path("id"), "the account view carries the account id");

        ExtractableResponse<Response> issued = login(name);
        assertEquals(200, issued.statusCode());
        ownerName = name;
        ownerCookie = issued.cookie(AuthService.COOKIE);
        ownerCsrf = issued.path("csrf");
        assertTrue(ownerCsrf != null && !ownerCsrf.isBlank());
    }

    @Test
    void ownerDisablesAnAccountAndADisabledLoginIsRefused() {
        String victim = "dv" + System.nanoTime();
        assertEquals(201, register(victim).statusCode());
        ExtractableResponse<Response> before = login(victim);
        assertEquals(200, before.statusCode());

        ExtractableResponse<Response> disabled = given().cookie(AuthService.COOKIE, ownerCookie)
                .header("X-CSRF-Token", ownerCsrf)
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/accounts/" + victim.toUpperCase() + "/disable")
                .then().statusCode(200).extract();
        assertEquals(true, disabled.path("disabled"), "disable reports the disabled state");
        assertEquals(victim.toLowerCase(java.util.Locale.ROOT), disabled.path("username"));
        assertNoCredentialMaterial(disabled.asString());

        ExtractableResponse<Response> after = login(victim);
        assertEquals(423, after.statusCode(), "a disabled account is refused at login");
        assertEquals("account_disabled", after.path("error"));
        assertNoCredentialMaterial(after.asString());

        ExtractableResponse<Response> wrongPassword = login(victim, PASSWORD + "x");
        assertEquals(423, wrongPassword.statusCode(),
                "a disabled account refuses before the password check, so the status is the same "
                        + "whether or not the supplied password is correct");
        assertEquals("account_disabled", wrongPassword.path("error"));

        given().cookie(AuthService.COOKIE, before.cookie(AuthService.COOKIE))
                .when().get("/api/auth/me")
                .then().statusCode(401);
    }

    @Test
    void nonOwnersAndCallersWithoutCsrfCannotDisable() {
        String victim = "cs" + System.nanoTime();
        String attacker = "at" + System.nanoTime();
        assertEquals(201, register(victim).statusCode());
        assertEquals(201, register(attacker).statusCode());
        ExtractableResponse<Response> attackerLogin = login(attacker);
        assertEquals(200, attackerLogin.statusCode());

        given().cookie(AuthService.COOKIE, attackerLogin.cookie(AuthService.COOKIE))
                .header("X-CSRF-Token", attackerLogin.path("csrf"))
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/accounts/" + victim + "/disable")
                .then().statusCode(403);

        given().contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/accounts/" + victim + "/disable")
                .then().statusCode(401);

        given().cookie(AuthService.COOKIE, ownerCookie)
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/accounts/" + victim + "/disable")
                .then().statusCode(403);

        given().cookie(AuthService.COOKIE, ownerCookie)
                .header("X-CSRF-Token", "not-the-issued-token")
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/accounts/" + victim + "/disable")
                .then().statusCode(403);

        assertEquals(200, login(victim).statusCode(), "a refused disable leaves the account enabled");
    }

    @Test
    void registrationNormalizesCaseAndRefusesTheCaseVariant() {
        String mixed = "Cx" + System.nanoTime();
        ExtractableResponse<Response> created = register(mixed);
        assertEquals(201, created.statusCode());
        assertEquals(mixed.toLowerCase(java.util.Locale.ROOT), created.path("username"));

        assertEquals(400, register(mixed.toUpperCase()).statusCode());
        ExtractableResponse<Response> upper = login(mixed.toUpperCase());
        assertEquals(200, upper.statusCode());
        Object createdId = created.path("id");
        Object upperId = upper.path("id");
        assertNotNull(createdId, "the account view carries the account id");
        assertEquals(createdId, upperId, "case variants resolve to one account");
    }

    @Test
    void theOwnerCannotDisableItself() {
        ExtractableResponse<Response> refused = given().cookie(AuthService.COOKIE, ownerCookie)
                .header("X-CSRF-Token", ownerCsrf)
                .contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/accounts/" + ownerName + "/disable")
                .then().statusCode(409).extract();
        assertEquals("owner_protected", refused.path("error"));
        assertNoCredentialMaterial(refused.asString());
        assertEquals(200, login(ownerName).statusCode(),
                "the only owner stays enabled, so administration is never locked out");
    }

    @Test
    void unknownAndWrongPasswordShareTheRefusalShape() {
        String existing = "wp" + System.nanoTime();
        assertEquals(201, register(existing).statusCode());

        ExtractableResponse<Response> unknown = login("zz" + System.nanoTime());
        ExtractableResponse<Response> wrong = given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + existing + "\",\"password\":\"" + PASSWORD + "x\"}")
                .when().post("/api/auth/login")
                .then().statusCode(401).extract();

        assertEquals(401, unknown.statusCode());
        assertEquals(unknown.statusCode(), wrong.statusCode());
        String unknownError = unknown.path("error");
        String wrongError = wrong.path("error");
        assertEquals(unknownError, wrongError);
        Map<String, Object> unknownBody = unknown.as(Map.class);
        Map<String, Object> wrongBody = wrong.as(Map.class);
        assertEquals(Set.copyOf(unknownBody.keySet()), Set.copyOf(wrongBody.keySet()));
        assertNoCredentialMaterial(unknown.asString());
        assertNoCredentialMaterial(wrong.asString());
    }

    @Test
    void registeredAccountsStayEnabledUntilTheOwnerDisablesThem() {
        String first = "pa" + System.nanoTime();
        String second = "pb" + System.nanoTime();
        assertEquals(201, register(first).statusCode());
        assertEquals(201, register(second).statusCode());

        assertEquals(200, login(first).statusCode(), "a new account logs in immediately");
        assertEquals(200, login(second).statusCode());
        assertNotEquals("owner", login(first).path("role"), "only the first account is the owner");
    }

    private static ExtractableResponse<Response> register(String username) {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/register")
                .then().extract();
    }

    private static ExtractableResponse<Response> login(String username) {
        return login(username, PASSWORD);
    }

    private static ExtractableResponse<Response> login(String username, String password) {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .when().post("/api/auth/login")
                .then().extract();
    }

    /** Failure messages name the rule only: a response body may hold the very material being checked. */
    private static void assertNoCredentialMaterial(String body) {
        assertFalse(body.contains("$argon2"), "a response must never export an Argon2 hash");
        for (String marker : new String[] {"hash", "argon", "salt", "password", "passwd", "credential"}) {
            assertFalse(body.toLowerCase(java.util.Locale.ROOT).contains("\"" + marker),
                    "a response must not carry a field named like \"" + marker + "\"");
        }
    }
}
