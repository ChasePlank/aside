package aside.games.fnaf3;

import aside.games.fnaf3.engine.Game;
import aside.games.fnaf3.engine.House;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Build {@code web/fnaf3.html} from the page template and the engine.
 *
 * <p>Same shape as the other four ports -- FNAF 4's {@code WebRoom}, FNAF 6's
 * {@code WebSalvage}, FNAF 7's {@code WebShift}, FNAF 8's {@code WebMeeting}
 * and FNAF 9's {@code WebFeed} -- and for the same reason: a phone build
 * written by hand is a second copy of the game, and a second copy of the game
 * is a copy that disagrees with the first one eventually. So the page carries
 * the layout and the drawing, and <b>everything with a number in it comes
 * from the engine</b> -- the building's graph, the night tables, the phantom
 * table, and the constants the rules are made of.
 *
 * <p>What the port does not carry is the desktop's art: the sprites and the
 * rooms are megabytes and the page has to be one file, so the office and the
 * monitor are drawn and the things in them are shapes. The rules are the
 * desktop's to the number, and {@code java.util.Random} is reproduced in
 * BigInt -- including {@code nextInt}, which is a rejection sampler -- so the
 * same seed deals the same night in both builds.
 *
 *     java -cp classes aside.games.fnaf3.WebHouse
 */
public final class WebHouse {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/fnaf3.html");
        Files.writeString(out, html(), StandardCharsets.UTF_8);
        System.out.println("wrote " + out + " (" + Files.size(out) / 1024 + " KB)");
    }

    /** The finished page. */
    public static String html() throws Exception {
        String tpl = new String(WebHouse.class.getResourceAsStream("/fnaf3/web.html")
                .readAllBytes(), StandardCharsets.UTF_8);
        return tpl.replace("/*__CONTENT__*/", content())
                .replace(aside.game.WebAudio.MARKER, aside.game.WebAudio.js());
    }

    /** Everything the page needs that the engine owns. */
    static String content() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("const C = {\n");

        // The building. The graph is the game: every room has one way back
        // toward you, so drawing him into a room is drawing him onto a path.
        sb.append("  adj: [");
        for (int r = 0; r <= House.ROOMS; r++) {
            if (r > 0) sb.append(",");
            sb.append("\n    [");
            int[] n = House.neighbours(r);
            for (int i = 0; i < n.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(n[i]);
            }
            sb.append("]");
        }
        sb.append("\n  ],\n");
        sb.append("  toOffice: [");
        for (int r = 0; r <= House.ROOMS; r++) {
            if (r > 0) sb.append(", ");
            sb.append(House.TO_OFFICE[r]);
        }
        sb.append("],\n");
        sb.append("  roomName: [");
        for (int r = 0; r <= House.ROOMS; r++) {
            if (r > 0) sb.append(", ");
            sb.append(str(House.NAME[r]));
        }
        sb.append("],\n");

        sb.append("  aiLevel: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(Game.aiLevel(n));
        }
        sb.append("],\n");
        sb.append("  drainMult: ").append(table(0)).append(",\n");
        sb.append("  notes: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(str(note(n)));
        }
        sb.append("],\n");

        sb.append("  phantomNames: [");
        for (int i = 0; i < Game.PHANTOM_NAMES.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(str(Game.PHANTOM_NAMES[i]));
        }
        sb.append("],\n");
        sb.append("  phantomTakes: [");
        for (int i = 0; i < Game.PHANTOM_TAKES.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(str(Game.PHANTOM_TAKES[i].name()));
        }
        sb.append("],\n");
        sb.append("  phantomSlots: [");
        for (int i = 0; i < Game.PHANTOM_SLOTS.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(str(Game.PHANTOM_SLOTS[i].name()));
        }
        sb.append("],\n");

        sb.append("  hourSeconds: ").append(num(Game.HOUR_SECONDS)).append(",\n");
        sb.append("  nightHours: ").append(Game.NIGHT_HOURS).append(",\n");
        sb.append("  rebootTime: ").append(num(Game.REBOOT_TIME)).append(",\n");
        sb.append("  ventMax: ").append(num(Game.VENT_MAX)).append(",\n");
        sb.append("  ventDrain: ").append(num(Game.VENT_DRAIN)).append(",\n");
        sb.append("  lureDuration: ").append(num(Game.LURE_DURATION)).append(",\n");
        sb.append("  lureCooldown: ").append(num(Game.LURE_COOLDOWN)).append(",\n");
        sb.append("  moveMax: ").append(num(Game.MOVE_MAX)).append(",\n");
        sb.append("  moveMin: ").append(num(Game.MOVE_MIN)).append(",\n");
        sb.append("  graceMax: ").append(num(Game.GRACE_MAX)).append(",\n");
        sb.append("  graceMin: ").append(num(Game.GRACE_MIN)).append(",\n");
        sb.append("  ventFailSpeedup: ").append(num(Game.VENT_FAIL_SPEEDUP)).append(",\n");
        sb.append("  phantomLife: ").append(num(Game.PHANTOM_LIFE)).append(",\n");
        sb.append("  phantomRateCalm: ").append(num(Game.PHANTOM_RATE_CALM)).append(",\n");
        sb.append("  phantomRateFailing: ").append(num(Game.PHANTOM_RATE_FAILING)).append(",\n");
        sb.append("  phantomMax: ").append(Game.PHANTOM_MAX).append(",\n");
        sb.append("  help: ").append(str(HELP)).append(",\n");
        sb.append("  assets: ").append(assets()).append("\n");
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * The line under a night on the phone's menu.
     *
     * <p>The desktop has no night notes, so these are the phone's, and they
     * are built out of the engine's own numbers rather than written beside
     * them: the drain multiplier is the honest difficulty lever and the move
     * interval is what it costs, so the sentence cannot drift from the night
     * it describes.
     */
    static String note(int night) {
        Game g = new Game(night, 1);
        String lead = switch (night) {
            case 1 -> "The air lasts a hundred seconds. Learn the lure.";
            case 2 -> "The air goes faster. So does he.";
            case 3 -> "Two panels a night, and both of them cost you the lure.";
            case 4 -> "The air is the night now. Everything else is what you do between reboots.";
            default -> "He moves every " + String.format("%.1f", g.moveInterval())
                    + " seconds and stands in the office for "
                    + String.format("%.1f", g.officeGrace()) + ".";
        };
        return lead + String.format(" Air drains at %.2f a second.", g.drainRate());
    }

    /** One of the night's tables, read out of the engine. */
    public static String table(int which) {
        StringBuilder sb = new StringBuilder("[");
        for (int n = 1; n <= 5; n++) {
            Game g = new Game(n, 1);
            if (n > 1) sb.append(", ");
            sb.append(num(g.drainRate()));
        }
        return sb.append("]").toString();
    }

    static final String HELP =
            "One animatronic, no doors, no mask. He cannot be blocked and he "
            + "cannot be made to leave the building -- the only thing you can "
            + "do to him is make a noise somewhere else, and the lure has a "
            + "duration and a cooldown, so it buys distance rather than "
            + "removing him. The ventilation drains and has to be rebooted, "
            + "and a reboot takes both hands. And the phantoms cannot hurt "
            + "you: they take a system on the way out, chosen for you.";

    /** Where the phone's own copies of the art live. */
    static final Path ART = Path.of("art", "phone", "fnaf3");

    /**
     * The art, as data URIs.
     *
     * <p>Inlined rather than linked, because the build has to be one file.
     * The copies are downscaled by {@code tools/fnaf3-phone-art.py} and
     * committed, so this method is reproducible from Java alone.
     *
     * <p>The room keys are the engine's own room numbers -- room1 is the
     * Entrance and room10 is the Vent, which is House.NAME's order -- so a
     * page that looks a camera up by number cannot show the wrong one.
     */
    static String assets() throws Exception {
        StringBuilder b = new StringBuilder("{\n");
        b.append("  \"office\":").append(uri("office.webp", "image/webp"));
        for (int r = 1; r <= House.ROOMS; r++) {
            b.append(",\n  \"room").append(r).append("\":")
             .append(uri("room" + r + ".webp", "image/webp"));
        }
        b.append(",\n  \"springtrap\":").append(uri("springtrap.webp", "image/webp"));
        for (String key : new String[]{"freddy", "chica", "foxy", "mangle", "puppet", "bb"}) {
            b.append(",\n  \"phantom:").append(key).append("\":")
             .append(uri("phantom" + key + ".webp", "image/webp"));
        }
        return b.append("\n}").toString();
    }

    static String uri(String name, String mime) throws Exception {
        Path f = ART.resolve(name);
        if (!Files.exists(f)) {
            throw new IllegalStateException("no " + f + " -- build it with: "
                    + "python3 tools/fnaf3-phone-art.py");
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
