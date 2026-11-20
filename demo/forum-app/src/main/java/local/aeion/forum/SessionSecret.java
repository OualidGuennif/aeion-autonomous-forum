package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001, the fail-closed setting: a deployment without a real session secret does not start.
 *
 * The value is read once, validated, and never logged, rendered or echoed: a refusal names the key
 * ({@code AGORA_SESSION_SECRET}) and nothing else. A placeholder is refused too, because a key
 * called "change-me" is the same accident as a missing one, only later.
 *
 * Feature 005 uses it to sign the session cookie: the cookie carries {@code <token>.<hmac>}, and a
 * value that was edited in place is refused before the database is asked anything. The signing key
 * can rotate without logging every live session out at once: an optional, bounded list of
 * verification keys ({@code AGORA_SESSION_VERIFICATION_SECRETS}) still accepts cookies the previous
 * key signed, while every new cookie is signed by the primary alone.
 */
@ApplicationScoped
public class SessionSecret {

    static final String KEY = "AGORA_SESSION_SECRET";
    static final String VERIFICATION_KEY = "AGORA_SESSION_VERIFICATION_SECRETS";
    static final int MIN_BYTES = 32;
    /** The verified keys a deployment may still accept beyond the signing key: a bounded history. */
    static final int MAX_VERIFICATION_KEYS = 4;

    private static final List<String> PLACEHOLDERS = List.of(
            "change-me", "changeme", "placeholder", "secret", "password", "test", "dev", "todo");

    /** The primary: the only key that ever signs, and the first key that verifies. */
    private final byte[] material;
    /** The signing key first, then the still-accepted keys of past rotations, in configuration order. */
    private final List<byte[]> verificationKeys;

    @jakarta.inject.Inject
    public SessionSecret(@ConfigProperty(name = "agora.session.secret") Optional<String> value,
                         @ConfigProperty(name = "agora.session.verification-secrets")
                         Optional<List<String>> previousKeys) {
        this.material = validate(value.orElse(null), KEY);
        List<byte[]> keys = new ArrayList<>();
        keys.add(this.material);
        for (String raw : previousKeys.orElse(List.of())) {
            String trimmed = raw == null ? "" : raw.strip();
            if (trimmed.isEmpty()) {
                continue; // a blank list entry is no key at all, not a refusal
            }
            if (keys.size() > MAX_VERIFICATION_KEYS) {
                throw new IllegalStateException("session_secret_too_many: " + VERIFICATION_KEY
                        + " carries more than " + MAX_VERIFICATION_KEYS + " verification keys");
            }
            keys.add(validate(trimmed, VERIFICATION_KEY));
        }
        this.verificationKeys = List.copyOf(keys);
    }

    /** Build and validate a secret outside CDI, for a test that wants a known key. */
    public static SessionSecret of(String value) {
        return new SessionSecret(Optional.ofNullable(value), Optional.empty());
    }

    /** Build one with a primary and the verification keys of a rotation, the way a deployment is. */
    public static SessionSecret of(String value, List<String> verificationKeys) {
        return new SessionSecret(Optional.ofNullable(value), Optional.ofNullable(verificationKeys));
    }

    /**
     * The CSRF token for a session (feature 006): derived from the cookie value with the primary
     * key, so it needs no storage, and constant-time compared on the way in. A token nobody can
     * forge without a key, and one that dies with the session it belongs to.
     */
    public String csrfFor(String token) {
        return encoded(hmac("csrf:" + token, material));
    }

    /**
     * True only when {@code presented} is this session's CSRF token under one of the verified keys,
     * compared in constant time: a session a rotated-out key still authenticates keeps mutating.
     */
    public boolean csrfMatches(String token, String presented) {
        if (token == null || presented == null || presented.isEmpty()) {
            return false;
        }
        byte[] given = presented.getBytes(StandardCharsets.UTF_8);
        for (byte[] key : verificationKeys) {
            if (java.security.MessageDigest.isEqual(
                    encoded(hmac("csrf:" + token, key)).getBytes(StandardCharsets.UTF_8), given)) {
                return true;
            }
        }
        return false;
    }

    /** Sign {@code token} with the primary key and return {@code <token>.<mac>}, the cookie value. */
    public String sign(String token) {
        return token + "." + encoded(hmac(token, material));
    }

    /**
     * Return the token a signed cookie carries, or empty when no verified key matches. An unsigned,
     * over-long or malformed value is simply empty: the caller answers 401 either way.
     */
    public Optional<String> verify(String signed) {
        if (signed == null || signed.length() > 200) {
            return Optional.empty();
        }
        int lastDot = signed.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == signed.length() - 1) {
            return Optional.empty();
        }
        String token = signed.substring(0, lastDot);
        String presented = signed.substring(lastDot + 1);
        for (byte[] key : verificationKeys) {
            String expected = encoded(hmac(token, key));
            if (java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8))) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    /** The one URL-safe, padding-free rendering every signature and CSRF token is written in. */
    private static String encoded(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] hmac(String token, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("session_signature_unavailable", impossible);
        }
    }

    /**
     * Force the bean to be built now. A normal-scoped bean is a client proxy, so without this the
     * validation would run on the first request and the deployment would serve before refusing.
     */
    public void selfCheck() {
        if (material.length < MIN_BYTES || verificationKeys.isEmpty()) {
            throw new IllegalStateException("session_secret_unvalidated: " + KEY);
        }
    }

    private static byte[] validate(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "session_secret_missing: " + key + " is required and was not provided");
        }
        String trimmed = value.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String placeholder : PLACEHOLDERS) {
            if (lower.equals(placeholder) || lower.startsWith(placeholder + "-") || lower.startsWith(placeholder + "_")) {
                throw new IllegalStateException(
                        "session_secret_placeholder: " + key + " must not be a placeholder value");
            }
        }
        byte[] bytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES) {
            throw new IllegalStateException("session_secret_too_short: " + key + " must be at least "
                    + MIN_BYTES + " bytes, got " + bytes.length);
        }
        return bytes;
    }
}
