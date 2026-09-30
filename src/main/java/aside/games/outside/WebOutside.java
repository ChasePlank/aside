package aside.games.outside;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generate the single-file phone build of outside.
 *
 * The fifth of mine to get one -- vigil, residue, ledger and testimony came
 * first -- and the first one where the interesting part is not the prose.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/outside/web.html) holds the layout and the rules. The
 * content -- seven days, twenty-eight visible things, the truth of each day,
 * and every fixed line the game says -- is generated from Outside.java, the
 * same class the engine screen reads. The prose was moved out of
 * OutsideScreen into the model to make that true.
 *
 * WHAT IS WRITTEN TWICE. The dispatcher's rule: it has no clock, and for each
 * concern it does what the first thing it ever heard said. That is about forty
 * lines of JavaScript. It is checked rather than trusted -- see below.
 *
 * AND ONE THING THAT IS NOT WRITTEN TWICE, WHICH IS THE POINT OF THIS FILE.
 * The closing line is chosen from the week's score, and the desktop screen
 * asks {@link Outside#closing(int, int)} for it every frame. Rather than port
 * that choice, this emits the choice: all 176 combinations of (decisions
 * right, days the truck ran) are resolved here, at build time, into a table of
 * six sentences and a flat index. The phone build therefore contains no
 * sentence-building code at all, and cannot pick a different ending than the
 * desktop does. The same is done for the score line, which is 176 strings.
 *
 * It also means the phone never needs {@link Outside#bestPossible()}. The
 * desktop had to cache that brute-force search because the end screen asked
 * for it every frame; here the answer is already in the table.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.outside.WebOutside [out.html]
 */
public final class WebOutside {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "outside", "web.html");

    /** Days the truck can run, 0..7. */
    static final int RAN = Outside.DAYS.size();
    /** Decisions that can be right, 0..21. */
    static final int RIGHT = Outside.DECISIONS;
    /** The most things that can ever have been said: four a day, every day. */
    static final int SAID = Outside.DAYS.size() * 4;
    /** The stride of every flat table below: one row per value of the other axis. */
    static final int STRIDE = RIGHT + 1;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/outside.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB)");
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
        b.append("\"decisions\":").append(Outside.DECISIONS).append(",\n");
        b.append("\"daysCount\":").append(Outside.DAYS.size()).append(",\n");

        // ---- the week
        b.append("\"days\":[\n");
        for (int i = 0; i < Outside.DAYS.size(); i++) {
            Outside.Day d = Outside.DAYS.get(i);
            b.append("  {\"heading\":").append(str(d.heading))
             .append(",\"title\":").append(str(d.title))
             .append(",\"scene\":").append(str(d.scene))
             .append(",\"facts\":[");
            for (int f = 0; f < d.facts.size(); f++) {
                Outside.Fact x = d.facts.get(f);
                if (f > 0) b.append(',');
                b.append("{\"id\":").append(str(x.id))
                 .append(",\"concern\":").append(str(x.concern.name()))
                 .append(",\"value\":").append(str(x.value))
                 .append(",\"day\":").append(x.day)
                 .append(",\"words\":").append(str(x.words)).append('}');
            }
            b.append("],\"truth\":{");
            for (int c = 0; c < Outside.Concern.values().length; c++) {
                Outside.Concern cc = Outside.Concern.values()[c];
                if (c > 0) b.append(',');
                b.append(str(cc.name())).append(':').append(str(d.truth.get(cc)));
            }
            b.append("}}");
            if (i < Outside.DAYS.size() - 1) b.append(',');
            b.append('\n');
        }
        b.append("],\n");

        // ---- the words a decision can be made in
        b.append("\"concerns\":[");
        for (int c = 0; c < Outside.Concern.values().length; c++) {
            Outside.Concern cc = Outside.Concern.values()[c];
            if (c > 0) b.append(',');
            b.append("{\"id\":").append(str(cc.name()))
             .append(",\"label\":").append(str(cc.label)).append('}');
        }
        b.append("],\n");

        b.append("\"values\":{");
        boolean firstValue = true;
        for (String v : List.of("ford", "bridge", "wait", "full", "light", "empty",
                                "dawn", "noon", "dark")) {
            if (!firstValue) b.append(',');
            firstValue = false;
            b.append(str(v)).append(':').append(str(Outside.valueLabel(v)));
        }
        b.append("},\n");

        // ---- the voice
        b.append("\"voice\":{\n");
        b.append("  \"theRidge\":").append(str(Outside.THE_RIDGE)).append(",\n");
        b.append("  \"opening\":").append(strList(Outside.OPENING)).append(",\n");
        b.append("  \"rulesHeading\":").append(str(Outside.RULES_HEADING)).append(",\n");
        b.append("  \"rules\":").append(strList(Outside.RULES)).append(",\n");
        b.append("  \"whatYouCanSee\":").append(str(Outside.WHAT_YOU_CAN_SEE)).append(",\n");
        b.append("  \"sayNothing\":").append(str(Outside.SAY_NOTHING)).append(",\n");
        b.append("  \"whatItHolds\":").append(str(Outside.WHAT_IT_HOLDS)).append(",\n");
        b.append("  \"nothing\":").append(str(Outside.NOTHING)).append(",\n");
        b.append("  \"firstLineNote\":").append(str(Outside.FIRST_LINE_NOTE)).append(",\n");
        b.append("  \"stripNote\":").append(str(Outside.STRIP_NOTE)).append(",\n");
        b.append("  \"theDispatcher\":").append(str(Outside.THE_DISPATCHER)).append(",\n");
        b.append("  \"truckWent\":").append(str(Outside.TRUCK_WENT)).append(",\n");
        b.append("  \"truckDidNotGo\":").append(str(Outside.TRUCK_DID_NOT_GO)).append(",\n");
        b.append("  \"theWorld\":").append(str(Outside.THE_WORLD)).append(",\n");
        b.append("  \"itDid\":").append(str(Outside.IT_DID)).append(",\n");
        b.append("  \"noReport\":").append(str(Outside.NO_REPORT)).append(",\n");
        b.append("  \"nothingRouted\":").append(str(Outside.NOTHING_ROUTED)).append(",\n");
        b.append("  \"weekOver\":").append(str(Outside.WEEK_OVER)).append(",\n");
        b.append("  \"believedHeading\":").append(str(Outside.BELIEVED_HEADING)).append(",\n");
        b.append("  \"neverSaid\":").append(str(Outside.NEVER_SAID)).append(",\n");
        b.append("  \"noClockLine\":").append(str(Outside.NO_CLOCK_LINE)).append(",\n");
        b.append("  \"theRidgeLog\":").append(str(Outside.THE_RIDGE_LOG)).append(",\n");
        b.append("  \"nothingWasEverSaid\":").append(str(Outside.NOTHING_WAS_EVER_SAID)).append(",\n");
        b.append("  \"weekIsOver\":").append(str(Outside.WEEK_IS_OVER)).append(",\n");
        b.append("  \"saidNothingToday\":").append(str(Outside.SAID_NOTHING_TODAY)).append(",\n");
        b.append("  \"actedOnWhatItHad\":").append(str(Outside.ACTED_ON_WHAT_IT_HAD)).append(",\n");

        // The lines that depend on a number, resolved for every number that can
        // occur. Index 0 is a blank so the array can be addressed by the number
        // itself rather than by the number minus one.
        b.append("  \"dayLabel\":").append(indexed(Outside.DAYS.size(), Outside::dayLabel)).append(",\n");
        b.append("  \"fromDay\":").append(indexed(Outside.DAYS.size(), Outside::fromDay)).append(",\n");
        b.append("  \"rightToday\":").append(indexed(3, Outside::rightToday)).append(",\n");
        b.append("  \"actedOnEarlier\":").append(indexed(Outside.DAYS.size(), Outside::actedOnEarlier)).append(",\n");

        // The header's right-hand line: one per day, and one more for a week
        // that is over. Slot 8 is the finished line, which is why the array is
        // longer than the week -- day 7 and "the week is over" are different
        // things and must not share a slot.
        b.append("  \"where\":[");
        for (int d = 0; d < Outside.DAYS.size(); d++) {
            b.append(str(Outside.where(d, false))).append(',');
        }
        b.append(str(Outside.where(0, true))).append("],\n");

        // One line per thing you could ever say, so the phone never has to
        // build "You said today: ..." out of pieces.
        b.append("  \"saidToday\":{");
        boolean firstSaid = true;
        for (Outside.Day d : Outside.DAYS) {
            for (Outside.Fact x : d.facts) {
                if (!firstSaid) b.append(',');
                firstSaid = false;
                b.append(str(x.id)).append(':').append(str(Outside.saidToday(x.words)));
            }
        }
        b.append("},\n");

        b.append("  \"reachedIt\":{");
        for (int c = 0; c < Outside.Concern.values().length; c++) {
            Outside.Concern cc = Outside.Concern.values()[c];
            if (c > 0) b.append(',');
            b.append(str(cc.name())).append(':').append(str(Outside.reachedIt(cc)));
        }
        b.append("},\n");

        // How many things were ever visible, and how many of them were said.
        // Indexed by what was said, which runs to four a day -- not by days,
        // which is the mistake this was written with the first time and which
        // the trace against the Java model caught.
        b.append("  \"visibleLine\":");
        b.append('[');
        for (int said = 0; said <= SAID; said++) {
            if (said > 0) b.append(',');
            b.append(str(Outside.visibleLine(said)));
        }
        b.append("],\n");

        // ---- the end, resolved rather than decided
        //
        // Every combination the end screen can be in is asked of the model here
        // and written down. The score line depends on (days the truck ran,
        // decisions right) -- 8 x 22 = 176 lines of prose the phone build will
        // never have to choose between. The closing line depends on (things
        // said, decisions right) -- 29 x 22 = 638 numbers saying which of six
        // sentences belongs to each. This is the file's whole argument: the
        // phone cannot pick a different ending, because it is not picking at
        // all.
        b.append("  \"scoreLine\":[\n");
        for (int ran = 0; ran <= RAN; ran++) {
            b.append("    ");
            for (int right = 0; right < STRIDE; right++) {
                if (right > 0) b.append(',');
                b.append(str(Outside.scoreLine(right, ran)));
            }
            b.append(ran < RAN ? ",\n" : "\n");
        }
        b.append("  ],\n");

        List<String> closings = distinctClosings();
        b.append("  \"closing\":").append(strList(closings)).append(",\n");
        b.append("  \"closingPick\":[\n");
        for (int said = 0; said <= SAID; said++) {
            b.append("    ");
            for (int right = 0; right < STRIDE; right++) {
                if (right > 0) b.append(',');
                b.append(closings.indexOf(Outside.closing(right, said)));
            }
            b.append(said < SAID ? ",\n" : "\n");
        }
        b.append("  ]\n");

        b.append("}\n");
        b.append("}\n");
        return b.toString();
    }

    /**
     * The distinct closing sentences, in the order the model produces them.
     *
     * Six of them, found by asking rather than by being listed here -- so a
     * seventh branch added to {@link Outside#closing(int, int)} shows up in the
     * build with no change in this file. The order is the order they are first
     * reached walking ran 0..7 and right 0..21, which is stable and meaningless,
     * which is what an index into a generated table should be.
     */
    static List<String> distinctClosings() {
        List<String> out = new java.util.ArrayList<>();
        for (int said = 0; said <= SAID; said++) {
            for (int right = 0; right < STRIDE; right++) {
                String s = Outside.closing(right, said);
                if (!out.contains(s)) out.add(s);
            }
        }
        return out;
    }

    /** A list of strings, indexed by the number itself. Slot 0 is blank. */
    static String indexed(int max, java.util.function.IntFunction<String> f) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i <= max; i++) {
            if (i > 0) b.append(',');
            b.append(str(i == 0 ? "" : f.apply(i)));
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

    private WebOutside() {}
}
