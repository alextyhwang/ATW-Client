package com.atw.renderboost.cache;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NamePrepCacheTest {
    @Test void expectedFormattingUnicodeEmptyAndEveryNullPosition() {
        NamePrepCache cache = new NamePrepCache();
        String[][] cases = {
            {"\u00a7c[Red] ", "Alice", "\u00a7r", "\u00a7c[Red] Alice\u00a7r"},
            {"", "", "", ""}, {"\ud83d\ude00", "\u03a9", "\u00a7l", "\ud83d\ude00\u03a9\u00a7l"},
            {null, "Alice", "!", "nullAlice!"}, {"[", null, "]", "[null]"},
            {"", "Alice", null, "Alicenull"}, {null, null, null, "nullnullnull"},
            {"null", "null", "null", "nullnullnull"}
        };
        for (String[] row : cases) {
            assertNull(cache.lookup(row[0], row[1], row[2]));
            cache.record(row[0], row[1], row[2], row[3]);
            assertSame(row[3], cache.lookup(row[0], row[1], row[2]));
        }
        assertEquals(cases.length, cache.hits);
    }
    @Test void liveValuesAndBoundariesAreKeysNotJustConcatenatedText() {
        NamePrepCache c = new NamePrepCache(); c.record("[", "A", "]", "[A]");
        assertNull(c.lookup("<", "A", "]")); assertNull(c.lookup("[", "B", "]")); assertNull(c.lookup("[", "A", ">"));
        c.record("<", "B", ">", "<B>"); assertEquals("<B>", c.lookup(new String("<"), new String("B"), new String(">")));
        c.record("ab", "c", "", "abc"); assertNull(c.lookup("a", "bc", ""));
        c.record(null, "x", "", "nullx"); assertNull(c.lookup("null", "x", ""));
    }
    @Test void collisionsCompareAllValuesAndEvictOnlyBoundedImmutableEntries() {
        // Independent known Java String hash collision family, not the cache's private bucket algorithm.
        String[] keys = {"AaAaAa", "AaAaBB", "AaBBAa", "AaBBBB", "BBAaAa", "BBAaBB"};
        assertEquals(1, Arrays.stream(keys).map(String::hashCode).distinct().count());
        NamePrepCache c = new NamePrepCache();
        for (String key : keys) c.record("[", key, "]", "[" + key + "]");
        assertEquals(4, c.size()); assertEquals(2, c.evictions); assertNull(c.lookup("[", keys[0], "]"));
        for (int i = 2; i < keys.length; i++) assertEquals("[" + keys[i] + "]", c.lookup("[", keys[i], "]"));
        assertTrue(c.collisions > 0);
    }
    @Test void entryCharacterAndIndividualTextLimitsRemainBoundedUnderChurn() {
        NamePrepCache c = new NamePrepCache();
        for (int i = 0; i < 10000; i++) {
            String key = "user-" + i; String out = "prefix:" + key + ":suffix";
            c.record("prefix:", key, ":suffix", out);
            assertTrue(c.size() <= 512); assertTrue(c.characters() <= 262144);
            assertEquals(out, c.lookup("prefix:", key, ":suffix"));
        }
        assertTrue(c.evictions > 0);
        String large = String.join("", Collections.nCopies(2049, "x"));
        assertNull(c.lookup(large, "", "")); c.record(large, "", "", large);
        assertEquals(1, c.oversized);
        String medium = large.substring(0, 1800);
        for (int i = 0; i < 1024; i++) { c.record(medium, "n" + i, "", medium + "n" + i); assertTrue(c.characters() <= 262144); }
        c.clear(); assertEquals(0, c.size()); assertEquals(0, c.characters());
        assertNull(c.lookup("prefix:", "user-9999", ":suffix"));
    }
    @Test void storageContainsOnlyStringsAndPrimitivesAndNoPerHitKeyObjects() {
        for (java.lang.reflect.Field f : NamePrepCache.class.getDeclaredFields())
            assertTrue(f.getType().isPrimitive() || f.getType() == String[].class || f.getType() == byte[].class, f.toString());
        NamePrepCache c = new NamePrepCache(); String output = new String("[same]"); c.record("[", "same", "]", output);
        long admitted = c.admissions;
        for (int i = 0; i < 1000; i++) assertSame(output, c.lookup("[", "same", "]"));
        assertEquals(admitted, c.admissions);
    }
}
