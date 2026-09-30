package aside.games.attribution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generate the single-file phone build of attribution.
 *
 * The tenth of mine, and the first one where the phone is told the answer.
 *
 * WHY THIS ONE IS DIFFERENT. Inventory's phone build was deliberately not told
 * what any object was for, because the game's subject was that a card is not a
 * description of a thing, and a build that knew the truth could have leaked it.
 * This game has the opposite shape. The truth of a line is the thing the player
 * *buys* with a call, so the build has to know it, and the report has to know
 * it for all twenty-four lines, not just the called ones. There is no version
 * of this file that does not contain the desk.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/attribution/web.html) holds the layout, the styles, and
 * the deal. The content -- the six names and beats, the six reliabilities, the
 * twenty-four lines of copy, and every fixed sentence the game says -- is
 * generated from {@link Attribution}, the same class the engine screen reads.
 *
 * WHAT IS RESOLVED RATHER THAN PORTED. The headline and the closing depend on
 * one number, so all twenty-five answers of each are emitted as a table and the
 * phone contains no sentence-building code. The verdict depends on five numbers
 * at once and cannot be tabled, so it goes out as the same template the desktop
 * fills, and the phone fills it with the same substitutions. The calls line is
 * three pieces -- the count, the "never called" template, and the "everyone"
 * ending -- because the list of names in the middle is the only part that has
 * to be built at runtime.
 *
 * WHAT IS DEALT RATHER THAN EMITTED. The night is not in this file. What is in
 * it is the desk -- six names, six beats, six reliabilities -- and the copy, and
 * the template shuffles them at load. A phone build that always dealt the same
 * night would not be a game, and the desktop deals from a seed for the same
 * reason.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.attribution.WebAttribution [out.html]
 */
public final class WebAttribution {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "attribution", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/attribution.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Attribution.ITEMS + " lines, " + Attribution.STRINGERS + " bylines)");
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
        Attribution a = Attribution.of(1);
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("\"wordmark\":").append(str(Attribution.WORDMARK)).append(",\n");
        b.append("\"whereOpen\":").append(str(Attribution.WHERE_OPEN)).append(",\n");
        b.append("\"whereNight\":").append(str(Attribution.WHERE_NIGHT)).append(",\n");
        b.append("\"whereReport\":").append(str(Attribution.WHERE_REPORT)).append(",\n");
        b.append("\"calls\":").append(Attribution.CALLS).append(",\n");
        b.append("\"rounds\":").append(Attribution.ROUNDS).append(",\n");
        b.append("\"perRound\":").append(Attribution.PER_ROUND).append(",\n");

        // The desk. Reliabilities go out in the model's own order; the template
        // shuffles them onto the names, which is what the seed does on desktop.
        b.append("\"reliabilities\":[");
        for (int i = 0; i < Attribution.RELIABILITY.length; i++) {
            if (i > 0) b.append(',');
            b.append(Attribution.RELIABILITY[i]);
        }
        b.append("],\n");
        b.append("\"stringers\":[");
        for (int i = 0; i < a.stringers.size(); i++) {
            Attribution.Stringer s = a.stringers.get(i);
            if (i > 0) b.append(',');
            b.append("{\"name\":").append(str(s.name))
             .append(",\"beat\":").append(str(s.beat)).append('}');
        }
        b.append("],\n");
        b.append("\"copy\":").append(strList(Attribution.COPY)).append(",\n");

        // ---- the voice
        b.append("\"voice\":{\n");
        b.append("  \"opening\":").append(strList(Attribution.OPENING)).append(",\n");
        b.append("  \"rulesHeading\":").append(str(Attribution.RULES_HEADING)).append(",\n");
        b.append("  \"rules\":[");
        for (int i = 0; i < Attribution.RULES.size(); i++) {
            String[] r = Attribution.RULES.get(i);
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(r[0])).append(",\"text\":").append(str(r[1])).append('}');
        }
        b.append("],\n");
        b.append("  \"startLine\":").append(str(Attribution.START_LINE)).append(",\n");
        b.append("  \"startButton\":").append(str(Attribution.START_BUTTON)).append(",\n");
        b.append("  \"callBtn\":").append(str(Attribution.CALL_BTN)).append(",\n");
        b.append("  \"theDesk\":").append(str(Attribution.THE_DESK)).append(",\n");
        b.append("  \"whoYouCalled\":").append(str(Attribution.WHO_YOU_CALLED)).append(",\n");
        b.append("  \"whoFilesHere\":").append(str(Attribution.WHO_FILES_HERE)).append(",\n");
        b.append("  \"nobodyCalled\":").append(str(Attribution.NOBODY_CALLED)).append(",\n");
        b.append("  \"theEdition\":").append(str(Attribution.THE_EDITION)).append(",\n");
        b.append("  \"filed\":").append(str(Attribution.FILED)).append(",\n");
        b.append("  \"trueOf\":").append(str(Attribution.TRUE_OF)).append(",\n");
        b.append("  \"called\":").append(str(Attribution.CALLED)).append(",\n");
        b.append("  \"notCalled\":").append(str(Attribution.NOT_CALLED)).append(",\n");
        b.append("  \"run\":").append(str(Attribution.RUN)).append(",\n");
        b.append("  \"spike\":").append(str(Attribution.SPIKE)).append(",\n");
        b.append("  \"noCall\":").append(str(Attribution.NO_CALL)).append(",\n");
        b.append("  \"wasTrue\":").append(str(Attribution.WAS_TRUE)).append(",\n");
        b.append("  \"wasFalse\":").append(str(Attribution.WAS_FALSE)).append(",\n");
        b.append("  \"roundFiled\":").append(str(Attribution.ROUND_FILED)).append(",\n");
        b.append("  \"noCalls\":").append(str(Attribution.NO_CALLS)).append(",\n");
        b.append("  \"alreadyCalled\":").append(str(Attribution.ALREADY_CALLED)).append(",\n");
        b.append("  \"alreadyFiled\":").append(str(Attribution.ALREADY_FILED)).append(",\n");
        b.append("  \"again\":").append(str(Attribution.AGAIN)).append(",\n");
        b.append("  \"or\":").append(str(Attribution.OR)).append(",\n");
        b.append("  \"callsLineNever\":").append(str(Attribution.CALLS_LINE_NEVER)).append(",\n");
        b.append("  \"callsLineAll\":").append(str(Attribution.CALLS_LINE_ALL)).append(",\n");

        // The lines that depend on a number, emitted once per number rather
        // than written into the build by hand.
        b.append("  \"callsLineMade\":[");
        for (int i = 0; i <= Attribution.CALLS; i++) {
            if (i > 0) b.append(',');
            b.append(str(Attribution.callsLineMade(i)));
        }
        b.append("],\n");
        b.append("  \"callsWhere\":[");
        for (int i = 0; i <= Attribution.CALLS; i++) {
            if (i > 0) b.append(',');
            b.append(str(Attribution.callsWhere(i)));
        }
        b.append("],\n");
        b.append("  \"roundWhere\":[");
        for (int i = 0; i <= Attribution.ROUNDS; i++) {
            if (i > 0) b.append(',');
            b.append(str(i < Attribution.ROUNDS ? Attribution.roundWhere(i) : ""));
        }
        b.append("]\n},\n");

        // ---- the report, resolved rather than decided
        b.append("\"headline\":").append(indexed(Attribution.ITEMS, Attribution::headline)).append(",\n");
        b.append("\"closing\":").append(indexed(Attribution.ITEMS, Attribution::closing)).append(",\n");
        b.append("\"verdictTemplate\":").append(str(Attribution.VERDICT_TEMPLATE)).append(",\n");
        b.append("\"standing\":").append(str(Attribution.STANDING));
        b.append("\n}\n");
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

    private WebAttribution() {}
}
