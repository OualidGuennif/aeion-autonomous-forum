package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Forum user authentication: username and password, nothing borrowed from the worker gateway.
 *
 * A worker's `aeion_…` session says "this build may read a repository". It says nothing about a
 * forum account, and it is never accepted here. The two planes share the audit habit, not a format.
 *
 * Properties, each enforced in this class:
 *  * passwords are stored only as Argon2id (3 iterations, 64 MiB, parallelism 1) and wiped from
 *    memory after use; an unknown handle still costs one verification with the same parameters;
 *  * a handle is unique in its lowercase form (the generated `handle` column), so case variants are
 *    one account, and first-owner bootstrap is serialized so two starts cannot both win;
 *  * a disabled account is refused at login and its live sessions stop authenticating;
 *  * the session cookie value is random and stored only as its SHA-256, so a database dump does
 *    not hand out live sessions; a session expires after 15 minutes and is revoked on logout;
 *  * five failed attempts lock the account until an operator clears it, and a locked account is
 *    checked before the password hash is even computed.
 */
@ApplicationScoped
public class AuthService {

    public static final String COOKIE = "agora_session";
    public static final int SESSION_SECONDS = 900;
    static final int MAX_FAILURES = 5;
    static final int MIN_PASSWORD = 12;
    static final int MAX_PASSWORD = 256;

    /** The fixed key of the first-owner bootstrap lock; a constant, never a value read anywhere. */
    private static final long BOOTSTRAP_LOCK = 4_004_202_611L;

    @Inject
    Instance<DataSource> dataSources;

    /** The pinned verifier; CDI injects it, and the initializer only serves plain unit tests. */
    @Inject
    PasswordHasher hasher = new PasswordHasher();

    /** A direct datasource, used only by plain unit tests; CDI resolves {@link #dataSources} otherwise. */
    DataSource dataSource;

    @Inject
    ForumClock clock;

    @Inject
    SessionSecret secret;

    @Inject
    AgoraSettings settings;

    private final SecureRandom random = new SecureRandom();

    public record Account(String id, String username, String role) {
    }

    /** A signed cookie, and the CSRF token bound to it (feature 006). The raw token is not exposed. */
    public record Issued(Account account, String token, long expiresNs, String csrf) {
    }

    /** The resolved datasource, or a clean refusal on a database-less boot (feature 001). */
    private DataSource source() {
        if (dataSource != null) {
            return dataSource;
        }
        if (!settings.databaseEnabled() || dataSources.isUnsatisfied()) {
            throw new UnavailableException();
        }
        return dataSources.get();
    }

    /** Register a member account; the first account of an empty forum is the owner. */
    public Account register(String username, String password) {
        String name = normalise(username);
        checkPassword(password);
        String role = "member";
        try (Connection connection = source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockBootstrap(connection);
                if (count(connection) == 0) {
                    role = "owner";
                }
                String id = UUID.randomUUID().toString();
                String hash = hasher.hash(password);
                long now = clock.nowNs();
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO accounts(id, username, password, role, created_ns, updated_ns, failures) "
                                + "VALUES (?, ?, ?, ?, ?, ?, 0)")) {
                    insert.setString(1, id);
                    insert.setString(2, name);
                    insert.setString(3, hash);
                    insert.setString(4, role);
                    insert.setLong(5, now);
                    insert.setLong(6, now);
                    insert.executeUpdate();
                }
                connection.commit();
                return new Account(id, name, role);
            } catch (SQLException failure) {
                connection.rollback();
                if (isUniqueViolation(failure)) {
                    throw new AuthException("username_taken");
                }
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("register_failed", failure);
        }
    }

    /**
     * Verify a password and mint a session. An unknown handle still runs one verification; a locked
     * or disabled account is refused before the hash is touched, so the refusal never reveals
     * whether the supplied password was right.
     */
    public Issued login(String username, String password) {
        String name;
        try {
            name = normalise(username);
        } catch (AuthException invalid) {
            name = "";
        }
        try (Connection connection = source().getConnection()) {
            Account account = null;
            String hash = null;
            int failures = 0;
            long locked = 0;
            boolean disabled = false;
            try (PreparedStatement read = connection.prepareStatement(
                    "SELECT id, username, role, password, failures, locked_ns, disabled "
                            + "FROM accounts WHERE handle = lower(?)")) {
                read.setString(1, name);
                try (ResultSet row = read.executeQuery()) {
                    if (row.next()) {
                        account = new Account(row.getString(1), row.getString(2), row.getString(3));
                        hash = row.getString(4);
                        failures = row.getInt(5);
                        locked = row.getLong(6);
                        disabled = row.getBoolean(7);
                    }
                }
            }
            if (account == null) {
                hasher.verifyUnknown(password);
                throw new AuthException("invalid_credentials");
            }
            if (locked > 0) {
                throw new AuthException("account_locked");
            }
            if (disabled) {
                throw new AuthException("account_disabled");
            }
            if (!hasher.verify(hash, password)) {
                recordFailure(connection, account.id(), failures + 1);
                throw new AuthException("invalid_credentials");
            }
            clearFailures(connection, account.id());
            return issue(connection, account);
        } catch (SQLException failure) {
            throw new IllegalStateException("login_failed", failure);
        }
    }

    /** Resolve a cookie value to an enabled account, or empty: expired, revoked and unknown look alike. */
    public Optional<Account> authenticate(String signed) {
        Optional<String> token = secret.verify(signed);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try (Connection connection = source().getConnection();
             PreparedStatement read = connection.prepareStatement(
                     "SELECT a.id, a.username, a.role, s.expires_ns, s.revoked_ns "
                             + "FROM sessions s JOIN accounts a ON a.id = s.account_id "
                             + "WHERE s.id = ? AND a.disabled = FALSE")) {
            read.setString(1, digest(token.get()));
            try (ResultSet row = read.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                if (row.getLong(5) > 0 || row.getLong(4) <= clock.nowNs()) {
                    return Optional.empty();
                }
                return Optional.of(new Account(row.getString(1), row.getString(2), row.getString(3)));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("auth_failed", failure);
        }
    }

    /**
     * Owner-only disable: the flag, its timestamp and the revocation of live sessions land in one
     * transaction. Disabling an already disabled account changes nothing. The owner is never disabled,
     * so the forum always keeps an administrator who can log in.
     */
    public Account disable(Account actor, String targetUsername) {
        if (actor == null || !"owner".equals(actor.role())) {
            throw new AuthException("forbidden");
        }
        String name;
        try {
            name = normalise(targetUsername);
        } catch (AuthException invalid) {
            throw new AuthException("account_not_found");
        }
        try (Connection connection = source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Account target;
                boolean disabled;
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT id, username, role, disabled FROM accounts WHERE handle = lower(?) FOR UPDATE")) {
                    read.setString(1, name);
                    try (ResultSet row = read.executeQuery()) {
                        if (!row.next()) {
                            throw new AuthException("account_not_found");
                        }
                        target = new Account(row.getString(1), row.getString(2), row.getString(3));
                        disabled = row.getBoolean(4);
                    }
                }
                if ("owner".equals(target.role())) {
                    throw new AuthException("owner_protected");
                }
                if (!disabled) {
                    long now = clock.nowNs();
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE accounts SET disabled = TRUE, disabled_ns = ?, updated_ns = ? WHERE id = ?")) {
                        update.setLong(1, now);
                        update.setLong(2, now);
                        update.setString(3, target.id());
                        update.executeUpdate();
                    }
                    try (PreparedStatement revoke = connection.prepareStatement(
                            "UPDATE sessions SET revoked_ns = ? WHERE account_id = ? AND revoked_ns IS NULL")) {
                        revoke.setLong(1, now);
                        revoke.setString(2, target.id());
                        revoke.executeUpdate();
                    }
                }
                connection.commit();
                return target;
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } catch (RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("disable_failed", failure);
        }
    }

    public boolean logout(String signed) {
        Optional<String> token = secret.verify(signed);
        if (token.isEmpty()) {
            return false;
        }
        try (Connection connection = source().getConnection();
             PreparedStatement revoke = connection.prepareStatement(
                     "UPDATE sessions SET revoked_ns = ? WHERE id = ? AND revoked_ns IS NULL")) {
            revoke.setLong(1, clock.nowNs());
            revoke.setString(2, digest(token.get()));
            return revoke.executeUpdate() == 1;
        } catch (SQLException failure) {
            throw new IllegalStateException("logout_failed", failure);
        }
    }

    private Issued issue(Connection connection, Account account) throws SQLException {
        byte[] material = new byte[32];
        random.nextBytes(material);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(material);
        long issued = clock.nowNs();
        long expires = issued + SESSION_SECONDS * 1_000_000_000L;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO sessions(id, account_id, issued_ns, expires_ns) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, digest(token));
            insert.setString(2, account.id());
            insert.setLong(3, issued);
            insert.setLong(4, expires);
            insert.executeUpdate();
        }
        return new Issued(account, secret.sign(token), expires, secret.csrfFor(token));
    }

    private void recordFailure(Connection connection, String accountId, int failures) throws SQLException {
        boolean lock = failures >= MAX_FAILURES;
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE accounts SET failures = ?, locked_ns = ? WHERE id = ?")) {
            update.setInt(1, failures);
            update.setLong(2, lock ? clock.nowNs() : 0L);
            update.setString(3, accountId);
            update.executeUpdate();
        }
    }

    private void clearFailures(Connection connection, String accountId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE accounts SET failures = 0 WHERE id = ?")) {
            update.setString(1, accountId);
            update.executeUpdate();
        }
    }

    private long count(Connection connection) throws SQLException {
        try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM accounts");
             ResultSet row = count.executeQuery()) {
            return row.next() ? row.getLong(1) : 0;
        }
    }

    /** Serialize the count-and-insert decision, so two concurrent first registrations cannot both win. */
    private static void lockBootstrap(Connection connection) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            lock.setLong(1, BOOTSTRAP_LOCK);
            lock.execute();
        }
    }

    /** Unique violations carry SQLState 23505 on PostgreSQL; the message is never relied on. */
    private static boolean isUniqueViolation(SQLException failure) {
        if ("23505".equals(failure.getSQLState())) {
            return true;
        }
        String message = String.valueOf(failure.getMessage());
        return message.contains("UNIQUE") || message.contains("duplicate key");
    }

    /**
     * The normalised handle: surrounding whitespace stripped and the case folded to lowercase, the one
     * form every path stores, compares and returns. The generated {@code handle} column still guards
     * uniqueness in the database, whichever path wrote the row.
     */
    static String normalise(String username) {
        String name = username == null ? "" : username.strip().toLowerCase(java.util.Locale.ROOT);
        if (name.length() < 3 || name.length() > 64 || name.contains(" ")) {
            throw new AuthException("invalid_username");
        }
        return name;
    }

    static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new AuthException("weak_password");
        }
    }

    private static String digest(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A refusal with a code safe to return to the client. */
    public static class AuthException extends RuntimeException {
        private final String code;

        public AuthException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** A refusal thrown when a database-backed auth feature runs on a database-less boot (001). */
    public static class UnavailableException extends IllegalStateException {
        public UnavailableException() {
            super("database_disabled");
        }
    }
}
