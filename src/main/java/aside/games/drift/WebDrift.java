package aside.games.drift;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generate the single-file phone build of drift.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/drift/web.html) holds the layout, the styles, and the
 * deal. The content -- the sixteen lines and every variant of each, and every
 * fixed sentence the game says -- is generated from {@link Drift}, the same
 * class the engine screen reads. There is no second copy of the log.
 *
 * WHAT IS RESOLVED RATHER THAN PORTED. Every line of the report depends on one
 * number, so all of them go out as tables: found and missed and worth and
 * closing by score, marked and false by count. The phone therefore contains no
 * sentence-building code at all, and cannot say something the desktop would not
 * say at the same score. That is the rule Outside's ending and Inventory's
 * report were resolved by, taken as far as it goes here -- this game has no
 * sentence anywhere that is not a table lookup.
 *
 * WHAT IS PORTED, AND WHY IT HAS TO BE. The deal is not a table. Sixteen lines
 * and a seed produce 16! orderings' worth of nights, so the phone has to run
 * the deal itself, and it can only do that because the draw is counter-based
 * rather than a stream: every number is a pure function of (seed, salt), so
 * there is no RNG position to carry and a seed replays a copy exactly in both
 * builds. The arithmetic is BigInt in JavaScript because a double loses the low
 * bits of a 64-bit Java long. tools/drift-trace.mjs drives both deals over 200
 * seeds and compares every line of every night.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.drift.WebDrift [out.html]
 */
public final class WebDrift {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "drift", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/drift.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Drift.LINES + " lines, " + Drift.CHANGED + " changes, "
                + Drift.REWORDED + " rewordings)");
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
        b.append("\"wordmark\":").append(str(Drift.WORDMARK)).append(",\n");
        b.append("\"whereThen\":").append(str(Drift.WHERE_THEN)).append(",\n");
        b.append("\"whereNow\":").append(str(Drift.WHERE_NOW)).append(",\n");
        b.append("\"whereReport\":").append(str(Drift.WHERE_REPORT)).append(",\n");
        b.append("\"lines\":").append(Drift.LINES).append(",\n");
        b.append("\"nights\":").append(Drift.NIGHTS).append(",\n");
        b.append("\"perNight\":").append(Drift.PER_NIGHT).append(",\n");
        b.append("\"changed\":").append(Drift.CHANGED).append(",\n");
        b.append("\"reworded\":").append(Drift.REWORDED).append(",\n");

        // The log itself, with every variant of every line. The phone is told
        // the whole corpus and no answer key: which lines changed is dealt, not
        // read, so there is nothing here for a player to look up.
        b.append("\"corpus\":[");
        for (int i = 0; i < Drift.LOG.size(); i++) {
            Drift.Line ln = Drift.LOG.get(i);
            if (i > 0) b.append(',');
            b.append("{\"night\":").append(ln.night())
             .append(",\"base\":").append(str(ln.base().text()))
             .append(",\"same\":").append(texts(ln.same()))
             .append(",\"changed\":").append(texts(ln.changed())).append('}');
        }
        b.append("],\n");

        b.append("\"voice\":{\n");
        b.append("  \"opening\":").append(strList(Drift.OPENING)).append(",\n");
        b.append("  \"rulesHeading\":").append(str(Drift.RULES_HEADING)).append(",\n");
        b.append("  \"rules\":[");
        for (int i = 0; i < Drift.RULES.size(); i++) {
            String[] r = Drift.RULES.get(i);
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(r[0])).append(",\"text\":").append(str(r[1])).append('}');
        }
        b.append("],\n");
        b.append("  \"thenHead\":").append(str(Drift.THEN_HEAD)).append(",\n");
        b.append("  \"nowHead\":").append(str(Drift.NOW_HEAD)).append(",\n");
        b.append("  \"reportRow\":").append(str(Drift.REPORT_ROW)).append(",\n");
        b.append("  \"startButton\":").append(str(Drift.START_BUTTON)).append(",\n");
        b.append("  \"again\":").append(str(Drift.AGAIN)).append(",\n");
        b.append("  \"notHere\":").append(str(Drift.NOT_HERE)).append(",\n");
        b.append("  \"reportHead\":").append(str(Drift.REPORT_HEAD)).append(",\n");
        b.append("  \"missedHead\":").append(str(Drift.MISSED_HEAD)).append(",\n");
        b.append("  \"falseHead\":").append(str(Drift.FALSE_HEAD)).append(",\n");
        b.append("  \"nothingMissed\":").append(str(Drift.NOTHING_MISSED)).append(",\n");
        b.append("  \"nothingFalse\":").append(str(Drift.NOTHING_FALSE)).append(",\n");

        // Every line of the report, emitted once per number rather than written
        // into the build by hand. This is the whole reason the phone cannot
        // disagree with the desktop about what a score means.
        b.append("  \"found\":").append(indexed(Drift.CHANGED, Drift::foundLine)).append(",\n");
        b.append("  \"marked\":").append(indexed(Drift.LINES, Drift::markedLine)).append(",\n");
        b.append("  \"false\":").append(indexed(Drift.LINES, Drift::falseLine)).append(",\n");
        b.append("  \"missed\":").append(indexed(Drift.CHANGED, Drift::missedLine)).append(",\n");
        b.append("  \"worth\":").append(indexed(Drift.CHANGED, Drift::worthLine)).append(",\n");
        b.append("  \"closing\":").append(indexed(Drift.CHANGED, Drift::closing)).append("\n");
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

    static String texts(List<Drift.Variant> vs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < vs.size(); i++) {
            if (i > 0) b.append(',');
            b.append(str(vs.get(i).text()));
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

    private WebDrift() {}
}
