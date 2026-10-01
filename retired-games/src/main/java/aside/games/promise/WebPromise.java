package aside.games.promise;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Generate the single-file phone build of promise.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/promise/web.html) holds the layout and the styles. The
 * content -- the eight asks, the five nights of water, every label, and every
 * sentence the game says -- is generated from {@link Promise}, the same class
 * the engine screen reads. There is no second copy of the season.
 *
 * WHAT IS RESOLVED RATHER THAN PORTED. Every line of the report is a function of
 * one number, so all of them go out as tables: the worth line and the closing by
 * score, the breaks line by count, the load line by load, the over line by
 * overage, and the cost and ordinal words. The phone therefore contains no
 * sentence-building code at all and cannot say something the desktop would not
 * say at the same season.
 *
 * WHAT IS PORTED, AND WHY IT IS SAFE HERE. The rule -- what is due tonight and
 * what the water takes both come out of the same four crossings -- has to be
 * computed by the phone, because the night screen depends on it. There is no
 * seed and no deal: the season is the season, so the ported part is arithmetic
 * over fixed data and there is nothing for the two builds to disagree about
 * except the arithmetic itself. tools/promise-trace.mjs drives both models over
 * every answer set and every break choice and compares the load, the overage,
 * the counts and the report lines.
 *
 * WHAT THE PHONE CAN SEE THAT THE DESKTOP CANNOT. The asks and the water are in
 * the file, so a player who reads the source can find the whole season. That is
 * true of every single-file build and it is not worth hiding: the game is a
 * judgement, and a player who reads the JSON has already decided not to make
 * one. Relay said the same thing and for the same reason.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.promise.WebPromise [out.html]
 */
public final class WebPromise {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "promise", "web.html");

    /** The lowest score a player can reach: everything promised, everything broken. */
    static final int FLOOR = -Promise.ASKS;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/promise.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Promise.ASKS + " asks over " + Promise.NIGHTS + " nights, "
                + Promise.CAPACITY + " crossings a night)");
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
        b.append("\"wordmark\":").append(str(Promise.WORDMARK)).append(",\n");
        b.append("\"openTitle\":").append(str(Promise.OPEN_TITLE)).append(",\n");
        b.append("\"openSub\":").append(str(Promise.OPEN_SUB)).append(",\n");
        b.append("\"openSituation\":").append(strList(Promise.OPEN_SITUATION)).append(",\n");
        b.append("\"rulesHeading\":").append(str(Promise.RULES_HEADING)).append(",\n");
        b.append("\"rules\":[");
        for (int i = 0; i < Promise.RULES.length; i++) {
            String[] r = Promise.RULES[i];
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(r[0])).append(",\"text\":").append(str(r[1])).append('}');
        }
        b.append("],\n");
        b.append("\"startButton\":").append(str(Promise.START_BUTTON)).append(",\n");
        b.append("\"nights\":").append(Promise.NIGHTS).append(",\n");
        b.append("\"capacity\":").append(Promise.CAPACITY).append(",\n");
        b.append("\"max\":").append(Promise.MAX).append(",\n");

        // The asks, in the order they are made. The night each one is made on is
        // here because the phone has to walk the season itself.
        b.append("\"asks\":[");
        for (int i = 0; i < Promise.ASKS; i++) {
            Promise.Ask a = Promise.REQUESTS.get(i);
            if (i > 0) b.append(',');
            b.append("{\"who\":").append(str(a.who()))
             .append(",\"what\":").append(str(a.what()))
             .append(",\"cost\":").append(a.cost())
             .append(",\"due\":").append(a.due())
             .append(",\"askedOn\":").append(Promise.ASKED_ON[i]).append('}');
        }
        b.append("],\n");

        // The water, indexed by night, with a null at zero so a night number is
        // an index in the phone too.
        b.append("\"water\":[null");
        for (int n = 1; n <= Promise.NIGHTS; n++) {
            b.append(",{\"what\":").append(str(Promise.WATER[n].what()))
             .append(",\"cost\":").append(Promise.WATER[n].cost()).append('}');
        }
        b.append("],\n");

        // Every line the game says, emitted once per number rather than written
        // into the build by hand. This is the whole reason the phone cannot
        // disagree with the desktop about what a season means.
        // These three are indexed by the night or the cost itself, so they carry
        // a null at zero -- the same hole the water has. A table that starts at
        // one and is read at one is off by one everywhere and looks fine.
        b.append("\"nightLabel\":").append(indexedFromOne(Promise.NIGHTS, Promise::nightLabel)).append(",\n");
        b.append("\"ordinal\":").append(indexedFromOne(Promise.NIGHTS, Promise::ordinal)).append(",\n");
        b.append("\"costWord\":").append(indexedFromOne(2, Promise::costWord)).append(",\n");
        b.append("\"dueLine\":").append(indexed(0, Promise.ASKS - 1,
                i -> Promise.dueLine(Promise.REQUESTS.get(i)))).append(",\n");
        b.append("\"loadLine\":").append(indexed(0, 12, Promise::loadLine)).append(",\n");
        b.append("\"overLine\":").append(indexed(0, 12, Promise::overLine)).append(",\n");
        b.append("\"breaksLine\":").append(indexed(0, Promise.ASKS, Promise::breaksLine)).append(",\n");
        b.append("\"worthLine\":").append(indexed(FLOOR, Promise.MAX, Promise::worthLine)).append(",\n");
        b.append("\"closing\":").append(indexed(FLOOR, Promise.MAX, Promise::closing)).append(",\n");
        b.append("\"closingEmpty\":").append(str(Promise.CLOSING_EMPTY)).append(",\n");
        b.append("\"asksHead\":").append(str(Promise.ASKS_HEAD)).append(",\n");
        b.append("\"nothingPromised\":").append(str(Promise.NOTHING_PROMISED)).append(",\n");
        b.append("\"waterHead\":").append(str(Promise.WATER_HEAD)).append(",\n");
        b.append("\"dueHead\":").append(str(Promise.DUE_HEAD)).append(",\n");
        b.append("\"nothingDue\":").append(str(Promise.NOTHING_DUE)).append(",\n");
        b.append("\"fitsLine\":").append(str(Promise.FITS_LINE)).append(",\n");
        b.append("\"breakKey\":").append(str(Promise.BREAK_KEY)).append(",\n");
        b.append("\"goOn\":").append(str(Promise.GO_ON)).append(",\n");
        b.append("\"breakButton\":").append(str(Promise.BREAK_BUTTON)).append(",\n");
        b.append("\"goOnButton\":").append(str(Promise.GO_ON_BUTTON)).append(",\n");
        b.append("\"brokenTag\":").append(str(Promise.BROKEN_TAG)).append(",\n");
        b.append("\"reportHead\":").append(str(Promise.REPORT_HEAD)).append(",\n");
        b.append("\"again\":").append(str(Promise.AGAIN)).append(",\n");
        b.append("\"keptHead\":").append(str(Promise.KEPT_HEAD)).append(",\n");
        b.append("\"brokenHead\":").append(str(Promise.BROKEN_HEAD)).append(",\n");
        b.append("\"declinedHead\":").append(str(Promise.DECLINED_HEAD)).append(",\n");
        b.append("\"sayYes\":").append(str(Promise.SAY_YES)).append(",\n");
        b.append("\"sayNo\":").append(str(Promise.SAY_NO)).append(",\n");
        b.append("\"yesButton\":").append(str(Promise.SAY_YES_BUTTON)).append(",\n");
        b.append("\"noButton\":").append(str(Promise.SAY_NO_BUTTON)).append(",\n");
        b.append("\"rowWord\":{\"kept\":").append(str(Promise.KEPT_HEAD))
         .append(",\"broken\":").append(str(Promise.BROKEN_TAG))
         .append(",\"declined\":").append(str(Promise.DECLINED_HEAD)).append("}\n");
        b.append("}\n");
        return b.toString();
    }

    /**
     * A list of strings indexed by the number itself, from `lo` to `hi`.
     *
     * The offset matters: a score can be negative, and the phone indexes the
     * table with the score plus the floor rather than with the score, so the
     * table has no hole in it and no clamping is needed.
     */
    static String indexed(int lo, int hi, IntFunction<String> f) {
        StringBuilder b = new StringBuilder("[");
        for (int i = lo; i <= hi; i++) {
            if (i > lo) b.append(',');
            b.append(str(f.apply(i)));
        }
        return b.append(']').toString();
    }

    /** A table read at the number itself, with a null at zero. */
    static String indexedFromOne(int hi, IntFunction<String> f) {
        StringBuilder b = new StringBuilder("[null");
        for (int i = 1; i <= hi; i++) b.append(',').append(str(f.apply(i)));
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

    private WebPromise() {}
}
