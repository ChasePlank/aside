package aside.games.drift;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * drift, the model.
 *
 * The eleventh game, and the second one you can be good at. Attribution was
 * the first: a scored night with a confusion matrix at the end. This one is
 * scored too, and its subject is the thing every other game in this library
 * has been circling -- what happens to a record while nobody is reading it.
 *
 * THE SITUATION. You kept a log. You were away. There are two copies of it in
 * front of you: the one you left in the drawer, and the one that is here now.
 * They are the same log. They are not the same.
 *
 * THE RULE THE WHOLE THING RESTS ON. A difference is not a change. Five lines
 * differ in what they say. Four differ only in how they say it -- the same
 * fact, written another way, by a hand that is not yours and a mood that is
 * not yours. Marking one of those costs you a line you did find, because a
 * report that flags every difference is a report nobody can use. So the game
 * is not "spot the difference". It is "decide what a difference means", and
 * the only instrument you have is your own reading of what a fact is.
 *
 * WHY IT IS FAIR. The facts are declared, not inferred. Every variant of every
 * line carries the facts it states, in canonical form, and the whole corpus is
 * checked against those declarations: a rewording must state exactly the facts
 * its line states, and a change must state at least one of them differently.
 * That check is the reason the prose and the answer key cannot drift apart --
 * see aside.games.drift.SelfTest.
 *
 * WHAT IS FIXED AND WHAT IS NOT. The sixteen lines are fixed: the same log
 * comes up every time. What the seed decides is which five of them changed,
 * which four were reworded, and which of each line's variants you are shown.
 * The log is not the variable. What happened to it is.
 */
public final class Drift {

    public static final String WORDMARK = "Drift";
    public static final String WHERE_THEN = "the copy you left";
    public static final String WHERE_NOW = "the copy that is here";
    public static final String WHERE_REPORT = "the report";

    public static final int LINES = 16;
    public static final int NIGHTS = 4;
    public static final int PER_NIGHT = 4;
    /** How many lines differ in what they say. */
    public static final int CHANGED = 5;
    /** How many differ only in how they say it. */
    public static final int REWORDED = 4;

    // ------------------------------------------------------------- the corpus

    /** One way a line can read, and the facts it states. */
    public record Variant(String text, String facts) {}

    /** One line of the log: what it said, and the ways it can read now. */
    public record Line(int night, Variant base, List<Variant> same, List<Variant> changed) {}

    public enum Kind { SAME, REWORDED, CHANGED }

    static Variant v(String text, String facts) { return new Variant(text, facts); }

    /**
     * The log. Sixteen lines, four nights, and for each line the ways it can
     * read: the base, two rewordings that state the same facts, and two
     * changes that state one of them differently.
     *
     * The `facts` string is the answer key, and it is written by hand next to
     * the prose on purpose. "gauge=3.1|watch=04:20|float=steady" is not
     * decoration: SelfTest parses it and checks that every rewording of the
     * line states exactly those facts and every change states one of them
     * differently. A line whose prose and whose key disagree fails the suite.
     */
    public static final List<Line> LOG = List.of(
        new Line(1,
            v("Gauge read 3.1 at the 04:20 watch; the float was steady all through it.",
              "gauge=3.1|watch=04:20|float=steady"),
            List.of(
                v("The 04:20 watch put the gauge at 3.1, float steady throughout.",
                  "gauge=3.1|watch=04:20|float=steady"),
                v("Gauge 3.1 at the 04:20 watch. Float steady.",
                  "gauge=3.1|watch=04:20|float=steady")),
            List.of(
                v("Gauge read 3.7 at the 04:20 watch; the float was steady all through it.",
                  "gauge=3.7|watch=04:20|float=steady"),
                v("The 04:20 watch put the gauge at 3.1, float unsteady throughout.",
                  "gauge=3.1|watch=04:20|float=unsteady"))),

        new Line(1,
            v("Wind backed to the south-west by six and held there until the change.",
              "wind=south-west|from=six"),
            List.of(
                v("By six the wind had backed south-west, and held until the change.",
                  "wind=south-west|from=six"),
                v("Wind SW by six; held until the change.",
                  "wind=south-west|from=six")),
            List.of(
                v("Wind backed to the south-east by six and held there until the change.",
                  "wind=south-east|from=six"),
                v("Wind backed to the south-west by nine and held there until the change.",
                  "wind=south-west|from=nine"))),

        new Line(1,
            v("The lamp on the outer mark was out; the depot was told at 09:00.",
              "lamp=outer|state=out|told=09:00"),
            List.of(
                v("Outer mark lamp out. Depot told at nine.",
                  "lamp=outer|state=out|told=09:00"),
                v("The outer mark's lamp was out, and the depot heard about it at 09:00.",
                  "lamp=outer|state=out|told=09:00")),
            List.of(
                v("The lamp on the inner mark was out; the depot was told at 09:00.",
                  "lamp=inner|state=out|told=09:00"),
                v("Outer mark lamp out; the depot was told at 11:00.",
                  "lamp=outer|state=out|told=11:00"))),

        new Line(1,
            v("No traffic after the 19:10; the board went quiet at eight.",
              "last=19:10|quiet=eight"),
            List.of(
                v("Nothing moved after the 19:10; the board was quiet from eight.",
                  "last=19:10|quiet=eight"),
                v("Board quiet from eight. Last traffic the 19:10.",
                  "last=19:10|quiet=eight")),
            List.of(
                v("No traffic after the 21:10; the board went quiet at eight.",
                  "last=21:10|quiet=eight"),
                v("No traffic after the 19:10; the board went quiet at nine.",
                  "last=19:10|quiet=nine"))),

        new Line(2,
            v("The pump in the north culvert ran for eleven minutes and stopped clean.",
              "pump=north|ran=eleven"),
            List.of(
                v("North culvert pump ran eleven minutes, stopped clean.",
                  "pump=north|ran=eleven"),
                v("The north culvert's pump ran for eleven minutes and stopped without complaint.",
                  "pump=north|ran=eleven")),
            List.of(
                v("The pump in the north culvert ran for seventeen minutes and stopped clean.",
                  "pump=north|ran=seventeen"),
                v("The pump in the south culvert ran for eleven minutes and stopped clean.",
                  "pump=south|ran=eleven"))),

        new Line(2,
            v("Vessel 4 passed the outer mark at 11:40 and did not answer the lamp.",
              "vessel=4|passed=11:40|answered=no"),
            List.of(
                v("Vessel 4 cleared the outer mark at 11:40; no answer to the lamp.",
                  "vessel=4|passed=11:40|answered=no"),
                v("At 11:40 Vessel 4 passed the outer mark without answering the lamp.",
                  "vessel=4|passed=11:40|answered=no")),
            List.of(
                v("Vessel 9 passed the outer mark at 11:40 and did not answer the lamp.",
                  "vessel=9|passed=11:40|answered=no"),
                v("Vessel 4 passed the outer mark at 14:10 and did not answer the lamp.",
                  "vessel=4|passed=14:10|answered=no"))),

        new Line(2,
            v("Barometer fell four points across the afternoon and kept falling.",
              "barometer=four|direction=falling"),
            List.of(
                v("The barometer lost four points in the afternoon and went on falling.",
                  "barometer=four|direction=falling"),
                v("Barometer down four points over the afternoon; still falling.",
                  "barometer=four|direction=falling")),
            List.of(
                v("Barometer fell nine points across the afternoon and kept falling.",
                  "barometer=nine|direction=falling"),
                v("Barometer rose four points across the afternoon and kept rising.",
                  "barometer=four|direction=rising"))),

        new Line(2,
            v("The spare float is in the second drawer, behind the log books.",
              "drawer=second|place=behind the log books"),
            List.of(
                v("Spare float: second drawer, behind the log books.",
                  "drawer=second|place=behind the log books"),
                v("The spare float sits behind the log books in the second drawer.",
                  "drawer=second|place=behind the log books")),
            List.of(
                v("The spare float is in the third drawer, behind the log books.",
                  "drawer=third|place=behind the log books"),
                v("The spare float is in the second drawer, in front of the log books.",
                  "drawer=second|place=in front of the log books"))),

        new Line(3,
            v("Gauge read 4.2 at the 03:50 watch, the highest this month.",
              "gauge=4.2|watch=03:50"),
            List.of(
                v("The 03:50 watch gave 4.2, the month's highest.",
                  "gauge=4.2|watch=03:50"),
                v("Gauge 4.2 at 03:50, the highest this month.",
                  "gauge=4.2|watch=03:50")),
            List.of(
                v("Gauge read 4.2 at the 05:30 watch, the highest this month.",
                  "gauge=4.2|watch=05:30"),
                v("Gauge read 4.8 at the 03:50 watch, the highest this month.",
                  "gauge=4.8|watch=03:50"))),

        new Line(3,
            v("The tide staff on the east groyne is two marks out and needs replacing.",
              "staff=east|out=two marks"),
            List.of(
                v("East groyne tide staff: two marks out, needs replacing.",
                  "staff=east|out=two marks"),
                v("The east groyne's tide staff is out by two marks and wants replacing.",
                  "staff=east|out=two marks")),
            List.of(
                v("The tide staff on the west groyne is two marks out and needs replacing.",
                  "staff=west|out=two marks"),
                v("The tide staff on the east groyne is four marks out and needs replacing.",
                  "staff=east|out=four marks"))),

        new Line(3,
            v("A trawler worked the bay all night and showed no lights at all.",
              "boat=trawler|lights=none"),
            List.of(
                v("Trawler in the bay all night, no lights shown.",
                  "boat=trawler|lights=none"),
                v("All night a trawler worked the bay and showed nothing.",
                  "boat=trawler|lights=none")),
            List.of(
                v("A coaster worked the bay all night and showed no lights at all.",
                  "boat=coaster|lights=none"),
                v("A trawler worked the bay all night and showed running lights.",
                  "boat=trawler|lights=running"))),

        new Line(3,
            v("The telephone line to the depot was down from 22:00 until dawn.",
              "line=down|from=22:00|until=dawn"),
            List.of(
                v("Depot line down 22:00 to dawn.",
                  "line=down|from=22:00|until=dawn"),
                v("From 22:00 the depot line was down, and it stayed down until dawn.",
                  "line=down|from=22:00|until=dawn")),
            List.of(
                v("The telephone line to the depot was down from 20:00 until dawn.",
                  "line=down|from=20:00|until=dawn"),
                v("The telephone line to the depot was down from 22:00 until noon.",
                  "line=down|from=22:00|until=noon"))),

        new Line(4,
            v("The relief keeper is due on the 14th and will bring the new seals.",
              "relief=14th|brings=seals"),
            List.of(
                v("Relief keeper due the 14th, bringing the new seals.",
                  "relief=14th|brings=seals"),
                v("On the 14th the relief keeper comes, with the new seals.",
                  "relief=14th|brings=seals")),
            List.of(
                v("The relief keeper is due on the 4th and will bring the new seals.",
                  "relief=4th|brings=seals"),
                v("The relief keeper is due on the 14th and will bring the new float.",
                  "relief=14th|brings=float"))),

        new Line(4,
            v("Two of the six batteries in the lamp room read low on the test.",
              "batteries=two of six|read=low"),
            List.of(
                v("Lamp room batteries: two of six low on test.",
                  "batteries=two of six|read=low"),
                v("The lamp room test left two of the six batteries reading low.",
                  "batteries=two of six|read=low")),
            List.of(
                v("Four of the six batteries in the lamp room read low on the test.",
                  "batteries=four of six|read=low"),
                v("Two of the six batteries in the lamp room read high on the test.",
                  "batteries=two of six|read=high"))),

        new Line(4,
            v("The north culvert was clear at the 16:00 check and running free.",
              "culvert=north|check=16:00|state=clear"),
            List.of(
                v("North culvert clear at the 16:00 check, running free.",
                  "culvert=north|check=16:00|state=clear"),
                v("At the 16:00 check the north culvert was clear and running free.",
                  "culvert=north|check=16:00|state=clear")),
            List.of(
                v("The north culvert was clear at the 18:00 check and running free.",
                  "culvert=north|check=18:00|state=clear"),
                v("The south culvert was clear at the 16:00 check and running free.",
                  "culvert=south|check=16:00|state=clear"))),

        new Line(4,
            v("Nothing else to report; the log is signed and the lamp is lit.",
              "log=signed|lamp=lit"),
            List.of(
                v("Nothing further. Log signed, lamp lit.",
                  "log=signed|lamp=lit"),
                v("The lamp is lit, the log is signed, and there is nothing else to report.",
                  "log=signed|lamp=lit")),
            List.of(
                v("Nothing else to report; the log is signed and the lamp is out.",
                  "log=signed|lamp=out"),
                v("Nothing else to report; the log is unsigned and the lamp is lit.",
                  "log=unsigned|lamp=lit"))));

    // ------------------------------------------------------------- the draw

    /**
     * The whole night's answer key, from a seed.
     *
     * Counter-based, not a stream: every number is a pure function of
     * (seed, salt), so there is no RNG position to carry and the phone build
     * can compute the same night without porting java.util.Random. That is
     * what makes the port possible and what makes it checkable -- see
     * tools/drift-trace.mjs.
     */
    static long mix(long seed, int salt) {
        long z = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A number in [0, n), unsigned, from a seed and a salt. */
    public static int pick(long seed, int salt, int n) {
        if (n <= 1) return 0;
        return (int) Long.remainderUnsigned(mix(seed, salt), n);
    }

    /** One line of the night: what it says now, and what that makes it. */
    public record Row(int index, int night, String then, String now, Kind kind) {
        public String thenFacts() { return LOG.get(index).base().facts(); }
    }

    public final long seed;
    public final List<Row> rows;
    public final boolean[] marked = new boolean[LINES];
    public boolean reported;

    Drift(long seed) {
        this.seed = seed;
        this.rows = deal(seed);
    }

    /**
     * Which lines changed, which were reworded, and which variant of each you
     * are shown.
     *
     * The order is a rank, not a shuffle: each line gets a number from the
     * seed, the lines are sorted by it, and the first CHANGED of that order
     * changed, the next REWORDED were reworded, and the rest are the same
     * line twice. A rank is used rather than a rejection loop so the count is
     * exact by construction -- there is no way for a seed to produce four
     * changes or six.
     */
    static List<Row> deal(long seed) {
        Integer[] order = new Integer[LINES];
        for (int i = 0; i < LINES; i++) order[i] = i;
        final long s = seed;
        java.util.Arrays.sort(order, (a, b) -> {
            long ra = mix(s, 1000 + a), rb = mix(s, 1000 + b);
            int c = Long.compareUnsigned(ra, rb);
            return c != 0 ? c : Integer.compare(a, b);
        });
        Kind[] kind = new Kind[LINES];
        for (int i = 0; i < LINES; i++) kind[i] = Kind.SAME;
        for (int i = 0; i < CHANGED; i++) kind[order[i]] = Kind.CHANGED;
        for (int i = CHANGED; i < CHANGED + REWORDED; i++) kind[order[i]] = Kind.REWORDED;

        List<Row> out = new ArrayList<>();
        for (int i = 0; i < LINES; i++) {
            Line ln = LOG.get(i);
            String now = ln.base().text();
            if (kind[i] == Kind.CHANGED) {
                now = ln.changed().get(pick(seed, 200 + i, ln.changed().size())).text();
            } else if (kind[i] == Kind.REWORDED) {
                now = ln.same().get(pick(seed, 300 + i, ln.same().size())).text();
            }
            out.add(new Row(i, ln.night(), ln.base().text(), now, kind[i]));
        }
        return out;
    }

    public static Drift of(long seed) { return new Drift(seed); }
    public static Drift of() { return new Drift(System.nanoTime()); }

    // ------------------------------------------------------------ the scoring

    public int markedCount() {
        int n = 0;
        for (boolean b : marked) if (b) n++;
        return n;
    }

    /** Lines marked that had changed. */
    public int hits() {
        int n = 0;
        for (int i = 0; i < LINES; i++) if (marked[i] && rows.get(i).kind() == Kind.CHANGED) n++;
        return n;
    }

    /** Lines marked that had not. */
    public int falseAlarms() {
        int n = 0;
        for (int i = 0; i < LINES; i++) if (marked[i] && rows.get(i).kind() != Kind.CHANGED) n++;
        return n;
    }

    /** Changes that were not marked. */
    public int missed() { return CHANGED - hits(); }

    /**
     * The score: what you found, less what you claimed that was not there.
     *
     * Never below zero. Marking everything scores zero, and so does marking
     * nothing, which is the point -- a report is only worth what it gets
     * right, and a report that flags every difference is worth nothing.
     */
    public int score() { return Math.max(0, hits() - falseAlarms()); }

    public void toggle(int i) {
        if (i >= 0 && i < LINES) marked[i] = !marked[i];
    }

    // -------------------------------------------------------------- the file

    /**
     * The night, as two lines of plain text: the seed, then the marks.
     *
     * The seed is the whole night, so a report can be picked up exactly where
     * it was left -- and a player can read the file and see which lines they
     * had marked, which is the same joke Ledger's plain-text save makes.
     */
    public void save(Path p) throws IOException {
        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < LINES; i++) marks.append(marked[i] ? '1' : '0');
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, seed + "\n" + marks + "\n" + (reported ? 1 : 0) + "\n");
    }

    public static Drift load(Path p) throws IOException {
        if (!Files.exists(p)) return of();
        List<String> lines = Files.readAllLines(p);
        if (lines.isEmpty()) return of();
        long seed;
        try { seed = Long.parseLong(lines.get(0).trim()); } catch (Exception e) { return of(); }
        Drift d = of(seed);
        if (lines.size() > 1) {
            String m = lines.get(1).trim();
            for (int i = 0; i < LINES && i < m.length(); i++) d.marked[i] = m.charAt(i) == '1';
        }
        if (lines.size() > 2) d.reported = lines.get(2).trim().equals("1");
        return d;
    }

    // -------------------------------------------------------------- the prose

    public static final List<String> OPENING = List.of(
            "You kept the log. Then you were away.",
            "Two copies of it are in front of you: the one you left in the drawer, "
                    + "and the one that is here now. They are the same log.",
            "They are not the same.");

    public static final String RULES_HEADING = "WHAT YOU ARE LOOKING AT";

    public static final List<String[]> RULES = List.of(
            new String[] { "Five lines differ in what they say.",
                    "A number, a name, a time, a direction. Something happened to the record." },
            new String[] { "Four differ only in how they say it.",
                    "The same fact, written another way. Nothing happened to the record." },
            new String[] { "Mark the five.",
                    "Every line you mark that had not changed costs you one that had." });

    public static final String THEN_HEAD = "THE COPY YOU LEFT";
    public static final String NOW_HEAD = "THE COPY THAT IS HERE";
    public static final String REPORT_ROW = "File the report";
    public static final String HINT_READ = "up/down read    SPACE mark    ENTER report (from the last row)";
    public static final String HINT_REPORT = "ENTER another copy    ESC the library";
    public static final String START_LINE = "ENTER to compare the two copies.";
    public static final String START_BUTTON = "Compare them";
    public static final String AGAIN = "Read another copy";
    public static final String NOT_HERE = "The report is filed from the last row.";

    public static final String REPORT_HEAD = "THE REPORT";
    public static final String MARKED_HEAD = "WHAT YOU MARKED";
    public static final String MISSED_HEAD = "WHAT YOU MISSED";
    public static final String FALSE_HEAD = "WHAT YOU MARKED THAT HAD NOT CHANGED";
    public static final String NOTHING_MISSED = "Nothing. You found all five.";
    public static final String NOTHING_FALSE = "Nothing. Every mark was a change.";
    public static final String NOTHING_MARKED = "You marked nothing at all.";
    public static final String NO_CHANGES = "(no changes)";

    /** What you found, which is not the same number as what the report is worth. */
    public static String foundLine(int hits) {
        if (hits <= 0) return "You found none of " + word(CHANGED) + ".";
        return "You found " + word(hits) + " of " + word(CHANGED) + ".";
    }

    public static String markedLine(int marked) {
        if (marked <= 0) return "You marked nothing.";
        return "You marked " + word(marked) + (marked == 1 ? " line." : " lines.");
    }

    public static String falseLine(int n) {
        if (n == 0) return "No mark was wasted.";
        return cap(word(n)) + (n == 1 ? " of them had not changed." : " of them had not changed.");
    }

    public static String missedLine(int n) {
        if (n == 0) return NOTHING_MISSED;
        return cap(word(n)) + (n == 1 ? " change went past you." : " changes went past you.");
    }

    /**
     * The score, said out loud. It is the found count less the wasted marks,
     * and the report says both so the arithmetic is never a mystery.
     */
    public static String worthLine(int score) {
        if (score <= 0) return "The report is worth nothing.";
        if (score >= CHANGED) return "The report is worth all five.";
        return "The report is worth " + word(score) + ".";
    }

    /**
     * The closing sentence. One per score, because a scored game that says the
     * same thing at four and at one is not reading the report back.
     */
    public static String closing(int score) {
        return switch (score) {
            case 5 -> "You read the same log twice and it agreed with itself except "
                    + "where it did not. That is the whole job.";
            case 4 -> "Four of five. The one that got past you is in the numbers, or "
                    + "in a line you read twice and decided to believe.";
            case 3 -> "Three. The record did not change much. It did not have to. It "
                    + "only had to change where nobody was looking.";
            case 2 -> "Two. Half of what moved, moved in the numbers. Numbers do not "
                    + "look wrong. They look like numbers.";
            case 1 -> "One. You found the one that shouted and walked past the ones "
                    + "that did not.";
            default -> "A copy you do not compare is a copy you are guessing about. "
                    + "You had two and you read one.";
        };
    }

    /** "5" -> "five". Small numbers only; the game never needs more. */
    public static String word(int n) {
        String[] w = { "no", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
                "fifteen", "sixteen" };
        return n >= 0 && n < w.length ? w[n] : String.valueOf(n);
    }

    public static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The facts of a variant, as a map, for the checks and the phone build. */
    public static Map<String, String> facts(String spec) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String part : spec.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0) m.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return m;
    }
}
