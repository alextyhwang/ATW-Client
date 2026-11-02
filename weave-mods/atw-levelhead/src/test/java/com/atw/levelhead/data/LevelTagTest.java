package com.atw.levelhead.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LevelTagTest {
    private static final UUID OWNER = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

    @Test void immutableValuesMatchTheOriginalOracleAcrossFormattingAndUnicode() {
        String[] values = {
                null, "", "Level ", "120", "NICKED", "nicked", "NiCkEd", "NICK", "ED",
                "&c&lNICKED", "\u00a7c\u00a7lNICKED", "NICKED&", "NICKED\u00a7", "&", "\u00a7",
                "&a", "&zNICKED", "&&NICKED", "\u00a7\u00a7NICKED", "NI&xCKED",
                " NICKED", "NICKED ", "\nNICKED", "\rNICKED", "\tNICKED", "\u0000NICKED",
                "&\nNICKED", "&\rNICKED", "&\r\nNICKED", "&\u0085NICKED",
                "&\u2028NICKED", "&\u2029NICKED", "&\tNICKED", "&\u0000NICKED",
                "N\u0131CKED", "N\u0130CKED", "NIC\u212aED", "\u4e2d\u6587\u272b",
                "\ud83d\ude00", "&\ud83d\ude00NICKED", "\ud83d", "\ude00", "&\ud83dNICKED"
        };
        for (String header : values) {
            for (String footer : values) {
                assertOriginalValues(new LevelTag(OWNER, header, footer), OWNER, header, footer, false);
            }
        }
        assertOriginalValues(new LevelTag(null, null, null), null, null, null, false);
    }

    @Test void splittingFormattingAcrossHeaderAndFooterPreservesCombinedClassification() {
        String[] combinedValues = {
                "&c&lNICKED", "Ni&xCkEd", "NICKED&a", "\u00a7\ud83d\ude00NICKED",
                "NICKED&", "&\nNICKED", "N\u0131CKED", "NIC\u212aED"
        };
        for (String combined : combinedValues) {
            for (int split = 0; split <= combined.length(); split++) {
                String header = combined.substring(0, split);
                String footer = combined.substring(split);
                assertOriginalValues(new LevelTag(OWNER, header, footer), OWNER, header, footer, false);
            }
        }
        assertTrue(new LevelTag(OWNER, "NICKED&", "a").isNicked());
        assertTrue(new LevelTag(OWNER, "&z", "nicked").isNicked());
        assertFalse(new LevelTag(OWNER, "NICKED", "&").isNicked());
        assertFalse(new LevelTag(OWNER, "&\n", "NICKED").isNicked());
        assertTrue(new LevelTag(OWNER, "N\u0131C", "KED").isNicked());
    }

    @Test void explicitFactoriesKeepTheirValuesAndForceNickedForAnyFooter() {
        for (UUID owner : new UUID[]{OWNER, null}) {
            assertOriginalValues(LevelTag.nicked(owner), owner, "", "&c&lNICKED", true);
            for (String footer : new String[]{null, "", "ordinary", "&c--\u272b &7| FKDR &c--",
                    "&zNICKED", "NICKED&", "&\nNICKED", "\ud83d\ude00"}) {
                assertOriginalValues(LevelTag.bedwarsNicked(owner, footer), owner, "", footer, true);
            }
        }
    }

    @Test void repeatedBaseTextReadsReuseTheCombinedString() {
        LevelTag tag = new LevelTag(OWNER, "&aLevel ", "120");
        String text = tag.getText();
        assertEquals("\u00a7aLevel 120", text);
        assertFalse(tag.isNicked());
        assertSame(text, tag.getText());
        assertFalse(tag.isNicked());
        assertSame(text, tag.getText());
    }

    @Test void baseClassificationIsDeferredPastTextReadsAndMemoizesBothResults() throws Exception {
        Field state = classificationField();
        LevelTag ordinary = new LevelTag(OWNER, "&aLevel ", "120");
        LevelTag textualNicked = new LevelTag(OWNER, "NICKED&", "a");
        byte[] results = new byte[2];
        LevelTag[] tags = {ordinary, textualNicked};
        for (int index = 0; index < tags.length; index++) {
            LevelTag tag = tags[index];
            assertEquals(0, state.getByte(tag), "construction must defer classification");
            String text = tag.getText();
            assertSame(text, tag.getText());
            assertEquals(0, state.getByte(tag), "text-only reads must not classify");
            boolean expected = originalNicked(text, false);
            assertEquals(expected, tag.isNicked());
            results[index] = state.getByte(tag);
            assertNotEquals(0, results[index], "first classification must settle the state");
            for (int read = 0; read < 32; read++) {
                assertEquals(expected, tag.isNicked());
                assertEquals(results[index], state.getByte(tag));
                assertSame(text, tag.getText());
            }
        }
        assertNotEquals(results[0], results[1], "true and false need distinct settled states");
    }

    @Test void explicitFactoriesBypassLazyClassificationState() throws Exception {
        Field state = classificationField();
        for (LevelTag tag : new LevelTag[]{LevelTag.nicked(null), LevelTag.bedwarsNicked(OWNER, null),
                LevelTag.bedwarsNicked(OWNER, "ordinary"), LevelTag.bedwarsNicked(OWNER, "&\nNICKED")}) {
            assertEquals(0, state.getByte(tag));
            for (int read = 0; read < 32; read++) {
                assertTrue(tag.isNicked());
                assertEquals(0, state.getByte(tag), "explicit flag bypasses derived classification");
            }
        }
    }

    @Test void volatileMemoizationAndConcurrentFirstReadersPreserveOracleValues() throws Exception {
        Field state = classificationField();
        // Stress alone cannot prove Java Memory Model visibility; guard the
        // publication mechanism as well as concurrent outward oracle parity.
        assertEquals(byte.class, state.getType());
        assertTrue(Modifier.isVolatile(state.getModifiers()));
        String[] headers = {"&aLevel ", "NICKED&", "&\n", "N\u0131C", "&\ud83d\ude00", null};
        String[] footers = {"120", "a", "NICKED", "KED", "NICKED", null};
        LevelTag[] tags = new LevelTag[256];
        String[] texts = new String[tags.length];
        boolean[] expected = new boolean[tags.length];
        for (int index = 0; index < tags.length; index++) {
            int fixture = index % headers.length;
            tags[index] = new LevelTag(OWNER, headers[fixture], footers[fixture]);
            texts[index] = originalNormalize(headers[fixture]) + originalNormalize(footers[fixture]);
            expected[index] = originalNicked(texts[index], false);
            assertEquals(0, state.getByte(tags[index]));
        }
        int readers = 6;
        ExecutorService workers = Executors.newFixedThreadPool(readers);
        CountDownLatch ready = new CountDownLatch(readers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> results = new ArrayList<>();
        try {
            for (int reader = 0; reader < readers; reader++) {
                results.add(workers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (int round = 0; round < 16; round++) {
                        for (int index = 0; index < tags.length; index++) {
                            assertEquals(texts[index], tags[index].getText());
                            assertEquals(expected[index], tags[index].isNicked());
                        }
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Void> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
            for (int index = 0; index < tags.length; index++) {
                assertNotEquals(0, state.getByte(tags[index]));
                assertEquals(expected[index], tags[index].isNicked());
            }
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test void subclassClassificationUsesCurrentVirtualTextExactlyOncePerCall() {
        // A nicked base value must not override the subclass's current text.
        MutableTextTag tag = new MutableTextTag("&cNICKED", "");
        assertEquals(0, tag.getTextCalls, "construction must not call subclass getters");
        String[] texts = {"ordinary", "\u00a7cNICKED", null, "", "nicked", "NICKED\u00a7",
                "&cNICKED", "\u00a7\nNICKED", "N\u0131CKED", "ordinary"};
        for (String text : texts) {
            tag.currentText = text;
            int before = tag.getTextCalls;
            assertEquals(originalNicked(text, false), tag.isNicked());
            assertEquals(before + 1, tag.getTextCalls);
        }
    }

    @Test void subclassGetterExceptionsRemainObservableAtClassificationTime() {
        IllegalStateException failure = new IllegalStateException("dynamic text failed");
        LevelTag tag = new LevelTag(OWNER, "", "NICKED") {
            @Override public String getText() {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IllegalStateException.class, tag::isNicked));
        assertSame(failure, assertThrows(IllegalStateException.class, tag::isNicked));
    }

    @Test void inheritedAndChainedSubclassGettersKeepTheirExistingSemantics() {
        LevelTag inherited = new LevelTag(OWNER, "&cNI", "CKED") {
            @Override public String getHeader() { return "different header"; }
            @Override public String getFooter() { return "different footer"; }
        };
        assertEquals("\u00a7cNICKED", inherited.getText(), "base text uses fields, not virtual getters");
        assertTrue(inherited.isNicked());

        class SuffixTag extends LevelTag {
            String suffix = "";
            SuffixTag() { super(OWNER, "&cNI", "CKED"); }
            @Override public String getText() { return super.getText() + suffix; }
        }
        SuffixTag chained = new SuffixTag();
        assertTrue(chained.isNicked());
        chained.suffix = "!";
        assertFalse(chained.isNicked());
        chained.suffix = "\u00a7a";
        assertTrue(chained.isNicked());

        LevelTag overridden = new LevelTag(OWNER, "", "NICKED") {
            @Override public boolean isNicked() { return false; }
        };
        assertFalse(overridden.isNicked(), "isNicked remains overridable");
    }

    @Test void bedwarsConsumersStillParseNormalTagsAndRecognizeExplicitEstimates() {
        BedwarsPlayerStats normal = new BedwarsPlayerStats(OWNER, "fixture", 300, 10, 2);
        BedwarsPlayerStats parsed = BedwarsPlayerStats.fromTag(OWNER, "fixture", normal.toLevelTag());
        assertNotNull(parsed);
        assertFalse(parsed.isNickedEstimate());
        assertEquals(300, parsed.getStar());
        assertEquals(5.0D, parsed.getFkdr());

        LevelTag explicit = BedwarsPlayerStats.nicked(OWNER, "fixture").toLevelTag();
        assertFalse(originalNicked(explicit.getText(), false), "estimate text is not the word NICKED");
        assertTrue(explicit.isNicked());
        assertTrue(BedwarsPlayerStats.fromTag(OWNER, "fixture", explicit).isNickedEstimate());
    }

    // Independent oracle: the exact pre-change normalization, concatenation,
    // Java regex, and equalsIgnoreCase operations (no production helper calls).
    private static void assertOriginalValues(LevelTag tag, UUID owner, String header, String footer,
                                             boolean explicitlyNicked) {
        String normalizedHeader = originalNormalize(header);
        String normalizedFooter = originalNormalize(footer);
        String text = normalizedHeader + normalizedFooter;
        assertEquals(owner, tag.getOwner());
        assertEquals(normalizedHeader, tag.getHeader());
        assertEquals(normalizedFooter, tag.getFooter());
        for (int read = 0; read < 2; read++) {
            assertEquals(text, tag.getText());
            assertEquals(originalNicked(text, explicitlyNicked), tag.isNicked());
        }
    }

    private static String originalNormalize(String value) {
        return value == null ? "" : value.replace('&', '\u00a7');
    }

    private static boolean originalNicked(String text, boolean explicitlyNicked) {
        return explicitlyNicked || "NICKED".equalsIgnoreCase(text == null ? "" : text.replaceAll("\u00a7.", ""));
    }

    private static Field classificationField() throws NoSuchFieldException {
        Field field = LevelTag.class.getDeclaredField("nickedClassification");
        field.setAccessible(true);
        return field;
    }

    private static class MutableTextTag extends LevelTag {
        String currentText;
        int getTextCalls;

        MutableTextTag(String header, String footer) {
            super(OWNER, header, footer);
        }

        @Override public String getText() {
            getTextCalls++;
            return currentText;
        }
    }
}
