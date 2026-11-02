package com.atw.optimalzone.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** HashSet is the independent exact-UUID oracle; no Minecraft or GL initialization. */
class ProfileUuidSetTest {
    @Test
    void emptySetsCanBeComparedClearedAndCopied() {
        ProfileUuidSet first = new ProfileUuidSet();
        ProfileUuidSet second = new ProfileUuidSet();
        assertEquals(0, first.size());
        assertFalse(first.contains(0, 0));
        assertEquals(0, first.countMissingFrom(second));
        first.clear();
        second.copyFrom(first);
        first.copyFrom(first);
        assertEquals(0, second.size());
    }

    @Test
    void duplicatesAndZeroHalvesStillUseAll128Bits() {
        ProfileUuidSet actual = new ProfileUuidSet();
        Set<UUID> expected = new HashSet<>();
        long[] halves = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long most : halves) {
            for (long least : halves) {
                UUID id = new UUID(most, least);
                actual.add(id);
                actual.add(new UUID(most, least));
                expected.add(id);
                assertEquals(expected.size(), actual.size());
            }
        }
        assertMatches(expected, actual);
        assertFalse(actual.contains(0, 2));
        assertFalse(actual.contains(2, 0));
    }

    @Test
    void collisionChainsWrapGrowAndRemainExact() throws Exception {
        // Find actual bucket collisions without duplicating the production hash.
        Method hash = ProfileUuidSet.class.getDeclaredMethod("hash", long.class, long.class);
        hash.setAccessible(true);
        List<UUID> collisions = new ArrayList<>();
        for (long candidate = 0; collisions.size() < 70; candidate++) {
            if (((int) hash.invoke(null, candidate, 0L) & 127) == 127) {
                collisions.add(new UUID(candidate, 0));
            }
        }
        ProfileUuidSet actual = new ProfileUuidSet();
        Set<UUID> expected = new HashSet<>();
        for (UUID id : collisions) {
            actual.add(id);
            expected.add(id);
            assertMatches(expected, actual);
            assertFalse(actual.contains(Long.MIN_VALUE, Long.MAX_VALUE));
        }
        for (UUID id : collisions) {
            actual.add(new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits()));
        }
        assertMatches(expected, actual);
        ProfileUuidSet copy = new ProfileUuidSet();
        copy.copyFrom(actual);
        assertMatches(expected, copy);
        assertEquals(0, actual.countMissingFrom(copy));
        actual.clear();
        for (int index = collisions.size() - 1; index >= 0; index--) {
            actual.add(collisions.get(index));
        }
        assertMatches(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 15, 16, 17, 31, 32, 33, 64, 128, 257, 1024, 4096})
    void growthAndTinyPopulationsMatchOracle(int population) {
        ProfileUuidSet actual = new ProfileUuidSet();
        Set<UUID> expected = new HashSet<>();
        for (int index = 0; index < population; index++) {
            // Equal halves and swapped halves must not imply equal UUIDs.
            UUID id = new UUID(index, index);
            actual.add(id);
            expected.add(id);
            if ((index & 15) == 0) {
                assertMatches(expected, actual);
            }
        }
        assertMatches(expected, actual);
        assertFalse(actual.contains(population, population));
        ProfileUuidSet empty = new ProfileUuidSet();
        assertEquals(population, actual.countMissingFrom(empty));
        assertEquals(0, empty.countMissingFrom(actual));
    }

    @Test
    void clearAndSmallRefillsReuseHighWaterStorageWithoutStaleMembership() throws Exception {
        ProfileUuidSet actual = new ProfileUuidSet();
        ProfileUuidSet copy = new ProfileUuidSet();
        for (int index = 0; index < 2048; index++) {
            actual.add(new UUID(index, -index));
        }
        copy.copyFrom(actual);
        Object[] actualStorage = storage(actual);
        Object[] copyStorage = storage(copy);
        for (int tick = 0; tick < 100; tick++) {
            actual.clear();
            actual.clear();
            assertEquals(0, actual.size());
            assertFalse(actual.contains(2047, -2047));
            Set<UUID> expected = new HashSet<>();
            for (int index = 0; index < tick % 4; index++) {
                UUID id = new UUID(tick, index);
                actual.add(id);
                expected.add(id);
            }
            copy.copyFrom(actual);
            assertMatches(expected, actual);
            assertMatches(expected, copy);
            assertSameStorage(actualStorage, actual);
            assertSameStorage(copyStorage, copy);
        }
    }

    @Test
    void copyReplacesContentsAcrossDifferentCapacitiesAndIsIndependent() {
        ProfileUuidSet source = new ProfileUuidSet();
        ProfileUuidSet destination = new ProfileUuidSet();
        Set<UUID> expected = new HashSet<>();
        destination.add(new UUID(-1, -1));
        for (int index = 0; index < 300; index++) {
            UUID id = new UUID(0, index);
            source.add(id);
            expected.add(id);
        }
        destination.copyFrom(source); // Grow destination.
        assertMatches(expected, destination);
        source.clear();
        assertMatches(expected, destination);
        source.add(new UUID(1, 0));
        destination.copyFrom(source); // Reuse a much larger destination.
        assertMatches(Set.of(new UUID(1, 0)), destination);
        destination.add(new UUID(2, 0));
        assertMatches(Set.of(new UUID(1, 0)), source);
        source.copyFrom(destination); // Copy back in the opposite direction.
        assertMatches(Set.of(new UUID(1, 0), new UUID(2, 0)), source);
        source.clear();
        destination.copyFrom(source);
        assertMatches(Set.of(), destination);
    }

    @Test
    void selfCopyPreservesMembershipIncludingAfterGrowth() {
        ProfileUuidSet actual = new ProfileUuidSet();
        Set<UUID> expected = new HashSet<>();
        for (int index = 0; index < 128; index++) {
            UUID id = new UUID(index, Long.MAX_VALUE - index);
            expected.add(id);
            actual.add(id);
            actual.copyFrom(actual);
            assertMatches(expected, actual);
        }
        assertEquals(0, actual.countMissingFrom(actual));
    }

    @Test
    void directionalDifferenceCountsMembersRatherThanSizeDifference() {
        ProfileUuidSet left = new ProfileUuidSet();
        ProfileUuidSet right = new ProfileUuidSet();
        Set<UUID> leftOracle = Set.of(new UUID(0, 0), new UUID(0, 1), new UUID(1, 0));
        Set<UUID> rightOracle = Set.of(new UUID(0, 0), new UUID(1, 1));
        leftOracle.forEach(left::add);
        rightOracle.forEach(right::add);
        assertEquals(2, left.countMissingFrom(right));
        assertEquals(1, right.countMissingFrom(left));
        assertDifferences(leftOracle, left, rightOracle, right);
        right.copyFrom(left);
        assertEquals(0, left.countMissingFrom(right));
        assertEquals(0, right.countMissingFrom(left));
    }

    @Test
    void randomizedOperationsMatchOracle() {
        Random random = new Random(0x415457);
        ProfileUuidSet[] actual = {new ProfileUuidSet(), new ProfileUuidSet()};
        List<Set<UUID>> expected = List.of(new HashSet<>(), new HashSet<>());
        List<UUID> pool = randomPopulation(random, 1024);
        for (int operation = 0; operation < 10000; operation++) {
            int target = random.nextInt(2);
            int source = random.nextInt(2);
            switch (random.nextInt(20)) {
                case 0 -> {
                    actual[target].clear();
                    expected.get(target).clear();
                }
                case 1 -> {
                    actual[target].copyFrom(actual[source]);
                    if (target != source) {
                        expected.get(target).clear();
                        expected.get(target).addAll(expected.get(source));
                    }
                }
                default -> {
                    UUID id = pool.get(random.nextInt(pool.size()));
                    actual[target].add(id);
                    expected.get(target).add(id);
                }
            }
            assertMatches(expected.get(target), actual[target]);
            UUID probe = pool.get(random.nextInt(pool.size()));
            assertEquals(expected.get(target).contains(probe),
                    actual[target].contains(probe.getMostSignificantBits(), probe.getLeastSignificantBits()));
            assertDifferences(expected.get(0), actual[0], expected.get(1), actual[1]);
        }
    }

    @Test
    void randomizedTickCensusAndMarkerChurnMatchOracleTotals() {
        Random random = new Random(0x18914117);
        List<UUID> pool = randomPopulation(random, 512);
        ProfileUuidSet previousLoaded = new ProfileUuidSet();
        ProfileUuidSet currentLoaded = new ProfileUuidSet();
        ProfileUuidSet previousMarkers = new ProfileUuidSet();
        ProfileUuidSet currentMarkers = new ProfileUuidSet();
        Set<UUID> previousLoadedOracle = new HashSet<>();
        Set<UUID> previousMarkerOracle = new HashSet<>();
        long[] actualTotals = new long[4];
        long[] oracleTotals = new long[4];
        for (int tick = 0; tick < 300; tick++) {
            currentLoaded.clear();
            currentMarkers.clear();
            Set<UUID> loaded = new HashSet<>();
            Set<UUID> markers = new HashSet<>();
            boolean enabled = tick % 13 >= 3;
            for (UUID id : pool) {
                if (random.nextBoolean()) {
                    currentLoaded.add(id);
                    currentLoaded.add(id);
                    loaded.add(id);
                    if (enabled && random.nextInt(4) == 0) {
                        currentMarkers.add(id);
                        currentMarkers.add(id);
                        markers.add(id);
                    }
                }
            }
            // Simulate world/reset seeding: existing members are not new arrivals.
            if (tick % 71 == 0) {
                previousLoaded.copyFrom(currentLoaded);
                previousMarkers.copyFrom(currentMarkers);
                previousLoadedOracle = new HashSet<>(loaded);
                previousMarkerOracle = new HashSet<>(markers);
            }
            actualTotals[0] += currentLoaded.countMissingFrom(previousLoaded);
            actualTotals[1] += previousLoaded.countMissingFrom(currentLoaded);
            oracleTotals[0] += missing(loaded, previousLoadedOracle);
            oracleTotals[1] += missing(previousLoadedOracle, loaded);
            previousLoaded.copyFrom(currentLoaded);
            previousLoadedOracle = loaded;
            if (enabled) {
                actualTotals[2] += currentMarkers.countMissingFrom(previousMarkers);
                actualTotals[3] += previousMarkers.countMissingFrom(currentMarkers);
                oracleTotals[2] += missing(markers, previousMarkerOracle);
                oracleTotals[3] += missing(previousMarkerOracle, markers);
                previousMarkers.copyFrom(currentMarkers);
                previousMarkerOracle = markers;
            } else {
                previousMarkers.clear();
                previousMarkerOracle.clear();
            }
            assertArrayEquals(oracleTotals, actualTotals, "tick " + tick);
            assertMatches(loaded, previousLoaded);
            assertMatches(previousMarkerOracle, previousMarkers);
        }
    }

    private static List<UUID> randomPopulation(Random random, int count) {
        List<UUID> pool = new ArrayList<>();
        pool.add(new UUID(0, 0));
        pool.add(new UUID(0, random.nextLong()));
        pool.add(new UUID(random.nextLong(), 0));
        while (pool.size() < count) {
            pool.add(new UUID(random.nextLong(), random.nextLong()));
        }
        return pool;
    }

    private static void assertMatches(Set<UUID> expected, ProfileUuidSet actual) {
        assertEquals(expected.size(), actual.size());
        for (UUID id : expected) {
            assertTrue(actual.contains(id.getMostSignificantBits(), id.getLeastSignificantBits()), id::toString);
        }
    }

    private static void assertDifferences(Set<UUID> leftOracle, ProfileUuidSet left,
                                          Set<UUID> rightOracle, ProfileUuidSet right) {
        assertEquals(missing(leftOracle, rightOracle), left.countMissingFrom(right));
        assertEquals(missing(rightOracle, leftOracle), right.countMissingFrom(left));
    }

    private static int missing(Set<UUID> left, Set<UUID> right) {
        Set<UUID> difference = new HashSet<>(left);
        difference.removeAll(right);
        return difference.size();
    }

    private static Object[] storage(ProfileUuidSet set) throws Exception {
        String[] fields = {"mostBits", "leastBits", "occupiedSlots", "slots"};
        Object[] arrays = new Object[fields.length];
        for (int index = 0; index < fields.length; index++) {
            Field field = ProfileUuidSet.class.getDeclaredField(fields[index]);
            field.setAccessible(true);
            arrays[index] = field.get(set);
        }
        return arrays;
    }

    private static void assertSameStorage(Object[] expected, ProfileUuidSet actual) throws Exception {
        Object[] current = storage(actual);
        for (int index = 0; index < expected.length; index++) {
            assertSame(expected[index], current[index]);
        }
    }
}
