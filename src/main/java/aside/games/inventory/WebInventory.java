package aside.games.inventory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generate the single-file phone build of inventory.
 *
 * The seventh of mine to get one. Handoff, the one before it, resolved its
 * whole simulation into a table; this one cannot, because the thing being
 * resolved is not a rule but a *sentence*, and there are two of them per card.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/inventory/web.html) holds the layout and the bench. The
 * content -- the eight objects, the six words a card can take, what the survey
 * did with each card, and every fixed line the game says -- is generated from
 * {@link Inventory}, the same class the engine screen reads. The prose was
 * moved out of {@link InventoryScreen} into the model to make that true.
 *
 * WHAT THE PHONE IS NOT TOLD, WHICH IS THE INTERESTING PART.
 *
 * The game's whole subject is that a card is not a description of a thing. The
 * desktop model knows what each object is for -- {@code Thing.truth} -- and
 * never shows it. This build is not told it at all. For each object it carries
 * three outcomes, one per name on the card, and three bits saying whether that
 * name was the true one. So the phone can say what the survey did and can count
 * how many cards were right, and it cannot say what any object *is*. There is
 * no `truth` field in the file to leak, and no line of code in the build that
 * could print one.
 *
 * WHAT IS RESOLVED RATHER THAN PORTED. The report's three sentences depend on
 * nothing but how many cards were right. The desktop asks
 * {@link Inventory#headline(int, int)} and its two siblings every frame; this
 * emits all nine answers of each, indexed by the count, so the phone contains
 * no sentence-building code and cannot write a different report than the
 * desktop does. Same rule as Outside's ending and Ledger's per-detail lines,
 * applied to a report that is three sentences long.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.inventory.WebInventory [out.html]
 */
public final class WebInventory {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "inventory", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/inventory.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Inventory.of().things.size() + " objects)");
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
        Inventory inv = Inventory.of();
        int n = inv.things.size();
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("\"wordmark\":").append(str(Inventory.WORDMARK)).append(",\n");
        b.append("\"whereOpen\":").append(str(Inventory.WHERE_OPEN)).append(",\n");
        b.append("\"whereReport\":").append(str(Inventory.WHERE_REPORT)).append(",\n");
        b.append("\"count\":").append(n).append(",\n");

        // ---- the voice
        b.append("\"voice\":{\n");
        b.append("  \"opening\":").append(strList(Inventory.OPENING)).append(",\n");
        b.append("  \"rulesHeading\":").append(str(Inventory.RULES_HEADING)).append(",\n");
        b.append("  \"rules\":[");
        for (int i = 0; i < Inventory.RULES.size(); i++) {
            String[] r = Inventory.RULES.get(i);
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(r[0])).append(",\"text\":").append(str(r[1])).append('}');
        }
        b.append("],\n");
        b.append("  \"inFront\":").append(str(Inventory.IN_FRONT)).append(",\n");
        b.append("  \"theNote\":").append(str(Inventory.THE_NOTE)).append(",\n");
        b.append("  \"whatDoYouWrite\":").append(str(Inventory.WHAT_DO_YOU_WRITE)).append(",\n");
        b.append("  \"theCatalogue\":").append(str(Inventory.THE_CATALOGUE)).append(",\n");
        b.append("  \"nothingWritten\":").append(str(Inventory.NOTHING_WRITTEN)).append(",\n");
        b.append("  \"alsoReadAs\":").append(str(Inventory.ALSO_READ_AS)).append(",\n");
        b.append("  \"whatYouWrote\":").append(str(Inventory.WHAT_YOU_WROTE)).append(",\n");
        b.append("  \"whatTheSurveyDid\":").append(str(Inventory.WHAT_THE_SURVEY_DID)).append(",\n");
        b.append("  \"nothingOnBench\":").append(str(Inventory.NOTHING_ON_BENCH)).append(",\n");
        b.append("  \"startLine\":").append(str(Inventory.START_LINE)).append(",\n");
        b.append("  \"startButton\":").append(str(Inventory.START_BUTTON)).append(",\n");
        b.append("  \"noCard\":").append(str(Inventory.NO_CARD)).append(",\n");

        // The lines that depend on a number, emitted once per number rather
        // than written into the build by hand. Index 0 is a blank so the array
        // can be addressed by the number itself.
        b.append("  \"cardWhere\":[");
        for (int i = 0; i <= n; i++) {
            if (i > 0) b.append(',');
            b.append(str(i == 0 ? "" : Inventory.cardWhere(i, n)));
        }
        b.append("],\n");
        b.append("  \"writtenOf\":[");
        for (int i = 0; i <= n; i++) {
            if (i > 0) b.append(',');
            b.append(str(i == 0 ? "" : Inventory.writtenOf(i, n)));
        }
        b.append("]\n},\n");

        // ---- the six words a card can take
        b.append("\"uses\":[");
        for (int i = 0; i < Inventory.Use.values().length; i++) {
            Inventory.Use u = Inventory.Use.values()[i];
            if (i > 0) b.append(',');
            b.append("{\"id\":").append(str(u.name()))
             .append(",\"label\":").append(str(u.label))
             .append(",\"does\":").append(str(u.does)).append('}');
        }
        b.append("],\n");

        // ---- the workshop
        //
        // No `truth` field, and that is deliberate: see the class comment. What
        // the phone gets is, per object, the three names on the card, what the
        // survey did with each of them, and whether each one was true.
        b.append("\"things\":[\n");
        for (int i = 0; i < n; i++) {
            Inventory.Thing t = inv.things.get(i);
            b.append("  {\"number\":").append(t.number)
             .append(",\"form\":").append(str(t.form))
             .append(",\"note\":").append(str(t.note == null ? "" : t.note))
             .append(",\"offered\":[");
            for (int s = 0; s < t.offered.length; s++) {
                if (s > 0) b.append(',');
                b.append(str(t.offered[s].name()));
            }
            b.append("],\"outcomes\":[");
            for (int s = 0; s < t.offered.length; s++) {
                if (s > 0) b.append(',');
                b.append(str(Inventory.outcome(i, t.offered[s])));
            }
            b.append("],\"said\":[");
            for (int s = 0; s < t.offered.length; s++) {
                if (s > 0) b.append(',');
                b.append(t.offered[s] == t.truth ? 1 : 0);
            }
            b.append("]}");
            if (i < n - 1) b.append(',');
            b.append('\n');
        }
        b.append("],\n");

        // ---- the report, resolved rather than decided
        b.append("\"headline\":").append(indexed(n, r -> Inventory.headline(r, n))).append(",\n");
        b.append("\"verdict\":").append(indexed(n, r -> Inventory.verdict(r, n))).append(",\n");
        b.append("\"closing\":").append(indexed(n, r -> Inventory.closing(n - r, n)));
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

    private WebInventory() {}
}
