// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Feature 004: the one pinned password verifier for product accounts.
 *
 * The package is pinned in the product build ({@link #DEPENDENCY}) and the parameters are explicit
 * code, not defaults: Argon2id, 3 iterations, 64 MiB and parallelism 1. A stored value never sets
 * the cost or the outcome. Only an encoding of the exact pinned shape ({@link #ENCODED_SHAPE})
 * reaches the library; anything else (unreadable, legacy or tampered) costs the same single pinned
 * verification as an unknown handle, and then refuses. The outcome of that unknown-handle
 * verification is discarded, so no stored value and no padding password can ever authenticate.
 *
 * The observation counters exist for unit tests: they record the verification count and the
 * parameter triple handed to the library. They never carry a password, a hash or a salt.
 */
@ApplicationScoped
public class PasswordHasher {

    public static final String DEPENDENCY = "de.mkammerer:argon2-jvm:2.11";
    public static final String ALGORITHM = "Argon2id";
    public static final int ITERATIONS = 3;
    public static final int MEMORY_KIB = 65536;
    public static final int PARALLELISM = 1;
    public static final String ENCODED_PARAMETERS = "m=" + MEMORY_KIB + ",t=" + ITERATIONS + ",p=" + PARALLELISM;

    /** The padding the unknown-handle hash is built from; its verification outcome is never used. */
    static final String UNKNOWN_PADDING = "unknown-handle-padding";

    /** The only prefix a usable stored hash may have: version 19 and the pinned triple, exactly. */
    static final String PINNED_PREFIX = "$argon2id$v=19$" + ENCODED_PARAMETERS + "$";

    /** An explicit bound on any stored encoding; a pinned Argon2id hash is about a hundred characters. */
    static final int MAX_ENCODED_LENGTH = 512;

    /**
     * The whole shape a usable stored hash may have: the pinned prefix, then one base64 salt of 8 to
     * 64 characters and one base64 tag of 16 to 128 characters. The library writes 22 and 43, so a
     * stored value can neither add a segment, pad, nor pick a salt or tag size outside these bounds.
     */
    static final Pattern ENCODED_SHAPE = Pattern.compile(
            Pattern.quote(PINNED_PREFIX) + "[A-Za-z0-9+/]{8,64}\\$[A-Za-z0-9+/]{16,128}");

    private static final String UNKNOWN_HASH = prepareUnknownHash();

    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private final AtomicInteger verifications = new AtomicInteger();
    private volatile int[] lastParameters;

    /** Hash one password with the pinned parameters; the clear text is wiped afterwards. */
    public String hash(String password) {
        char[] material = material(password);
        try {
            return argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, material);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("password_hash_failed");
        } finally {
            argon2.wipeArray(material);
        }
    }

    /**
     * Verify a stored hash: exactly one pinned Argon2id verification. Only a hash that declares the
     * pinned encoding can succeed; every other stored value refuses after the same single verification.
     */
    public boolean verify(String encoded, String password) {
        if (!hasPinnedParameters(encoded)) {
            verifyPinned(UNKNOWN_HASH, password);
            return false;
        }
        return verifyPinned(encoded, password);
    }

    /** The unknown-handle path: one pinned verification whose outcome is discarded, so it never authenticates. */
    public void verifyUnknown(String password) {
        verifyPinned(UNKNOWN_HASH, password);
    }

    public int verifications() {
        return verifications.get();
    }

    public int[] lastParameters() {
        int[] observed = lastParameters;
        return observed == null ? null : observed.clone();
    }

    public void resetObservations() {
        verifications.set(0);
        lastParameters = null;
    }

    /** True only for an encoding of the exact pinned shape that also fits the explicit length bound. */
    static boolean hasPinnedParameters(String encoded) {
        return encoded != null && encoded.length() <= MAX_ENCODED_LENGTH
                && ENCODED_SHAPE.matcher(encoded).matches();
    }

    /** One verification of an encoding already known to declare the pinned parameters. */
    private boolean verifyPinned(String encoded, String password) {
        verifications.incrementAndGet();
        lastParameters = new int[] {MEMORY_KIB, ITERATIONS, PARALLELISM};
        char[] material = material(password);
        try {
            return argon2.verify(encoded, material);
        } catch (RuntimeException malformed) {
            return false;
        } finally {
            argon2.wipeArray(material);
        }
    }

    private static String prepareUnknownHash() {
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        char[] material = material(UNKNOWN_PADDING);
        String encoded;
        try {
            encoded = argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, material);
        } catch (RuntimeException failure) {
            throw new ExceptionInInitializerError("password_hash_unavailable");
        } finally {
            argon2.wipeArray(material);
        }
        if (!hasPinnedParameters(encoded)) {
            throw new ExceptionInInitializerError("password_hash_unpinned");
        }
        return encoded;
    }

    private static char[] material(String password) {
        return (password == null ? "" : password).toCharArray();
    }
}
