package aside.games.handoff;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generate the single-file phone build of handoff.
 *
 * The sixth of mine to get one -- vigil, residue, ledger, testimony and
 * outside came first -- and the one where the previous five's idea is taken
 * as far as it goes.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/handoff/web.html) holds the layout and the state
 * machine. The content -- the ten signals, Bell's four, the five watches,
 * the five lines you can earn, the eight things an action does on the
 * successor's night, the four ways a concern can go unanswered, and every
 * fixed line the game says -- is generated from {@link Handoff}, the same
 * class the engine screen reads. The prose was moved out of
 * {@link HandoffScreen} into the model to make that true.
 *
 * WHAT IS NOT WRITTEN TWICE AT ALL, WHICH IS THE POINT OF THIS FILE.
 * Outside resolved its *ending* into a table: 176 score lines and a 638-entry
 * map saying which closing belongs to each combination, so the phone build
 * contains no sentence-building code. Handoff resolves the whole
 * *simulation*.
 *
 * The successor's night is the entire game -- he reads the orders in the
 * order you wrote them, takes the first line that applies to each part of
 * the station, and does it, with no judgment and no asking what you meant.
 * That rule is about twenty-five lines of Java. Rather than write it a second
 * time in JavaScript, every set of orders a player can possibly leave is
 * played out here, at build time, and written down: nine lines, three slots,
 * ordered, so 9 x 8 x 7 = 504 outcomes. The phone build therefore contains no
 * rules for the night at all. It looks the answer up.
 *
 * That is a stronger claim than Outside's, and it is checkable in a stronger
 * way. Outside's tables were checked entry by entry because a table can be
 * indexed along the wrong axis and every entry can still be a real sentence.
 * Here the same check is *complete*: {@link SelfTest} walks all 504 triples,
 * parses the build's table back out, and compares it element by element
 * against {@link Handoff#succeed}. There is no set of orders the phone can be
 * wrong about, because there is no set of orders the phone computes.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.handoff.WebHandoff [out.html]
 */
public final class WebHandoff {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "handoff", "web.html");

    /**
     * Every line that exists, in one fixed order, Bell's first.
     *
     * The table below is indexed by three positions in this list, so the order
     * is part of the file format and must not be sorted or reordered casually.
     * The phone build carries the same list and reads it the same way.
     */
    static final List<Handoff.Order> LINES = Handoff.allOrders();

    /** How many lines there are: nine. Three slots, ordered, is 504. */
    static final int N = LINES.size();

    /** The stride of the flat table: one row per value of the other two axes. */
    static final int STRIDE = N * N;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/handoff.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + table().size() + " nights resolved)");
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
        b.append("\"ordersHeld\":").append(Handoff.ORDERS_HELD).append(",\n");
        b.append("\"wordmark\":").append(str(Handoff.WORDMARK)).append(",\n");
        b.append("\"subtitle\":").append(str(Handoff.SUBTITLE)).append(",\n");

        // ---- the voice
        b.append("\"voice\":{\n");
        b.append("  \"whatYouCanSee\":").append(str(Handoff.WHAT_YOU_CAN_SEE)).append(",\n");
        b.append("  \"whatYouDo\":").append(str(Handoff.WHAT_YOU_DO)).append(",\n");
        b.append("  \"theOrders\":").append(str(Handoff.THE_ORDERS)).append(",\n");
        b.append("  \"keptBefore\":").append(str(Handoff.KEPT_BEFORE)).append(",\n");
        b.append("  \"appliesTonight\":").append(str(Handoff.APPLIES_TONIGHT)).append(",\n");
        b.append("  \"youLearned\":").append(str(Handoff.YOU_LEARNED)).append(",\n");
        b.append("  \"learnedNothingFollow\":")
         .append(str(Handoff.LEARNED_NOTHING_FOLLOW)).append(",\n");
        b.append("  \"learnedNothingFollow2\":")
         .append(str(Handoff.LEARNED_NOTHING_FOLLOW_2)).append(",\n");
        b.append("  \"learnedNothingHold\":")
         .append(str(Handoff.LEARNED_NOTHING_HOLD)).append(",\n");
        b.append("  \"writingHeading\":").append(str(Handoff.WRITING_HEADING)).append(",\n");
        b.append("  \"writingIntro\":").append(str(Handoff.WRITING_INTRO)).append(",\n");
        b.append("  \"bellsFour\":").append(str(Handoff.BELLS_FOUR)).append(",\n");
        b.append("  \"whatYouLearned\":").append(str(Handoff.WHAT_YOU_LEARNED)).append(",\n");
        b.append("  \"whatYouLeave\":").append(str(Handoff.WHAT_YOU_LEAVE)).append(",\n");
        b.append("  \"written\":").append(str(Handoff.WRITTEN)).append(",\n");
        b.append("  \"learnedNothingEmpty\":")
         .append(str(Handoff.LEARNED_NOTHING_EMPTY)).append(",\n");
        b.append("  \"whatHeCouldSee\":").append(str(Handoff.WHAT_HE_COULD_SEE)).append(",\n");
        b.append("  \"whatHeDid\":").append(str(Handoff.WHAT_HE_DID)).append(",\n");
        b.append("  \"becauseYouWrote\":").append(str(Handoff.BECAUSE_YOU_WROTE)).append(",\n");
        b.append("  \"whatYouLeftHim\":").append(str(Handoff.WHAT_YOU_LEFT_HIM)).append(",\n");
        b.append("  \"readInThisOrder\":").append(str(Handoff.READ_IN_THIS_ORDER)).append(",\n");
        b.append("  \"theOrdersYouLeft\":").append(str(Handoff.THE_ORDERS_YOU_LEFT)).append(",\n");
        b.append("  \"standingOrder\":").append(str(Handoff.STANDING_ORDER)).append(",\n");
        b.append("  \"verdict\":").append(str(Handoff.VERDICT)).append(",\n");

        // The tags and the slot labels depend on a number, so they are emitted
        // once per number rather than written into the build by hand.
        b.append("  \"tags\":{")
         .append("\"FOLLOW\":").append(str(Handoff.TAG_AS_WRITTEN)).append(',')
         .append("\"JUDGE\":").append(str(Handoff.TAG_AS_NEEDED)).append(',')
         .append("\"HOLD\":").append(str(Handoff.TAG_NOTHING)).append("},\n");
        b.append("  \"lineLabel\":[");
        for (int i = 0; i <= Handoff.ORDERS_HELD; i++) {
            if (i > 0) b.append(',');
            b.append(str(i == 0 ? "" : Handoff.lineLabel(i)));
        }
        b.append("]\n}");
        b.append(",\n");

        // ---- signals
        b.append("\"signals\":{");
        boolean first = true;
        for (String id : signalIds()) {
            if (!first) b.append(',');
            first = false;
            b.append(str(id)).append(':').append(str(Handoff.signalText(id)));
        }
        b.append("},\n");

        // ---- every line that exists
        b.append("\"lineIds\":[");
        for (int i = 0; i < N; i++) {
            if (i > 0) b.append(',');
            b.append(str(LINES.get(i).id));
        }
        b.append("],\n");

        b.append("\"orders\":[\n");
        for (int i = 0; i < N; i++) {
            Handoff.Order o = LINES.get(i);
            b.append("  {\"id\":").append(str(o.id))
             .append(",\"text\":").append(str(o.text))
             .append(",\"requires\":").append(strSet(o.requires))
             .append(",\"action\":").append(str(o.action))
             .append(",\"concern\":").append(str(o.concern.name()))
             .append(",\"bell\":").append(o.bell)
             .append(",\"origin\":").append(str(o.origin)).append('}');
            if (i < N - 1) b.append(',');
            b.append('\n');
        }
        b.append("],\n");

        // ---- the five watches
        b.append("\"watches\":[\n");
        for (int i = 0; i < Handoff.WATCHES.size(); i++) {
            Handoff.Watch w = Handoff.WATCHES.get(i);
            b.append("  {\"heading\":").append(str(w.heading))
             .append(",\"title\":").append(str(w.title))
             .append(",\"scene\":").append(str(w.scene))
             .append(",\"signals\":").append(strList(w.signals))
             .append(",\"options\":[");
            for (int j = 0; j < w.options.size(); j++) {
                Handoff.Option o = w.options.get(j);
                if (j > 0) b.append(',');
                b.append("{\"text\":").append(str(o.text))
                 .append(",\"kind\":").append(str(o.kind.name()))
                 .append(",\"outcome\":").append(str(o.outcome))
                 .append(",\"unlock\":").append(o.unlock == null ? "null" : str(o.unlock.id))
                 .append('}');
            }
            b.append("]}");
            if (i < Handoff.WATCHES.size() - 1) b.append(',');
            b.append('\n');
        }
        b.append("],\n");

        // ---- the night after you
        b.append("\"succession\":{\"heading\":").append(str(Handoff.SUCCESSION_HEADING))
         .append(",\"title\":").append(str(Handoff.SUCCESSION_TITLE))
         .append(",\"scene\":").append(str(Handoff.SUCCESSION_SCENE))
         .append(",\"signals\":").append(strList(Handoff.SUCCESSION_SIGNALS))
         .append("},\n");

        // What each action does on his night. The phone never decides which
        // action happens -- the table below says -- but it does need the words.
        b.append("\"acts\":{");
        List<String> actionKeys = actionKeys();
        for (int i = 0; i < actionKeys.size(); i++) {
            if (i > 0) b.append(',');
            String key = actionKeys.get(i);
            b.append(str(key)).append(":{\"text\":").append(str(actText(key)))
             .append(",\"good\":").append(actGood(key)).append('}');
        }
        b.append("},\n");

        b.append("\"unmet\":{");
        for (int i = 0; i < Handoff.Concern.values().length; i++) {
            Handoff.Concern c = Handoff.Concern.values()[i];
            if (i > 0) b.append(',');
            b.append(str(c.name())).append(':').append(str(unmetText(c)));
        }
        b.append("},\n");

        // ---- the end, resolved rather than decided
        List<String> closings = distinctClosings();
        b.append("\"closings\":").append(strList(closings)).append(",\n");

        // ---- the whole simulation, resolved rather than ported
        b.append("\"table\":[\n");
        List<String> table = table();
        for (int i = 0; i < table.size(); i++) {
            b.append("  ").append(str(table.get(i)));
            if (i < table.size() - 1) b.append(',');
            b.append('\n');
        }
        b.append("]\n");

        b.append("}\n");
        return b.toString();
    }

    // --------------------------------------------------------------- the night

    /**
     * Every set of orders a player can leave, played out.
     *
     * The index is {@code i*81 + j*9 + k} for the three positions in
     * {@link #LINES}, so the entry for a set of orders is a lookup and not a
     * computation. Entries for repeated lines are written too -- they are
     * unreachable, since you cannot write the same line twice, and leaving
     * them in keeps the index arithmetic honest instead of conditional.
     *
     * The format is {@code good|closing|raised|events}, where each event is
     * {@code CONCERN,ID} for a line that fired and {@code CONCERN,-} for a
     * part of the station nothing answered.
     */
    public static List<String> table() {
        List<String> closings = distinctClosings();
        List<String> out = new ArrayList<>(N * N * N);
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                for (int k = 0; k < N; k++) {
                    out.add(entry(LINES.get(i), LINES.get(j), LINES.get(k), closings));
                }
            }
        }
        return out;
    }

    /** One night, in the table's own format. */
    static String entry(Handoff.Order a, Handoff.Order b, Handoff.Order c,
                        List<String> closings) {
        Handoff.Succession s = Handoff.succeed(List.of(a, b, c));
        StringBuilder e = new StringBuilder();
        e.append(s.good).append('|')
         .append(closings.indexOf(s.closing)).append('|')
         .append(s.raised).append('|');
        for (int i = 0; i < s.events.size(); i++) {
            Handoff.Event ev = s.events.get(i);
            if (i > 0) e.append(';');
            e.append(ev.concern.name()).append(',')
             .append(ev.by == null ? "-" : ev.by.id);
        }
        return e.toString();
    }

    /**
     * The distinct closing sentences, in the order the model produces them.
     *
     * Five of them, found by asking rather than by being listed here -- so a
     * sixth branch added to {@link Handoff#succeed} shows up in the build with
     * no change in this file. The order is the order they are first reached
     * walking the table, which is stable and meaningless, which is what an
     * index into a generated table should be.
     */
    static List<String> distinctClosings() {
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                for (int k = 0; k < N; k++) {
                    seen.add(Handoff.succeed(List.of(LINES.get(i), LINES.get(j),
                            LINES.get(k))).closing);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /** Every action any line can name, in a fixed order. */
    static List<String> actionKeys() {
        Set<String> seen = new LinkedHashSet<>();
        for (Handoff.Order o : LINES) seen.add(o.action);
        return new ArrayList<>(seen);
    }

    static String actText(String action) {
        return Handoff.actText(action);
    }

    static boolean actGood(String action) {
        return Handoff.actGood(action);
    }

    static String unmetText(Handoff.Concern c) {
        return Handoff.unmetText(c);
    }

    /** The ten signals, in the order the model declares them. */
    static List<String> signalIds() {
        return new ArrayList<>(Handoff.signalIds());
    }

    // ------------------------------------------------------------- plumbing

    static String strList(List<String> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) b.append(',');
            b.append(str(xs.get(i)));
        }
        return b.append(']').toString();
    }

    static String strSet(Set<String> xs) {
        // Sorted, because these come out of Set.of() and Set.of() has no
        // iteration order -- it is salted per JVM, so an unsorted set makes
        // the generated file differ between two runs of the same generator.
        // The game never reads the order (a line applies when every signal it
        // names is present), so sorting costs nothing and buys determinism.
        List<String> sorted = new ArrayList<>(xs);
        java.util.Collections.sort(sorted);
        return strList(sorted);
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

    private WebHandoff() {}
}
