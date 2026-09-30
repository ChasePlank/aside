package aside.games.redaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every way the file can be sent, and what the board makes of it.
 *
 * There are sixteen lines and each is withheld or released, so the whole space
 * is 2^16 = 65,536 filings. That is small enough to enumerate exactly, which
 * means this is not a sample of the game -- it is the game. Every outcome the
 * player can reach is in the table below, and the table is the reason the
 * thresholds in {@link Redaction} are where they are rather than where they
 * felt right.
 *
 * The thing it is looking for is the FRONTIER: the filings where you cannot
 * improve one of the board's three lines without making another one worse. If
 * the frontier is a single point, the game is a puzzle with an answer and no
 * decision. If it is a line, the game is a trade. It is a line.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.redaction.Trace
 */
public final class Trace {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--dump")) {
            dump(java.nio.file.Path.of(args.length > 1 ? args[1] : "/tmp/redaction-java.txt"));
            return;
        }
        Redaction r = Redaction.of();
        int n = r.lines.size();
        int total = 1 << n;

        // outcome -> how many filings produce it
        Map<String, Integer> outcomes = new LinkedHashMap<>();
        // the best filing for each (nameOut, failsOut) pair, by withheld count
        Map<String, int[]> best = new LinkedHashMap<>();

        int nameOutCount = 0;
        int minWithheldNameSafe = Integer.MAX_VALUE;
        int minWithheldNameSafeFailsZero = Integer.MAX_VALUE;

        for (int mask = 0; mask < total; mask++) {
            for (int i = 0; i < n; i++) r.lines.get(i).withheld = (mask & (1 << i)) != 0;
            Redaction.Finding f = r.finding();
            String key = Redaction.headline(f) + " | " + Redaction.personLine(f.nameOut)
                    + " | " + Redaction.recordLine(f.failsOut)
                    + " | " + Redaction.withholdingLine(f.withheld);
            outcomes.merge(key, 1, Integer::sum);
            if (f.nameOut) nameOutCount++;

            String pair = f.nameOut + "/" + f.failsOut;
            int[] cur = best.get(pair);
            if (cur == null || f.withheld < cur[0]) best.put(pair, new int[]{f.withheld, mask});

            if (!f.nameOut) {
                minWithheldNameSafe = Math.min(minWithheldNameSafe, f.withheld);
                if (f.failsOut == 0) {
                    minWithheldNameSafeFailsZero = Math.min(minWithheldNameSafeFailsZero, f.withheld);
                }
            }
        }

        System.out.println("redaction: " + n + " lines, " + Redaction.DIGS
                + " questions, " + total + " filings");
        System.out.println();

        System.out.println("--- the distinct findings, and how many filings reach each ---");
        List<Map.Entry<String, Integer>> es = new ArrayList<>(outcomes.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> e : es) {
            System.out.printf("  %6d  %s%n", e.getValue(), e.getKey());
        }
        System.out.println();
        System.out.println("  filings that name the complainant: " + nameOutCount
                + " of " + total + " (" + pct(nameOutCount, total) + ")");
        System.out.println("  cheapest filing that keeps the name out: "
                + minWithheldNameSafe + " withheld");
        System.out.println("  cheapest filing that keeps the name out AND the record clean: "
                + (minWithheldNameSafeFailsZero == Integer.MAX_VALUE
                        ? "none -- unreachable" : minWithheldNameSafeFailsZero + " withheld"));
        System.out.println();

        System.out.println("--- the frontier: fewest bars for each (name out?, failures out) ---");
        System.out.println("  nameOut  failsOut  fewest bars  the filing");
        for (Map.Entry<String, int[]> e : best.entrySet()) {
            int[] v = e.getValue();
            System.out.printf("  %-8s %-9s %-12d %s%n",
                    e.getKey().split("/")[0], e.getKey().split("/")[1], v[0], show(v[1], n));
        }
        System.out.println();

        System.out.println("--- the named plays ---");
        play("release everything", n, new int[]{});
        play("withhold the name only", n, new int[]{14});
        play("withhold the name and four decoys", n, new int[]{1, 2, 3, 4, 14});
        play("withhold the name and four decoys, avoiding line 3", n, new int[]{1, 2, 4, 6, 14});
        play("withhold the name and five decoys, avoiding line 3", n, new int[]{1, 2, 4, 6, 7, 14});
        play("... and bury one failure", n, new int[]{1, 2, 4, 6, 7, 14, 15});
        play("... and bury two failures", n, new int[]{1, 2, 4, 6, 7, 11, 14, 15});
        play("... and bury three failures", n, new int[]{1, 2, 4, 6, 7, 9, 11, 14, 15});
        play("withhold the failures and nothing else", n, new int[]{5, 9, 11, 15});
        play("withhold everything", n, all(n));
    }

    /**
     * Every filing and what the board made of it, one per line.
     *
     * This is the reference the phone build is checked against. The reading is
     * the one part of the phone that cannot be resolved into a table -- 2^16
     * filings is too many -- so it is ported, and a port is the thing most
     * likely to be wrong. tools/redaction-trace.mjs drives the build's own
     * finding() through all 65,536 and compares every line to this file.
     *
     * Format: mask nameOut failsOut withheld extra read,read,...
     * The mask's bit i is line i+1, which is the same bit the harness sets.
     */
    static void dump(java.nio.file.Path out) throws Exception {
        Redaction r = Redaction.of();
        int n = r.lines.size();
        StringBuilder b = new StringBuilder();
        for (int mask = 0; mask < (1 << n); mask++) {
            for (int i = 0; i < n; i++) r.lines.get(i).withheld = (mask & (1 << i)) != 0;
            Redaction.Finding f = r.finding();
            b.append(mask).append(' ').append(f.nameOut ? 1 : 0).append(' ')
             .append(f.failsOut).append(' ').append(f.withheld).append(' ')
             .append(f.extra).append(' ');
            for (int i = 0; i < f.recovered.size(); i++) {
                if (i > 0) b.append(',');
                b.append(f.recovered.get(i).number);
            }
            b.append('\n');
        }
        java.nio.file.Files.writeString(out, b.toString());
        System.out.println("wrote " + out + "  (" + (1 << n) + " filings)");
    }

    static int[] all(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i + 1;
        return a;
    }

    static void play(String label, int n, int[] lines) {
        Redaction r = Redaction.of();
        for (int i = 0; i < n; i++) r.lines.get(i).withheld = false;
        for (int ln : lines) r.lines.get(ln - 1).withheld = true;
        Redaction.Finding f = r.finding();
        System.out.printf("  %-52s %2d bars  name %-3s  fails %d/%d  %s%n",
                label, f.withheld, f.nameOut ? "OUT" : "in", f.failsOut, f.failsTotal,
                Redaction.headline(f));
    }

    static String show(int mask, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if ((mask & (1 << i)) != 0) {
                if (b.length() > 0) b.append(',');
                b.append(i + 1);
            }
        }
        return b.length() == 0 ? "(nothing withheld)" : "withhold " + b;
    }

    static String pct(int a, int b) {
        return String.format("%.1f%%", 100.0 * a / b);
    }

    private Trace() {}
}
