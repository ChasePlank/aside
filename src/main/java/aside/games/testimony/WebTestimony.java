package aside.games.testimony;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generate the single-file phone build of testimony.
 *
 * The fourth of the eight to get one, after Vigil, Residue and Ledger, and the
 * first one whose whole shape is a conversation rather than a room or a
 * ledger. What that changed: the other three are state machines you look at,
 * and this one asks you questions and then reads your answers back, so the
 * phone build has to carry the interview itself and not just a rendering of it.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/testimony/web.html) holds the layout and the rules. The
 * content -- the evening, the eight questions, their options and phrases, the
 * account lines, the four closings, the tally sentences and every heading the
 * game says in its own voice -- is generated from Testimony.java, the same
 * class the engine screen reads. That last part is new work: the headings and
 * the closing sentences used to be string literals inside TestimonyScreen, and
 * they were moved into the model on this fire so that the screen and the phone
 * build read the same constants. A sentence written in a screen is a sentence
 * the other build does not have.
 *
 * WHAT IS WRITTEN TWICE. The interview's arithmetic: eight questions in a
 * fixed order, an answer written into memory whether or not it was true,
 * nothing anywhere recording which, propagation into the phrasing of later
 * questions, and the four-way choice of closing. That is about sixty lines of
 * JavaScript. It is checked rather than trusted -- aside.games.testimony.
 * SelfTest regenerates this file and fails if the checked-in copy has gone
 * stale, and drives the same playthroughs through both builds and compares
 * what they say. A generated file that has gone stale is worse than no file:
 * it is a second copy of the game quietly disagreeing with the first.
 *
 * THE SAVE FORMAT IS THE SAME ONE. Testimony.save() writes qid|option|sure,
 * one line per answer, and Testimony.load() reads exactly that; the phone build
 * writes and reads that format into localStorage, so an account can be carried
 * between the desktop and the phone by copying eight lines of text.
 *
 * WHAT THE PORT FOUND. Question.note() is written for all eight questions --
 * eight paragraphs, and some of the best writing in the file -- and nothing
 * has ever drawn it. Not the screen, not the verdict, not the phone build.
 * It is left undrawn here rather than drawn on one build only, because a note
 * that appears on the phone and not on the desktop is a divergence dressed up
 * as a feature. The verdict is the place it belongs, and the desktop verdict
 * has about 190px of canvas left after the closing, so drawing it there is a
 * layout change and not a one-line change. Recorded, not fixed.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.testimony.WebTestimony [out.html]
 */
public final class WebTestimony {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "testimony", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/testimony.html");
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
        return (t.substring(0, at) + content() + t.substring(at + MARKER.length()))
                .replace(aside.game.WebAudio.MARKER, aside.game.WebAudio.js());
    }

    // ------------------------------------------------------------- content

    static String content() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");

        b.append("\"sceneTitle\":").append(str(Testimony.SCENE_TITLE)).append(",\n");
        b.append("\"scene\":").append(str(Testimony.SCENE)).append(",\n");
        b.append("\"sceneNote\":").append(str(Testimony.SCENE_NOTE)).append(",\n");

        b.append("\"memoryHead\":").append(str(Testimony.MEMORY_HEAD)).append(",\n");
        b.append("\"memoryEmpty\":").append(str(Testimony.MEMORY_EMPTY)).append(",\n");
        b.append("\"confidenceHead\":").append(str(Testimony.CONFIDENCE_HEAD)).append(",\n");
        b.append("\"confidenceNote\":").append(str(Testimony.CONFIDENCE_NOTE)).append(",\n");
        b.append("\"accountHead\":").append(str(Testimony.ACCOUNT_HEAD)).append(",\n");
        b.append("\"accountNote\":").append(str(Testimony.ACCOUNT_NOTE)).append(",\n");
        b.append("\"verdictHead\":").append(str(Testimony.VERDICT_HEAD)).append(",\n");
        b.append("\"verdictSaid\":").append(str(Testimony.VERDICT_SAID)).append(",\n");
        b.append("\"verdictHappened\":").append(str(Testimony.VERDICT_HAPPENED)).append(",\n");
        b.append("\"notesHead\":").append(str(Testimony.NOTES_HEAD)).append(",\n");
        b.append("\"notesLead\":").append(str(Testimony.NOTES_LEAD)).append(",\n");

        // The closings ship with their placeholders intact. The phone build
        // fills them, because it is the one that knows the counts; what it must
        // not do is own the sentences.
        b.append("\"closing\":{")
         .append("\"nothing\":").append(str(Testimony.CLOSING_NOTHING)).append(',')
         .append("\"perfect\":").append(str(Testimony.CLOSING_PERFECT)).append(',')
         .append("\"honest\":").append(str(Testimony.CLOSING_HONEST)).append(',')
         .append("\"certain\":").append(str(Testimony.CLOSING_CERTAIN))
         .append("},\n");

        b.append("\"verdictTrue\":").append(str(Testimony.VERDICT_TRUE)).append(",\n");
        b.append("\"verdictTally\":").append(str(Testimony.VERDICT_TALLY)).append(",\n");

        b.append("\"questions\":[\n");
        for (int i = 0; i < Testimony.QUESTIONS.size(); i++) {
            Testimony.Question q = Testimony.QUESTIONS.get(i);
            b.append("  {\"id\":").append(str(q.id()))
             .append(",\"prompt\":").append(str(q.prompt()))
             .append(",\"truth\":").append(str(q.truth()))
             .append(",\"note\":").append(str(q.note()))
             .append(",\"accountLine\":").append(str(q.accountLine()))
             .append(",\"options\":[");
            for (int o = 0; o < q.options().size(); o++) {
                if (o > 0) b.append(',');
                Testimony.Option op = q.options().get(o);
                b.append("{\"id\":").append(str(op.id()))
                 .append(",\"text\":").append(str(op.text()))
                 .append(",\"phrase\":").append(str(op.phrase())).append('}');
            }
            b.append("]}");
            if (i < Testimony.QUESTIONS.size() - 1) b.append(',');
            b.append('\n');
        }
        b.append("]\n");

        b.append("}\n");
        return b.toString();
    }

    /**
     * JSON string escaping, and the one thing that matters here: the prose is
     * full of apostrophes and em dashes, and it is embedded in a script tag, so
     * a literal &lt;/script&gt; in a sentence would end the block. Nothing
     * writes one today, and the escape is here so that nothing can.
     */
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

    private WebTestimony() {}
}
