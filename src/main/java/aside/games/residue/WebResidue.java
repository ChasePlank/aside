package aside.games.residue;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generate the single-file phone build of residue.
 *
 * The engine is JavaFX and JavaFX does not run on a phone, so every code game
 * in the library is desktop-only unless somebody writes it a second time in a
 * language a phone can run. Vigil was the first (aside.games.vigil.WebVigil);
 * this is the second, and the pattern is now a pattern rather than an
 * experiment.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/residue/web.html) holds the layout and the rules. The
 * content -- the five things, their three gestures each, every decay line,
 * every arrival, the kind notes, the closing, the beat heads -- is generated
 * from Thing.java and Room.java, the same classes the engine screen reads. The
 * prose was moved out of ResidueScreen into the model to make that true, so a
 * sentence edited in the model is edited in both builds, and a sentence edited
 * in a screen is a sentence the other build does not have.
 *
 * WHAT IS WRITTEN TWICE. The room's arithmetic: capacity three, a thing ages
 * one step per visit, three unkept steps and it is gone, leaving something
 * already here changes it rather than adding a second one. That is about forty
 * lines of JavaScript. It is checked rather than trusted --
 * aside.games.residue.SelfTest regenerates this file and fails if the
 * checked-in copy has gone stale, and drives both builds through the same
 * rooms and compares what they say. A generated file that has gone stale is
 * worse than no file: it is a second copy of the game quietly disagreeing with
 * the first.
 *
 * THE SAVE FORMAT IS THE SAME ONE. Room.serialize() writes a small plain-text
 * room and Room.deserialize() reads it, and the phone build writes and reads
 * exactly that format into localStorage. So a room can be carried between the
 * desktop and the phone by copying six lines of text, which is the cheapest
 * kind of portability and the only kind this game needs.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.residue.WebResidue [out.html]
 */
public final class WebResidue {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "residue", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/residue.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB)");
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
        b.append("\"capacity\":").append(Room.CAPACITY).append(",\n");

        b.append("\"things\":[\n");
        Thing[] things = Thing.values();
        for (int i = 0; i < things.length; i++) {
            Thing t = things[i];
            b.append("  {\"id\":").append(str(t.id))
             .append(",\"kind\":").append(str(t.kind.name()))
             .append(",\"decay\":").append(t.decay)
             .append(",\"leavingHead\":").append(str(Room.headLeaving(t)))
             .append(",\"kindNote\":").append(str(t.kindNote()))
             .append(",\"gestures\":[");
            for (int g = 0; g < t.gestures.size(); g++) {
                if (g > 0) b.append(',');
                b.append("{\"id\":").append(str(t.gestures.get(g).id))
                 .append(",\"text\":").append(str(t.gestures.get(g).text)).append('}');
            }
            b.append("],\"decayLines\":{");
            // Keyed by age, and only the ages that have something to say. A
            // dense array would be ninety-eight nulls for the lamp, whose
            // decay is 99, and the lamp is the one thing in the room that
            // mostly does not decay at all.
            boolean first = true;
            for (int age = 1; age < t.decay; age++) {
                String line = t.decayText(age);
                if (line == null) continue;
                if (!first) b.append(',');
                first = false;
                b.append(str(String.valueOf(age))).append(':').append(str(line));
            }
            b.append("}}");
            if (i < things.length - 1) b.append(',');
            b.append('\n');
        }
        b.append("],\n");

        // The room you find on your first visit, straight out of the model.
        b.append("\"seeded\":[");
        Room seed = Room.seeded();
        for (int i = 0; i < seed.traces.size(); i++) {
            Trace t = seed.traces.get(i);
            if (i > 0) b.append(',');
            b.append("{\"thing\":").append(str(t.thing.id))
             .append(",\"gesture\":").append(str(t.gesture))
             .append(",\"age\":").append(t.age)
             .append(",\"mine\":").append(t.mine)
             .append(",\"bornVisit\":").append(t.bornVisit).append('}');
        }
        b.append("],\n");

        b.append("\"arrivals\":{")
         .append("\"first\":").append(str(Room.ARRIVAL_FIRST)).append(',')
         .append("\"empty\":").append(str(Room.ARRIVAL_EMPTY)).append(',')
         .append("\"lampOn\":").append(str(Room.ARRIVAL_LAMP_ON)).append(',')
         .append("\"lampOff\":").append(str(Room.ARRIVAL_LAMP_OFF)).append(',')
         .append("\"mineAging\":").append(str(Room.ARRIVAL_MINE_AGING)).append(',')
         .append("\"mine\":").append(str(Room.ARRIVAL_MINE)).append(',')
         .append("\"someone\":").append(str(Room.ARRIVAL_SOMEONE))
         .append("},\n");

        b.append("\"closingFull\":").append(str(Room.CLOSING_FULL)).append(",\n");
        b.append("\"nothingSurvived\":").append(str(Room.NOTHING_SURVIVED)).append(",\n");
        b.append("\"departureNothingLeft\":").append(str(Room.DEPARTURE_NOTHING_LEFT)).append(",\n");
        b.append("\"headPick\":").append(str(Room.HEAD_PICK)).append(",\n");
        b.append("\"headDeparture\":").append(str(Room.HEAD_DEPARTURE)).append(",\n");
        b.append("\"alreadyHere\":").append(str(Room.ALREADY_HERE)).append('\n');

        b.append("}\n");
        return b.toString();
    }

    /**
     * JSON string escaping, and the one thing that matters here: the prose is
     * full of apostrophes and em dashes, and it is embedded in a script tag, so
     * a literal &lt;/script&gt; in a sentence would end the block. Nothing
     * writes one today, and the escape is here so that nothing can.
     */
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

    private WebResidue() {}
}
