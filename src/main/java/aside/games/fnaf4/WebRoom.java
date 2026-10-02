package aside.games.fnaf4;

import aside.games.fnaf4.engine.Game;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Build {@code web/fnaf4.html} from the page template and the engine.
 *
 * <p>Same shape as FNAF 6's {@code WebSalvage}, FNAF 7's {@code WebShift},
 * FNAF 8's {@code WebMeeting} and FNAF 9's {@code WebFeed}, and for the same
 * reason: a phone build written by hand is a second copy of the game, and a
 * second copy of the game is a copy that disagrees with the first one
 * eventually. So the page carries the layout and the drawing, and
 * <b>everything with a number in it comes from the engine</b> -- the three
 * night tables, the four threats, and the constants the rules are made of.
 *
 * <p>What the port does not carry is the desktop's art: the sprites and the
 * room are megabytes and the page has to be one file, so the room is drawn
 * and the threats are shapes, which is the choice the other three ports made.
 * The rules are the desktop's to the number, and {@code java.util.Random} is
 * reproduced in BigInt so the same seed deals the same night in both builds.
 *
 *     java -cp classes aside.games.fnaf4.WebRoom
 */
public final class WebRoom {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/fnaf4.html");
        Files.writeString(out, html(), StandardCharsets.UTF_8);
        System.out.println("wrote " + out + " (" + Files.size(out) / 1024 + " KB)");
    }

    /** The finished page. */
    public static String html() throws Exception {
        String tpl = new String(WebRoom.class.getResourceAsStream("/fnaf4/web.html")
                .readAllBytes(), StandardCharsets.UTF_8);
        return tpl.replace("/*__CONTENT__*/", content())
                .replace(aside.game.WebAudio.MARKER, aside.game.WebAudio.js());
    }

    /** Everything the page needs that the engine owns. */
    static String content() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("const C = {\n");
        sb.append("  baseInterval: ").append(table(0)).append(",\n");
        sb.append("  grace: ").append(table(1)).append(",\n");
        sb.append("  fredbearRate: ").append(table(2)).append(",\n");
        sb.append("  aiLevel: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(aiLevel(n));
        }
        sb.append("],\n");
        sb.append("  hourSeconds: ").append(num(Game.HOUR_SECONDS)).append(",\n");
        sb.append("  nightHours: ").append(Game.NIGHT_HOURS).append(",\n");
        sb.append("  hopTime: ").append(num(Game.HOP_TIME)).append(",\n");
        sb.append("  flashTime: ").append(num(Game.FLASH_TIME)).append(",\n");
        sb.append("  litTime: ").append(num(Game.LIT_TIME)).append(",\n");
        sb.append("  distMax: ").append(Game.DIST_MAX).append(",\n");
        sb.append("  pushTo: ").append(Game.PUSH_TO).append(",\n");
        sb.append("  breathEvery: ").append(num(Game.BREATH_EVERY)).append(",\n");
        sb.append("  noiseMax: ").append(num(Game.NOISE_MAX)).append(",\n");
        sb.append("  noisePerFlash: ").append(num(Game.NOISE_PER_FLASH)).append(",\n");
        sb.append("  noiseDecay: ").append(num(Game.NOISE_DECAY)).append(",\n");
        sb.append("  noiseWeight: ").append(num(Game.NOISE_WEIGHT)).append(",\n");
        sb.append("  fredbearGrace: ").append(num(Game.FREDBEAR_GRACE)).append(",\n");
        sb.append("  fredbearCooldown: ").append(num(Game.FREDBEAR_COOLDOWN)).append(",\n");
        sb.append("  notes: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(str(note(n)));
        }
        sb.append("],\n");
        sb.append("  help: ").append(str(HELP)).append(",\n");
        sb.append("  assets: ").append(assets()).append("\n");
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * The line under a night on the phone's menu.
     *
     * <p>The desktop has no night notes -- its menu is five numbers -- so
     * these are the phone's, and they are built out of the engine's own
     * numbers rather than written beside them: the grace is the dial the week
     * turns on, and the worst-case trip is a constant, so the sentence cannot
     * drift away from the night it describes.
     */
    static String note(int night) {
        Game g = new Game(night, 1);
        String lead = switch (night) {
            case 1 -> "Comfortable. You can answer a breath from anywhere.";
            case 2 -> "Still comfortable, and the room is busier.";
            case 3 -> "The trip starts to cost you.";
            case 4 -> "Answering a breath is no longer enough.";
            default -> "The grace is shorter than the worst-case trip. Pre-empt or lose.";
        };
        return lead + String.format(" Grace %.1fs against a %.1fs trip.",
                g.grace(), Game.worstTrip());
    }

    static int aiLevel(int night) {
        try {
            var m = Game.class.getDeclaredMethod("aiLevel", int.class);
            m.setAccessible(true);
            return (int) m.invoke(null, night);
        } catch (Exception e) {
            return 0;
        }
    }

    /** One of the night's three tables, read out of the engine. */
    public static String table(int which) {
        StringBuilder sb = new StringBuilder("[");
        for (int n = 1; n <= 5; n++) {
            Game g = new Game(n, 1);
            double v = switch (which) {
                case 0 -> g.baseInterval();
                case 1 -> g.grace();
                default -> g.fredbearRate();
            };
            if (n > 1) sb.append(", ");
            sb.append(num(v));
        }
        return sb.append("]").toString();
    }

    static final String HELP =
            "Four sides to the room and one body. The bed is the hub, so a door "
            + "is one hop away and the doors are two hops from each other. Four "
            + "things count down in moves toward four stations, and the "
            + "countdown is audible: a step when one is a move out, a breath "
            + "when it is standing there. The light reaches one move out as "
            + "well as standing-here -- and every flash is noise, and noise is "
            + "what brings Fredbear, who does not walk and has to be found.";

    /** Where the phone's own copies of the art live. */
    static final Path ART = Path.of("art", "phone", "fnaf4");

    /**
     * The art, as data URIs.
     *
     * <p>Inlined rather than linked, because the build has to be one file.
     * The copies are downscaled by {@code tools/fnaf4-phone-art.py} and
     * committed, so this method is reproducible from Java alone.
     *
     * <p>Each station has three frames and the difference between them is the
     * game: dark is a station you have not lit, lit is the answer to the
     * question you were asking, and here is the thing standing in it. The
     * keys are the engine's own station names, because the page looks a
     * station up by name.
     */
    static String assets() throws Exception {
        StringBuilder b = new StringBuilder("{\n");
        String[] stations = {"BED", "LEFT", "RIGHT", "CLOSET"};
        boolean first = true;
        for (String st : stations) {
            for (String state : new String[]{"dark", "lit", "here"}) {
                if (!first) b.append(",\n");
                first = false;
                b.append("  \"").append(st).append(":").append(state).append("\":")
                 .append(uri(st.toLowerCase() + "_" + state + ".webp", "image/webp"));
            }
        }
        b.append(",\n  \"fredbear\":").append(uri("fredbear.webp", "image/webp"));
        for (String key : new String[]{"bonnie", "chica", "foxy", "fredbear", "freddy"}) {
            b.append(",\n  \"scare:").append(key).append("\":")
             .append(uri("scare_" + key + ".webp", "image/webp"));
        }
        return b.append("\n}").toString();
    }

    static String uri(String name, String mime) throws Exception {
        Path f = ART.resolve(name);
        if (!Files.exists(f)) {
            throw new IllegalStateException("no " + f + " -- build it with: "
                    + "python3 tools/fnaf4-phone-art.py");
        }
        return "\"data:" + mime + ";base64,"
                + Base64.getEncoder().encodeToString(Files.readAllBytes(f)) + "\"";
    }

    static String str(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A number the page and the engine can agree on exactly. */
    public static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
