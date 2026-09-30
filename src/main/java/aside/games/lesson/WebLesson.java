package aside.games.lesson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate the single-file phone build of lesson.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/lesson/web.html) holds the layout, the styles, and the
 * deal. The content -- the pools, the thresholds, every fixed sentence, and the
 * student's whole vocabulary -- is generated from {@link Lesson}, the same
 * class the engine screen reads. There is no second copy of the prose.
 *
 * WHAT IS RESOLVED RATHER THAN PORTED. The student's belief is a function of
 * the set of rules still standing, and there are 256 such sets, so all 256
 * sentences go out as a table indexed by the bitmask. The phone therefore
 * contains no sentence-building code at all and cannot say something the
 * desktop would not say at the same standing set. That is the rule Interval's
 * report was resolved by, and it matters more here, because the belief is the
 * one line in the game the player is reading for information.
 *
 * WHAT IS PORTED, AND WHY IT HAS TO BE. The deal is not a table. A board is
 * twelve states drawn from four pools and a shift is three more, and the player
 * chooses from the board, so the phone has to deal it itself -- and it can only
 * do that because the draw is counter-based rather than a stream: every number
 * is a pure function of (seed, salt), so there is no RNG position to carry and a
 * seed replays a handover exactly in both builds. The arithmetic is BigInt in
 * JavaScript because a double loses the low bits of a 64-bit Java long.
 * tools/lesson-trace.mjs drives both deals over 200 seeds and compares every
 * state, every corner, and the standing set after the same three nights.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.lesson.WebLesson [out.html]
 */
public final class WebLesson {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "lesson", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/lesson.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Lesson.BOARD + " states on the board, " + Lesson.SHOWS + " nights, "
                + Lesson.SHIFT + " on the shift)");
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
        b.append("\"wordmark\":").append(str(Lesson.WORDMARK)).append(",\n");
        b.append("\"where\":").append(str(Lesson.WHERE)).append(",\n");
        b.append("\"whereBoard\":").append(str(Lesson.WHERE_BOARD)).append(",\n");
        b.append("\"whereTonight\":").append(str(Lesson.WHERE_TONIGHT)).append(",\n");
        b.append("\"whereReport\":").append(str(Lesson.WHERE_REPORT)).append(",\n");
        b.append("\"pressureUp\":").append(Lesson.PRESSURE_UP).append(",\n");
        b.append("\"heatUp\":").append(Lesson.HEAT_UP).append(",\n");
        b.append("\"board\":").append(Lesson.BOARD).append(",\n");
        b.append("\"shows\":").append(Lesson.SHOWS).append(",\n");
        b.append("\"shift\":").append(Lesson.SHIFT).append(",\n");

        b.append("\"boardCount\":[");
        for (int i = 0; i < Lesson.BOARD_COUNT.length; i++) {
            if (i > 0) b.append(',');
            b.append(Lesson.BOARD_COUNT[i]);
        }
        b.append("],\n");

        b.append("\"boardPool\":").append(pools(Lesson.BOARD_POOL)).append(",\n");
        b.append("\"shiftPool\":").append(pools(Lesson.SHIFT_POOL)).append(",\n");

        // The eight rules, as the clause that follows "you open it when". The
        // phone never composes a sentence out of these; it looks the whole
        // sentence up in the belief table below. They are here so the trace can
        // compare the two builds' rule spaces by name.
        b.append("\"rules\":[");
        for (int i = 0; i < Lesson.ORDER.size(); i++) {
            if (i > 0) b.append(',');
            b.append(str(Lesson.whenOf(Lesson.ORDER.get(i))));
        }
        b.append("],\n");

        // Every belief there is, indexed by the bitmask of the standing set.
        // 256 strings, and the reason the phone cannot disagree with the
        // desktop about what the student just said.
        b.append("\"belief\":[");
        for (int mask = 0; mask < 256; mask++) {
            if (mask > 0) b.append(',');
            b.append(str(Lesson.belief(Lesson.maskToSet(mask))));
        }
        b.append("],\n");

        b.append("\"confidence\":[");
        for (int n = 0; n <= 8; n++) {
            if (n > 0) b.append(',');
            b.append(str(Lesson.confidence(n)));
        }
        b.append("],\n");

        b.append("\"corner\":[");
        for (int i = 0; i < Lesson.Quadrant.values().length; i++) {
            if (i > 0) b.append(',');
            b.append(str(Lesson.cornerName(Lesson.Quadrant.values()[i])));
        }
        b.append("],\n");

        // The verdict and the closing, resolved the same way: one per corner
        // rather than a sentence built on the phone.
        b.append("\"verdict\":{\n");
        b.append("  \"certain\":").append(str(Lesson.verdict(true, true, List.of()))).append(",\n");
        b.append("  \"unsure\":").append(byCorner(q -> Lesson.verdict(true, false, List.of(q)))).append(",\n");
        b.append("  \"unsafe\":").append(byCorner(q -> Lesson.verdict(false, false, List.of(q)))).append("\n");
        b.append("},\n");
        b.append("\"closing\":{\n");
        b.append("  \"certain\":").append(str(Lesson.closing(true, true, false))).append(",\n");
        b.append("  \"certainRedundant\":").append(str(Lesson.closing(true, true, true))).append(",\n");
        b.append("  \"unsure\":").append(str(Lesson.closing(true, false, false))).append(",\n");
        b.append("  \"unsafe\":").append(str(Lesson.closing(false, false, false))).append("\n");
        b.append("},\n");

        b.append("\"voice\":{\n");
        b.append("  \"opening\":").append(strList(Lesson.OPENING)).append(",\n");
        b.append("  \"rulesHeading\":").append(str(Lesson.RULES_HEADING)).append(",\n");
        b.append("  \"rules\":[");
        for (int i = 0; i < Lesson.RULES.size(); i++) {
            String[] r = Lesson.RULES.get(i);
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(r[0])).append(",\"text\":").append(str(r[1])).append('}');
        }
        b.append("],\n");
        b.append("  \"boardHead\":").append(str(Lesson.BOARD_HEAD)).append(",\n");
        b.append("  \"tonightHead\":").append(str(Lesson.TONIGHT_HEAD)).append(",\n");
        b.append("  \"keeperHead\":").append(str(Lesson.KEEPER_HEAD)).append(",\n");
        b.append("  \"pressure\":").append(str(Lesson.PRESSURE)).append(",\n");
        b.append("  \"heat\":").append(str(Lesson.HEAT)).append(",\n");
        b.append("  \"nightsLeft\":").append(str(Lesson.NIGHTS_LEFT)).append(",\n");
        b.append("  \"night\":").append(str(Lesson.NIGHT)).append(",\n");
        b.append("  \"of\":").append(str(Lesson.OF)).append(",\n");
        b.append("  \"show\":").append(str(Lesson.SHOW)).append(",\n");
        b.append("  \"shown\":").append(str(Lesson.SHOWN)).append(",\n");
        b.append("  \"startButton\":").append(str(Lesson.START_BUTTON)).append(",\n");
        b.append("  \"again\":").append(str(Lesson.AGAIN)).append(",\n");
        b.append("  \"watching\":").append(str(Lesson.WATCHING)).append(",\n");
        b.append("  \"nothingNew\":").append(str(Lesson.NOTHING_NEW)).append(",\n");
        b.append("  \"handOver\":").append(str(Lesson.HAND_OVER_SHORT)).append(",\n");
        b.append("  \"handOverButton\":").append(str(Lesson.HAND_OVER_BUTTON)).append(",\n");
        b.append("  \"alreadyShown\":").append(str(Lesson.ALREADY_SHOWN)).append(",\n");
        b.append("  \"reportHead\":").append(str(Lesson.REPORT_HEAD)).append(",\n");
        b.append("  \"shiftHead\":").append(str(Lesson.SHIFT_HEAD)).append(",\n");
        b.append("  \"beliefHead\":").append(str(Lesson.BELIEF_HEAD)).append(",\n");
        b.append("  \"opened\":").append(str(Lesson.OPENED)).append(",\n");
        b.append("  \"shut\":").append(str(Lesson.SHUT)).append(",\n");
        b.append("  \"right\":").append(str(Lesson.RIGHT)).append(",\n");
        b.append("  \"wrong\":").append(str(Lesson.WRONG)).append(",\n");
        b.append("  \"shouldOpen\":").append(str(Lesson.SHOULD_OPEN)).append(",\n");
        b.append("  \"shouldShut\":").append(str(Lesson.SHOULD_SHUT)).append(",\n");
        b.append("  \"nothingShown\":").append(str(Lesson.NOTHING_SHOWN)).append("\n");
        b.append("}\n}\n");
        return b.toString();
    }

    static String pools(java.util.Map<Lesson.Quadrant, List<Lesson.Reading>> m) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < Lesson.Quadrant.values().length; i++) {
            if (i > 0) b.append(',');
            b.append('[');
            List<Lesson.Reading> pool = m.get(Lesson.Quadrant.values()[i]);
            for (int j = 0; j < pool.size(); j++) {
                if (j > 0) b.append(',');
                b.append('[').append(pool.get(j).pressure()).append(',')
                        .append(pool.get(j).heat()).append(']');
            }
            b.append(']');
        }
        return b.append(']').toString();
    }

    static String byCorner(java.util.function.Function<Lesson.Quadrant, String> f) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < Lesson.Quadrant.values().length; i++) {
            if (i > 0) b.append(',');
            b.append(str(f.apply(Lesson.Quadrant.values()[i])));
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

    private WebLesson() { }
}
