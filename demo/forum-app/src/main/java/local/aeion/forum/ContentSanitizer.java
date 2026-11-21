// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Feature 007: user text becomes normalized plain Markdown and a small, attribute-free HTML subset.
 *
 * Storage keeps the normalized Markdown; the HTML is derived here on every write and every read, so
 * no transport byte reaches a page. Every character of user text is escaped before it is emitted, so
 * raw HTML, event handlers and javascript:/data: URLs stay inert text. Both caps refuse and never
 * truncate: a stored row that a cap refuses (only a row written before feature 007 can be one) is
 * refused on read too, never served in part and never replaced by a fallback. Normalization is
 * idempotent: a stored body submitted again renders the same bytes.
 */
public final class ContentSanitizer {

    /** The cap on the raw text and on the normalized text, in Unicode codepoints. */
    public static final int MAX_CODEPOINTS = 4000;

    /** The cap on the rendered HTML, in UTF-8 bytes. */
    public static final int MAX_HTML_BYTES = 32768;

    private static final String CODE_FENCE = "```";
    private static final Pattern HORIZONTAL = Pattern.compile("[\\t\\p{Zs}]+");

    private ContentSanitizer() {
    }

    /** The normalized Markdown that is stored, and the derived HTML that is shown. */
    public record Sanitized(String markdown, String html) {
    }

    /** Text over a cap. The API answers 413 content_too_large and stores nothing. */
    public static class ContentTooLargeException extends RuntimeException {

        public ContentTooLargeException() {
            super("content_too_large");
        }
    }

    /**
     * Normalizes and renders user text; throws ContentTooLargeException over either cap. Writes and
     * reads both go through here, so a stored row is judged by the same caps as a new one.
     */
    public static Sanitized sanitize(String raw) {
        String source = raw == null ? "" : raw;
        if (source.codePointCount(0, source.length()) > MAX_CODEPOINTS) {
            throw new ContentTooLargeException();
        }
        String markdown = normalize(source);
        if (markdown.codePointCount(0, markdown.length()) > MAX_CODEPOINTS) {
            throw new ContentTooLargeException();
        }
        String html = render(markdown);
        if (html.getBytes(StandardCharsets.UTF_8).length > MAX_HTML_BYTES) {
            throw new ContentTooLargeException();
        }
        return new Sanitized(markdown, html);
    }

    private static String normalize(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        // NFC runs after the strip, so a removed zero-width mark cannot leave an uncomposed pair behind.
        return normalizeLines(Normalizer.normalize(stripInvisible(text), Normalizer.Form.NFC));
    }

    /** Drops control, format and unpaired-surrogate codepoints; LF and TAB stay. */
    private static String stripInvisible(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\t' || !isInvisible(cp)) {
                kept.appendCodePoint(cp);
            }
        });
        return kept.toString();
    }

    private static boolean isInvisible(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE;
    }

    /**
     * Line structure outside code: horizontal whitespace collapsed, line edges stripped, blank runs
     * reduced to one blank line and outer blank lines dropped. Fenced code keeps its lines verbatim.
     */
    private static String normalizeLines(String text) {
        List<String> out = new ArrayList<>();
        boolean fenced = false;
        boolean blankBefore = false;
        for (String line : text.split("\n", -1)) {
            if (fenced) {
                if (edges(line).equals(CODE_FENCE)) {
                    fenced = false;
                    out.add(CODE_FENCE);
                } else {
                    out.add(line);
                }
                blankBefore = false;
                continue;
            }
            String clean = edges(HORIZONTAL.matcher(line).replaceAll(" "));
            if (clean.isEmpty()) {
                if (!blankBefore && !out.isEmpty()) {
                    out.add("");
                }
                blankBefore = true;
                continue;
            }
            out.add(clean);
            blankBefore = false;
            fenced = clean.startsWith(CODE_FENCE);
        }
        while (!out.isEmpty() && edges(out.get(out.size() - 1)).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return String.join("\n", out);
    }

    /** The line without horizontal whitespace (spaces, tabs, Unicode space separators) at its ends. */
    private static String edges(String line) {
        int start = 0;
        int end = line.length();
        while (start < end && isHorizontal(line.charAt(start))) {
            start++;
        }
        while (end > start && isHorizontal(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(start, end);
    }

    private static boolean isHorizontal(char c) {
        return c == '\t' || c == ' ' || Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    /** Blocks in document order, one LF between them and none after the last. */
    private static String render(String markdown) {
        if (markdown.isEmpty()) {
            return "";
        }
        String[] lines = markdown.split("\n", -1);
        List<String> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.isEmpty()) {
                i++;
            } else if (line.startsWith(CODE_FENCE)) {
                i++;
                List<String> code = new ArrayList<>();
                while (i < lines.length && !lines[i].equals(CODE_FENCE)) {
                    code.add(lines[i]);
                    i++;
                }
                i++;
                blocks.add("<pre><code>" + escape(String.join("\n", code)) + "\n</code></pre>");
            } else if (line.startsWith(">")) {
                List<String> quoted = new ArrayList<>();
                while (i < lines.length && lines[i].startsWith(">")) {
                    String rest = lines[i].substring(1);
                    quoted.add(rest.startsWith(" ") ? rest.substring(1) : rest);
                    i++;
                }
                blocks.add("<blockquote><p>" + inline(joinNonEmpty(quoted)) + "</p></blockquote>");
            } else if (line.startsWith("- ")) {
                StringBuilder items = new StringBuilder("<ul>");
                while (i < lines.length && lines[i].startsWith("- ")) {
                    items.append("<li>").append(inline(lines[i].substring(2))).append("</li>");
                    i++;
                }
                blocks.add(items.append("</ul>").toString());
            } else if (orderedMarker(line) > 0) {
                StringBuilder items = new StringBuilder("<ol>");
                while (i < lines.length && orderedMarker(lines[i]) > 0) {
                    items.append("<li>").append(inline(lines[i].substring(orderedMarker(lines[i]))))
                            .append("</li>");
                    i++;
                }
                blocks.add(items.append("</ol>").toString());
            } else {
                List<String> paragraph = new ArrayList<>();
                while (i < lines.length && !lines[i].isEmpty() && !startsBlock(lines[i])) {
                    paragraph.add(lines[i]);
                    i++;
                }
                blocks.add("<p>" + inline(String.join(" ", paragraph)) + "</p>");
            }
        }
        return String.join("\n", blocks);
    }

    private static boolean startsBlock(String line) {
        return line.startsWith(CODE_FENCE) || line.startsWith(">") || line.startsWith("- ")
                || orderedMarker(line) > 0;
    }

    /** The length of an ordered-list marker such as "12. ", or 0 when the line has none. */
    private static int orderedMarker(String line) {
        int digits = 0;
        while (digits < line.length() && line.charAt(digits) >= '0' && line.charAt(digits) <= '9') {
            digits++;
        }
        return digits > 0 && line.startsWith(". ", digits) ? digits + 2 : 0;
    }

    private static String joinNonEmpty(List<String> parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                kept.add(part);
            }
        }
        return String.join(" ", kept);
    }

    /** Escaped text with **strong** and *em* spans; every other character is text. */
    private static String inline(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int strongEnd = closeStrong(text, i);
            if (strongEnd > 0) {
                out.append("<strong>").append(inline(text.substring(i + 2, strongEnd))).append("</strong>");
                i = strongEnd + 2;
                continue;
            }
            int emEnd = closeEm(text, i);
            if (emEnd > 0) {
                out.append("<em>").append(inline(text.substring(i + 1, emEnd))).append("</em>");
                i = emEnd + 1;
                continue;
            }
            out.append(escapeChar(text.charAt(i)));
            i++;
        }
        return out.toString();
    }

    /** The index of the closing "**" of a strong span opened at start, or -1. */
    private static int closeStrong(String text, int start) {
        if (!text.startsWith("**", start) || start + 2 >= text.length() || !isContent(text.charAt(start + 2))) {
            return -1;
        }
        for (int j = start + 3; j + 1 < text.length(); j++) {
            if (text.charAt(j) == '*' && text.charAt(j + 1) == '*' && isContent(text.charAt(j - 1))
                    && !starAt(text, j + 2)) {
                return j;
            }
        }
        return -1;
    }

    /** The index of the closing "*" of an em span opened at start, or -1. */
    private static int closeEm(String text, int start) {
        if (text.charAt(start) != '*' || start + 1 >= text.length() || !isContent(text.charAt(start + 1))) {
            return -1;
        }
        for (int j = start + 2; j < text.length(); j++) {
            if (text.charAt(j) == '*' && isContent(text.charAt(j - 1)) && !starAt(text, j + 1)) {
                return j;
            }
        }
        return -1;
    }

    /** A character that may touch an emphasis delimiter: not a space and not a star. */
    private static boolean isContent(char c) {
        return c != ' ' && c != '*';
    }

    private static boolean starAt(String text, int index) {
        return index < text.length() && text.charAt(index) == '*';
    }

    /** Every HTML metacharacter escaped, quotes included, so no attribute can open. */
    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            out.append(escapeChar(text.charAt(i)));
        }
        return out.toString();
    }

    private static String escapeChar(char c) {
        return switch (c) {
            case '&' -> "&amp;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            case '"' -> "&quot;";
            case '\'' -> "&#39;";
            default -> String.valueOf(c);
        };
    }
}
