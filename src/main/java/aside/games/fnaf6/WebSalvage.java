package aside.games.fnaf6;

import aside.games.fnaf6.engine.Salvage;
import aside.games.fnaf6.engine.Unit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Generate the single-file phone build of the salvage bay.
 *
 * <p><b>This is the first FNAF game on the phone shelf, and the reason it
 * is this one.</b> FNAF 1 through 8 are desktop-only and the shelf says so
 * under its own heading. The reason is not that they are real-time -- Night
 * Shift is real-time and has been on the shelf since the shelf existed. The
 * reason is weight: the bay is 2.7 MB of PNG and the shelf inlines every
 * build it carries as base64, so the desktop art is a 3.6 MB page on top of
 * a 2 MB shelf. FNAF 6 is the one of the eight that can afford a phone
 * build, because it is the one with the least to draw: one room, one desk,
 * one figure per unit, and no picture per pose. The pose is drawn, by
 * scaling and raising the unit out of the chair, and that is arithmetic
 * rather than art.
 *
 * <h2>What is not duplicated</h2>
 *
 * <p>The template ({@code src/main/resources/fnaf6/web.html}) holds the
 * layout, the styles and the engine's JavaScript. The content -- the week's
 * tables, the cast, every threshold, the pose names, the clock and every
 * fixed sentence -- is generated from {@link Salvage}, {@link Unit},
 * {@link Voice} and {@link MouseMap}, the same classes the desktop screen
 * reads. There is no second copy of the numbers and no second copy of the
 * prose.
 *
 * <h2>What is ported, and why it has to be</h2>
 *
 * <p>The night is not a table. It is a clock, a lamp with a warm-up, an
 * agitation budget, a hidden coin flip and a jittered advance interval, and
 * the phone has to step it itself. The JavaScript in the template is a
 * transliteration of {@link Salvage#update}, including the RNG:
 * {@code java.util.Random} is a 48-bit LCG and it is reproduced in BigInt,
 * so the same seed deals the same night in both builds. That is what makes
 * a night reproducible when something looks wrong, and it is the same
 * reason tell's phone build carries its own counter-based draw.
 *
 * <h2>What is resolved rather than ported</h2>
 *
 * <p>The band and the strip are DOM rather than canvas, and the room is
 * cropped horizontally rather than letterboxed. Those are the two places
 * the phone is allowed to differ, and both are about the device rather
 * than about the game:
 *
 * <ul>
 *   <li><b>The crop.</b> The desktop's scene is 1280x526 and a phone is
 *       nothing like that. The phone shows the same 526 rows -- the same
 *       floor line, the same desk line, the same unit height against the
 *       room -- and gives up the empty dark wall at the sides. The window
 *       is never narrower than {@link #VIEW_MIN}, so the widest unit at
 *       the top of its climb still fits.</li>
 *   <li><b>The band and the strip.</b> Text in a scaled canvas is text at
 *       whatever size the scale lands on, and on a phone that is either
 *       unreadable or enormous. Both are DOM, at the same positions, in
 *       the same order, with the same words.</li>
 * </ul>
 *
 * <p>Run from the repository root:
 * <pre>
 *   java -cp classes aside.games.fnaf6.WebSalvage [out.html]
 * </pre>
 */
public final class WebSalvage {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "fnaf6", "web.html");
    static final Path ART = Path.of("art", "phone", "fnaf6");

    /**
     * The narrowest window of the room the phone will show, in scene pixels.
     *
     * <p>A floor rather than a preference. The widest thing the player has
     * to see is the widest unit at the top of its climb -- Scrap Baby, at
     * 713x900 drawn 470 tall, which is 372 scene pixels across -- and a
     * window that clipped it would clip the one read the night is made of.
     * 520 leaves the figure and a little of the room on either side of it.
     */
    public static final int VIEW_MIN = 520;

    /** The whole room. Above this the crop stops and the picture letterboxes. */
    public static final int VIEW_MAX = 1280;

    /** The chair, and the centre the crop and the light are both taken about. */
    public static final double CHAIR_X = MouseMap.W / 2;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/fnaf6.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + MouseMap.NIGHTS + " nights, " + Unit.forNight(1).name() + " first)");
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

    static String content() throws Exception {
        StringBuilder b = new StringBuilder();
        b.append("{\n");

        // ---- the words ---------------------------------------------------
        b.append("\"title\":").append(str(Voice.TITLE)).append(",\n");
        b.append("\"subtitle\":").append(str(Voice.SUBTITLE)).append(",\n");
        b.append("\"nightRow\":").append(str(tpl(Voice.NIGHT_ROW))).append(",\n");
        b.append("\"selectHelp1\":").append(str(Voice.SELECT_HELP_1)).append(",\n");
        b.append("\"selectHelp2\":").append(str(Voice.SELECT_HELP_2)).append(",\n");
        b.append("\"selectHelp3\":").append(str(Voice.SELECT_HELP_3)).append(",\n");
        b.append("\"selectHelp4\":").append(str(Voice.SELECT_HELP_4)).append(",\n");
        b.append("\"selectHelp1Phone\":").append(str(Voice.SELECT_HELP_1_PHONE)).append(",\n");
        b.append("\"selectHelp4Phone\":").append(str(Voice.SELECT_HELP_4_PHONE)).append(",\n");
        b.append("\"bandNight\":").append(str(tpl(Voice.BAND_NIGHT))).append(",\n");
        b.append("\"bandChair\":").append(str(Voice.BAND_CHAIR)).append(",\n");
        b.append("\"meterLabel\":").append(str(Voice.METER_LABEL)).append(",\n");
        b.append("\"shockReady\":").append(str(Voice.SHOCK_READY)).append(",\n");
        b.append("\"shockSpent\":").append(str(Voice.SHOCK_SPENT)).append(",\n");
        b.append("\"lampOn\":").append(str(Voice.LAMP_ON)).append(",\n");
        b.append("\"lampOff\":").append(str(Voice.LAMP_OFF)).append(",\n");
        b.append("\"stripLabel\":").append(str(Voice.STRIP_LABEL)).append(",\n");
        b.append("\"warming\":").append(str(Voice.WARMING)).append(",\n");
        b.append("\"dark\":").append(str(Voice.DARK)).append(",\n");
        b.append("\"hintOver\":").append(str(Voice.HINT_OVER)).append(",\n");
        b.append("\"hintUp\":").append(str(Voice.HINT_UP)).append(",\n");
        b.append("\"hintLit\":").append(str(Voice.HINT_LIT)).append(",\n");
        b.append("\"hintCostly\":").append(str(Voice.HINT_COSTLY)).append(",\n");
        b.append("\"hintIdle\":").append(str(Voice.HINT_IDLE)).append(",\n");
        b.append("\"hintIdlePhone\":").append(str(Voice.HINT_IDLE_PHONE)).append(",\n");
        b.append("\"hintOverPhone\":").append(str(Voice.HINT_OVER_PHONE)).append(",\n");
        b.append("\"counts\":").append(str(tpl(Voice.COUNTS))).append(",\n");
        b.append("\"scareOver\":").append(str(Voice.SCARE_OVER)).append(",\n");
        b.append("\"winDestroyed\":").append(str(Voice.WIN_DESTROYED)).append(",\n");
        b.append("\"winSurvived\":").append(str(Voice.WIN_SURVIVED)).append(",\n");
        b.append("\"winDestroyedLine\":").append(str(Voice.WIN_DESTROYED_LINE)).append(",\n");
        b.append("\"winSurvivedLine\":").append(str(tpl(Voice.WIN_SURVIVED_LINE))).append(",\n");
        b.append("\"winStats\":").append(str(tpl(Voice.WIN_STATS))).append(",\n");
        b.append("\"winCost\":").append(str(tpl(Voice.WIN_COST))).append(",\n");
        b.append("\"backToNights\":").append(str(Voice.BACK_TO_NIGHTS)).append(",\n");

        // ---- the clock ---------------------------------------------------
        // Read off the engine rather than restated, so the phone cannot
        // disagree with the band about what hour it is.
        Salvage probe = new Salvage(1, 0);
        b.append("\"clocks\":[");
        for (int i = 0; i < Voice.CLOCKS.length; i++) {
            probe.hour = i;
            if (i > 0) b.append(',');
            b.append(str(probe.clock()));
        }
        b.append("],\n");

        // ---- the week's tables -------------------------------------------
        // Every one of these is a method on Salvage rather than a constant,
        // so the sweep can be run against a variant without editing a file.
        // The phone reads the same methods, which is why a balance pass
        // reaches the phone without anybody remembering it exists.
        b.append("\"advance\":[");
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            if (n > 1) b.append(',');
            b.append(num(new Salvage(n, 0).advance()));
        }
        b.append("],\n");
        b.append("\"litRate\":[");
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            if (n > 1) b.append(',');
            b.append(num(new Salvage(n, 0).litRate()));
        }
        b.append("],\n");
        b.append("\"creakRate\":[");
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            if (n > 1) b.append(',');
            b.append(num(new Salvage(n, 0).creakRate()));
        }
        b.append("],\n");

        // ---- the thresholds ----------------------------------------------
        b.append("\"hourSeconds\":").append(num(Salvage.HOUR_SECONDS)).append(",\n");
        b.append("\"nightHours\":").append(Salvage.NIGHT_HOURS).append(",\n");
        b.append("\"lampWarm\":").append(num(Salvage.LAMP_WARM)).append(",\n");
        b.append("\"agitationMax\":").append(num(Salvage.AGITATION_MAX)).append(",\n");
        b.append("\"coolRate\":").append(num(Salvage.COOL_RATE)).append(",\n");
        b.append("\"poseMax\":").append(Salvage.POSE_MAX).append(",\n");
        b.append("\"shockMin\":").append(Salvage.SHOCK_MIN).append(",\n");
        b.append("\"hostileChance\":").append(num(Salvage.HOSTILE_CHANCE)).append(",\n");
        b.append("\"intervalJitter\":").append(num(Salvage.INTERVAL_JITTER)).append(",\n");
        b.append("\"nights\":").append(MouseMap.NIGHTS).append(",\n");

        // ---- the poses ---------------------------------------------------
        b.append("\"poses\":[");
        Salvage.Pose[] poses = Salvage.Pose.values();
        for (int i = 0; i < poses.length; i++) {
            if (i > 0) b.append(',');
            b.append(str(poses[i].label));
        }
        b.append("],\n");

        // ---- the cast ----------------------------------------------------
        // Unit.forNight, not a list: the week is the order of how quiet they
        // are, and the mapping from a night to a unit is the engine's.
        b.append("\"units\":[");
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            Unit u = Unit.forNight(n);
            if (n > 1) b.append(',');
            b.append("{\"key\":").append(str(u.key()))
             .append(",\"name\":").append(str(u.name()))
             .append(",\"note\":").append(str(u.note()))
             .append(",\"silent\":").append(num(u.silent())).append('}');
        }
        b.append("],\n");

        // ---- the geometry ------------------------------------------------
        // The desktop's, to the pixel. The phone crops this rectangle; it
        // does not redraw it.
        b.append("\"scene\":").append(rect(MouseMap.SCENE)).append(",\n");
        b.append("\"chairX\":").append(num(CHAIR_X)).append(",\n");
        b.append("\"viewMin\":").append(VIEW_MIN).append(",\n");
        b.append("\"viewMax\":").append(VIEW_MAX).append(",\n");
        b.append("\"unitH\":").append(num(MouseMap.UNIT_H)).append(",\n");
        b.append("\"unitScaleMin\":").append(num(MouseMap.UNIT_SCALE_MIN)).append(",\n");
        b.append("\"unitTilt\":").append(num(MouseMap.UNIT_TILT)).append(",\n");
        b.append("\"unitBottom\":").append(num(MouseMap.UNIT_BOTTOM)).append(",\n");

        // ---- the art -----------------------------------------------------
        b.append("\"assets\":").append(assets()).append('\n');

        return b.append("}\n").toString();
    }

    /**
     * The art, as data URIs.
     *
     * <p>Built by {@code tools/fnaf6-phone-art.py} and committed, because
     * Java cannot write WebP and the units need alpha. If the directory is
     * not there the generator says which command builds it rather than
     * emitting a page with holes in it -- a phone build that loads and
     * draws nothing is worse than one that does not exist.
     */
    static String assets() throws Exception {
        StringBuilder b = new StringBuilder("{\n");
        b.append("  \"room\":").append(uri("room.jpg", "image/jpeg")).append(",\n");
        b.append("  \"desk\":").append(uri("desk.webp", "image/webp"));
        for (String key : new String[]{"scraptrap", "scrapbaby", "moltenfreddy", "lefty"}) {
            b.append(",\n  \"unit:").append(key).append("\":")
             .append(uri("unit_" + key + ".webp", "image/webp"));
            b.append(",\n  \"scare:").append(key).append("\":")
             .append(uri("scare_" + key + ".jpg", "image/jpeg"));
        }
        return b.append("\n}").toString();
    }

    static String uri(String name, String mime) throws Exception {
        Path f = ART.resolve(name);
        if (!Files.exists(f)) {
            throw new IllegalStateException("no " + f + " -- build it with: "
                    + "python3 tools/fnaf6-phone-art.py");
        }
        return "\"data:" + mime + ";base64,"
                + Base64.getEncoder().encodeToString(Files.readAllBytes(f)) + "\"";
    }

    // ------------------------------------------------------------- helpers

    /**
     * A Java format string, as a JavaScript one.
     *
     * <p>The desktop builds its sentences with {@code String.format}, where
     * {@code %%} is how a literal percent is written. The phone substitutes
     * into the same string with a plain replace, so a template that reaches
     * it unescaped says "cost 0%% of what it takes" -- which is exactly
     * what the first version of this page said, and it took a screenshot of
     * a finished night to see it. Both builds read the same line from
     * {@link Voice}; only the escaping is translated, and it is translated
     * in one place.
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

    /** A number JavaScript will read back as the same double. */
    static String num(double d) {
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

    private WebSalvage() {}
}
