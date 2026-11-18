// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Feature 004: the operator-only first-administrator command. It creates the owner with the same
 * normalized handle and pinned Argon2id hash the application uses, takes its password only from the
 * environment, refuses a second owner and never prints a username, password or hash.
 */
class AccountsOperatorTest {

    private static final String ADMIN_PASSWORD = "first-owner-password";
    private static final String ARGV_PASSWORD = "argv-secret-value";

    @Test
    void createsTheFirstOwnerWithAPinnedHashAndPrintsNoCredentials() throws Exception {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = DatabaseOperator.run(new String[] {"create-admin", "Root-Admin"},
                    withPassword(environment(database), ADMIN_PASSWORD), new PrintStream(out),
                    new PrintStream(err));
            // Failure messages are fixed descriptions: captured output may hold credentials, so it is
            // never echoed into a CI log.
            assertEquals(0, code, "create-admin must create the first owner");
            String printed = out + "\n" + err;
            assertTrue(printed.contains("create-admin ok role=owner"), "create-admin must report the owner role");
            assertFalse(printed.contains("Root-Admin"), "create-admin must not print the username as typed");
            assertFalse(printed.contains("root-admin"), "create-admin must not print the normalized username");
            assertFalse(printed.contains(ADMIN_PASSWORD), "create-admin must not print the password");
            assertFalse(printed.contains("$argon2"), "create-admin must not print a password hash");

            try (Connection connection = database.ownerDataSource().getConnection();
                 PreparedStatement read = connection.prepareStatement(
                         "SELECT username, handle, role, disabled, password FROM accounts")) {
                try (ResultSet rows = read.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("root-admin", rows.getString(1));
                    assertEquals("root-admin", rows.getString(2));
                    assertEquals("owner", rows.getString(3));
                    assertFalse(rows.getBoolean(4));
                    assertTrue(PasswordHasher.hasPinnedParameters(rows.getString(5)),
                            "the stored credential must use the pinned encoding");
                    assertFalse(rows.next(), "exactly one account is created");
                }
            }

            ByteArrayOutputStream refusal = new ByteArrayOutputStream();
            assertEquals(1, DatabaseOperator.run(new String[] {"create-admin", "second-owner"},
                    withPassword(environment(database), ADMIN_PASSWORD),
                    new PrintStream(new ByteArrayOutputStream()), new PrintStream(refusal)));
            assertTrue(refusal.toString().contains("owner_exists"), "a second owner must be refused as owner_exists");

            // The application verifies what the operator wrote: the created owner can log in.
            AuthService auth = new AuthService();
            auth.dataSource = database.dataSource();
            auth.clock = new ForumClock();
            auth.secret = SessionSecret.of("a-real-secret-with-32-bytes-at-least");
            AuthService.Issued issued = auth.login("ROOT-ADMIN", ADMIN_PASSWORD);
            assertEquals("owner", issued.account().role());
        }
    }

    @Test
    void acceptsThePasswordFromTheEnvironment() {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            assertEquals(0, DatabaseOperator.run(new String[] {"create-admin", "env-admin"},
                    withPassword(environment(database), ADMIN_PASSWORD),
                    new PrintStream(new ByteArrayOutputStream()), new PrintStream(err)),
                    "the password must be accepted from the environment");
        }
    }

    @Test
    void aPasswordArgumentIsRefusedUnreadAndNeverEchoed() throws Exception {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = DatabaseOperator.run(new String[] {"create-admin", "argv-admin", ARGV_PASSWORD},
                    withPassword(environment(database), ADMIN_PASSWORD), new PrintStream(out),
                    new PrintStream(err));
            assertEquals(1, code);
            String printed = out + "\n" + err;
            assertTrue(printed.contains("password_argument_refused"),
                    "a password argument must be refused by its code");
            assertFalse(printed.contains(ARGV_PASSWORD), "a password argument must never be echoed");
            assertFalse(printed.contains("argv-admin"), "a refused command must not echo the username");
            assertEquals(0, accountCount(database), "a refused command creates no account");
        }
    }

    @Test
    void refusesWhenNoPasswordArrivesThroughTheEnvironment() throws Exception {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            assertEquals(1, DatabaseOperator.run(new String[] {"create-admin", "no-password"},
                    environment(database), new PrintStream(new ByteArrayOutputStream()),
                    new PrintStream(err)));
            assertTrue(err.toString().contains("admin_password_missing"),
                    "a missing password must be refused as admin_password_missing");
            assertEquals(0, accountCount(database));
        }
    }

    @Test
    void refusesAWeakPasswordWithoutCreatingAnAccount() throws Exception {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            assertEquals(1, DatabaseOperator.run(new String[] {"create-admin", "weak-admin"},
                    withPassword(environment(database), "short"),
                    new PrintStream(new ByteArrayOutputStream()), new PrintStream(err)));
            assertTrue(err.toString().contains("weak_password"), "a weak password must be refused as weak_password");
            assertEquals(0, accountCount(database));
        }
    }

    private static long accountCount(EphemeralDatabase database) throws Exception {
        try (Connection connection = database.ownerDataSource().getConnection();
             PreparedStatement read = connection.prepareStatement("SELECT COUNT(*) FROM accounts");
             ResultSet rows = read.executeQuery()) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static Map<String, String> withPassword(Map<String, String> base, String password) {
        Map<String, String> env = new HashMap<>(base);
        env.put("AGORA_ADMIN_PASSWORD", password);
        return env;
    }

    private static Map<String, String> environment(EphemeralDatabase database) {
        DatabaseOperator.Target target = target(database);
        return Map.of(
                "QUARKUS_DATASOURCE_JDBC_URL", target.url(),
                "QUARKUS_DATASOURCE_USERNAME", target.username(),
                "QUARKUS_DATASOURCE_PASSWORD", target.password(),
                "AEION_DB_APP_ROLE", target.appRole());
    }

    private static DatabaseOperator.Target target(EphemeralDatabase database) {
        String[] admin = EphemeralDatabase.adminCredentials.get();
        return new DatabaseOperator.Target(
                EphemeralDatabase.withDatabase(admin[0], database.database()), admin[1], admin[2],
                database.database().replaceFirst(EphemeralDatabase.PREFIX, "agora_app_"));
    }
}
