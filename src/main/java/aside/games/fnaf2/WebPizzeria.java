package aside.games.fnaf2;

import aside.games.fnaf2.engine.Animatronic;
import aside.games.fnaf2.engine.Game;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build {@code web/fnaf2.html} from the page template and the engine.
 *
 * <p>Same shape as the other five ports -- FNAF 3's {@code WebHouse}, FNAF 4's
 * {@code WebRoom}, FNAF 6's {@code WebSalvage}, FNAF 7's {@code WebShift},
 * FNAF 8's {@code WebMeeting} and FNAF 9's {@code WebFeed} -- and for the same
 * reason: a phone build written by hand is a second copy of the game, and a
 * second copy of the game is a copy that disagrees with the first one
 * eventually. So the page carries the layout and the drawing, and
 * <b>everything with a number in it comes from the engine</b> -- the cast and
 * their paths, the night tables, and the constants the rules are made of.
 *
 * <p>What the port does not carry is the desktop's art: the sprites and the
 * office are megabytes and the page has to be one file, so the office and the
 * monitor are drawn and the things in them are shapes. The rules are the
 * desktop's to the number, and {@code java.util.Random} is reproduced in
 * BigInt -- including {@code nextInt}, which is a rejection sampler -- so the
 * same seed deals the same night in both builds.
 *
 *     java -cp classes aside.games.fnaf2.WebPizzeria
 */
public final class WebPizzeria {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/fnaf2.html");
        Files.writeString(out, html(), StandardCharsets.UTF_8);
        System.out.println("wrote " + out + " (" + Files.size(out) / 1024 + " KB)");
    }

    /** The finished page. */
    public static String html() throws Exception {
        String tpl = new String(WebPizzeria.class.getResourceAsStream("/fnaf2/web.html")
                .readAllBytes(), StandardCharsets.UTF_8);
        return tpl.replace("/*__CONTENT__*/", content());
    }

    /** Everything the page needs that the engine owns. */
    static String content() {
        Game g = new Game(1, 1);
        StringBuilder sb = new StringBuilder();
        sb.append("const C = {\n");

        // The cast. The AI level is per-night and comes from the table below,
        // so what is emitted here is the part that does not move: who they
        // are, the rooms they walk, the opening they arrive at, what answers
        // them, and how often they roll.
        sb.append("  cast: [");
        Animatronic[] all = {g.toyFreddy, g.toyBonnie, g.toyChica, g.mangle,
                g.witheredBonnie, g.balloonBoy, g.witheredFoxy, g.puppet};
        for (int i = 0; i < all.length; i++) {
            Animatronic a = all[i];
            if (i > 0) sb.append(",");
            sb.append("\n    {name:").append(str(a.name))
              .append(", path: [");
            for (int j = 0; j < a.path.length; j++) {
                if (j > 0) sb.append(", ");
                sb.append(a.path[j]);
            }
            sb.append("], opening:").append(str(a.opening.name()))
              .append(", answer:").append(str(a.answer.name()))
              .append(", lethal:").append(a.lethal)
              .append(", staged:").append(a.staged)
              .append(", moveInterval:").append(num(a.moveInterval))
              .append(", puppet:").append(a == g.puppet)
              .append("}");
        }
        sb.append("\n  ],\n");

        sb.append("  aiLevel: [");
        for (int n = 1; n <= 6; n++) {
            if (n > 1) sb.append(", ");
            sb.append(Game.aiLevel(n));
        }
        sb.append("],\n");
        sb.append("  drainMult: [");
        for (int i = 0; i < Game.DRAIN_MULT.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(num(Game.DRAIN_MULT[i]));
        }
        sb.append("],\n");
        sb.append("  notes: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(str(note(n)));
        }
        sb.append("],\n");

        sb.append("  hourSeconds: ").append(num(Game.HOUR_SECONDS)).append(",\n");
        sb.append("  nightHours: ").append(Game.NIGHT_HOURS).append(",\n");
        sb.append("  musicBoxMax: ").append(num(Game.MUSIC_BOX_MAX)).append(",\n");
        sb.append("  musicBoxDrain: ").append(num(Game.MUSIC_BOX_DRAIN)).append(",\n");
        sb.append("  musicBoxWind: ").append(num(Game.MUSIC_BOX_WIND)).append(",\n");
        sb.append("  puppetGrace: ").append(num(Game.PUPPET_GRACE)).append(",\n");
        sb.append("  foxyHallWindow: ").append(num(Game.FOXY_HALL_WINDOW)).append(",\n");
        sb.append("  foxyRepelTime: ").append(num(Game.FOXY_REPEL_TIME)).append(",\n");
        sb.append("  openingGraceMax: ").append(num(Game.OPENING_GRACE_MAX)).append(",\n");
        sb.append("  openingGraceMin: ").append(num(Game.OPENING_GRACE_MIN)).append(",\n");
        sb.append("  camCount: ").append(Game.CAM_COUNT).append(",\n");
        sb.append("  coveCam: ").append(Game.COVE_CAM).append(",\n");
        sb.append("  musicBoxCam: ").append(Game.MUSIC_BOX_CAM).append(",\n");
        sb.append("  help: ").append(str(HELP)).append("\n");
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * The line under a night on the phone's menu.
     *
     * <p>The desktop has no night notes, so these are the phone's, and they are
     * built out of the engine's own numbers rather than written beside them:
     * the drain multiplier is the honest difficulty lever and the opening
     * grace is what it costs, so the sentence cannot drift from the night it
     * describes.
     */
    static String note(int night) {
        Game g = new Game(night, 1);
        String lead = switch (night) {
            case 1 -> "The box lasts a minute. Learn where CAM 11 is.";
            case 2 -> "The box goes faster. So does everything else.";
            case 3 -> "You will be on the monitor when something arrives.";
            case 4 -> "Two things at once, and the mask costs you the box.";
            default -> "The box drains at " + String.format("%.2f", g.drainRate())
                    + " a second and an opening gives you "
                    + String.format("%.1f", g.openingGrace()) + ".";
        };
        return lead + String.format(" An unanswered opening waits %.1fs.", g.openingGrace());
    }

    static final String HELP =
            "Three openings and no doors: a hall and two vents. Nothing here can "
            + "be blocked. The mask fools most of them -- and it blocks the "
            + "camera and every light while it is on, so hiding is the moment "
            + "you stop watching the music box. The box drains in under a "
            + "minute and winds only while you hold CAM 11, and if it empties "
            + "the Puppet comes and nothing stops it. There is one flashlight "
            + "and three openings, so looking at one is choosing not to look at "
            + "the other two.";

    static String str(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A number the page and the engine can agree on exactly. */
    public static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
