package aside.games.fnaf7;

import aside.games.fnaf7.engine.Shift;
import aside.games.fnaf7.engine.Unit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Build {@code web/fnaf7.html} from the page template and the engine.
 *
 * <p>Same shape as FNAF 6's {@code WebSalvage} and FNAF 9's {@code WebFeed},
 * and for the same reason: a phone build that is written by hand is a second
 * copy of the game, and a second copy of the game is a copy that disagrees
 * with the first one eventually. So the page carries the layout and the
 * drawing, and <b>everything with a number in it comes from the engine</b> --
 * the five units, the four night tables, and the numbers the rules are made
 * of.
 *
 * <p>What the port does <i>not</i> carry is the desktop's art. The unit
 * sprites and the room are three megabytes, and the page has to be one file;
 * so the office is drawn and the unit is a shape, which is the same choice
 * FNAF 9's phone build made. The rules are the desktop's to the number, and
 * {@code java.util.Random} is reproduced in BigInt so the same seed deals the
 * same night in both builds.
 *
 *     java -cp classes aside.games.fnaf7.WebShift
 */
public final class WebShift {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/fnaf7.html");
        Files.writeString(out, html(), StandardCharsets.UTF_8);
        System.out.println("wrote " + out + " (" + Files.size(out) / 1024 + " KB)");
    }

    /** The finished page. */
    public static String html() throws Exception {
        String tpl = aside.game.Templates.read("fnaf7/web.html");
        return tpl.replace("/*__CONTENT__*/", content())
                .replace(aside.game.WebAudio.MARKER, aside.game.WebAudio.js());
    }

    /** Everything the page needs that the engine owns. */
    static String content() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("const C = {\n");
        sb.append("  units: [");
        Unit[] all = Unit.all();
        for (int i = 0; i < 5; i++) {
            Unit u = Unit.forNight(i + 1);
            if (i > 0) sb.append(",");
            sb.append("\n    {key:").append(str(u.key()))
              .append(", name:").append(str(u.name()))
              .append(", note:").append(str(u.note()))
              .append(", tell:").append(num(u.tell())).append("}");
        }
        sb.append("\n  ],\n");
        sb.append("  away: ").append(table(0)).append(",\n");
        sb.append("  approach: ").append(table(1)).append(",\n");
        sb.append("  explore: ").append(table(2)).append(",\n");
        sb.append("  memory: ").append(table(3)).append(",\n");
        sb.append("  hourSeconds: ").append(num(Shift.HOUR_SECONDS)).append(",\n");
        sb.append("  nightHours: ").append(Shift.NIGHT_HOURS).append(",\n");
        sb.append("  lightWarm: ").append(num(Shift.LIGHT_WARM)).append(",\n");
        sb.append("  lightMax: ").append(num(Shift.LIGHT_MAX)).append(",\n");
        sb.append("  lightCool: ").append(num(Shift.LIGHT_COOL)).append(",\n");
        sb.append("  lightReset: ").append(num(Shift.LIGHT_RESET)).append(",\n");
        sb.append("  barMove: ").append(num(Shift.BAR_MOVE)).append(",\n");
        sb.append("  strike: ").append(num(Shift.STRIKE)).append(",\n");
        sb.append("  sure: ").append(num(Shift.SURE)).append(",\n");
        sb.append("  help: ").append(str(HELP)).append(",\n");
        sb.append("  assets: ").append(assets()).append("\n");
        sb.append("};\n");
        return sb.toString();
    }

    /** One of the night's four tables, read out of the engine. */
    public static String table(int which) {
        StringBuilder sb = new StringBuilder("[");
        for (int n = 1; n <= 5; n++) {
            Shift s = new Shift(n, 1);
            double v = switch (which) {
                case 0 -> s.away();
                case 1 -> s.approach();
                case 2 -> s.explore();
                default -> s.memory();
            };
            if (n > 1) sb.append(", ");
            sb.append(num(v));
        }
        return sb.append("]").toString();
    }

    static final String HELP =
            "One bar, two doors, and one pair of hands: the bar cannot move "
            + "while the light is on. The light has to warm up before it shows "
            + "you anything, and the filament only lasts so long. The readout "
            + "says where it thinks you are -- so it comes to the other side.";

    /** Where the phone's own copies of the art live. */
    static final Path ART = Path.of("art", "phone", "fnaf7");

    /**
     * The art, as data URIs.
     *
     * <p>Inlined rather than linked, because the build has to be one file.
     * The copies are downscaled by {@code tools/fnaf7-phone-art.py} and
     * committed, so this method is reproducible from Java alone.
     *
     * <p>The unit keys are the engine's own, so the page looks a unit up by
     * the key the engine gives it rather than by the night number -- a night
     * that showed the wrong one would look perfectly fine doing it.
     */
    static String assets() throws Exception {
        StringBuilder b = new StringBuilder("{\n");
        b.append("  \"room\":").append(uri("room.webp", "image/webp"));
        for (Unit u : Unit.all()) {
            b.append(",\n  \"unit:").append(u.key()).append("\":")
             .append(uri("unit_" + u.key() + ".webp", "image/webp"));
            b.append(",\n  \"scare:").append(u.key()).append("\":")
             .append(uri("scare_" + u.key() + ".webp", "image/webp"));
        }
        return b.append("\n}").toString();
    }

    static String uri(String name, String mime) throws Exception {
        Path f = ART.resolve(name);
        if (!Files.exists(f)) {
            throw new IllegalStateException("no " + f + " -- build it with: "
                    + "python3 tools/fnaf7-phone-art.py");
        }
        return "\"data:" + mime + ";base64,"
                + Base64.getEncoder().encodeToString(Files.readAllBytes(f)) + "\"";
    }

    static String str(String s) {
        // The full set, not just the backslash and the quote. "<" and ">"
        // because the JSON sits in a document and a "</script>" in the content
        // would end the block early; "&" because it is markup; and U+2028 and
        // U+2029 because they are line terminators in JavaScript and break a
        // string literal that contains one. The other generators escape all
        // five and this one escaped none of them -- found by writing the check
        // that feeds the escaper a string, which is the check nothing had.
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
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

    /** A number the page and the engine can agree on exactly. */
    public static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
