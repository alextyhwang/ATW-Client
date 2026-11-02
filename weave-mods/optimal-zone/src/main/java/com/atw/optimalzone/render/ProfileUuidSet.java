package com.atw.optimalzone.render;

import java.util.Arrays;
import java.util.UUID;

/** Reusable exact UUID census, owned by the client's tick thread; not thread-safe. */
final class ProfileUuidSet {
    private long[] mostBits = new long[16];
    private long[] leastBits = new long[16];
    private int[] occupiedSlots = new int[16];
    // Zero means empty; other values are dense entry indices plus one. Load <= 1/2.
    private int[] slots = new int[32];
    private int size;

    int size() {
        return size;
    }

    void clear() {
        // Only touch active slots, even after a large population has left.
        for (int index = 0; index < size; index++) {
            slots[occupiedSlots[index]] = 0;
        }
        size = 0;
    }

    void add(UUID id) {
        add(id.getMostSignificantBits(), id.getLeastSignificantBits());
    }

    private void add(long most, long least) {
        int slot = findSlot(most, least);
        if (slots[slot] != 0) {
            return;
        }
        if (size == mostBits.length) {
            ensureCapacity(size + 1);
            slot = findSlot(most, least);
        }
        mostBits[size] = most;
        leastBits[size] = least;
        occupiedSlots[size] = slot;
        slots[slot] = ++size;
    }

    boolean contains(long most, long least) {
        return slots[findSlot(most, least)] != 0;
    }

    int countMissingFrom(ProfileUuidSet other) {
        int missing = 0;
        for (int index = 0; index < size; index++) {
            if (!other.contains(mostBits[index], leastBits[index])) {
                missing++;
            }
        }
        return missing;
    }

    void copyFrom(ProfileUuidSet other) {
        if (other == this) {
            return;
        }
        clear();
        ensureCapacity(other.size);
        for (int index = 0; index < other.size; index++) {
            add(other.mostBits[index], other.leastBits[index]);
        }
    }

    private int findSlot(long most, long least) {
        int mask = slots.length - 1;
        int slot = hash(most, least) & mask;
        while (slots[slot] != 0) {
            int index = slots[slot] - 1;
            if (mostBits[index] == most && leastBits[index] == least) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private static int hash(long most, long least) {
        long mixed = mix(most ^ Long.rotateLeft(mix(least), 27));
        return (int) (mixed ^ (mixed >>> 32));
    }

    private static long mix(long bits) {
        bits = (bits ^ (bits >>> 33)) * 0xff51afd7ed558ccdL;
        bits = (bits ^ (bits >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return bits ^ (bits >>> 33);
    }

    private void ensureCapacity(int capacity) {
        if (capacity <= mostBits.length) {
            return;
        }
        int newCapacity = mostBits.length;
        while (newCapacity < capacity) {
            newCapacity = Math.multiplyExact(newCapacity, 2);
        }
        int slotCapacity = Math.multiplyExact(newCapacity, 2);
        mostBits = Arrays.copyOf(mostBits, newCapacity);
        leastBits = Arrays.copyOf(leastBits, newCapacity);
        occupiedSlots = Arrays.copyOf(occupiedSlots, newCapacity);
        slots = new int[slotCapacity];
        for (int index = 0; index < size; index++) {
            int slot = findSlot(mostBits[index], leastBits[index]);
            occupiedSlots[index] = slot;
            slots[slot] = index + 1;
        }
    }
}
