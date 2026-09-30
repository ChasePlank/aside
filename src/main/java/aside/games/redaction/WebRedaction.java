package aside.games.redaction;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generate the single-file phone build of redaction.
 *
 * The eleventh of mine, and the first one where the whole report is resolved
 * rather than ported.
 *
 * WHAT IS RESOLVED. The board's finding is five sentences chosen by three
 * numbers -- whether the complainant is named, how many of the office's
 * failures came out, and how many lines were withheld. That is 2 x 5 x 17 =
 * 170 combinations, and all 170 are emitted as a table. The phone contains no
 * sentence-building code for the finding at all: it counts, forms a key, and
 * looks the answer up. Outside resolved the ending and Inventory resolved the
 * report; this resolves the report's every line, including the verdict.
 *
 * WHAT IS PORTED, AND WHY IT HAS TO BE. The reading cannot be resolved. Which
 * lines the board reads depends on which lines were withheld and in what
 * order, and that is 2^16 filings -- too many to table. So the reading loop is
 * ported to JavaScript, and because it is ported it is the thing most likely
 * to be wrong. It is therefore the thing the trace checks: tools/redaction-trace.mjs
 * drives the build's own {@code finding()} through all 65,536 filings and
 * compares every one against the Java model. Not a sample. All of them.
 *
 * WHAT THE BUILD IS TOLD. The kind of every line (safe, failure, name) and
 * whether it refers to the complainant. It has to be: the board's reading is a
 * function of the file, and the file is public. This is the opposite of
 * Inventory, where the truth of a thing was deliberately withheld from the
 * build because the game was about not being able to reach it.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.redaction.WebRedaction [out.html]
 */
public final class WebRedaction {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "redaction", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/redaction.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Redaction.LINES + " lines, " + Redaction.findingKeys() + " findings)");
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
        b.append("\"wordmark\":").append(str(Redaction.WORDMARK)).append(",\n");
        b.append("\"whereOpen\":").append(str(Redaction.WHERE_OPEN)).append(",\n");
        b.append("\"whereFile\":").append(str(Redaction.WHERE_FILE)).append(",\n");
        b.append("\"whereReport\":").append(str(Redaction.WHERE_REPORT)).append(",\n");
        b.append("\"digs\":").append(Redaction.DIGS).append(",\n");

        // The file. The kind is the model's, not a guess: the board's reading
        // is a function of the file, and the file is what the player is given.
        b.append("\"lines\":[");
        Redaction r = Redaction.of();
        for (int i = 0; i < r.lines.size(); i++) {
            Redaction.Line l = r.lines.get(i);
            if (i > 0) b.append(',');
            b.append("{\"n\":").append(l.number)
             .append(",\"text\":").append(str(l.text))
             .append(",\"kind\":").append(str(kind(l.kind)))
             .append(",\"refers\":").append(l.refers).append('}');
        }
        b.append("],\n");

        // ---- the voice
        b.append("\"voice\":{\n");
        b.append("  \"opening\":").append(strList(Redaction.OPENING)).append(",\n");
        b.append("  \"rulesHeading\":").append(str(Redaction.RULES_HEADING)).append(",\n");
        b.append("  \"rules\":[");
        for (int i = 0; i < Redaction.RULES.size(); i++) {
            String[] rule = Redaction.RULES.get(i);
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(rule[0])).append(",\"text\":").append(str(rule[1])).append('}');
        }
        b.append("],\n");
        b.append("  \"startLine\":").append(str(Redaction.START_LINE)).append(",\n");
        b.append("  \"startButton\":").append(str(Redaction.START_BUTTON)).append(",\n");
        b.append("  \"sendButton\":").append(str(Redaction.SEND_BUTTON)).append(",\n");
        b.append("  \"again\":").append(str(Redaction.AGAIN)).append(",\n");
        b.append("  \"withholdBtn\":").append(str(Redaction.WITHHOLD_BTN)).append(",\n");
        b.append("  \"releaseBtn\":").append(str(Redaction.RELEASE_BTN)).append(",\n");
        b.append("  \"heldLine\":").append(str(Redaction.HELD_LINE)).append(",\n");
        b.append("  \"digsLine\":").append(str(Redaction.DIGS_LINE)).append(",\n");
        b.append("  \"extraLine\":").append(str(Redaction.EXTRA_LINE)).append(",\n");
        b.append("  \"extraNone\":").append(str(Redaction.EXTRA_NONE)).append(",\n");
        b.append("  \"sendWarning\":").append(str(Redaction.SEND_WARNING)).append(",\n");
        b.append("  \"theFile\":").append(str(Redaction.THE_FILE)).append(",\n");
        b.append("  \"theFinding\":").append(str(Redaction.THE_FINDING)).append(",\n");
        b.append("  \"whatWasRead\":").append(str(Redaction.WHAT_WAS_READ)).append(",\n");
        b.append("  \"nothingRead\":").append(str(Redaction.NOTHING_READ)).append(",\n");
        b.append("  \"withheldMark\":").append(str(Redaction.WITHHELD_MARK)).append(",\n");
        b.append("  \"releasedMark\":").append(str(Redaction.RELEASED_MARK)).append(",\n");
        b.append("  \"recoveredMark\":").append(str(Redaction.RECOVERED_MARK)).append(",\n");
        b.append("  \"notReached\":").append(str(Redaction.NOT_REACHED)).append(",\n");
        b.append("  \"refersMark\":").append(str(Redaction.REFERS_MARK)).append(",\n");
        b.append("  \"standing\":").append(str(Redaction.STANDING)).append(",\n");
        b.append("  \"closingTemplate\":").append(str(Redaction.CLOSING_TEMPLATE)).append(",\n");
        b.append("  \"recoveredNone\":").append(str(Redaction.RECOVERED_NONE)).append(",\n");
        b.append("  \"recoveredSome\":").append(str(Redaction.RECOVERED_SOME)).append("\n},\n");

        // ---- the finding, resolved rather than decided
        b.append("\"finding\":[");
        for (int k = 0; k < Redaction.findingKeys(); k++) {
            if (k > 0) b.append(',');
            int withheld = k % (Redaction.LINES + 1);
            int rest = k / (Redaction.LINES + 1);
            int failsOut = rest % (Redaction.FAILS_TOTAL + 1);
            boolean nameOut = rest / (Redaction.FAILS_TOTAL + 1) != 0;
            String[] e = Redaction.findingEntry(nameOut, failsOut, withheld);
            b.append("{\"headline\":").append(str(e[0]))
             .append(",\"person\":").append(str(e[1]))
             .append(",\"record\":").append(str(e[2]))
             .append(",\"held\":").append(str(e[3]))
             .append(",\"verdict\":").append(str(e[4])).append('}');
        }
        b.append("]\n}\n");
        return b.toString();
    }

    static String kind(int k) {
        if (k == Redaction.FAIL) return "fail";
        if (k == Redaction.NAME) return "name";
        return "safe";
    }

    static String strList(java.util.List<String> xs) {
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

    private WebRedaction() {}
}
