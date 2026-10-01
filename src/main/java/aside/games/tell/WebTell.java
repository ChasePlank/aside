package aside.games.tell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generate the single-file phone build of tell.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/tell/web.html) holds the layout, the styles, and the
 * house. The content -- the five nights' constants, every fixed sentence, the
 * direction names, and the number words -- is generated from {@link Tell}, the
 * same class the engine screen reads. There is no second copy of the prose.
 *
 * WHAT IS RESOLVED RATHER THAN PORTED. Every line that varies is a template
 * with a number in it, or a fixed sentence, and both come from {@link Tell}:
 * the closing, the caught line, the read line, the night label, the distance
 * line, the turn line and the covers line. The phone substitutes a number into
 * a template and never composes a sentence, so it cannot say something the
 * desktop would not say at the same state. That is the rule lesson's belief
 * table was resolved by, and it matters here for the same reason: the read and
 * the expectation are the two things the player is reading *for information*,
 * and a phone that phrased them differently would be a different game.
 *
 * WHAT IS PORTED, AND WHY IT HAS TO BE. The house is not a table. A night is a
 * size, a pair of opposite corners, a set of lit rooms and a growing count of
 * departures, and the player moves through it -- so the phone has to deal it
 * and step it itself. It can only do that because the draw is counter-based
 * rather than a stream: every number is a pure function of (seed, salt), so
 * there is no RNG position to carry and a seed replays a house exactly in both
 * builds. The arithmetic is BigInt in JavaScript because a double loses the
 * low bits of a 64-bit Java long.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.tell.WebTell [out.html]
 */
public final class WebTell {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "tell", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/tell.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Tell.NIGHTS + " nights, " + Tell.DIRS + " directions)");
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
        b.append("\"wordmark\":").append(str(Tell.WORDMARK)).append(",\n");
        b.append("\"where\":").append(str(Tell.WHERE)).append(",\n");
        b.append("\"whereReport\":").append(str(Tell.WHERE_REPORT)).append(",\n");
        b.append("\"nights\":").append(Tell.NIGHTS).append(",\n");
        b.append("\"readMax\":").append(Tell.READ_MAX).append(",\n");
        b.append("\"dirs\":").append(Tell.DIRS).append(",\n");

        b.append("\"dx\":").append(ints(Tell.DX)).append(",\n");
        b.append("\"dy\":").append(ints(Tell.DY)).append(",\n");
        b.append("\"dirName\":").append(strings(Tell.DIR_NAME)).append(",\n");
        b.append("\"dirKey\":").append(strings(Tell.DIR_KEY)).append(",\n");

        b.append("\"size\":").append(ints(Tell.SIZE)).append(",\n");
        b.append("\"reach\":").append(ints(Tell.REACH)).append(",\n");
        b.append("\"base\":").append(ints(Tell.BASE)).append(",\n");
        b.append("\"miss\":").append(ints(Tell.MISS)).append(",\n");
        b.append("\"hit\":").append(ints(Tell.HIT)).append(",\n");
        b.append("\"lit\":").append(ints(Tell.LIT)).append(",\n");
        b.append("\"lamps\":").append(ints(Tell.LAMPS)).append(",\n");
        b.append("\"limit\":").append(ints(Tell.LIMIT)).append(",\n");

        b.append("\"nightNames\":").append(strings(Tell.NIGHT_NAMES)).append(",\n");
        b.append("\"words\":").append(strings(Tell.WORDS)).append(",\n");

        // The templates. The phone substitutes a number into these and nothing
        // else, which is the only substitution that cannot change a sentence.
        b.append("\"nightLabel\":").append(str(Tell.NIGHT_LABEL)).append(",\n");
        b.append("\"awayOne\":").append(str(Tell.AWAY_ONE)).append(",\n");
        b.append("\"awayMany\":").append(str(Tell.AWAY_MANY)).append(",\n");
        b.append("\"turnLabel\":").append(str(Tell.TURN_LABEL)).append(",\n");
        b.append("\"coversOne\":").append(str(Tell.COVERS_ONE)).append(",\n");
        b.append("\"coversMany\":").append(str(Tell.COVERS_MANY)).append(",\n");
        b.append("\"wonHead\":").append(str(Tell.WON_HEAD)).append(",\n");
        b.append("\"tookLine\":").append(str(Tell.TOOK_LINE)).append(",\n");
        b.append("\"readOf\":").append(str(Tell.READ_OF)).append(",\n");

        b.append("\"caughtFar\":").append(str(Tell.CAUGHT_FAR)).append(",\n");
        b.append("\"caughtNear\":").append(str(Tell.CAUGHT_NEAR)).append(",\n");
        b.append("\"caughtClose\":").append(str(Tell.CAUGHT_CLOSE)).append(",\n");
        b.append("\"closingLost\":").append(str(Tell.CLOSING_LOST)).append(",\n");
        b.append("\"closingLow\":").append(str(Tell.CLOSING_LOW)).append(",\n");
        b.append("\"closingMid\":").append(str(Tell.CLOSING_MID)).append(",\n");
        b.append("\"closingHigh\":").append(str(Tell.CLOSING_HIGH)).append(",\n");
        b.append("\"readBarely\":").append(str(Tell.READ_BARELY)).append(",\n");
        b.append("\"readShape\":").append(str(Tell.READ_SHAPE)).append(",\n");
        b.append("\"readWays\":").append(str(Tell.READ_WAYS)).append(",\n");
        b.append("\"readKnew\":").append(str(Tell.READ_KNEW)).append(",\n");

        b.append("\"opening\":").append(strings(Tell.OPENING)).append(",\n");
        b.append("\"rulesHeading\":").append(str(Tell.RULES_HEADING)).append(",\n");
        b.append("\"rules\":[");
        for (int i = 0; i < Tell.RULES.size(); i++) {
            String[] r = Tell.RULES.get(i);
            if (i > 0) b.append(',');
            b.append('[').append(str(r[0])).append(',').append(str(r[1])).append(']');
        }
        b.append("],\n");

        b.append("\"expectHead\":").append(str(Tell.EXPECT_HEAD)).append(",\n");
        b.append("\"expectNone\":").append(str(Tell.EXPECT_NONE)).append(",\n");
        b.append("\"countHead\":").append(str(Tell.COUNT_HEAD)).append(",\n");
        b.append("\"readHead\":").append(str(Tell.READ_HEAD)).append(",\n");
        b.append("\"doorHead\":").append(str(Tell.DOOR_HEAD)).append(",\n");
        b.append("\"startLine\":").append(str(Tell.START_LINE)).append(",\n");
        b.append("\"startButton\":").append(str(Tell.START_BUTTON)).append(",\n");
        b.append("\"again\":").append(str(Tell.AGAIN)).append(",\n");
        b.append("\"nextNight\":").append(str(Tell.NEXT_NIGHT)).append(",\n");
        b.append("\"noSuchDoor\":").append(str(Tell.NO_SUCH_DOOR)).append(",\n");
        b.append("\"reportHead\":").append(str(Tell.REPORT_HEAD)).append(",\n");
        b.append("\"caughtHead\":").append(str(Tell.CAUGHT_HEAD)).append("\n");
        b.append("}\n");
        return b.toString();
    }

    static String ints(int[] xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) b.append(',');
            b.append(xs[i]);
        }
        return b.append(']').toString();
    }

    static String strings(String[] xs) { return strings(List.of(xs)); }

    static String strings(List<String> xs) {
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

    private WebTell() { }
}
