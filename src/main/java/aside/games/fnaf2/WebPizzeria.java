package aside.games.fnaf2;

import aside.games.fnaf2.engine.Animatronic;
import aside.games.fnaf2.engine.Game;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

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
        return tpl.replace("/*__CONTENT__*/", content())
                .replace(aside.game.WebAudio.MARKER, aside.game.WebAudio.js());
    }

    /** Everything the page needs that the engine owns. */
    static String content() throws Exception {
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
              .append(", key:").append(str(keyOf(a.name)))
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
        sb.append("  help: ").append(str(HELP)).append(",\n");
        sb.append("  assets: ").append(assets()).append("\n");
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

    /**
     * The art's filename for a unit, which is not always its name.
     *
     * <p>The engine calls one of them "The Puppet" and the file is
     * {@code puppet.png}; another is "Balloon Boy" and the file is
     * {@code balloonboy.png}. Deriving a key from the name would look right
     * for six of the eight and be wrong for the other two, so it is written
     * down.
     */
    public static String keyOf(String name) {
        return switch (name) {
            case "Toy Freddy" -> "toyfreddy";
            case "Toy Bonnie" -> "toybonnie";
            case "Toy Chica" -> "toychica";
            case "Mangle" -> "mangle";
            case "Withered Bonnie" -> "witheredbonnie";
            case "Withered Foxy" -> "witheredfoxy";
            case "Balloon Boy" -> "balloonboy";
            case "The Puppet" -> "puppet";
            default -> name.toLowerCase().replace(" ", "");
        };
    }

    /** Where the phone's own copies of the art live. */
    static final Path ART = Path.of("art", "phone", "fnaf2");

    /**
     * The art, as data URIs.
     *
     * <p>Inlined rather than linked, because the build has to be one file.
     * The copies are downscaled by {@code tools/fnaf2-phone-art.py} and
     * committed, so this method is reproducible from Java alone.
     */
    static String assets() throws Exception {
        StringBuilder b = new StringBuilder("{\n");
        boolean first = true;
        for (String n : new String[]{"office", "office.hall", "office.ventL", "office.ventR"}) {
            if (!first) b.append(",\n");
            first = false;
            b.append("  \"").append(n).append("\":").append(uri(n + ".webp", "image/webp"));
        }
        for (int r = 1; r <= Game.CAM_COUNT; r++) {
            b.append(",\n  \"room").append(r).append("\":")
             .append(uri("room" + r + ".webp", "image/webp"));
        }
        for (String k : new String[]{"toyfreddy", "toybonnie", "toychica", "mangle",
                "witheredbonnie", "witheredfoxy", "balloonboy", "puppet"}) {
            b.append(",\n  \"unit:").append(k).append("\":")
             .append(uri("unit_" + k + ".webp", "image/webp"));
        }
        b.append(",\n  \"mask\":").append(uri("mask.webp", "image/webp"));
        for (String k : new String[]{"toyfreddy", "toychica", "mangle",
                "witheredfoxy", "balloonboy", "puppet"}) {
            b.append(",\n  \"scare:").append(k).append("\":")
             .append(uri("scare_" + k + ".webp", "image/webp"));
        }
        return b.append("\n}").toString();
    }

    static String uri(String name, String mime) throws Exception {
        Path f = ART.resolve(name);
        if (!Files.exists(f)) {
            throw new IllegalStateException("no " + f + " -- build it with: "
                    + "python3 tools/fnaf2-phone-art.py");
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
