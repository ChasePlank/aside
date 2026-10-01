package aside.games.interval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generate the single-file phone build of interval.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/interval/web.html) holds the layout, the styles, and the
 * deal. The content -- the twelve log lines, the twelve names, and every fixed
 * sentence the game says -- is generated from {@link Interval}, the same class
 * the engine screen reads. There is no second copy of the prose.
 *
 * WHAT IS RESOLVED RATHER THAN PORTED. Every line of the report depends on one
 * or two numbers, so all of them go out as tables: the outcome by day, the
 * closing by ending and by watches wasted, the wasted line the same way. The
 * phone therefore contains no sentence-building code at all, and cannot say
 * something the desktop would not say at the same ending. That is the rule
 * Drift's report was resolved by, and it matters more here, because the
 * ending is a three-way branch and a hand-written second copy would get the
 * never case wrong first.
 *
 * WHAT IS PORTED, AND WHY IT HAS TO BE. The deal is not a table. A season is
 * twelve draws for the record plus one for the ship, and the record is what
 * the player reads, so the phone has to run the deal itself -- and it can only
 * do that because the draw is counter-based rather than a stream: every number
 * is a pure function of (seed, salt), so there is no RNG position to carry and
 * a seed replays a season exactly in both builds. The arithmetic is BigInt in
 * JavaScript because a double loses the low bits of a 64-bit Java long, and
 * the cumulative walk over the weight table is the same walk on both sides.
 * tools/interval-trace.mjs drives both deals over 200 seeds and compares every
 * draw of every season.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.interval.WebInterval [out.html]
 */
public final class WebInterval {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "interval", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/interval.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Interval.DAYS + " days, " + Interval.WATCHES + " watches, "
                + Interval.RECORD + " on the record)");
    }

    /** The generated file, as a string, so a test can compare it to the checked-in one. */
    public static String html() throws Exception {
        if (!Files.exists(TEMPLATE)) {
            throw new IllegalStateException("no template at " + TEMPLATE.toAbsolutePath()
                    + " -- run this from the repository root");
        }
        String t = Files.readString(TEMPLATE);
        int at = t.indexOf(MARKER);
        if (at < 0) throw new IllegalStateException("the template has no " + MARKER + " in it");
        return t.substring(0, at) + content() + t.substring(at + MARKER.length());
    }

    // ------------------------------------------------------------- content

    static String content() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("\"wordmark\":").append(str(Interval.WORDMARK)).append(",\n");
        b.append("\"where\":").append(str(Interval.WHERE)).append(",\n");
        b.append("\"whereRecord\":").append(str(Interval.WHERE_RECORD)).append(",\n");
        b.append("\"whereReport\":").append(str(Interval.WHERE_REPORT)).append(",\n");
        b.append("\"days\":").append(Interval.DAYS).append(",\n");
        b.append("\"watches\":").append(Interval.WATCHES).append(",\n");
        b.append("\"record\":").append(Interval.RECORD).append(",\n");
        b.append("\"maxRun\":").append(Interval.MAX_RUN).append(",\n");

        // The table itself, so the phone can deal. It is the whole of the
        // game's information and there is nothing here for a player to look
        // up: the record on the wall is twelve draws from it and so is she.
        b.append("\"weight\":[");
        for (int i = 0; i < Interval.WEIGHT.length; i++) {
            if (i > 0) b.append(',');
            b.append(Interval.WEIGHT[i]);
        }
        b.append("],\n");

        b.append("\"names\":").append(strList(Interval.NAMES)).append(",\n");
        b.append("\"log\":").append(strList(Interval.LOG)).append(",\n");
        b.append("\"ordinal\":").append(indexed(Interval.DAYS, Interval::ordinal)).append(",\n");

        b.append("\"voice\":{\n");
        b.append("  \"opening\":").append(strList(Interval.OPENING)).append(",\n");
        b.append("  \"rulesHeading\":").append(str(Interval.RULES_HEADING)).append(",\n");
        b.append("  \"rules\":[");
        for (int i = 0; i < Interval.RULES.size(); i++) {
            String[] r = Interval.RULES.get(i);
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(r[0])).append(",\"text\":").append(str(r[1])).append('}');
        }
        b.append("],\n");
        b.append("  \"recordHead\":").append(str(Interval.RECORD_HEAD)).append(",\n");
        b.append("  \"journalHead\":").append(str(Interval.JOURNAL_HEAD)).append(",\n");
        b.append("  \"journalBlank\":").append(str(Interval.JOURNAL_BLANK)).append(",\n");
        b.append("  \"watch\":").append(str(Interval.WATCH)).append(",\n");
        b.append("  \"sleep\":").append(str(Interval.SLEEP)).append(",\n");
        b.append("  \"startButton\":").append(str(Interval.START_BUTTON)).append(",\n");
        b.append("  \"again\":").append(str(Interval.AGAIN)).append(",\n");
        b.append("  \"cannotWatch\":").append(str(Interval.CANNOT_WATCH)).append(",\n");
        b.append("  \"noWatchesLeft\":").append(str(Interval.NO_WATCHES_LEFT)).append(",\n");
        b.append("  \"reportHead\":").append(str(Interval.REPORT_HEAD)).append(",\n");

        // Every line of the report, emitted once per number rather than written
        // into the build by hand. This is the whole reason the phone cannot
        // disagree with the desktop about what an ending means.
        b.append("  \"watched\":").append(indexed(Interval.WATCHES, Interval::watchedLine)).append(",\n");
        b.append("  \"outcome\":{\n");
        b.append("    \"CAUGHT\":").append(indexed(Interval.DAYS,
                d -> d == 0 ? "" : Interval.outcomeLine(Interval.End.CAUGHT, d))).append(",\n");
        b.append("    \"MISSED\":").append(indexed(Interval.DAYS,
                d -> d == 0 ? "" : Interval.outcomeLine(Interval.End.MISSED, d))).append(",\n");
        b.append("    \"NEVER\":").append(str(Interval.outcomeLine(Interval.End.NEVER, 0))).append("\n");
        b.append("  },\n");
        b.append("  \"wasted\":{\n");
        b.append("    \"CAUGHT\":").append(indexed(Interval.WATCHES,
                w -> Interval.wastedLine(Interval.End.CAUGHT, w))).append(",\n");
        b.append("    \"MISSED\":").append(indexed(Interval.WATCHES,
                w -> Interval.wastedLine(Interval.End.MISSED, w))).append(",\n");
        b.append("    \"NEVER\":").append(indexed(Interval.WATCHES,
                w -> Interval.wastedLine(Interval.End.NEVER, w))).append("\n");
        b.append("  },\n");
        b.append("  \"closing\":{\n");
        b.append("    \"CAUGHT\":").append(indexed(Interval.WATCHES,
                w -> Interval.closing(Interval.End.CAUGHT, w))).append(",\n");
        b.append("    \"MISSED\":").append(indexed(Interval.WATCHES,
                w -> Interval.closing(Interval.End.MISSED, w))).append(",\n");
        b.append("    \"NEVER\":").append(indexed(Interval.WATCHES,
                w -> Interval.closing(Interval.End.NEVER, w))).append("\n");
        b.append("  }\n");
        b.append("}\n}\n");
        return b.toString();
    }

    /** A list of strings, indexed by the number itself. Slot 0 is real, not blank. */
    static String indexed(int max, java.util.function.IntFunction<String> f) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i <= max; i++) {
            if (i > 0) b.append(',');
            b.append(str(f.apply(i)));
        }
        return b.append(']').toString();
    }

    static String strList(List<String> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) b.append(',');
            b.append(str(xs.get(i)));
        }
        return b.append(']').toString();
    }

    /** JSON string escaping; the prose is embedded in a script tag. */
    static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '<' -> b.append("\\u003c");
                case '>' -> b.append("\\u003e");
                case '&' -> b.append("\\u0026");
                case '\u2028' -> b.append("\\u2028");
                case '\u2029' -> b.append("\\u2029");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    private WebInterval() {}
}
