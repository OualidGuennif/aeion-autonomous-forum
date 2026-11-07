// Spec: knowledge/components/request-validation.md
// Docs-Checked: 2026-11-07 feature 001 changes only where the <=16KiB body cap value comes from (AGORA_REQUEST_MAX_BYTES, default 16384, ceiling unchanged); no fact of knowledge/components/request-validation.md changed and the cap's configuration is demo/forum-docs/settings.md.
package local.aeion.forum;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Validate bounded bytes before authentication, JSON coercion or handler side effects. */
@Provider
@Priority(Priorities.AUTHENTICATION - 1)
public class RequestValidationFilter implements ContainerRequestFilter {
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "api/auth/register", Set.of("username", "password"),
            "api/auth/login", Set.of("username", "password"),
            "api/threads", Set.of("title", "body"));
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Inject
    ForumBounds bounds;

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        boolean hasBody = request.hasEntity();
        int cap = bounds.requestMaxBytes();
        byte[] body = hasBody ? request.getEntityStream().readNBytes(cap + 1) : new byte[0];
        if (body.length > cap) {
            request.abortWith(Response.status(413).type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(Map.of("error", "request_too_large")).build());
            return;
        }
        // Other routes and valid handlers must receive the original bounded bytes too.
        if (hasBody) request.setEntityStream(new ByteArrayInputStream(body));
        MediaType media = request.getMediaType();
        if (!"POST".equals(request.getMethod()) || media == null
                || !MediaType.APPLICATION_JSON_TYPE.isCompatible(media)) {
            return;
        }
        String path = request.getUriInfo().getPath();
        if (path.startsWith("/")) path = path.substring(1);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        Set<String> required = FIELDS.get(path);
        if (required == null) return;

        JsonNode object;
        try {
            object = JSON.readTree(body);
        } catch (IOException invalid) {
            refuse(request, List.of("body"));
            return;
        }
        if (object == null || !object.isObject()) {
            refuse(request, List.of("body"));
            return;
        }
        Set<String> invalid = new TreeSet<>();
        object.fieldNames().forEachRemaining(name -> {
            if (!required.contains(name)) invalid.add(name);
        });
        for (String name : required) {
            JsonNode value = object.get(name);
            if (value == null || !value.isTextual()) invalid.add(name);
        }
        if (!invalid.isEmpty()) refuse(request, List.copyOf(invalid));
    }

    private static void refuse(ContainerRequestContext request, List<String> fields) {
        request.abortWith(Response.status(422).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("error", "invalid_request", "fields", fields)).build());
    }
}
