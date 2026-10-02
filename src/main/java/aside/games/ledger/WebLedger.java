package aside.games.ledger;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generate the single-file phone build of ledger.
 *
 * The third of the eight to get one. Vigil was first (aside.games.vigil.WebVigil),
 * residue second (aside.games.residue.WebResidue), and this is the point at
 * which the phone route stops being a thing I do to a game and becomes a thing
 * every game has.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/ledger/web.html) holds the layout and the rules. The
 * content -- six nights, twenty-four details, three reckonings, the reckoning
 * schedule, and every fixed line the game says -- is generated from Ledger.java,
 * the same class the engine screen reads. The prose was moved out of
 * LedgerScreen into the model to make that true.
 *
 * Note what is generated rather than written twice: the two lines that depend
 * on a detail id (what you answered, and what the next write would cost) are
 * emitted once per detail, and the closing notes are emitted once per score.
 * There is no template language on the phone side and nothing to interpolate,
 * so there is nothing that can interpolate differently.
 *
 * WHAT IS WRITTEN TWICE. The ledger's arithmetic: five lines, writing a sixth
 * costs the oldest, the reckonings fall after nights two, four and six, and the
 * inspector is never told what you kept. That is about forty lines of
 * JavaScript. It is checked rather than trusted -- aside.games.ledger.SelfTest
 * regenerates this file and fails if the checked-in copy has gone stale, and
 * both builds were driven through the same playthroughs and compared.
 *
 * THE SAVE FORMAT IS THE SAME ONE. Ledger.serialize() writes a small plain-text
 * ledger and Ledger.deserialize() reads it; the phone build writes and reads
 * exactly that into localStorage, so a clerk can be carried between the desktop
 * and the phone by copying a few lines of text.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.ledger.WebLedger [out.html]
 */
public final class WebLedger {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "ledger", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/ledger.html");
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
        return (t.substring(0, at) + content() + t.substring(at + MARKER.length()))
                .replace(aside.game.WebAudio.MARKER, aside.game.WebAudio.js());
    }

    // ------------------------------------------------------------- content

    static String content() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("\"capacity\":").append(Ledger.CAPACITY).append(",\n");

        b.append("\"nights\":[\n");
        for (int i = 0; i < Ledger.NIGHTS.size(); i++) {
            Ledger.Night n = Ledger.NIGHTS.get(i);
            b.append("  {\"heading\":").append(str(n.heading))
             .append(",\"title\":").append(str(n.title))
             .append(",\"scene\":").append(str(n.scene))
             .append(",\"details\":[");
            for (int d = 0; d < n.details.size(); d++) {
                if (d > 0) b.append(',');
                b.append("{\"id\":").append(str(n.details.get(d).id))
                 .append(",\"text\":").append(str(n.details.get(d).text)).append('}');
            }
            b.append("]}");
            if (i < Ledger.NIGHTS.size() - 1) b.append(',');
            b.append('\n');
        }
        b.append("],\n");

        b.append("\"reckonings\":[");
        for (int i = 0; i < Ledger.RECKONINGS.size(); i++) {
            Ledger.Reckoning r = Ledger.RECKONINGS.get(i);
            if (i > 0) b.append(',');
            b.append("{\"question\":").append(str(r.question))
             .append(",\"answerId\":").append(str(r.answerId))
             .append(",\"explanation\":").append(str(r.explanation)).append('}');
        }
        b.append("],\n");

        b.append("\"reckoningAfter\":[");
        for (int i = 0; i < Ledger.RECKONING_AFTER.length; i++) {
            if (i > 0) b.append(',');
            b.append(Ledger.RECKONING_AFTER[i]);
        }
        b.append("],\n");

        // Every line that depends on a detail id, written once per detail here
        // so the phone build never has to build a sentence out of pieces.
        b.append("\"answered\":{");
        b.append("\"__none\":").append(str(Ledger.SAID_NOTHING));
        for (Ledger.Night n : Ledger.NIGHTS) {
            for (Ledger.Detail d : n.details) {
                b.append(',').append(str(d.id)).append(':').append(str(Ledger.answered(d.id)));
            }
        }
        b.append("},\n");

        b.append("\"costWarning\":{");
        boolean firstCost = true;
        for (Ledger.Night n : Ledger.NIGHTS) {
            for (Ledger.Detail d : n.details) {
                if (!firstCost) b.append(',');
                firstCost = false;
                b.append(str(d.id)).append(':').append(str(Ledger.costWarning(d.id)));
            }
        }
        b.append("},\n");

        b.append("\"answeredCount\":[");
        for (int c = 0; c <= Ledger.RECKONINGS.size(); c++) {
            if (c > 0) b.append(',');
            b.append(str(Ledger.answeredCount(c)));
        }
        b.append("],\n");

        b.append("\"endNotes\":[");
        for (int c = 0; c <= Ledger.RECKONINGS.size(); c++) {
            if (c > 0) b.append(',');
            b.append(str(Ledger.endNote(c)));
        }
        b.append("],\n");

        b.append("\"voice\":{")
         .append("\"inspectorNote\":").append(str(Ledger.INSPECTOR_NOTE)).append(',')
         .append("\"hadIt\":").append(str(Ledger.HAD_IT)).append(',')
         .append("\"didNotHaveIt\":").append(str(Ledger.DID_NOT_HAVE_IT)).append(',')
         .append("\"closes\":").append(str(Ledger.CLOSES)).append(',')
         .append("\"nothing\":").append(str(Ledger.NOTHING)).append(',')
         .append("\"iDoNotKnow\":").append(str(Ledger.I_DO_NOT_KNOW))
         .append("}\n");

        b.append("}\n");
        return b.toString();
    }

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

    private WebLedger() {}
}
