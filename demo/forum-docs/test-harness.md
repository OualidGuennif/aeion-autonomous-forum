# Disposable test databases (feature 003)

Backend suites run on Quarkus/JUnit against PostgreSQL databases the harness creates, owns and
releases. Nothing in this page reaches a developer's or a production database, and nothing here
changes what the application or the operator job does in a deployment. The canonical specification
lives in `knowledge/components/agora-product.md`; the code is `EphemeralDatabase` and
`QuarkusTestDatabase` on the test classpath only.

## The fixture environment is separate and loopback-only

| Setting | Meaning |
| --- | --- |
| `AEION_TEST_POSTGRES_JDBC_URL` | Cluster admin URL for the fixture. Exactly one loopback host. |
| `AEION_TEST_POSTGRES_USERNAME` | The fixture admin role. Never a deployment's migration owner. |
| `AEION_TEST_POSTGRES_PASSWORD` | Its password. Never logged, echoed or repeated in a refusal. |

These are fixture-only settings; the application and the operator read `QUARKUS_DATASOURCE_*`.
A test never inherits a production connection setting, and the harness refuses to build one.

Before anything is opened, the fixture URL must name exactly one host, and that host must be one of
`127.0.0.1`, `localhost` or `::1`. Anything else is refused with `test_postgres_host_not_allowed`
and **no connection is attempted**, so no DDL of any kind can reach a server the harness was not
given for testing. The whole authority of the URL names that one host, and nothing else names it:

* a URL that embeds credentials (`user@host`, whose real netloc is the part after the last `@`) is
  refused instead of being re-read, because a fixture URL never carries credentials — they are the
  separate settings above;
* a comma-separated host list is refused rather than trusted on its first entry, because the driver
  tries such a list in order and a loopback first host would leave a remote fallback reachable;
* any other authority spelling is refused: a protocol-address form, a socket path, a shorthand or
  hexadecimal loopback address, or anything trailing the host and its single optional port;
* a query parameter that could name the server itself (`host`, `hostname`, `server`, `serverName`,
  `service`, `serviceFile`, `pgServiceFile`) is refused, so a parameter is never a second way to
  reach a host the authority was not allowed to name.

A missing fixture environment is refused the same way, before any statement.

## Each test class owns exactly one database

`QuarkusTestDatabase` is a `QuarkusTestResourceLifecycleManager`: it creates the database before the
application boots and hands Quarkus the datasource settings, so two suites running at once get
separate databases and cannot see each other's rows. It also implements the injection hook
`inject(TestInjector)` from the official Quarkus lifecycle contract, and exposes its owned
`EphemeralDatabase` for a test that has to clean up after itself.

Names are generated, never supplied: `agora_test_` plus twelve hex digits. Any other name is refused
with `test_database_not_disposable` before a statement runs, which is what makes "point the harness
at a database without the disposable marker" abort rather than mutate. The database segment of the
fixture URL is replaced by that generated name — or inserted before the query string when the URL
names no database — so the migrations and both data sources always target the database this instance
created, never the driver's default one. The paired application role
is `agora_app_` plus the same twelve digits, and the migrations applied to the fixture are the
product's own Flyway files, run by the same `DatabaseOperator.migrate` a deployment uses. So a test
runs against the schema and the least-privilege grants it will meet in production.

## Isolation follows the transaction boundary that actually exists

* Isolation is per owner, not per connection: each `@QuarkusTest` class and each plain JUnit test
  that asks for one gets its own generated database, so no other suite's rows are ever visible.
* A rollback only undoes what the *same* client transaction wrote. A unit test that opens its own
  connection and ends it with `rollback()` therefore leaves nothing behind, and `EphemeralDatabase`
  hands out that connection through `dataSource()`.
* An HTTP or async test writes through the **server's** transactions, so a client rollback cannot
  cover them and the harness does not pretend otherwise. Those tests seed their own fixture rows and
  clear them explicitly: `ThreadServiceTest` deletes the tables it writes in `@BeforeEach` and
  re-seeds the one account it acts as, with `ON CONFLICT DO NOTHING`.

A row one test leaves behind therefore cannot change the next test's result: the databases differ,
and inside one database the fixture is re-seeded and cleared.

## Teardown releases only what this instance created

The harness tracks the database it created and every role it created.

* `close()` drops the tracked database with `DROP DATABASE ... WITH (FORCE)` and only the tracked
  roles.
* A role that already existed when setup ran is never claimed and never dropped, even when setup
  fails afterwards. Dropping a role the harness did not create would remove an authority belonging
  to something else.
* A database the harness did not create is never dropped either: a failed `CREATE DATABASE` leaves
  no ownership behind, so teardown for that instance is a no-op.
* `stop()` and `close()` are idempotent, so an interrupted test that already released its database
  can be released again by the framework without error and without touching anything else.

Refusals carry fixed codes and the opaque generated database name only —
`test_db_unprepared`, `test_db_release_failed`, `test_db_statement_failed`,
`test_db_admin_statement_failed`, `test_db_inspect_failed`, `test_postgres_missing`,
`test_postgres_host_not_allowed`, `test_database_not_disposable`. No host, no credential and no row
value appears in any of them.

## Determinism: a frozen clock and a seeded source

Every instance freezes its `ForumClock` at the documented instant
(`1_790_000_000_000_000_000` ns) and starts a `java.util.Random` seeded from that same fixed value.
`clock()` and `random()` return them, so a suite's timestamps and its random draws are a function of
the draw's position, not of when the suite happened to run. Two fixtures started in the same order
produce the same values, which is what makes a failure reproducible.

## Running the suites

```bash
AEION_TEST_POSTGRES_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/postgres \
AEION_TEST_POSTGRES_USERNAME=<fixture-admin> AEION_TEST_POSTGRES_PASSWORD=<fixture-admin> \
gradle -p demo/forum-app test --no-daemon
```

The fixture admin must be able to create databases and roles. Run two suites concurrently by running
two Gradle test invocations: each `@QuarkusTest` class gets its own generated database, so they do
not collide, and each releases only its own.
