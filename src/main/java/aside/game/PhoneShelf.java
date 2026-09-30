package aside.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

/**
 * The phone shelf: every web build, in one file.
 *
 * The problem this solves is not a game problem, it is a delivery problem. The
 * library screen is JavaFX and lives on the desktop; the phone builds are
 * separate HTML files in web/, and before this there was no way to get from one
 * to the other except by already knowing a filename. Four files to download and
 * keep together is not a shelf, it is a folder.
 *
 * So: web/aside.html contains every web build, base64'd, in one document, and
 * plays them in an iframe. One file to download, one file to open, everything
 * in it, and the relative links between games stop mattering because there are
 * none. The iframe is not decoration -- it is what keeps each game's CSS and
 * its globals to itself, so two games cannot collide in one document.
 *
 * WHAT IS NOT DUPLICATED. The shelf is derived, not listed: it walks
 * Games.all() and puts on the shelf every game that has a web build, reading
 * the title and the blurb off the game itself. Add a game, give it a phone
 * build, and it is on the shelf with no change here. The one thing declared by
 * hand is the builds that are not games -- the demo story, which has no Game
 * module.
 *
 * AND IT SAYS WHAT IS MISSING. The same walk produces the games with no phone
 * build, and they are listed underneath the shelf under their own heading.
 * Without that, a shelf of six reads as the whole library -- and the games
 * absent from it are precisely the ones a player cannot tell are absent. They
 * are drawn as text and not as cards, because there is nothing on the other
 * end of them on a phone.
 *
 * AND IT IS CHECKED. aside.engine.SelfTest fails if the shelf has gone stale,
 * if a file it lists is missing, or if web/ has a build the shelf does not
 * mention. That last one is the failure that would otherwise be silent: a new
 * game gets a phone build and the shelf quietly does not know about it.
 *
 * Run from the repository root:
 *   java -cp classes aside.game.PhoneShelf
 */
public final class PhoneShelf {

    static final String MARKER = "/*__SHELF__*/";
    static final String MISSING_MARKER = "/*__MISSING__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "web", "shelf.html");
    static final Path WEB = Path.of("web");

    /** The shelf's own filename. It is the one build the shelf does not list. */
    public static final String SHELF_FILE = "aside.html";

    /** One thing on the shelf. */
    public record Entry(String file, String title, String blurb, String note) {}

    /**
     * Builds that are not games.
     *
     * Night Shift is the engine's demo story and the audit bot's test fixture;
     * it has no Game module because it is not a game, it is a script, and the
     * shelf is for things you can play on a phone. It is playable, so it is on
     * the shelf, and the note says what it is.
     */
    static final List<Entry> STORIES = List.of(
            new Entry("web/night-shift.html", "Night Shift",
                    "A night shift at the pizzeria, and the first thing the engine could do.",
                    "Roxanne's. Also the audit bot's test fixture \u2014 two of its bugs are planted.")
    );

    /** A line about how this one plays, for the games that need one. */
    static String noteFor(String id) {
        return switch (id) {
            case "residue" -> "Keeps its room in this browser. Come back and it will have aged.";
            case "ledger" -> "Keeps its ledger in this browser. Six nights, and one sitting.";
            case "testimony" -> "Keeps its account in this browser. Eight questions, and one sitting.";
            case "outside" -> "Keeps its week in this browser. Seven days, and one sitting.";
            case "handoff" -> "Keeps your orders in this browser. Five watches, and one sitting.";
            case "inventory" -> "Keeps the bench in this browser. Eight objects, and one sitting.";
            case "bearings" -> "Keeps the voyage in this browser. Sixteen days, and one sitting.";
            case "vigil" -> "Twelve days. One sitting.";
            case "corroboration" -> "Keeps the night in this browser. Two observers, and one sitting.";
            case "attribution" -> "Scored, and dealt fresh every night. Five calls, one sitting.";
            case "overtime" -> "A visual novel. What you say carries.";
            default -> "";
        };
    }

    /**
     * What is on the shelf, in the order the library shows them.
     *
     * Derived from Games.all(), so a game with a phone build is on the shelf
     * without anybody remembering to add it.
     */
    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Game g : Games.all()) {
            String file = "web/" + g.id() + ".html";
            if (!Files.exists(Path.of(file))) continue;
            out.add(new Entry(file, g.title(), g.blurb(), noteFor(g.id())));
        }
        for (Entry e : STORIES) if (Files.exists(Path.of(e.file()))) out.add(e);
        return out;
    }

    /**
     * The games that have no phone build yet, in library order.
     *
     * The shelf's other half, and it was missing. A shelf of six looked like
     * the whole library, and the games absent from it were exactly the ones
     * nobody could tell were absent -- the four that are desktop-only because
     * the engine is JavaFX. Derived from Games.all() the same way entries() is,
     * so a game that gets a build leaves this list and joins the shelf in the
     * same edit, with nothing to remember.
     */
    public static List<Entry> missing() {
        List<Entry> out = new ArrayList<>();
        for (Game g : Games.all()) {
            if (Files.exists(Path.of("web", g.id() + ".html"))) continue;
            out.add(new Entry("web/" + g.id() + ".html", g.title(), g.blurb(), ""));
        }
        return out;
    }

    /** Every web build on disk, by filename, except the shelf itself. */
    public static List<String> webBuilds() throws Exception {
        if (!Files.isDirectory(WEB)) {
            throw new IllegalStateException("no " + WEB.toAbsolutePath() + " -- run from the repository root");
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(WEB)) {
            s.map(p -> p.getFileName().toString())
             .filter(n -> n.endsWith(".html"))
             .filter(n -> !n.equals(SHELF_FILE))
             .sorted()
             .forEach(out::add);
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/" + SHELF_FILE);
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + entries().size() + " on the shelf, " + missing().size() + " still on the desktop)");
    }

    /** The generated file, as a string, so a test can compare it to the checked-in one. */
    public static String html() throws Exception {
        if (!Files.exists(TEMPLATE)) {
            throw new IllegalStateException("no template at " + TEMPLATE.toAbsolutePath()
                    + " -- run this from the repository root");
        }
        String t = Files.readString(TEMPLATE);
        for (String marker : List.of(MARKER, MISSING_MARKER)) {
            if (t.indexOf(marker) < 0) {
                throw new IllegalStateException("the template has no " + marker + " in it");
            }
        }
        String out = t.substring(0, t.indexOf(MARKER)) + shelf()
                   + t.substring(t.indexOf(MARKER) + MARKER.length());
        int at = out.indexOf(MISSING_MARKER);
        return out.substring(0, at) + missingJson() + out.substring(at + MISSING_MARKER.length());
    }

    /**
     * The missing list, in the shelf's own shape minus the build.
     *
     * There is no file to inline, so these entries carry a title and a line and
     * nothing else. They are not links and they are not buttons: there is
     * nothing on the other end of them on a phone, and a card that looks
     * tappable and is not is worse than a list.
     */
    static String missingJson() {
        StringBuilder b = new StringBuilder("[\n");
        List<Entry> m = missing();
        for (int i = 0; i < m.size(); i++) {
            Entry e = m.get(i);
            b.append("  {\"title\":").append(str(e.title()))
             .append(",\"blurb\":").append(str(e.blurb())).append('}');
            if (i < m.size() - 1) b.append(',');
            b.append('\n');
        }
        return b.append("]\n").toString();
    }

    // -------------------------------------------------------------- the shelf

    static String shelf() throws Exception {
        StringBuilder b = new StringBuilder("[\n");
        List<Entry> entries = entries();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            Path f = Path.of(e.file());
            if (!Files.exists(f)) {
                throw new IllegalStateException("the shelf lists " + e.file() + " and it is not there");
            }
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(f));
            b.append("  {\"title\":").append(str(e.title()))
             .append(",\"blurb\":").append(str(e.blurb()))
             .append(",\"note\":").append(str(e.note()))
             .append(",\"file\":").append(str(e.file()))
             .append(",\"kb\":").append(Files.size(f) / 1024)
             // Base64 on purpose. The builds are whole HTML documents with
             // their own <script> tags in them, so embedding them as text
             // would need the one sequence that can end a script block to be
             // escaped everywhere it appears. Base64 has no < in it at all.
             .append(",\"html\":").append(str(base64))
             .append('}');
            if (i < entries.size() - 1) b.append(',');
            b.append('\n');
        }
        return b.append("]\n").toString();
    }

    /** JSON string escaping. Base64 never needs it; the titles and blurbs might. */
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

    private PhoneShelf() {}
}
