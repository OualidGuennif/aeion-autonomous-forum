// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Feature 007 in isolation: normalization, the Markdown subset, escaping and both caps, with no
 * database and no HTTP. The API-level cases live in ThreadsResourceTest and ThreadServiceTest.
 *
 * Every expected string is written out by hand from the feature contract, never copied from the
 * renderer, so a regression in the normalizer or the renderer shows up here.
 */
class ContentSanitizerTest {

    /** U+1F600: one supplementary codepoint, two UTF-16 units and four UTF-8 bytes. */
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private static String markdown(String raw) {
        return ContentSanitizer.sanitize(raw).markdown();
    }

    private static String html(String raw) {
        return ContentSanitizer.sanitize(raw).html();
    }

    @Test
    void nullAndInvisibleOnlyInputNormalizeToNothing() {
        assertEquals("", markdown(null));
        assertEquals("", html(null));
        assertEquals("", markdown(" \u200B \r\n\t\r \n "));
        assertEquals("", html(" \u200B \r\n\t\r \n "));
    }

    @Test
    void hostileMarkupRendersAsInertEscapedText() {
        String[] payloads = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(2)>",
            "<div onclick=alert(3)>x</div>",
            "<a href=\"javascript:alert(4)\">x</a>",
            "<a href=\"data:text/html,<script>alert(5)</script>\">x</a>",
            "<svg onload=alert(6)>",
            "<scr\u0000ipt>alert(7)</scr\u0000ipt>",
            "<<script>alert(8);//<</script>",
        };
        for (String payload : payloads) {
            String rendered = html(payload);
            assertTrue(rendered.startsWith("<p>") && rendered.endsWith("</p>"), payload);
            String inner = rendered.substring(3, rendered.length() - 4);
            assertFalse(inner.contains("<"), payload + " -> " + rendered);
            assertFalse(inner.contains("\""), payload + " -> " + rendered);
        }
        assertEquals("<p>&lt;script&gt;alert(7)&lt;/script&gt;</p>",
                html("<scr\u0000ipt>alert(7)</scr\u0000ipt>"));
        assertEquals("<p>&lt;a href=&quot;javascript:alert(4)&quot;&gt;x&lt;/a&gt;</p>",
                html("<a href=\"javascript:alert(4)\">x</a>"));
    }

    @Test
    void theMarkdownSubsetRendersItsElementsAndNothingElse() {
        assertEquals("<p><strong>bold</strong> and <em>em</em></p>", html("**bold** and *em*"));
        assertEquals("<ul><li>one</li><li>two</li></ul>", html("- one\n- two"));
        assertEquals("<ol><li>one</li><li>two</li></ol>", html("1. one\n2. two"));
        assertEquals("<blockquote><p>quoted line</p></blockquote>", html("> quoted line"));
        assertEquals("<p>first line second line</p>", html("first line\nsecond line"));
        assertEquals("<p>one</p>\n<p>second</p>", html("one\n\n\n\nsecond"));
        assertEquals("<p>Intro text</p>\n<blockquote><p>quote</p></blockquote>\n<ul><li>a</li><li>b</li></ul>",
                html("Intro text\n\n> quote\n\n- a\n- b"));
        assertEquals("<p>a &lt; b &amp;&amp; c</p>", html("a < b && c"));
        assertEquals("<p>say &quot;hi&quot;</p>", html("say \"hi\""));
    }

    @Test
    void aFencedBlockKeepsItsLinesLiteralAndIgnoresTheLabel() {
        assertEquals("```java\n  <x>  \n```", markdown("```java\n  <x>  \n```"));
        assertEquals("<pre><code>  &lt;x&gt;  \n</code></pre>", html("```java\n  <x>  \n```"));
        assertEquals("<pre><code>plain &lt;b&gt;code&lt;/b&gt;\n</code></pre>",
                html("```\nplain <b>code</b>\n```"));
        assertEquals("<pre><code>**not bold** *not em*\n</code></pre>",
                html("```\n**not bold** *not em*\n```"));
    }

    @Test
    void normalizationComposesStripsAndCollapsesButKeepsTheHyphen() {
        assertEquals("caf\u00e9", markdown("cafe\u0301"));
        assertEquals("abc", markdown("a\u200Bb\u200Dc"));
        assertEquals("xyzwv", markdown("x\uFEFFy\u200Ez\u200Fw\u2060v"));
        assertEquals("abcdefgh", markdown("a\u0000b\u0007c\u000Bd\u001Fe\u007Ff\u009Fg\u0085h"));
        assertEquals("a\nb\nc", markdown("a\r\nb\rc"));
        assertEquals("a b", markdown("a \t  b"));
        assertEquals("well-known co-op", markdown("well-known co-op"));
        assertEquals("start and end", markdown("\n\n  start and end  \n\n"));
    }

    @Test
    void fourThousandSupplementaryCodepointsAreAcceptedWholeAndTheNextOneIsRefused() {
        String fourThousand = EMOJI.repeat(4000);
        ContentSanitizer.Sanitized accepted = ContentSanitizer.sanitize(fourThousand);
        assertEquals(fourThousand, accepted.markdown());
        assertEquals("<p>" + fourThousand + "</p>", accepted.html());
        ContentSanitizer.ContentTooLargeException refusal = assertThrows(
                ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize(EMOJI.repeat(4001)));
        assertEquals("content_too_large", refusal.getMessage());
    }

    @Test
    void theRawAndNormalizedTextAreCappedInCodepoints() {
        assertEquals("a".repeat(4000), markdown("a".repeat(4000)));
        assertThrows(ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize("a".repeat(4001)));
        // Invisible characters count against the raw cap even though they never reach the stored text.
        assertThrows(ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize("\u200B".repeat(4001)));
        // NFC turns each U+0344 into two codepoints, so 2001 of them normalize past the cap.
        assertThrows(ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize("\u0344".repeat(2001)));
        // "e" plus combining acute, repeated 4001 times, is 8002 raw codepoints; NFC would store
        // 4001 composed codepoints. Both counts cross a cap, so the text is refused, never cut.
        assertThrows(ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize("e\u0301".repeat(4001)));
        // The same text already composed is exactly 4001 codepoints: one over the raw cap.
        assertThrows(ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize("\u00e9".repeat(4001)));
    }

    @Test
    void theRenderedHtmlIsCappedInUtf8BytesWithoutTruncation() {
        String quotes = "\"".repeat(4000);
        String rendered = html(quotes);
        assertEquals("<p>" + "&quot;".repeat(4000) + "</p>", rendered);
        assertEquals(24007, rendered.getBytes(StandardCharsets.UTF_8).length);
        // Each ">\"" line is its own 38-byte blockquote, so 1000 of them exceed 32768 bytes.
        assertThrows(ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize(">\"\n\n".repeat(1000)));
    }

    @Test
    void theRenderedHtmlCapIsExactlyThirtyTwoKiBAndTheNextBlockIsRefused() {
        // Each empty blockquote renders as 32 bytes and each separator is one byte: 993 of them make
        // exactly 32768 bytes from 2977 codepoints, and 994 make 32801 bytes.
        String atCap = String.join("\n\n", Collections.nCopies(993, ">"));
        assertEquals(32768, html(atCap).getBytes(StandardCharsets.UTF_8).length);
        assertThrows(ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize(String.join("\n\n", Collections.nCopies(994, ">"))));
    }

    @Test
    void aStoredRowOverACapIsRefusedAndNeverTruncated() {
        // Legacy text may normalize past the codepoint cap: each U+0344 becomes two codepoints here.
        assertThrows(ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize("\u0344".repeat(2001)));
        // Empty blockquotes fit the raw cap (3999 codepoints), but 1333 of them exceed 32768 bytes.
        assertThrows(ContentSanitizer.ContentTooLargeException.class,
                () -> ContentSanitizer.sanitize(">\n\n".repeat(1333)));
        // A stored row inside both caps renders exactly as the write path renders it.
        ContentSanitizer.Sanitized written = ContentSanitizer.sanitize("cafe\u0301 \u200Bx");
        assertEquals("caf\u00e9 x", written.markdown());
        assertEquals("<p>caf\u00e9 x</p>", written.html());
        assertEquals(written, ContentSanitizer.sanitize(written.markdown()));
    }

    @Test
    void normalizationAndRenderingAreIdempotent() {
        String[] inputs = {
            "  Hello \t\u200B world  \r\n\r\n\r\n**bold** and *em*",
            "```java\n  <x>  \n```",
            "- one\n-  two\n\n> quote\n> more",
            "1. first\n2. second",
            "cafe\u0301 \u200Ez",
            "<scr\u0000ipt>alert(1)</scr\u0000ipt>",
        };
        for (String input : inputs) {
            ContentSanitizer.Sanitized once = ContentSanitizer.sanitize(input);
            ContentSanitizer.Sanitized twice = ContentSanitizer.sanitize(once.markdown());
            assertEquals(once.markdown(), twice.markdown(), input);
            assertEquals(once.html(), twice.html(), input);
        }
    }
}
