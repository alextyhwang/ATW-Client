package com.atw.renderboost.cache;

import java.util.Arrays;
import java.util.Objects;

/** Four-way bounded immutable text storage. No key objects, builders, teams or components. */
public final class NamePrepCache {
    public static final int LIMIT = 512, MAX_TEXT = 2048, MAX_CHARACTERS = 262144;
    private final String[] prefixes = new String[LIMIT], names = new String[LIMIT],
            suffixes = new String[LIMIT], outputs = new String[LIMIT];
    private final byte[] next = new byte[LIMIT / 4];
    private int size, characters;
    public long hits, misses, admissions, collisions, evictions, oversized;

    private static int length(String s) { return s == null ? 4 : s.length(); }
    public boolean supported(String p, String n, String s) {
        return (long)length(p) + length(n) + length(s) <= MAX_TEXT;
    }
    private static int bucket(String p, String n, String s) {
        int hash = 31 * (31 * Objects.hashCode(p) + Objects.hashCode(n)) + Objects.hashCode(s);
        return (hash & 127) * 4;
    }
    private boolean equal(int i, String p, String n, String s) {
        return outputs[i] != null && Objects.equals(prefixes[i], p)
                && Objects.equals(names[i], n) && Objects.equals(suffixes[i], s);
    }
    public String lookup(String p, String n, String s) {
        if (!supported(p, n, s)) { oversized++; return null; }
        int start = bucket(p, n, s);
        for (int i = start; i < start + 4; i++) {
            if (equal(i, p, n, s)) { hits++; return outputs[i]; }
            if (outputs[i] != null) collisions++;
        }
        misses++; return null;
    }
    /** Only an actual original toString result is admitted; this helper never assembles text. */
    public void record(String p, String n, String s, String output) {
        if (output == null || !supported(p, n, s) || output.length() > MAX_TEXT) return;
        int start = bucket(p, n, s), slot = -1;
        for (int i = start; i < start + 4; i++) {
            if (equal(i, p, n, s)) return;
            if (outputs[i] == null && slot < 0) slot = i;
        }
        int weight = length(p) + length(n) + length(s) + output.length();
        if (slot < 0) { slot = start + next[start / 4]; next[start / 4] = (byte)((next[start / 4] + 1) & 3); remove(slot); }
        // Bound retained characters as well as entry count. Deterministic scan only on admission.
        for (int i = 0; characters + weight > MAX_CHARACTERS && i < LIMIT; i++) remove(i);
        prefixes[slot] = p; names[slot] = n; suffixes[slot] = s; outputs[slot] = output;
        characters += weight; size++; admissions++;
    }
    private void remove(int i) {
        if (outputs[i] == null) return;
        characters -= length(prefixes[i]) + length(names[i]) + length(suffixes[i]) + outputs[i].length();
        prefixes[i] = names[i] = suffixes[i] = outputs[i] = null; size--; evictions++;
    }
    public void clear() {
        Arrays.fill(prefixes, null); Arrays.fill(names, null); Arrays.fill(suffixes, null);
        Arrays.fill(outputs, null); Arrays.fill(next, (byte)0); size = characters = 0;
    }
    public int size() { return size; }
    public int characters() { return characters; }
}
