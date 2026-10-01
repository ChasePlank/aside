package aside.games.relay;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Generate the single-file phone build of relay.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/relay/web.html) holds the layout and the styles. The
 * content -- the six messages, every way of carrying each, every label the
 * report draws, and every sentence the game says -- is generated from
 * {@link Relay}, the same class the engine screen reads. There is no second
 * copy of the night.
 *
 * WHAT IS RESOLVED RATHER THAN PORTED. Every line of the report is a function
 * of one number, so all of them go out as tables: the worth line, the breaks
 * line and the closing by score, and the three words a single message can be
 * worth. The phone therefore contains no sentence-building code, and cannot say
 * something the desktop would not say at the same score.
 *
 * WHAT IS PORTED, AND WHY IT IS SAFE HERE. The rule -- a message is worth the
 * smaller of what it carries -- has to be computed by the phone, because the
 * report depends on it. Unlike drift there is no seed and no deal: the six
 * messages are the six messages, so the ported part is arithmetic over fixed
 * data and there is nothing for the two builds to disagree about except the
 * arithmetic itself. tools/relay-trace.mjs drives both models over every
 * message and every option and compares the counts, the worth, the kept and
 * lost labels, and the report lines at every score.
 *
 * WHAT THE PHONE CAN SEE THAT THE DESKTOP CANNOT. The element ids are in the
 * file, so a player who reads the source can find the answer key. That is true
 * of every single-file build and it is not worth hiding: the game is a
 * judgement, and a player who reads the JSON has already decided not to make
 * one. Drift could avoid it by dealing; relay cannot, because its content is
 * fixed. Said here rather than left for someone to discover.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.relay.WebRelay [out.html]
 */
public final class WebRelay {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "relay", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/relay.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Relay.MESSAGES + " messages, " + (Relay.MESSAGES * Relay.OPTIONS)
                + " ways of carrying them)");
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
        b.append("\"wordmark\":").append(str(Relay.WORDMARK)).append(",\n");
        b.append("\"openTitle\":").append(str(Relay.OPEN_TITLE)).append(",\n");
        b.append("\"openSub\":").append(str(Relay.OPEN_SUB)).append(",\n");
        b.append("\"openSituation\":").append(strList(Relay.OPEN_SITUATION)).append(",\n");
        b.append("\"rulesHeading\":").append(str(Relay.RULES_HEADING)).append(",\n");
        b.append("\"rules\":[");
        for (int i = 0; i < Relay.RULES.length; i++) {
            String[] r = Relay.RULES[i];
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(r[0])).append(",\"text\":").append(str(r[1])).append('}');
        }
        b.append("],\n");
        b.append("\"startButton\":").append(str(Relay.START_BUTTON)).append(",\n");
        b.append("\"arrow\":").append(str(Relay.ARROW)).append(",\n");
        b.append("\"carryNote\":").append(str(Relay.CARRY_NOTE)).append(",\n");
        b.append("\"max\":").append(Relay.MAX).append(",\n");

        // The messages, with the elements each one carries and the elements each
        // way of carrying it keeps. The labels are here because the report draws
        // them; the keyword is not, because the phone never checks the prose.
        b.append("\"messages\":[");
        for (int m = 0; m < Relay.NIGHT.size(); m++) {
            Relay.Message msg = Relay.NIGHT.get(m);
            if (m > 0) b.append(',');
            b.append("{\"from\":").append(str(msg.from()))
             .append(",\"to\":").append(str(msg.to()))
             .append(",\"situation\":").append(str(msg.situation()))
             .append(",\"facts\":").append(elements(msg.facts()))
             .append(",\"points\":").append(elements(msg.points()))
             .append(",\"options\":[");
            for (int o = 0; o < msg.options().size(); o++) {
                Relay.Rendering r = msg.options().get(o);
                if (o > 0) b.append(',');
                b.append("{\"text\":").append(str(r.text()))
                 .append(",\"facts\":").append(strList(r.facts()))
                 .append(",\"points\":").append(strList(r.points())).append('}');
            }
            b.append("]}");
        }
        b.append("],\n");

        // Every line of the report, emitted once per number rather than written
        // into the build by hand. This is the whole reason the phone cannot
        // disagree with the desktop about what a score means.
        b.append("\"progress\":").append(indexed(Relay.MESSAGES - 1,
                i -> String.format(Relay.PROGRESS, i + 1, Relay.MESSAGES))).append(",\n");
        b.append("\"worthWord\":").append(indexed(Relay.PER_MESSAGE, Relay::worthWord)).append(",\n");
        b.append("\"worthLine\":").append(indexed(Relay.MAX, Relay::worthLine)).append(",\n");
        b.append("\"breaksLine\":").append(indexed(Relay.MESSAGES, Relay::breaksLine)).append(",\n");
        b.append("\"closing\":").append(indexed(Relay.MAX, Relay::closing)).append(",\n");
        b.append("\"reportHead\":").append(str(Relay.REPORT_HEAD)).append(",\n");
        b.append("\"keptHead\":").append(str(Relay.KEPT_HEAD)).append(",\n");
        b.append("\"lostHead\":").append(str(Relay.LOST_HEAD)).append(",\n");
        b.append("\"nothingLost\":").append(str(Relay.NOTHING_LOST)).append(",\n");
        b.append("\"again\":").append(str(Relay.AGAIN)).append("\n");
        b.append("}\n");
        return b.toString();
    }

    static String elements(List<Relay.Element> els) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < els.size(); i++) {
            if (i > 0) b.append(',');
            b.append("{\"id\":").append(str(els.get(i).id()))
             .append(",\"label\":").append(str(els.get(i).label())).append('}');
        }
        return b.append(']').toString();
    }

    /** A list of strings, indexed by the number itself. Slot 0 is real, not blank. */
    static String indexed(int max, IntFunction<String> f) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i <= max; i++) {
            if (i > 0) b.append(',');
            b.append(str(f.apply(i)));
        }
        return b.append(']').toString();
    }

    static String strList(List<String> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) b.append(',');
            b.append(str(xs.get(i)));
        }
        return b.append(']').toString();
    }

    static String strList(String[] xs) { return strList(List.of(xs)); }

    /** JSON string escaping; the prose is embedded in a script tag. */
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

    private WebRelay() {}
}
