package local.aeion.forum;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Forum user endpoints: register, login, logout, sign out everywhere, "who am I", the CSRF
 * bootstrap and the owner-only account disable. A database-less boot (feature 001) answers 503 on
 * all of them instead of a database error. */
@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService auth;

    @Inject
    SessionSecret secret;

    @POST
    @Path("register")
    public Response register(Map<String, String> body) {
        try {
            AuthService.Account account = auth.register(value(body, "username"), value(body, "password"));
            return Response.status(201).entity(view(account)).build();
        } catch (AuthService.UnavailableException offline) {
            return databaseless();
        } catch (AuthService.AuthException refusal) {
            return Response.status(400).entity(Map.of("error", refusal.code())).build();
        }
    }

    /**
     * Feature 005: a login that carries a live session cookie rotates it. The stateless CSRF guard
     * has already demanded that cookie's token (403 otherwise, no database change), so the resource
     * only ever sees a request whose presented cookie belongs to its own session.
     */
    @POST
    @Path("login")
    public Response login(@CookieParam(AuthService.COOKIE) Cookie presentedCookie,
                          @Context UriInfo uriInfo,
                          Map<String, String> body) {
        try {
            AuthService.Issued issued = auth.login(value(body, "username"), value(body, "password"),
                    presentedCookie == null ? null : presentedCookie.getValue());
            NewCookie cookie = SessionCookie.issued(issued.token(),
                    SessionCookie.secure(uriInfo.getRequestUri().getScheme()),
                    auth.cookieLifetimeSeconds());
            Map<String, Object> payload = new LinkedHashMap<>(view(issued.account()));
            payload.put("expires_ns", issued.expiresNs());
            // The double-submit half: the client must send this back on every mutation.
            payload.put("csrf", issued.csrf());
            return Response.ok(payload).cookie(cookie).build();
        } catch (AuthService.UnavailableException offline) {
            return databaseless();
        } catch (AuthService.AuthException refusal) {
            boolean blocked = "account_locked".equals(refusal.code())
                    || "account_disabled".equals(refusal.code());
            int status = blocked ? 423 : 401;
            return Response.status(status).entity(Map.of("error", refusal.code())).build();
        }
    }

    /** Feature 004: only the owner may disable an account; the CSRF guard runs before this method. */
    @POST
    @Path("accounts/{username}/disable")
    @Consumes(MediaType.WILDCARD)
    public Response disable(@CookieParam(AuthService.COOKIE) Cookie cookie,
                            @PathParam("username") String username) {
        try {
            Optional<AuthService.Account> actor = cookie == null
                    ? Optional.empty() : auth.authenticate(cookie.getValue());
            if (actor.isEmpty()) {
                return Response.status(401).entity(Map.of("error", "unauthorized")).build();
            }
            return Response.ok(disabledView(auth.disable(actor.get(), username))).build();
        } catch (AuthService.UnavailableException offline) {
            return databaseless();
        } catch (AuthService.AuthException refusal) {
            int status = switch (refusal.code()) {
                case "forbidden" -> 403;
                case "account_not_found" -> 404;
                case "owner_protected" -> 409;
                default -> 400;
            };
            return Response.status(status).entity(Map.of("error", refusal.code())).build();
        }
    }

    /**
     * Feature 005: logout revokes the presented live session. A request that names no live session
     * to revoke — no cookie, an unusable value or an already revoked row — keeps the contract it
     * had before this feature: {@code 200 {"revoked": false}} and a cleared cookie.
     */
    @POST
    @Path("logout")
    @Consumes(MediaType.WILDCARD)
    public Response logout(@CookieParam(AuthService.COOKIE) Cookie cookie) {
        boolean revoked;
        try {
            revoked = cookie != null && auth.logout(cookie.getValue());
        } catch (AuthService.UnavailableException offline) {
            return databaseless();
        }
        return Response.ok(Map.of("revoked", revoked)).cookie(clearedCookie()).build();
    }

    /**
     * Feature 005: sign out everywhere. The CSRF guard has refused a presented session without its
     * token (403) before this method runs; an unauthenticated caller answers 401 and revokes
     * nothing. The response names how many sessions of this account were revoked now, and the
     * presented cookie is cleared like any logout.
     */
    @POST
    @Path("logout-all")
    @Consumes(MediaType.WILDCARD)
    public Response logoutAll(@CookieParam(AuthService.COOKIE) Cookie cookie) {
        Optional<AuthService.Account> account;
        try {
            account = cookie == null ? Optional.empty() : auth.authenticate(cookie.getValue());
        } catch (AuthService.UnavailableException offline) {
            return databaseless();
        }
        if (account.isEmpty()) {
            return Response.status(401).entity(Map.of("error", "unauthorized")).build();
        }
        int revoked;
        try {
            revoked = auth.logoutAll(account.get());
        } catch (AuthService.UnavailableException offline) {
            return databaseless();
        }
        return Response.ok(Map.of("revoked", revoked)).cookie(clearedCookie()).build();
    }

    /** The emptied cookie a logout answers with: nothing left for the browser to keep. */
    private static NewCookie clearedCookie() {
        return new NewCookie.Builder(AuthService.COOKIE).value("").path("/").maxAge(0).build();
    }

    @GET
    @Path("me")
    public Response me(@CookieParam(AuthService.COOKIE) Cookie cookie) {
        Optional<AuthService.Account> account;
        try {
            account = cookie == null ? Optional.empty() : auth.authenticate(cookie.getValue());
        } catch (AuthService.UnavailableException offline) {
            return databaseless();
        }
        if (account.isEmpty()) {
            return Response.status(401).entity(Map.of("error", "unauthorized")).build();
        }
        return Response.ok(view(account.get())).build();
    }

    /**
     * Feature 006: the CSRF bootstrap. The client that kept its cookie but not its token re-fetches
     * it here before any mutation, and {@code CsrfGuard} demands it back on that mutation, so a
     * stolen cookie alone still cannot act for the user. The answer is the exact token the login
     * payload carried — re-derived from the cookie value with the signing key, never re-minted —
     * and it leaves only for a session that is live right now: without a cookie or with an
     * unusable one, or for an expired, revoked or disabled session, the answer is the same 401 the
     * other authenticated endpoints give, and a database-less boot answers 503.
     */
    @GET
    @Path("csrf")
    public Response csrf(@CookieParam(AuthService.COOKIE) Cookie cookie) {
        Optional<String> token = cookie == null ? Optional.empty() : secret.verify(cookie.getValue());
        if (token.isEmpty()) {
            return Response.status(401).entity(Map.of("error", "unauthorized")).build();
        }
        Optional<AuthService.Account> account;
        try {
            account = auth.authenticate(cookie.getValue());
        } catch (AuthService.UnavailableException offline) {
            return databaseless();
        }
        if (account.isEmpty()) {
            return Response.status(401).entity(Map.of("error", "unauthorized")).build();
        }
        return Response.ok(Map.of("csrf", secret.csrfFor(token.get()))).build();
    }

    /** The database-less refusal: a 503 that names the state and exposes no value. */
    private static Response databaseless() {
        return Response.status(503).entity(Map.of("error", "database_disabled")).build();
    }

    private static Map<String, Object> view(AuthService.Account account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", account.id());
        payload.put("username", account.username());
        payload.put("role", account.role());
        return payload;
    }

    /** The disable acknowledgement: the target handle and its new state, nothing else. */
    private static Map<String, Object> disabledView(AuthService.Account account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", account.username());
        payload.put("disabled", true);
        return payload;
    }

    private static String value(Map<String, String> body, String name) {
        return body == null ? null : body.get(name);
    }
}
