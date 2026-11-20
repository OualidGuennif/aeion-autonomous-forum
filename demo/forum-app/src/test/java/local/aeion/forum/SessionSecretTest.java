package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Feature 001's fail-closed setting and feature 005's signed cookie, without a running application.
 *
 * A refusal must name the key and nothing else, and a cookie edited in place must never reach the
 * database lookup: both are asserted here, byte for byte.
 */
class SessionSecretTest {

    private static final String GOOD = "a-real-secret-with-32-bytes-at-least";

    @Test
    void refusesAMissingSecret() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> SessionSecret.of(""));
        assertTrue(refusal.getMessage().startsWith("session_secret_missing"), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("="), "the refusal must not look like a value");
    }

    @Test
    void refusesAShortSecretAndSaysHowShort() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> SessionSecret.of("too-short"));
        assertTrue(refusal.getMessage().startsWith("session_secret_too_short"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("32"), refusal.getMessage());
    }

    @Test
    void refusesAPlaceholder() {
        for (String placeholder : new String[] {"changeme", "change-me-0123456789abcdefghijklmnop", "test-secret-0123456789abcdefghijklmn"}) {
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> SessionSecret.of(placeholder), placeholder);
            assertTrue(refusal.getMessage().startsWith("session_secret_placeholder"), refusal.getMessage());
        }
    }

    @Test
    void signsAndVerifiesACookie() {
        SessionSecret secret = SessionSecret.of(GOOD);
        String signed = secret.sign("token-value");
        assertTrue(signed.startsWith("token-value."), signed);
        assertEquals(Optional.of("token-value"), secret.verify(signed));
    }

    @Test
    void refusesACookieEditedInPlace() {
        SessionSecret secret = SessionSecret.of(GOOD);
        String signed = secret.sign("token-value");
        String tampered = "other-value." + signed.substring(signed.indexOf('.') + 1);
        assertTrue(secret.verify(tampered).isEmpty());
        assertTrue(secret.verify(signed + "x").isEmpty());
        assertTrue(secret.verify("no-dot-at-all").isEmpty());
        assertTrue(secret.verify(null).isEmpty());
        assertTrue(secret.verify("").isEmpty());
    }

    @Test
    void aDifferentSecretDoesNotVerifyTheSameCookie() {
        String signed = SessionSecret.of(GOOD).sign("token-value");
        assertTrue(SessionSecret.of("another-real-secret-with-32-bytes").verify(signed).isEmpty());
    }

    @Test
    void twoSignaturesOfTheSameTokenAreIdenticalAndStable() {
        SessionSecret secret = SessionSecret.of(GOOD);
        assertEquals(secret.sign("token-value"), secret.sign("token-value"));
        assertNotEquals(secret.sign("token-value"), secret.sign("other-value"));
    }

    // ---- Feature 005: verification keys let a rotated signing key keep old cookies alive ----

    private static final String OLD = "the-previous-secret-that-was-rotated-out";
    private static final String NEW = "the-current-secret-after-the-rotation";

    @Test
    void aRotatedKeyStillAuthenticatesCookiesThePreviousKeySigned() {
        String signedByOld = SessionSecret.of(OLD).sign("token-value");

        SessionSecret rotated = SessionSecret.of(NEW, List.of(OLD));
        assertEquals(Optional.of("token-value"), rotated.verify(signedByOld),
                "a verification key keeps the sessions the previous key issued");
        assertEquals(Optional.of("token-value"), rotated.verify(rotated.sign("token-value")),
                "the primary still signs and verifies new cookies");

        assertTrue(SessionSecret.of("a-third-secret-of-32-bytes-at-least").verify(signedByOld).isEmpty(),
                "a key that never signed the value and was never configured must not accept it");
    }

    @Test
    void severalVerificationKeysAreAcceptedInTheConfiguredOrder() {
        String signedByOldest = SessionSecret.of(OLD).sign("token-value");
        String signedByMiddle = SessionSecret.of("the-middle-verification-key-000000").sign("token-value");

        SessionSecret rotated = SessionSecret.of(NEW, List.of("the-middle-verification-key-000000", OLD));
        assertEquals(Optional.of("token-value"), rotated.verify(signedByOldest));
        assertEquals(Optional.of("token-value"), rotated.verify(signedByMiddle));
    }

    @Test
    void blankVerificationEntriesAreNoKeysAtAll() {
        String signedByOld = SessionSecret.of(OLD).sign("token-value");
        SessionSecret rotated = SessionSecret.of(NEW, List.of("", "   ", OLD));
        assertEquals(Optional.of("token-value"), rotated.verify(signedByOld));
        assertTrue(rotated.verify(SessionSecret.of(NEW).sign("token-value")).isPresent());
    }

    @Test
    void aBadVerificationKeyRefusesNamingTheVerificationSetting() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> SessionSecret.of(NEW, List.of("short")));
        assertTrue(refusal.getMessage().startsWith("session_secret_too_short"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(SessionSecret.VERIFICATION_KEY), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("short,"), "a refusal never repeats the value");
    }

    @Test
    void tooManyVerificationKeysAreRefused() {
        List<String> keys = List.of(
                "the-verification-key-number-one-0001", "the-verification-key-number-two-0002",
                "the-verification-key-number-three-03", "the-verification-key-number-four-04",
                "the-verification-key-number-five-05");
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> SessionSecret.of(NEW, keys));
        assertTrue(refusal.getMessage().startsWith("session_secret_too_many"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(SessionSecret.VERIFICATION_KEY), refusal.getMessage());
    }

    @Test
    void aVerificationKeyKeepsTheCsrfTokenAliveAcrossTheRotation() {
        String oldCsrf = SessionSecret.of(OLD).csrfFor("token-value");
        SessionSecret rotated = SessionSecret.of(NEW, List.of(OLD));
        assertTrue(rotated.csrfMatches("token-value", oldCsrf),
                "a session the old key still authenticates keeps mutating with its own token");
        assertTrue(rotated.csrfMatches("token-value", rotated.csrfFor("token-value")));
        assertFalse(rotated.csrfMatches("token-value", "not-a-token-from-any-key"),
                "a token nobody derived from a verified key is refused");
        assertFalse(rotated.csrfMatches("other-value", oldCsrf));
        assertFalse(rotated.csrfMatches("token-value", null));
    }
}
