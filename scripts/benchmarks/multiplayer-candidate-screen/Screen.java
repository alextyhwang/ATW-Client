package screen;

import java.lang.management.ManagementFactory;
import java.util.*;

public final class Screen {
    public static final UUID[] HIGHWATER = population(512);
    public static final String[] HEADERS = new String[64], FOOTERS = new String[64], PLAIN = new String[64];
    private static volatile long consumed;
    private static final com.sun.management.ThreadMXBean ALLOCATION =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static {
        for (int i = 0; i < 64; i++) {
            HEADERS[i] = (i % 2 == 0 ? "&bLevel: &e" : "\u00a7b[\u00a7e") + (i % 2 == 0 ? "" : (100 + i) + "\u00a7b] ");
            FOOTERS[i] = (i % 2 == 0 ? "" : "&f") + (40 + i) + (i % 2 == 0 ? "&a\u2605" : " &7| &a2." + i);
            PLAIN[i] = Integer.toString(40 + i);
        }
    }

    private static UUID[] population(int n) {
        Random random = new Random(0x41545720261006L);
        UUID[] ids = new UUID[n];
        for (int i = 0; i < n; i++) {
            long most = random.nextLong(), least = random.nextLong();
            most = (most & 0xffffffffffff0fffL) | 0x4000L;
            least = (least & 0x3fffffffffffffffL) | 0x8000000000000000L;
            ids[i] = new UUID(most, least);
        }
        return ids;
    }

    private static UUID[][] sequence(int n, boolean churn, boolean marker) {
        UUID[][] sequence = new UUID[64][];
        for (int t = 0; t < 64; t++) {
            List<UUID> list = new ArrayList<>();
            int offset = churn ? (t / 4) * Math.max(1, n / 8) : 0;
            for (int i = 0; i < n; i++) {
                if (!marker || (i + (churn ? t / 2 : 0)) % 4 != 0) list.add(HIGHWATER[(i + offset) % HIGHWATER.length]);
            }
            sequence[t] = list.toArray(UUID[]::new);
        }
        return sequence;
    }

    private record Case(String name, int n, boolean churn) {}
    private static long allocated() { return ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().getId()); }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        int fork = Integer.parseInt(args[0]), samples = Integer.parseInt(args[1]);
        if (!ALLOCATION.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Allocation counter unsupported");
        ALLOCATION.setThreadAllocatedMemoryEnabled(true);
        List<Case> cases = new ArrayList<>();
        for (int n : new int[]{0, 1, 2, 8, 16, 32, 64, 128, 256}) {
            cases.add(new Case("map-steady/" + n, n, false));
            cases.add(new Case("map-churn/" + n, n, true));
            cases.add(new Case("map-growth/" + n, n, false));
        }
        for (int n : new int[]{0, 1, 2, 8, 16}) cases.add(new Case("map-highwater/" + n, n, true));
        for (String flavor : new String[]{"mix", "plain", "formatted", "factory"}) {
            for (String mode : new String[]{"construct", "cold-text", "few-text", "warm-text", "warm-nicked", "warm-both"})
                cases.add(new Case("label-" + mode + "/" + flavor, 0, false));
        }
        for (int reads : new int[]{1, 4, 16}) cases.add(new Case("label-few-both/" + reads + "/mix", 0, false));
        // Reverse traversal on alternating forks to expose case-order/JIT drift.
        if ((fork & 1) != 0) Collections.reverse(cases);
        System.err.println("PARITY: source-derived arms + independent HashSet and label-value oracle; allocation supported=true");
        System.out.println("fork,case,sample,order,arm,operations,elapsed_ns,ns_per_op,allocated_bytes,bytes_per_op,checksum");
        for (Case c : cases) {
            UUID[][] census = sequence(c.n, c.churn, false), markers = sequence(c.n, c.churn, true);
            UUID[] ids = Arrays.copyOf(HIGHWATER, c.n);
            var candidate = new screen.candidate.Arms(c.name, census, markers, ids);
            var reference = new screen.reference.Arms(c.name, census, markers, ids);
            candidate.validate(); reference.validate();
            candidate.prepare(1024); reference.prepare(1024);
            long checkC = candidate.run(1024), checkR = reference.run(1024);
            if (checkC != checkR) throw new AssertionError(c.name + " checksum mismatch");
            consumed ^= checkC ^ Long.rotateLeft(checkR, 7);
            // Balanced repeated direct-call warmup; preparation is never measured.
            long warmEnd = System.nanoTime() + 120_000_000L;
            int rounds = 0;
            do {
                if (((rounds + fork) & 1) == 0) {
                    candidate.prepare(1024); consumed ^= candidate.run(1024);
                    reference.prepare(1024); consumed ^= reference.run(1024);
                } else {
                    reference.prepare(1024); consumed ^= reference.run(1024);
                    candidate.prepare(1024); consumed ^= candidate.run(1024);
                }
                rounds++;
            } while (System.nanoTime() < warmEnd && rounds < 1024);
            candidate.prepare(4096); reference.prepare(4096);
            long start = System.nanoTime(); consumed ^= candidate.run(4096); long ct = System.nanoTime() - start;
            start = System.nanoTime(); consumed ^= reference.run(4096); long rt = System.nanoTime() - start;
            // Common operation count and checksum per pair, target ~8ms slower arm.
            int operations = (int) Math.max(1024, Math.min(262144, 4096.0 * 8_000_000 / Math.max(ct, rt)));
            operations = (operations + 63) / 64 * 64;
            for (int sample = 0; sample < samples; sample++) {
                boolean candidateFirst = ((fork + sample) & 1) == 0;
                long pairC = 0, pairR = 0;
                for (int order = 0; order < 2; order++) {
                    boolean isCandidate = (order == 0) == candidateFirst;
                    if (isCandidate) candidate.prepare(operations); else reference.prepare(operations);
                    long bytes = allocated();
                    start = System.nanoTime();
                    long checksum = isCandidate ? candidate.run(operations) : reference.run(operations);
                    long elapsed = System.nanoTime() - start;
                    bytes = allocated() - bytes;
                    consumed ^= checksum;
                    if (isCandidate) pairC = checksum; else pairR = checksum;
                    System.out.printf("%d,%s,%d,%d,%s,%d,%d,%.6f,%d,%.6f,%d%n", fork, c.name, sample,
                        order, isCandidate ? "candidate" : "reference", operations, elapsed,
                        (double) elapsed / operations, bytes, (double) bytes / operations, checksum);
                }
                if (pairC != pairR) throw new AssertionError(c.name + " timed checksum mismatch");
            }
        }
        System.err.println("PASS: every case oracle and every paired checksum; consumed=" + consumed);
    }
}
