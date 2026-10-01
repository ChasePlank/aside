package aside.games.fnaf9;

import aside.games.fnaf9.engine.Feed;
import aside.games.fnaf9.engine.Pair;
import aside.games.fnaf9.engine.Walker;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generate the single-file phone build of the FNAF 9 office.
 *
 * <p><b>This is the ninth game in the franchise and the second one on the
 * phone shelf.</b> FNAF 1 through 5, 7 and 8 are desktop-only and the shelf
 * says so under its own heading. The reason FNAF 9 can afford a build is the
 * same reason FNAF 6 could and the others could not: <b>there is no art to
 * carry.</b> The office draws everything -- the corridor, the walker, the
 * static, the face at the end -- and the drawing is the game rather than a
 * shortcut, because the one thing the player has to read off the monitor is
 * <i>how far up the hall is it</i>, and that is a fact about position that the
 * engine already knows exactly. A photograph per distance would be a
 * photograph per distance that had to agree with all the others about
 * lighting, angle and scale, and they would not.
 *
 * <p>So this page inlines no images at all. It is the lightest build on the
 * shelf by an order of magnitude, and the only one whose art is arithmetic.
 *
 * <h2>What is not duplicated</h2>
 *
 * <p>The template ({@code src/main/resources/fnaf9/web.html}) holds the
 * layout, the styles and the engine's JavaScript. The content -- the week's
 * tables, the cast, every threshold, the clock and every fixed sentence --
 * is generated from {@link Feed}, {@link Walker}, {@link Pair} and
 * {@link Voice}, the same classes the desktop screen reads. There is no
 * second copy of the numbers and no second copy of the prose; {@link Voice}
 * exists because of this port.
 *
 * <h2>What is ported, and why it has to be</h2>
 *
 * <p>The night is not a table. It is a clock, a delay line, a door with a
 * travel time and a duty cycle, a jittered walk rate and two clocks per
 * doorway, and the phone has to step it itself. The JavaScript in the
 * template is a transliteration of {@link Feed#update}, including the RNG:
 * {@code java.util.Random} is a 48-bit LCG and it is reproduced in BigInt, so
 * the same seed deals the same night in both builds. That is what makes a
 * night reproducible when something looks wrong, and it is the same reason
 * FNAF 6's phone build carries its own copy.
 *
 * <h2>What is resolved rather than ported</h2>
 *
 * <p>Two things differ, and both are about the device:
 *
 * <ul>
 *   <li><b>The crop.</b> The desktop draws a room with a monitor in it, at
 *       {@code MouseMap.SCREEN} = 940x440 inside a 1280x526 scene. A phone
 *       shows <b>the screen and nothing else</b>, full bleed, because the
 *       monitor is the only thing in that room that is not empty dark. The
 *       corridor geometry is the desktop's, drawn into the whole canvas
 *       instead of into a rectangle inside it.</li>
 *   <li><b>The band and the strip.</b> Text in a scaled canvas is text at
 *       whatever size the scale lands on, and on a phone that is either
 *       unreadable or enormous. Both are DOM, in the same order, with the
 *       same words, and the four buttons become a 2x2 grid because four
 *       buttons across is 960 pixels and a phone is not.</li>
 * </ul>
 *
 * <p>Run from the repository root:
 * <pre>
 *   java -cp classes aside.games.fnaf9.WebFeed [out.html]
 * </pre>
 */
public final class WebFeed {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "fnaf9", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/fnaf9.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + MouseMap.NIGHTS + " nights, " + Pair.forNight(1).left().name() + " first)");
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

        // ---- the words ---------------------------------------------------
        // Every one of these comes out of Voice, which is also what the two
        // desktop screens read. A line edited for the desktop reaches the
        // phone in the same commit or not at all.
        b.append("\"title\":").append(str(Voice.TITLE)).append(",\n");
        b.append("\"subtitle\":").append(str(Voice.SUBTITLE)).append(",\n");
        b.append("\"nightRow\":").append(str(tpl(Voice.NIGHT_ROW_PHONE))).append(",\n");
        b.append("\"help1Phone\":").append(str(Voice.HELP_1_PHONE)).append(",\n");
        b.append("\"help2\":").append(str(Voice.HELP_2)).append(",\n");
        b.append("\"help3\":").append(str(Voice.HELP_3)).append(",\n");
        b.append("\"help4\":").append(str(Voice.HELP_4)).append(",\n");
        b.append("\"help5Phone\":").append(str(Voice.HELP_5_PHONE)).append(",\n");
        b.append("\"bandNight\":").append(str(tpl(Voice.BAND_NIGHT))).append(",\n");
        b.append("\"feedHead\":").append(str(Voice.FEED_HEAD)).append(",\n");
        b.append("\"sensorHead\":").append(str(Voice.SENSOR_HEAD)).append(",\n");
        b.append("\"sensorNotDown\":").append(str(Voice.SENSOR_NOT_DOWN)).append(",\n");
        b.append("\"sensorTouching\":").append(str(Voice.SENSOR_TOUCHING)).append(",\n");
        b.append("\"sensorClear\":").append(str(Voice.SENSOR_CLEAR)).append(",\n");
        b.append("\"monOff1\":").append(str(Voice.MON_OFF_1)).append(",\n");
        b.append("\"monOff2\":").append(str(Voice.MON_OFF_2)).append(",\n");
        b.append("\"faded1\":").append(str(Voice.FADED_1)).append(",\n");
        b.append("\"faded2\":").append(str(tpl(Voice.FADED_2))).append(",\n");
        b.append("\"hall\":").append(str(tpl(Voice.HALL))).append(",\n");
        b.append("\"btnMonLeft\":").append(str(Voice.BTN_MON_LEFT)).append(",\n");
        b.append("\"btnMonRight\":").append(str(Voice.BTN_MON_RIGHT)).append(",\n");
        b.append("\"btnDark\":").append(str(Voice.BTN_DARK)).append(",\n");
        b.append("\"btnHold\":").append(str(Voice.BTN_HOLD)).append(",\n");
        b.append("\"circuitJammed\":").append(str(Voice.CIRCUIT_JAMMED)).append(",\n");
        b.append("\"circuitDark\":").append(str(Voice.CIRCUIT_DARK)).append(",\n");
        b.append("\"circuitWatch\":").append(str(tpl(Voice.CIRCUIT_WATCH))).append(",\n");
        b.append("\"circuitHold\":").append(str(Voice.CIRCUIT_HOLD)).append(",\n");
        b.append("\"takenHead\":").append(str(Voice.TAKEN_HEAD)).append(",\n");
        b.append("\"takenLine\":").append(str(Voice.TAKEN_LINE)).append(",\n");
        b.append("\"winHead\":").append(str(Voice.WIN_HEAD)).append(",\n");
        b.append("\"winLine\":").append(str(Voice.WIN_LINE)).append(",\n");
        b.append("\"backToNights\":").append(str(Voice.BACK_TO_NIGHTS)).append(",\n");

        // ---- the clock ---------------------------------------------------
        // Read off the engine rather than restated, so the phone cannot
        // disagree with the band about what hour it is.
        Feed probe = new Feed(1, 0);
        b.append("\"clocks\":[");
        for (int i = 0; i < Feed.NIGHT_HOURS + 1; i++) {
            probe.hour = i;
            if (i > 0) b.append(',');
            b.append(str(probe.clock()));
        }
        b.append("],\n");

        // ---- the week's tables -------------------------------------------
        // Every one of these is a method on Feed rather than a constant, so
        // the sweep can be run against a variant without editing a file. The
        // phone reads the same methods, which is why a balance pass reaches
        // the phone without anybody remembering it exists.
        b.append("\"ageRate\":").append(perNight(f -> f.ageRate())).append(",\n");
        b.append("\"ageDecay\":").append(perNight(f -> f.ageDecay())).append(",\n");
        b.append("\"pace\":").append(perNight(f -> f.pace())).append(",\n");
        b.append("\"grace\":").append(perNight(f -> f.grace())).append(",\n");

        // ---- the thresholds ----------------------------------------------
        b.append("\"nights\":").append(MouseMap.NIGHTS).append(",\n");
        b.append("\"max\":").append(Feed.MAX).append(",\n");
        b.append("\"hourSeconds\":").append(num(Feed.HOUR_SECONDS)).append(",\n");
        b.append("\"nightHours\":").append(Feed.NIGHT_HOURS).append(",\n");
        b.append("\"ageMax\":").append(num(Feed.AGE_MAX)).append(",\n");
        b.append("\"shutTime\":").append(num(Feed.SHUT_TIME)).append(",\n");
        b.append("\"holdMax\":").append(num(Feed.HOLD_MAX)).append(",\n");
        b.append("\"cool\":").append(num(Feed.COOL)).append(",\n");
        b.append("\"rearm\":").append(num(Feed.REARM)).append(",\n");
        b.append("\"jitter\":").append(num(Feed.JITTER)).append(",\n");
        b.append("\"hist\":").append(Feed.HIST).append(",\n");

        // ---- the cast ----------------------------------------------------
        // Pair.forNight, not a list: which two walk the halls is the engine's
        // decision, and a phone that restated it would be a phone that could
        // disagree about which night it is.
        b.append("\"pairs\":[");
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            Pair p = Pair.forNight(n);
            if (n > 1) b.append(',');
            b.append("\n  {\"left\":").append(walker(p.left()))
             .append(",\"right\":").append(walker(p.right()))
             .append(",\"note\":").append(str(p.note())).append('}');
        }
        b.append("\n],\n");

        // ---- the geometry ------------------------------------------------
        // The desktop's monitor, to the pixel. The phone draws the corridor
        // into the whole canvas; these are the fractions the desktop's
        // drawCorridor and drawWalker use, so the two pictures are the same
        // picture at two sizes rather than two pictures.
        b.append("\"scene\":").append(rect(MouseMap.SCREEN)).append(",\n");
        b.append("\"corridor\":").append(corridor()).append('\n');

        return b.append("}\n").toString();
    }

    /** One night's value from a table that is a method on Feed. */
    interface Table { double at(Feed f); }

    static String perNight(Table t) {
        StringBuilder b = new StringBuilder("[");
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            if (n > 1) b.append(',');
            b.append(num(t.at(new Feed(n, 0))));
        }
        return b.append(']').toString();
    }

    static String walker(Walker w) {
        return "{\"key\":" + str(w.key())
                + ",\"name\":" + str(w.name())
                + ",\"note\":" + str(w.note())
                + ",\"speed\":" + num(w.speed())
                + ",\"sharp\":" + num(w.sharp())
                + ",\"patience\":" + num(w.patience()) + '}';
    }

    /**
     * The corridor's proportions, as the desktop draws them.
     *
     * <p>These are not constants anywhere in the engine -- they are the
     * numbers inside {@code GameScreen.drawCorridor} and
     * {@code GameScreen.drawWalker}, which is the one place in this port where
     * a number had to be copied rather than read. They are gathered here so
     * there is one copy of them instead of two, and so the next person to
     * change the picture has one place to look.
     */
    static String corridor() {
        StringBuilder b = new StringBuilder("{\n");
        b.append("  \"vanishY\":0.46,\n");
        b.append("  \"far\":0.16,\n");
        b.append("  \"rungs\":6,\n");
        b.append("  \"rungTop\":0.06,\n");
        b.append("  \"rungSpan\":0.88,\n");
        b.append("  \"rungCurve\":1.6,\n");
        b.append("  \"doorHalf\":0.30,\n");
        b.append("  \"doorTop\":0.14,\n");
        b.append("  \"doorH\":0.74,\n");
        b.append("  \"walkerH\":0.86,\n");
        b.append("  \"walkerW\":0.42,\n");
        b.append("  \"walkerShrink\":0.80,\n");
        b.append("  \"feetNear\":0.88,\n");
        b.append("  \"eyeY\":0.14,\n");
        b.append("  \"eyeSpread\":0.16\n");
        return b.append('}').toString();
    }

    // ------------------------------------------------------------- helpers

    /**
     * A Java format string, as a JavaScript one.
     *
     * <p>The desktop builds its sentences with {@code String.format}, where
     * {@code %%} is how a literal percent is written. The phone substitutes
     * into the same string with a plain replace, so a template that reaches it
     * unescaped says "cost 0%% of what it takes". Both builds read the same
     * line from {@link Voice}; only the escaping is translated, and it is
     * translated in one place.
     */
    static String tpl(String s) {
        return s.replace("%%", "%");
    }

    static String rect(double[] r) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < r.length; i++) {
            if (i > 0) b.append(',');
            b.append(num(r[i]));
        }
        return b.append(']').toString();
    }

    /**
     * A number JavaScript will read back as the same double.
     *
     * <p>Public because {@code SelfTest} checks the page's thresholds against
     * the engine's constants, and it has to compare them <i>as written</i> --
     * an integral double is emitted as {@code 8}, not {@code 8.0}, and a
     * check that spelled the value its own way would fail on a page that was
     * correct.
     */
    public static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        return Double.toString(d);
    }

    /** JSON string escaping, the same shape PhoneShelf uses. */
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

    private WebFeed() {}
}
