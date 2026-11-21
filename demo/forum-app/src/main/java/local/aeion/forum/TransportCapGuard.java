// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Feature 007: a body over the transport cap keeps the API's JSON error shape at every layer.
 *
 * Quarkus stops a body over its own 16 KiB HTTP limit before any RESTEasy filter runs, so the
 * request validation filter's `413 {"error":"request_too_large"}` never reached such a client and
 * the refusal carried no error code. This guard answers the over-cap refusal at the earliest
 * routing point, with the same threshold, the same status and the same JSON body the filter
 * answers. A body inside the cap, or one that declares no length, is handed on unchanged.
 */
@ApplicationScoped
public class TransportCapGuard {

    /** Before every handler the framework registers, so the cap is answered here and only here. */
    private static final int FIRST = -1_000_000_000;

    /** After every handler that can refuse with 413, so their failures keep one JSON shape. */
    private static final int LAST = 1_000_000_000;

    void install(@Observes Router router) {
        router.route().order(FIRST).handler(TransportCapGuard::refuseOversized);
        router.route().order(LAST).failureHandler(TransportCapGuard::reshapeFrameworkRefusal);
    }

    /** A declared body over the transport cap is refused before any byte is consumed. */
    private static void refuseOversized(RoutingContext context) {
        String declared = context.request().headers().get("Content-Length");
        if (declared != null && declaredLength(declared) > RequestValidationFilter.MAX_BODY_BYTES) {
            answer(context);
            return;
        }
        context.next();
    }

    /** The declared body size in bytes, or -1 when the declaration is not a plain number. */
    private static long declaredLength(String header) {
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException unreadable) {
            return -1L;
        }
    }

    private static void answer(RoutingContext context) {
        context.response().setStatusCode(413)
                .putHeader("content-type", "application/json")
                .end("{\"error\":\"request_too_large\"}");
    }

    /**
     * A 413 the HTTP layer raised itself (a streamed body past the cap) is reshaped to the same
     * JSON before the framework's default error body replaces it. A response that is already
     * written, including every refusal the RESTEasy filters and resources answer themselves, is
     * left alone.
     */
    private static void reshapeFrameworkRefusal(RoutingContext context) {
        if (context.statusCode() == 413 && !context.response().ended()
                && !context.response().headWritten()) {
            answer(context);
            return;
        }
        context.next();
    }
}
