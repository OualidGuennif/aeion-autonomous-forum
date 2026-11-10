package local.aeion.forum;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Feature 001's bounded environment keys, mapped through the typed settings on a running app.
 *
 * The overrides reach the application exactly as operator-supplied environment values would, so a
 * wrong mapping here would show its face: the typed settings must carry the pinned values, and the
 * boot stays database-less while the caps are in range.
 */
@QuarkusTest
@TestProfile(CapsProfile.class)
class TypedCapsTest {

    @Inject
    AgoraSettings settings;

    @Test
    void thePinnedBoundsFlowFromTheEnvironmentIntoTheTypedSettings() {
        assertEquals(1024, settings.requestMaxBytes());
        assertEquals(400, settings.textMaxCodepoints());
        assertEquals(1024, settings.renderedMaxBytes());
        assertFalse(settings.databaseEnabled());
        assertTrue(settings.datasourceUrlConfigured(), "the transition baseline configures its datasource URL");
    }

    @Test
    void livenessKeepsAnsweringWhileCapsArePinned() {
        given()
                .when().get("/healthz")
                .then().statusCode(200);
    }
}
