package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Feature 001's bounded environment: absent keys mean the documented defaults, in-range values are
 * read exactly, and every other value refuses the start naming its key and the allowed range —
 * never the value that caused the refusal.
 */
class ForumBoundsTest {

    @Test
    void anAbsentKeyMeansTheDocumentedDefault() {
        ForumBounds absent = new ForumBounds(
                Optional.empty(), Optional.empty(), Optional.empty(), true);
        assertEquals(16384, absent.requestMaxBytes());
        assertEquals(4000, absent.textMaxCodepoints());
        assertEquals(32768, absent.renderedMaxBytes());
    }

    @Test
    void boundariesAreInclusiveAtBothEnds() {
        ForumBounds atTheEdges = new ForumBounds(
                Optional.of("1"), Optional.of("4000"), Optional.of("32768"), true);
        atTheEdges.selfCheck();
        assertEquals(1, atTheEdges.requestMaxBytes());
        assertEquals(4000, atTheEdges.textMaxCodepoints());
        assertEquals(32768, atTheEdges.renderedMaxBytes());
    }

    @Test
    void surroundingWhitespaceIsNotAWrongValue() {
        ForumBounds padded = new ForumBounds(
                Optional.of(" 16384 "), Optional.of(" 4000 "), Optional.of(" 32768 "), true);
        padded.selfCheck();
        assertEquals(16384, padded.requestMaxBytes());
        assertEquals(4000, padded.textMaxCodepoints());
        assertEquals(32768, padded.renderedMaxBytes());
    }

    @Test
    void aLoweredRequestCapIsAcceptedAboveOne() {
        ForumBounds lowered = new ForumBounds(
                Optional.of("1024"), Optional.empty(), Optional.empty(), true);
        assertEquals(1024, lowered.requestMaxBytes());
    }

    @Test
    void aboveTheRangeRefusesNamingOnlyItsOwnKey() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> new ForumBounds(
                        Optional.of("16385"), Optional.empty(), Optional.empty(), true).selfCheck());
        assertTrue(refusal.getMessage().startsWith("bound_invalid"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("AGORA_REQUEST_MAX_BYTES"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("between 1 and 16384"), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("16385"), "the value must not be echoed");
    }

    @Test
    void belowTheDefaultValueButInsideTheRangeIsFine() {
        ForumBounds lowered = new ForumBounds(
                Optional.of("2"), Optional.empty(), Optional.empty(), true);
        assertEquals(2, lowered.requestMaxBytes());
    }

    @Test
    void aNegativeOrNonNumberRefusesWithoutEchoingTheValue() {
        for (String bad : new String[] {"-1", "16384.5", "abc", "16 384"}) {
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> new ForumBounds(
                            Optional.of(bad), Optional.empty(), Optional.empty(), true).selfCheck(),
                    bad);
            assertTrue(refusal.getMessage().startsWith("bound_invalid"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains("AGORA_REQUEST_MAX_BYTES"), refusal.getMessage());
            assertFalse(refusal.getMessage().contains(bad), "the value must not be echoed");
        }
    }

    @Test
    void theTextCapCountsCodepointsUpToItsCeiling() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> new ForumBounds(
                        Optional.empty(), Optional.of("4001"), Optional.empty(), true).selfCheck());
        assertTrue(refusal.getMessage().contains("AGORA_TEXT_MAX_CODEPOINTS"), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("4001"), "the value must not be echoed");
    }

    @Test
    void theRenderedCapRefusesAboveItsCeiling() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> new ForumBounds(
                        Optional.empty(), Optional.empty(), Optional.of("32769"), true).selfCheck());
        assertTrue(refusal.getMessage().contains("AGORA_RENDERED_MAX_BYTES"), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("32769"), "the value must not be echoed");
    }

    @Test
    void anUnparsableRenderedValueRefusesWithoutEchoingIt() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> new ForumBounds(
                        Optional.empty(), Optional.empty(), Optional.of("32768x"), true).selfCheck());
        assertTrue(refusal.getMessage().startsWith("bound_invalid"), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("32768x"), "the value must not be echoed");
    }
}
