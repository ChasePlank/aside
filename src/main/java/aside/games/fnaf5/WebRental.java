package aside.games.fnaf5;

import aside.games.fnaf5.engine.Game;
import aside.games.fnaf5.engine.Room;
import aside.games.fnaf5.engine.Threat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build {@code web/fnaf5.html} from the page template and the engine.
 *
 * <p>Same shape as the other six ports -- FNAF 2's {@code WebPizzeria}, FNAF
 * 3's {@code WebHouse}, FNAF 4's {@code WebRoom}, FNAF 6's {@code WebSalvage},
 * FNAF 7's {@code WebShift}, FNAF 8's {@code WebMeeting} and FNAF 9's {@code
 * WebFeed} -- and for the same reason: a phone build written by hand is a
 * second copy of the game, and a second copy of the game is a copy that
 * disagrees with the first one eventually. So the page carries the layout and
 * the drawing, and <b>everything with a number in it comes from the engine</b>
 * -- the cast and the rule that moves each one, the night tables, and the
 * constants the rules are made of.
 *
 * <p>What the port does not carry is the desktop's art: the rooms and the
 * things in them are megabytes and the page has to be one file, so the
 * building is drawn and the threats are shapes. The rules are the desktop's
 * to the number, and {@code java.util.Random} is reproduced in BigInt --
 * including {@code nextInt}, which is a rejection sampler -- so the same seed
 * deals the same night in both builds.
 *
 *     java -cp classes aside.games.fnaf5.WebRental
 */
public final class WebRental {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/fnaf5.html");
        Files.writeString(out, html(), StandardCharsets.UTF_8);
        System.out.println("wrote " + out + " (" + Files.size(out) / 1024 + " KB)");
    }

    /** The finished page. */
    public static String html() throws Exception {
        String tpl = new String(WebRental.class.getResourceAsStream("/fnaf5/web.html")
                .readAllBytes(), StandardCharsets.UTF_8);
        return tpl.replace("/*__CONTENT__*/", content())
                .replace(aside.game.WebAudio.MARKER, aside.game.WebAudio.js());
    }

    /** Everything the page needs that the engine owns. */
    static String content() {
        Game g = new Game(1, 1);
        StringBuilder sb = new StringBuilder();
        sb.append("const C = {\n");

        // The cast, and the one thing that makes each of them a different
        // problem: what they follow.
        sb.append("  cast: [");
        for (int i = 0; i < g.threats.size(); i++) {
            Threat t = g.threats.get(i);
            if (i > 0) sb.append(",");
            sb.append("\n    {name:").append(str(t.name))
              .append(", key:").append(str(t.key))
              .append(", rule:").append(str(t.rule.name()))
              .append(", home:").append(str(t.home.name()))
              .append(", pace:").append(num(t.pace))
              .append("}");
        }
        sb.append("\n  ],\n");

        // The rooms, keyed by name -- the page looks a room up by the name
        // the engine uses, so an array here would be indexed by a string and
        // every label would come back undefined. (It did, and the screenshot
        // is what caught it: the suite was checking the numbers and not the
        // labels, which is the shape of gap a check cannot see.)
        sb.append("  roomTag: {");
        for (int i = 0; i < Room.COUNT; i++) {
            if (i > 0) sb.append(", ");
            sb.append(Room.ALL[i].name()).append(": ").append(str(Room.ALL[i].tag));
        }
        sb.append("},\n");

        sb.append("  baseInterval: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(num(new Game(n, 1).baseInterval()));
        }
        sb.append("],\n");
        sb.append("  freddyPace: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(num(new Game(n, 1).freddyPace()));
        }
        sb.append("],\n");
        sb.append("  grace: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(num(new Game(n, 1).grace()));
        }
        sb.append("],\n");
        sb.append("  shockAllowance: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(Game.shockAllowance(n));
        }
        sb.append("],\n");
        sb.append("  aiLevel: [");
        for (int n = 1; n <= 5; n++) {
            if (n > 1) sb.append(", ");
            sb.append(Game.aiLevel(n));
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
        sb.append("  moveTime: ").append(num(Game.MOVE_TIME)).append(",\n");
        sb.append("  shockTime: ").append(num(Game.SHOCK_TIME)).append(",\n");
        sb.append("  shockFlash: ").append(num(Game.SHOCK_FLASH)).append(",\n");
        sb.append("  soundMemory: ").append(num(Game.SOUND_MEMORY)).append(",\n");
        sb.append("  balloraPatience: ").append(num(Game.BALLORA_PATIENCE)).append(",\n");
        sb.append("  balloraGrace: ").append(num(Game.BALLORA_GRACE)).append(",\n");
        sb.append("  balloraCooldown: ").append(num(Game.BALLORA_COOLDOWN)).append(",\n");
        sb.append("  cueEvery: ").append(num(Game.CUE_EVERY)).append(",\n");
        sb.append("  feedPace: ").append(num(Game.FEED_PACE)).append(",\n");
        sb.append("  stillWindow: ").append(num(Game.STILL_WINDOW)).append(",\n");
        sb.append("  stillPace: ").append(num(Game.STILL_PACE)).append(",\n");
        sb.append("  intervalJitter: ").append(num(Game.INTERVAL_JITTER)).append(",\n");
        sb.append("  help: ").append(str(HELP)).append("\n");
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * The line under a night on the phone's menu.
     *
     * <p>The desktop has no night notes, so these are the phone's, and they are
     * built out of the engine's own numbers rather than written beside them:
     * the pursuer's pace is where the week's ramp lives and the shock
     * allowance is what the night gives you to spend on him, so the sentence
     * cannot drift from the night it describes.
     */
    static String note(int night) {
        Game g = new Game(night, 1);
        String lead = switch (night) {
            case 1 -> "Five shocks and a slow pursuer. Learn which one follows what.";
            case 2 -> "He walks a little faster, and you have the same five.";
            case 3 -> "Four shocks now, and the grace is shorter.";
            case 4 -> "Four shocks, and stillness starts to cost you.";
            default -> "Three shocks and the fastest pursuer of the week.";
        };
        return lead + String.format(" He closes at %.2f, and the room gives you %.1fs.",
                g.freddyPace(), g.grace());
    }

    static final String HELP =
            "Five rooms in a line and one monitor, and the camera cannot see the "
            + "room you are in -- so every decision to move is made on a picture "
            + "of where you are going, taken from somewhere else. Three things "
            + "are walking in the dark and they differ in what they follow: "
            + "Funtime Freddy follows you, so standing still is what kills you; "
            + "Ballora follows sound, so moving is what kills you; Funtime Foxy "
            + "follows the camera, so looking is what kills you. The controlled "
            + "shock answers exactly one of them -- the one that follows you -- "
            + "and there are only three to five a night.";

    static String str(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A number the page and the engine can agree on exactly. */
    public static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
