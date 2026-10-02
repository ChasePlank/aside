package aside.games.fnaf8;

import aside.games.fnaf8.engine.Meeting;
import aside.games.fnaf8.engine.Pair;
import aside.games.fnaf8.engine.Unit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build {@code web/fnaf8.html} from the page template and the engine.
 *
 * <p>Same shape as FNAF 6's {@code WebSalvage}, FNAF 7's {@code WebShift} and
 * FNAF 9's {@code WebFeed}, and for the same reason: a phone build written by
 * hand is a second copy of the game, and a second copy of the game is a copy
 * that disagrees with the first one eventually. So the page carries the layout
 * and the drawing, and <b>everything with a number in it comes from the
 * engine</b> -- the five pairs, the four night tables, and the numbers the
 * rules are made of.
 *
 * <p>What the port does not carry is the desktop's art. The sprites and the
 * room are three megabytes and the page has to be one file, so the office is
 * drawn and the units are shapes, which is the choice FNAF 7's and FNAF 9's
 * ports made. The rules are the desktop's to the number, and
 * {@code java.util.Random} is reproduced in BigInt so the same seed deals the
 * same night in both builds.
 *
 *     java -cp classes aside.games.fnaf8.WebMeeting
 */
public final class WebMeeting {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/fnaf8.html");
        Files.writeString(out, html(), StandardCharsets.UTF_8);
        System.out.println("wrote " + out + " (" + Files.size(out) / 1024 + " KB)");
    }

    /** The finished page. */
    public static String html() throws Exception {
        String tpl = new String(WebMeeting.class.getResourceAsStream("/fnaf8/web.html")
                .readAllBytes(), StandardCharsets.UTF_8);
        return tpl.replace("/*__CONTENT__*/", content())
                .replace(aside.game.WebAudio.MARKER, aside.game.WebAudio.js());
    }

    /** Everything the page needs that the engine owns. */
    static String content() {
        StringBuilder sb = new StringBuilder();
        sb.append("const C = {\n");
        sb.append("  pairs: [");
        for (int n = 1; n <= 5; n++) {
            Pair p = Pair.forNight(n);
            if (n > 1) sb.append(",");
            sb.append("\n    {left:").append(unit(p.left()))
              .append(", right:").append(unit(p.right()))
              .append(", note:").append(str(p.note())).append("}");
        }
        sb.append("\n  ],\n");
        sb.append("  pace: ").append(table(0)).append(",\n");
        sb.append("  retreat: ").append(table(1)).append(",\n");
        sb.append("  call: ").append(table(2)).append(",\n");
        sb.append("  patience: ").append(table(3)).append(",\n");
        sb.append("  hourSeconds: ").append(num(Meeting.HOUR_SECONDS)).append(",\n");
        sb.append("  nightHours: ").append(Meeting.NIGHT_HOURS).append(",\n");
        sb.append("  max: ").append(Meeting.MAX).append(",\n");
        sb.append("  meet: ").append(Meeting.MEET).append(",\n");
        sb.append("  swivel: ").append(num(Meeting.SWIVEL)).append(",\n");
        sb.append("  dimRush: ").append(num(Meeting.DIM_RUSH)).append(",\n");
        sb.append("  brightRush: ").append(num(Meeting.BRIGHT_RUSH)).append(",\n");
        sb.append("  help: ").append(str(HELP)).append("\n");
        sb.append("};\n");
        return sb.toString();
    }

    static String unit(Unit u) {
        return "{key:" + str(u.key()) + ", name:" + str(u.name())
                + ", note:" + str(u.note()) + ", speed:" + num(u.speed())
                + ", stubborn:" + num(u.stubborn()) + "}";
    }

    /** One of the night's four tables, read out of the engine. */
    public static String table(int which) {
        StringBuilder sb = new StringBuilder("[");
        for (int n = 1; n <= 5; n++) {
            Meeting m = new Meeting(n, 1);
            double v = switch (which) {
                case 0 -> m.pace();
                case 1 -> m.retreat();
                case 2 -> m.call();
                default -> m.patience();
            };
            if (n > 1) sb.append(", ");
            sb.append(num(v));
        }
        return sb.append("]").toString();
    }

    static final String HELP =
            "They are not coming for you. They are coming for each other, and "
            + "the night ends when both of them are standing in a doorway at "
            + "once. The lamp reaches the hall and not the doorway, so the one "
            + "in your door can only be got rid of by turning its partner "
            + "around -- and the hall you are not looking at is the hall that "
            + "is moving.";

    static String str(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A number the page and the engine can agree on exactly. */
    public static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
