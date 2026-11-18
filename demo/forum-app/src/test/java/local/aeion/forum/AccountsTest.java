// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Feature 004: normalized handles, the pinned Argon2id verifier and the owner-controlled disabled
 * state, without a running application.
 */
class AccountsTest {

    private static final String PASSWORD = "correct-horse-battery";

    private EphemeralDatabase database;
    private AuthService auth;
    private PasswordHasher hasher;

    @BeforeEach
    void open() {
        database = EphemeralDatabase.create();
        hasher = new PasswordHasher();
        auth = new AuthService();
        auth.dataSource = database.dataSource();
        auth.clock = database.clock();
        auth.secret = SessionSecret.of("a-real-secret-with-32-bytes-at-least");
        auth.hasher = hasher;
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void pinsTheArgon2idPackageAndParameters() {
        assertEquals("de.mkammerer:argon2-jvm:2.11", PasswordHasher.DEPENDENCY);
        assertEquals("Argon2id", PasswordHasher.ALGORITHM);
        assertEquals("m=65536,t=3,p=1", PasswordHasher.ENCODED_PARAMETERS);
        assertEquals(3, PasswordHasher.ITERATIONS);
        assertEquals(65536, PasswordHasher.MEMORY_KIB);
        assertEquals(1, PasswordHasher.PARALLELISM);
    }

    @Test
    void handlesAreNormalizedAndCaseVariantsAreOneAccount() {
        AuthService.Account created = auth.register("AliceCase", PASSWORD);
        assertEquals("alicecase", created.username(), "the stored handle is the lowercase form");
        assertEquals("owner", created.role());

        assertEquals("username_taken", assertThrows(AuthService.AuthException.class,
                () -> auth.register("ALICECASE", "another-long-password")).code());
        assertEquals("username_taken", assertThrows(AuthService.AuthException.class,
                () -> auth.register("alicecase", "another-long-password")).code());

        AuthService.Issued upper = auth.login("ALICECASE", PASSWORD);
        assertEquals(created.id(), upper.account().id(), "every case variant resolves to the same account");
        assertEquals("alicecase", upper.account().username());
    }

    @Test
    void concurrentCaseVariantsCreateExactlyOneAccount() throws Exception {
        String base = "raced" + System.nanoTime();
        List<String> variants = List.of(base, base.toUpperCase(), "Raced" + base.substring(5));
        ExecutorService pool = Executors.newFixedThreadPool(variants.size());
        try {
            List<Callable<String>> attempts = new ArrayList<>();
            for (String variant : variants) {
                attempts.add(() -> {
                    try {
                        auth.register(variant, PASSWORD);
                        return "created";
                    } catch (AuthService.AuthException refusal) {
                        return refusal.code();
                    }
                });
            }
            List<String> results = new ArrayList<>();
            for (Future<String> future : pool.invokeAll(attempts)) {
                results.add(future.get());
            }
            assertEquals(1, Collections.frequency(results, "created"), results.toString());
            assertEquals(variants.size() - 1,
                    Collections.frequency(results, "username_taken"), results.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, accountCount(base));
    }

    @Test
    void theDatabaseAlsoRefusesUnnormalizedCaseDuplicates() throws Exception {
        try (Connection connection = database.ownerDataSource().getConnection()) {
            insertDirect(connection, "direct-case", "Direct-Case");
            SQLException refusal = assertThrows(SQLException.class,
                    () -> insertDirect(connection, "DIRECT-CASE", "second-direct"));
            assertEquals("23505", refusal.getSQLState(), "the generated handle index is the authority");
        }
    }

    @Test
    void hashesDeclareThePinnedArgon2idParameters() throws Exception {
        auth.register("hashshape", PASSWORD);
        try (Connection connection = database.ownerDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT password FROM accounts WHERE username = 'hashshape'")) {
            assertTrue(rows.next());
            String stored = rows.getString(1);
            // Messages never carry the stored value: a failure report must not expose a hash.
            assertTrue(stored.startsWith("$argon2id$v=19$" + PasswordHasher.ENCODED_PARAMETERS + "$"),
                    "the stored credential must declare the pinned Argon2id encoding");
            assertTrue(PasswordHasher.hasPinnedParameters(stored),
                    "the stored credential must match the pinned shape");
            assertFalse(stored.contains(PASSWORD), "the clear text is never stored");
        }
    }

    @Test
    void aStoredEncodingCannotChooseItsOwnCost() {
        String canonical = hasher.hash(PASSWORD);
        assertTrue(PasswordHasher.hasPinnedParameters(canonical),
                "a freshly computed credential must match the pinned shape");
        hasher.resetObservations();
        assertTrue(hasher.verify(canonical, PASSWORD));
        assertEquals(1, hasher.verifications());

        String prefix = "$argon2id$v=19$" + PasswordHasher.ENCODED_PARAMETERS + "$";
        String salt = "XzSNuEfKs1NA2xbrv4vG0Q";
        String tag = "OxPu7rYFJe9p8xr1g4EEztVvvV3wvUi7+tDtTKzmiE4";
        List<String> hostile = List.of(
                canonical + "$extra",
                canonical + "==",
                canonical.replace("m=65536", "m=4194304"),
                prefix + "A".repeat(65) + "$" + tag,
                prefix + salt + "$" + "B".repeat(129),
                prefix + salt + "$",
                prefix + "A".repeat(400) + "$" + tag);
        for (int index = 0; index < hostile.size(); index++) {
            String encoded = hostile.get(index);
            assertFalse(PasswordHasher.hasPinnedParameters(encoded),
                    "hostile encoding " + index + " must not match the pinned shape");
            hasher.resetObservations();
            assertFalse(hasher.verify(encoded, PASSWORD),
                    "hostile encoding " + index + " must not authenticate");
            assertEquals(1, hasher.verifications(),
                    "a stored encoding outside the pinned shape still costs exactly one pinned verification");
            assertArrayEquals(new int[] {PasswordHasher.MEMORY_KIB, PasswordHasher.ITERATIONS,
                    PasswordHasher.PARALLELISM}, hasher.lastParameters());
        }
    }

    @Test
    void unknownAndWrongPasswordEachRunOneVerificationWithTheSameParameters() {
        auth.register("wrongpass", PASSWORD);

        hasher.resetObservations();
        AuthService.AuthException unknown = assertThrows(AuthService.AuthException.class,
                () -> auth.login("ghost-" + System.nanoTime(), PASSWORD));
        assertEquals("invalid_credentials", unknown.code());
        assertEquals(1, hasher.verifications(), "an unknown handle still verifies exactly once");
        assertArrayEquals(new int[] {PasswordHasher.MEMORY_KIB, PasswordHasher.ITERATIONS,
                PasswordHasher.PARALLELISM}, hasher.lastParameters());

        hasher.resetObservations();
        AuthService.AuthException wrong = assertThrows(AuthService.AuthException.class,
                () -> auth.login("wrongpass", PASSWORD + "x"));
        assertEquals("invalid_credentials", wrong.code());
        assertEquals(1, hasher.verifications(), "a wrong password verifies exactly once");
        assertArrayEquals(new int[] {PasswordHasher.MEMORY_KIB, PasswordHasher.ITERATIONS,
                PasswordHasher.PARALLELISM}, hasher.lastParameters());
        assertEquals(unknown.code(), wrong.code(), "the refusal never reveals whether the handle exists");
    }

    @Test
    void disabledAccountsAreRefusedAndLoseTheirLiveSessions() {
        AuthService.Account owner = auth.register("owner-person", PASSWORD);
        AuthService.Account victim = auth.register("victim-person", PASSWORD);
        AuthService.Issued session = auth.login("VICTIM-PERSON", PASSWORD);
        assertTrue(auth.authenticate(session.token()).isPresent());

        AuthService.Account disabled = auth.disable(owner, "Victim-Person");
        assertEquals(victim.id(), disabled.id(), "the target is resolved through its normalized handle");
        assertEquals("account_disabled", assertThrows(AuthService.AuthException.class,
                () -> auth.login("victim-person", PASSWORD)).code());
        assertTrue(auth.authenticate(session.token()).isEmpty(), "a disabled account loses its live session");
        assertEquals("account_disabled", assertThrows(AuthService.AuthException.class,
                () -> auth.login("victim-person", PASSWORD + "x")).code(),
                "a disabled account refuses before the password check, so the status never reveals "
                        + "whether the supplied password was correct");
    }

    @Test
    void onlyAnOwnerMayDisableAnAccount() {
        AuthService.Account owner = auth.register("owner-only", PASSWORD);
        auth.register("reader-only", PASSWORD);
        AuthService.Account reader = auth.login("reader-only", PASSWORD).account();

        assertEquals("forbidden", assertThrows(AuthService.AuthException.class,
                () -> auth.disable(reader, "owner-only")).code());
        assertEquals("owner_protected", assertThrows(AuthService.AuthException.class,
                () -> auth.disable(owner, "owner-only")).code(),
                "the only owner cannot disable itself: there is no re-enable path and no second owner");
        assertEquals("owner", auth.login("owner-only", PASSWORD).account().role(),
                "a refused disable leaves the owner enabled and administering the forum");
    }

    @Test
    void schemaCarriesTheNormalizedHandleDisabledFlagAndTimestamps() throws Exception {
        try (Connection connection = database.ownerDataSource().getConnection()) {
            assertColumn(connection, "handle", "NO");
            assertColumn(connection, "disabled", "NO");
            assertColumn(connection, "disabled_ns", "YES");
            assertColumn(connection, "updated_ns", "NO");
            try (PreparedStatement read = connection.prepareStatement(
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'accounts' AND column_name = 'disabled'");
                 ResultSet rows = read.executeQuery()) {
                assertTrue(rows.next());
                assertTrue(String.valueOf(rows.getString(1)).startsWith("false"),
                        "the disabled flag defaults to enabled");
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'accounts' "
                                 + "AND indexname = 'accounts_handle_key'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getLong(1), "normalized handles are unique in the database");
            }
        }
        auth.register("enabled-by-default", PASSWORD);
        try (Connection connection = database.ownerDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT disabled, handle FROM accounts WHERE username = 'enabled-by-default'")) {
            assertTrue(rows.next());
            assertFalse(rows.getBoolean(1), "a new account is enabled");
            assertEquals("enabled-by-default", rows.getString(2));
        }
    }

    private long accountCount(String handle) {
        try (Connection connection = database.ownerDataSource().getConnection();
             PreparedStatement read = connection.prepareStatement(
                     "SELECT COUNT(*) FROM accounts WHERE handle = ?")) {
            read.setString(1, handle.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rows = read.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void insertDirect(Connection connection, String username, String id) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO accounts(id, username, password, role, created_ns, updated_ns) "
                        + "VALUES (?, ?, 'not-a-real-hash', 'member', 1, 1)")) {
            insert.setString(1, id);
            insert.setString(2, username);
            insert.executeUpdate();
        }
    }

    private void assertColumn(Connection connection, String column, String nullable) throws SQLException {
        try (PreparedStatement read = connection.prepareStatement(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'accounts' AND column_name = ?")) {
            read.setString(1, column);
            try (ResultSet rows = read.executeQuery()) {
                assertTrue(rows.next(), "missing column " + column);
                assertEquals(nullable, rows.getString(1), column);
            }
        }
    }
}
