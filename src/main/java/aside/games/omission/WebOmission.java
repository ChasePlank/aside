package aside.games.omission;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generate the single-file phone build of omission.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/omission/web.html) holds the layout, the styles, and the
 * deal. The content -- the eighteen things you know, their weights, their
 * lines, their assumptions, and every fixed sentence -- is generated from
 * {@link Omission}, the same class the engine screen reads. There is no second
 * copy of the prose.
 *
 * WHAT IS RESOLVED RATHER THAN PORTED. The verdict is one of four fixed
 * sentences, so all four go out as strings. The closing depends on how many
 * things nobody else knew, so all thirteen of it go out as a table. The score
 * line depends on the count, so thirteen templates go out with %score% and
 * %total% in them, which is the placeholder pattern Testimony and Handoff
 * established. The phone therefore composes no sentence the desktop could not
 * have composed, which matters here because the report is the one screen the
 * player reads for information.
 *
 * WHAT IS PORTED, AND WHY IT HAS TO BE. The deal is not a table: a list is
 * twelve things drawn from eighteen, and the player chooses from the list, so
 * the phone has to deal it itself -- and it can only do that because the draw
 * is counter-based rather than a stream: every number is a pure function of
 * (seed, salt), so there is no RNG position to carry and a seed replays a house
 * exactly in both builds. The arithmetic is BigInt in JavaScript because a
 * double loses the low bits of a 64-bit Java long. tools/omission-trace.mjs
 * drives both deals over 200 seeds and compares every thing on the list, its
 * weight, its line, and what each build remembers about it.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.omission.WebOmission [out.html]
 */
public final class WebOmission {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "omission", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/omission.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Omission.LIST + " things on the list, " + Omission.SLOTS + " slots, "
                + Omission.POOL.size() + " in the pool)");
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
        b.append("\"wordmark\":").append(str(Omission.WORDMARK)).append(",\n");
        b.append("\"where\":").append(str(Omission.WHERE)).append(",\n");
        b.append("\"whereList\":").append(str(Omission.WHERE_LIST)).append(",\n");
        b.append("\"whereReport\":").append(str(Omission.WHERE_REPORT)).append(",\n");
        b.append("\"list\":").append(Omission.LIST).append(",\n");
        b.append("\"slots\":").append(Omission.SLOTS).append(",\n");

        // The eighteen things you know, in pool order. The phone deals from
        // this, so the order matters: it is the order the desktop deals from.
        b.append("\"pool\":[\n");
        for (int i = 0; i < Omission.POOL.size(); i++) {
            Omission.Detail d = Omission.POOL.get(i);
            b.append("  {\"id\":").append(str(d.id()))
             .append(",\"text\":").append(str(d.text()))
             .append(",\"said\":").append(str(d.said()))
             .append(",\"matters\":").append(d.matters())
             .append(",\"told\":").append(d.told())
             .append(",\"assumed\":").append(str(d.assumed())).append('}');
            if (i < Omission.POOL.size() - 1) b.append(',');
            b.append('\n');
        }
        b.append("],\n");

        // A weight, said as a word. Indexed by the weight itself, so the phone
        // never builds "most" out of a number.
        b.append("\"worth\":[");
        for (int m = 0; m <= 3; m++) {
            if (m > 0) b.append(',');
            b.append(str(m == 0 ? "" : Omission.worth(m)));
        }
        b.append("],\n");

        // The score line, one per possible count, with the two numbers left as
        // placeholders because they are the only part that is not a word.
        b.append("\"scoreLine\":[");
        for (int n = 0; n <= Omission.LIST; n++) {
            if (n > 0) b.append(',');
            b.append(str(Omission.scoreLine(n, Omission.LIST, 0, 0)
                    .replace("worth 0 of the 0 that was there.", "worth %score% of the %total% that was there.")));
        }
        b.append("],\n");
        b.append("\"bestLine\":").append(str(Omission.bestLine(0)
                .replace(": 0.", ": %best%."))).append(",\n");

        // The verdict, all four of it. There is no interpolation in it, so
        // there is nothing for the phone to get wrong.
        b.append("\"verdict\":{\n");
        b.append("  \"perfect\":").append(str(Omission.verdict(12, 12, null, null))).append(",\n");
        b.append("  \"wasted\":").append(str(Omission.verdict(0, 12,
                Omission.POOL.get(0), null))).append(",\n");
        b.append("  \"lost\":").append(str(Omission.verdict(0, 12, null, Omission.POOL.get(6)))).append(",\n");
        b.append("  \"short\":").append(str(Omission.verdict(0, 12, null, null))).append("\n");
        b.append("},\n");

        // The closing, resolved rather than built: one per possible count of
        // things nobody else knew, one per possible count of wasted slots.
        b.append("\"closing\":{\n");
        b.append("  \"perfect\":[");
        for (int n = 0; n <= Omission.LIST; n++) {
            if (n > 0) b.append(',');
            b.append(str(Omission.closing(12, 12, n, 0)));
        }
        b.append("],\n");
        b.append("  \"wasted\":[");
        for (int n = 0; n <= Omission.SLOTS; n++) {
            if (n > 0) b.append(',');
            b.append(str(Omission.closing(0, 12, 8, n)));
        }
        b.append("],\n");
        b.append("  \"short\":").append(str(Omission.closing(0, 12, 8, 0))).append("\n");
        b.append("},\n");

        b.append("\"voice\":{\n");
        b.append("  \"opening\":").append(strList(Omission.OPENING)).append(",\n");
        b.append("  \"rulesHeading\":").append(str(Omission.RULES_HEADING)).append(",\n");
        b.append("  \"rules\":[");
        for (int i = 0; i < Omission.RULES.size(); i++) {
            String[] r = Omission.RULES.get(i);
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(r[0])).append(",\"text\":").append(str(r[1])).append('}');
        }
        b.append("],\n");
        b.append("  \"listHead\":").append(str(Omission.LIST_HEAD)).append(",\n");
        b.append("  \"bagHead\":").append(str(Omission.BAG_HEAD)).append(",\n");
        b.append("  \"keptTag\":").append(str(Omission.KEPT_TAG)).append(",\n");
        b.append("  \"cameBackTag\":").append(str(Omission.CAME_BACK_TAG)).append(",\n");
        b.append("  \"youBelieve\":").append(str(Omission.YOU_BELIEVE)).append(",\n");
        b.append("  \"readBackHead\":").append(str(Omission.READ_BACK_HEAD)).append(",\n");
        b.append("  \"scoreHead\":").append(str(Omission.SCORE_HEAD)).append(",\n");
        b.append("  \"nothingYet\":").append(str(Omission.NOTHING_YET)).append(",\n");
        b.append("  \"slotsLeft\":").append(str(Omission.SLOTS_LEFT)).append(",\n");
        b.append("  \"startButton\":").append(str(Omission.START_BUTTON)).append(",\n");
        b.append("  \"again\":").append(str(Omission.AGAIN)).append(",\n");
        b.append("  \"leaveShort\":").append(str(Omission.LEAVE_LINE)).append(",\n");
        b.append("  \"leaveButton\":").append(str(Omission.LEAVE_BUTTON)).append(",\n");
        b.append("  \"take\":").append(str(Omission.TAKE)).append(",\n");
        b.append("  \"full\":").append(str(Omission.FULL)).append("\n");
        b.append("}\n}\n");
        return b.toString();
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

    private WebOmission() { }
}
