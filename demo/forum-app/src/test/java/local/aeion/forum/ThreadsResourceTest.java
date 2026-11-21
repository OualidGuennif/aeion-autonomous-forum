package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Feature 010 over HTTP: anyone reads, only a forum session writes.
 *
 * A worker's gateway session is not accepted here, and the test proves the refusal rather than
 * trusting the comment: no cookie means 401, and a made-up one means 401 too.
 */
@QuarkusTest
@WithTestResource(QuarkusTestDatabase.class)
class ThreadsResourceTest {

    private static final String PASSWORD = "correct-horse-battery";

    /** Newer than any clock reading, so a planted legacy row is the first one the list reaches. */
    private static final long NEWEST_NS = 9_000_000_000_000_000_000L;

    /** The fixture database, used only to plant a legacy row that no write path can store. */
    @Inject
    DataSource dataSource;

    /** Log in through HTTP and keep both halves: the cookie and the CSRF token bound to it. */
    private String[] signIn(String name) {
        given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201);
        var login = given().contentType(ContentType.JSON)
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract();
        return new String[] {login.cookie(AuthService.COOKIE), login.path("csrf")};
    }

    @Test
    void aMutationWithTheSessionButWithoutTheCsrfTokenIsRefused() {
        String[] session = signIn("no-token" + System.nanoTime());
        given().cookie(AuthService.COOKIE, session[0])
                .contentType(ContentType.JSON)
                .body("{\"title\":\"A real title\",\"body\":\"A cross-site form could send this\"}")
                .when().post("/api/threads")
                .then().statusCode(403)
                .body("error", equalTo("csrf_invalid"));

        given().cookie(AuthService.COOKIE, session[0])
                .header("X-CSRF-Token", "not-the-token")
                .contentType(ContentType.JSON)
                .body("{\"title\":\"A real title\",\"body\":\"nor this one\"}")
                .when().post("/api/threads")
                .then().statusCode(403);

        // Reading is never gated, and a GET with the cookie still works.
        given().cookie(AuthService.COOKIE, session[0])
                .when().get("/api/threads")
                .then().statusCode(200);
    }

    @Test
    void readingIsPublicCreatingIsNot() {
        given().when().get("/api/threads")
                .then().statusCode(200)
                .body("count", greaterThanOrEqualTo(0));

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Anonymous\",\"body\":\"should not work\"}")
                .when().post("/api/threads")
                .then().statusCode(401)
                .body("error", equalTo("unauthorized"));

        given().cookie(AuthService.COOKIE, "aeion_not_a_forum_cookie.aaaa")
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Worker\",\"body\":\"a build session is not a person\"}")
                .when().post("/api/threads")
                .then().statusCode(401);
    }

    @Test
    void aMemberCreatesAThreadAndEveryoneCanReadIt() {
        String[] session = signIn("poster" + System.nanoTime());
        String cookie = session[0];
        String csrf = session[1];

        String id = given().cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", csrf)
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Kafka leaves the request path\",\"body\":\"The event is queued.\"}")
                .when().post("/api/threads")
                .then().statusCode(201)
                .body("title", equalTo("Kafka leaves the request path"))
                .extract().path("id");

        given().when().get("/api/threads/" + id)
                .then().statusCode(200)
                .body("id", equalTo(id));

        given().when().get("/api/threads/absent-thread")
                .then().statusCode(404)
                .body("error", equalTo("unknown_thread"));

        given().when().get("/api/threads?limit=5")
                .then().statusCode(200);
    }

    @Test
    void refusesAThreadWithoutTitleOrBody() {
        String[] session = signIn("vague" + System.nanoTime());
        String cookie = session[0];
        String csrf = session[1];

        given().cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", csrf)
                .contentType(ContentType.JSON)
                .body("{\"title\":\"ab\",\"body\":\"too short a title\"}")
                .when().post("/api/threads")
                .then().statusCode(400)
                .body("error", equalTo("invalid_title"));

        given().cookie(AuthService.COOKIE, cookie)
                .header("X-CSRF-Token", csrf)
                .contentType(ContentType.JSON)
                .body("{\"title\":\"A fine title\",\"body\":\"   \"}")
                .when().post("/api/threads")
                .then().statusCode(400)
                .body("error", equalTo("invalid_body"));
    }

    @Test
    void aBodyOverTheCodepointCapIsRefusedWith413() {
        String[] session = signIn("big" + System.nanoTime());
        given().cookie(AuthService.COOKIE, session[0])
                .header("X-CSRF-Token", session[1])
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Too long\",\"body\":\"" + "a".repeat(4001) + "\"}")
                .when().post("/api/threads")
                .then().statusCode(413)
                .body("error", equalTo("content_too_large"));
    }

    @Test
    void theBodyIsStoredNormalizedAndShownAsSafeHtml() {
        String[] session = signIn("render" + System.nanoTime());
        String id = given().cookie(AuthService.COOKIE, session[0])
                .header("X-CSRF-Token", session[1])
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Markup stays text\",\"body\":\"<img src=x onerror=alert(1)> and **bold**\"}")
                .when().post("/api/threads")
                .then().statusCode(201)
                .body("body", equalTo("<img src=x onerror=alert(1)> and **bold**"))
                .body("body_html", equalTo("<p>&lt;img src=x onerror=alert(1)&gt; and <strong>bold</strong></p>"))
                .extract().path("id");

        given().when().get("/api/threads/" + id)
                .then().statusCode(200)
                .body("body", equalTo("<img src=x onerror=alert(1)> and **bold**"))
                .body("body_html", equalTo("<p>&lt;img src=x onerror=alert(1)&gt; and <strong>bold</strong></p>"));
    }

    @Test
    void aBodyWhoseRenderedHtmlOutgrowsTheCapIsRefusedWith413() {
        // 994 empty blockquotes: 2980 codepoints, but 32801 rendered bytes, over the 32768-byte cap.
        String[] session = signIn("quotes" + System.nanoTime());
        String body = String.join("\\n\\n", Collections.nCopies(994, ">"));
        given().cookie(AuthService.COOKIE, session[0])
                .header("X-CSRF-Token", session[1])
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Rendered too large\",\"body\":\"" + body + "\"}")
                .when().post("/api/threads")
                .then().statusCode(413)
                .body("error", equalTo("content_too_large"));
    }

    /**
     * Feature 007: a row stored before the caps can normalize or render past them is never served.
     * While it is stored, its single read and the list answer 413 content_too_large, and once the
     * planted rows are removed the list answers 200 again, so nothing else was refused.
     */
    @Test
    void aLegacyRowOverACapIsRefusedOnReadAndInTheList() throws Exception {
        String expanded = "legacy-expanded-" + System.nanoTime();
        String outgrown = "legacy-outgrown-" + System.nanoTime();
        List<String> planted = List.of(expanded, outgrown);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement author = connection.prepareStatement(
                    "INSERT INTO accounts(id, username, password, role, created_ns, failures) "
                            + "VALUES (?, ?, 'legacy-fixture-hash', 'member', 0, 0) "
                            + "ON CONFLICT (id) DO NOTHING")) {
                author.setString(1, "legacy-author");
                author.setString(2, "legacy-writer");
                author.executeUpdate();
            }
            // 2001 U+0344 are 2001 raw codepoints, but NFC makes 4002; 1333 empty blockquotes are 3999
            // raw codepoints that render past 32768 bytes.
            insertLegacyThread(connection, expanded, "\u0344".repeat(2001));
            insertLegacyThread(connection, outgrown, ">\n\n".repeat(1333));
            try {
                for (String id : planted) {
                    given().when().get("/api/threads/" + id)
                            .then().statusCode(413)
                            .body("error", equalTo("content_too_large"));
                }
                given().when().get("/api/threads?limit=100")
                        .then().statusCode(413)
                        .body("error", equalTo("content_too_large"));
            } finally {
                for (String id : planted) {
                    deleteThread(connection, id);
                }
            }
        }
        given().when().get("/api/threads?limit=100").then().statusCode(200);
    }

    /** Parameters are bound by 1-based JDBC index: id, title, body, then the creation time. */
    private static void insertLegacyThread(Connection connection, String id, String body) throws Exception {
        try (PreparedStatement thread = connection.prepareStatement(
                "INSERT INTO threads(id, title, body, author_id, created_ns) "
                        + "VALUES (?, ?, ?, 'legacy-author', ?)")) {
            thread.setString(1, id);
            thread.setString(2, "Legacy row");
            thread.setString(3, body);
            thread.setLong(4, NEWEST_NS);
            thread.executeUpdate();
        }
    }

    /** Removes one planted row by its 1-based bound id, so later tests in this class see the forum again. */
    private static void deleteThread(Connection connection, String id) throws Exception {
        try (PreparedStatement thread = connection.prepareStatement("DELETE FROM threads WHERE id = ?")) {
            thread.setString(1, id);
            thread.executeUpdate();
        }
    }
}
