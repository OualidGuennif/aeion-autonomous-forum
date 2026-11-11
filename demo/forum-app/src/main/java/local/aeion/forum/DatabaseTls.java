package local.aeion.forum;

/**
 * Feature 002: the documented database transport policy, enforced at startup.
 *
 * A remote database connection must verify the server certificate and the host name
 * ({@code sslmode=verify-full}); only an explicitly isolated local transport - a loopback host or
 * a unix socket - may omit TLS. A refusal names the requirement and never repeats the URL, which
 * carries the topology a deployment should not disclose.
 */
final class DatabaseTls {

    static final String REFUSAL = "transport_unverified: a non-local database URL requires sslmode=verify-full";

    private DatabaseTls() {
    }

    /** Refuse a non-local JDBC URL that cannot verify certificate and host name. */
    static void requireVerifiedTransport(String url) {
        if (url == null || url.isBlank() || isLocal(url) || hasVerifyFull(url)) {
            return;
        }
        throw new IllegalStateException(REFUSAL);
    }

    /** True for the documented isolated local transports: loopback hosts and unix sockets. */
    static boolean isLocal(String url) {
        String host = hostOf(url);
        return host.isEmpty() || host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1");
    }

    private static boolean hasVerifyFull(String url) {
        int query = url.indexOf('?');
        if (query < 0) {
            return false;
        }
        for (String parameter : url.substring(query + 1).split("&")) {
            String[] pair = parameter.strip().split("=", 2);
            if (pair.length == 2 && pair[0].strip().equalsIgnoreCase("sslmode")
                    && pair[1].strip().equalsIgnoreCase("verify-full")) {
                return true;
            }
        }
        return false;
    }

    private static String hostOf(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return "";
        }
        String rest = url.substring(scheme + 3);
        if (rest.startsWith("[")) {
            int closing = rest.indexOf(']');
            return closing < 0 ? rest : rest.substring(1, closing);
        }
        int colon = rest.indexOf(':');
        int slash = rest.indexOf('/');
        if (slash >= 0 && (colon < 0 || slash < colon)) {
            return rest.substring(0, slash);
        }
        return colon < 0 ? rest : rest.substring(0, colon);
    }
}
