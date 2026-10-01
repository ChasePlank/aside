package aside.games.corroboration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generate the single-file phone build of corroboration.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/corroboration/web.html) holds the layout and the CSS.
 * Every fixed line the game says -- the premise, the headings, the labels, the
 * two hints, the five verdict words, the closing paragraphs -- is generated
 * from {@link Corroboration}, the same class the engine screen reads. The
 * prose was moved out of {@link CorroborationScreen} into the model to make
 * that true; the screen now only draws it.
 *
 * WHAT IS PORTED RATHER THAN RESOLVED. The night cannot be resolved into a
 * table, because what the player is doing is spending a budget of questions
 * against a set of answers that depend on the questions already spent. So the
 * phone runs the model, and it can only do that because the draw is
 * counter-based rather than a stream: every number is a pure function of
 * (seed, salt), so there is no RNG position to carry and a seed replays a
 * night identically in both builds. The arithmetic is BigInt because Java's
 * {@code long} is 64-bit and a JS number is not -- a double would lose the low
 * bits and the two builds would disagree about who is stuck on what.
 *
 * That is also what makes it checkable. {@code SelfTest} drives both models
 * through the same seeds and the same checks and compares the entry after
 * every question -- see tools/corroboration-trace.mjs. Two builds can both be
 * self-consistent and still disagree about what a night was.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.corroboration.WebCorroboration [out.html]
 */
public final class WebCorroboration {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "corroboration", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/corroboration.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Corroboration.N + " lines, " + Corroboration.BUDGET + " questions)");
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
        b.append("\"head\":").append(str(Corroboration.OPEN_HEAD)).append(",\n");
        b.append("\"sub\":").append(str(Corroboration.OPEN_SUB)).append(",\n");
        b.append("\"askHead\":").append(str(Corroboration.ASK_HEAD)).append(",\n");
        b.append("\"fileHead\":").append(str(Corroboration.FILE_HEAD)).append(",\n");
        b.append("\"endHead\":").append(str(Corroboration.END_HEAD)).append(",\n");
        b.append("\"fileLine\":").append(str(Corroboration.FILE_LINE)).append(",\n");
        b.append("\"open\":").append(strList(Corroboration.OPEN)).append(",\n");
        b.append("\"budget\":").append(Corroboration.BUDGET).append(",\n");
        b.append("\"valueCount\":").append(Corroboration.VALUES).append(",\n");
        b.append("\"blankMark\":").append(str(Corroboration.valueName(0, Corroboration.BLANK))).append(",\n");
        b.append("\"observers\":").append(strList(Corroboration.OBSERVERS)).append(",\n");

        b.append("\"axes\":[");
        for (int a = 0; a < Corroboration.N; a++) {
            if (a > 0) b.append(',');
            b.append(str(Corroboration.axisName(a)));
        }
        b.append("],\n");

        // The three values of each line, in ordinal order, so the build never
        // has to know what a line is called to draw one. Named `values`, not
        // `valueCount` -- the count is the one above, and emitting both under
        // one key is how the trace found the port reading a name table as a
        // number on its first run.
        b.append("\"values\":[");
        for (int a = 0; a < Corroboration.N; a++) {
            if (a > 0) b.append(',');
            b.append('[');
            for (int v = 0; v < Corroboration.VALUES; v++) {
                if (v > 0) b.append(',');
                b.append(str(Corroboration.valueName(a, v)));
            }
            b.append(']');
        }
        b.append("],\n");

        b.append("\"labelSays\":").append(str(Corroboration.LABEL_SAYS)).append(",\n");
        b.append("\"labelCheck\":").append(str(Corroboration.LABEL_CHECK)).append(",\n");
        b.append("\"labelWrote\":").append(str(Corroboration.LABEL_WROTE)).append(",\n");
        b.append("\"labelWas\":").append(str(Corroboration.LABEL_WAS)).append(",\n");
        b.append("\"checkNone\":").append(str(Corroboration.CHECK_NONE)).append(",\n");
        b.append("\"checkClean\":").append(str(Corroboration.CHECK_CLEAN)).append(",\n");
        b.append("\"checkCaught\":").append(str(Corroboration.CHECK_CAUGHT)).append(",\n");
        b.append("\"logHead\":").append(str(Corroboration.LOG_HEAD)).append(",\n");
        b.append("\"logOrder\":").append(str(Corroboration.LOG_ORDER)).append(",\n");
        b.append("\"askHint\":").append(str(Corroboration.ASK_HINT)).append(",\n");
        b.append("\"fileHint\":").append(str(Corroboration.FILE_HINT)).append(",\n");
        b.append("\"endHint\":").append(str(Corroboration.END_HINT)).append(",\n");
        b.append("\"noQuestions\":").append(str(Corroboration.NO_QUESTIONS)).append(",\n");
        b.append("\"askHintPhone\":").append(str(Corroboration.ASK_HINT_PHONE)).append(",\n");
        b.append("\"fileHintPhone\":").append(str(Corroboration.FILE_HINT_PHONE)).append(",\n");
        b.append("\"endHintPhone\":").append(str(Corroboration.END_HINT_PHONE)).append(",\n");

        // The count line, emitted once per number rather than written into the
        // build by hand -- the singular is the one that gets forgotten.
        b.append("\"questionsLeftOne\":").append(str(Corroboration.questionsLeft(1))).append(",\n");
        b.append("\"questionsLeftMany\":")
         .append(str(Corroboration.questionsLeft(2).replace("2", "%s"))).append(",\n");

        b.append("\"verdict\":{");
        Corroboration.Verdict[] vs = Corroboration.Verdict.values();
        for (int i = 0; i < vs.length; i++) {
            if (i > 0) b.append(',');
            b.append(str(vs[i].name())).append(':').append(str(Corroboration.verdictWord(vs[i])));
        }
        b.append("},\n");

        b.append("\"closing\":{")
         .append("\"empty\":").append(str(Corroboration.CLOSING_EMPTY)).append(',')
         .append("\"best\":").append(str(Corroboration.CLOSING_BEST))
         .append("},\n");

        b.append("\"btn\":{")
         .append("\"begin\":").append(str(Corroboration.BTN_BEGIN)).append(',')
         .append("\"file\":").append(str(Corroboration.BTN_FILE)).append(',')
         .append("\"back\":").append(str(Corroboration.BTN_BACK)).append(',')
         .append("\"end\":").append(str(Corroboration.BTN_END)).append(',')
         .append("\"again\":").append(str(Corroboration.BTN_AGAIN)).append(',')
         .append("\"library\":").append(str(Corroboration.BTN_LIBRARY))
         .append("}\n");

        b.append("}\n");
        return b.toString();
    }

    // ------------------------------------------------------------- helpers

    static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.append('"').toString();
    }

    static String strList(String[] xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) b.append(',');
            b.append(str(xs[i]));
        }
        return b.append(']').toString();
    }

    private WebCorroboration() { }
}
