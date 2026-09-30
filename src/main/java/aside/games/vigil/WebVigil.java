package aside.games.vigil;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generate the single-file phone build of vigil.
 *
 * Why this exists: the engine is JavaFX, and JavaFX does not run on a phone.
 * The .aside stories already have a way onto a phone (aside.engine.WebExport),
 * but every game of mine is code rather than script, so none of them has ever
 * been playable anywhere except a desktop. This is the first one that is.
 *
 * The important part is what is NOT duplicated. The template holds the layout
 * and the rules; the content -- the five things and their four states each,
 * the twelve day lines, the premise, the rules panel, the arithmetic, every
 * arrival and every closing -- is generated from the model in Vigil.java, the
 * same one the engine screen reads. So the writing lives in one place, and a
 * change to it cannot leave the phone build saying something the desktop build
 * does not.
 *
 * What IS written twice is the rules themselves: keep costs one, change costs
 * two, three unkept days is the whole of a thing. That is thirty lines of
 * arithmetic in a language a phone can run, and it is checked -- SelfTest
 * regenerates the file and fails if the checked-in copy has gone stale, so the
 * two builds cannot drift apart silently.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.vigil.WebVigil [out.html]
 */
public final class WebVigil {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "vigil", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/vigil.html");
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
        b.append("\"promise\":").append(str(Vigil.PROMISE)).append(",\n");
        b.append("\"days\":").append(Vigil.DAYS).append(",\n");
        b.append("\"full\":").append(Vigil.FULL).append(",\n");
        b.append("\"keepCost\":").append(Vigil.KEEP_COST).append(",\n");
        b.append("\"changeCost\":").append(Vigil.CHANGE_COST).append(",\n");
        b.append("\"maxSurvivors\":").append(Vigil.maxSurvivors()).append(",\n");
        b.append("\"totalUnits\":").append(Vigil.totalUnits()).append(",\n");

        b.append("\"units\":[");
        for (int d = 1; d <= Vigil.DAYS; d++) {
            if (d > 1) b.append(',');
            b.append(Vigil.unitsFor(d));
        }
        b.append("],\n");

        b.append("\"words\":[");
        for (int i = 0; i < Vigil.WORDS.length; i++) {
            if (i > 0) b.append(',');
            b.append(str(Vigil.WORDS[i]));
        }
        b.append("],\n");

        b.append("\"dayLines\":[");
        for (int d = 1; d <= Vigil.DAYS; d++) {
            if (d > 1) b.append(',');
            b.append(str(Vigil.dayLine(d)));
        }
        b.append("],\n");

        b.append("\"things\":[");
        for (int i = 0; i < Vigil.defaultThings().size(); i++) {
            Vigil.Thing t = Vigil.defaultThings().get(i);
            if (i > 0) b.append(',');
            b.append("{\"name\":").append(str(t.name))
             .append(",\"where\":").append(str(t.where))
             .append(",\"kept\":").append(str(t.keptText))
             .append(",\"changed\":").append(str(t.changedText))
             .append(",\"replaced\":").append(str(t.replacedText))
             .append(",\"lost\":").append(str(t.lostText))
             .append('}');
        }
        b.append("],\n");

        b.append("\"open\":").append(strArray(Vigil.openParagraphs())).append(",\n");

        b.append("\"rules\":[");
        String[][] rules = Vigil.rules();
        for (int i = 0; i < rules.length; i++) {
            if (i > 0) b.append(',');
            b.append('[').append(str(rules[i][0])).append(',').append(str(rules[i][1])).append(']');
        }
        b.append("],\n");

        b.append("\"sums\":").append(strArray(Vigil.sums())).append(",\n");

        b.append("\"arrivals\":{")
         .append("\"nothing\":").append(str(Vigil.ARRIVAL_NOTHING)).append(',')
         .append("\"kept\":").append(str(Vigil.ARRIVAL_KEPT)).append(',')
         .append("\"allYours\":").append(str(Vigil.ARRIVAL_ALL_YOURS)).append(',')
         .append("\"mixed\":").append(str(Vigil.ARRIVAL_MIXED))
         .append("},\n");

        b.append("\"closings\":{")
         .append("\"nothing\":").append(str(Vigil.CLOSING_NOTHING)).append(',')
         .append("\"madeIt\":").append(str(Vigil.CLOSING_MADE_IT)).append(',')
         .append("\"kept\":").append(str(Vigil.CLOSING_KEPT)).append(',')
         .append("\"allYours\":").append(str(Vigil.CLOSING_ALL_YOURS)).append(',')
         .append("\"mixed\":").append(str(Vigil.CLOSING_MIXED))
         .append("}\n");

        b.append("}\n");
        return b.toString();
    }

    static String strArray(String[] xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) b.append(',');
            b.append(str(xs[i]));
        }
        return b.append(']').toString();
    }

    /**
     * JSON string escaping, and the one thing that matters here: the prose is
     * full of apostrophes and em dashes, and it is embedded in a script tag,
     * so a literal &lt;/script&gt; in a sentence would end the block. Nothing
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

    private WebVigil() {}
}
