package aside.games.redaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * redaction, the model.
 *
 * The eleventh game, and the eleventh verb: the other ten are about what you
 * send. This one is about what you take out, and about the fact that taking it
 * out is itself a message.
 *
 * THE RULE THE WHOLE THING RESTS ON. A black bar is not a hole. It is a
 * pointer. The board does not read a withheld line as an absence; it reads it
 * as a place to look. So the only way to keep something out of the finding is
 * to put other things in front of it -- and every one of those is a bar too,
 * and every bar is counted.
 *
 * WHAT MAKES IT A GAME RATHER THAN A MENU. You cannot protect the person
 * without protecting the office, and you cannot protect the office without
 * gutting the file, and a gutted file is its own finding. The three things the
 * board writes down -- whether the complainant is named, how much of the
 * office's own record survived, and how much was withheld -- cannot all be
 * made good at once. Each failure you bury costs exactly one more bar, and the
 * bars are counted. That trade is the game.
 *
 * THE ONE THAT CANNOT BE BURIED. Line 5 is the first failure in the file and
 * the fifth line, so there are only four lines in front of it. A bar can only
 * be buried behind other bars, and there are not four other bars before it.
 * The earliest failure always comes out. That is not a difficulty setting; it
 * is a fact about the file, and it is the first thing a player learns.
 *
 * THE TRAP. Line 3 is harmless on its face -- the complainant asked not to be
 * named, and the request was noted. It is also the line that says a name
 * exists. Read it and the board learns to keep looking, and gets another
 * question to spend. The safest-looking line in the file is the one that
 * points at the person, which is the whole subject of the game in one line.
 */
public class Redaction {

    public static final String WORDMARK = "Redaction";
    public static final String WHERE_OPEN = "the records office";
    public static final String WHERE_FILE = "complaint 41-0887";
    public static final String WHERE_REPORT = "the board's finding";

    public static final int LINES = 16;
    /** How many withheld lines the board reads before its attention runs out. */
    public static final int DIGS = 5;

    public static final int SAFE = 0, FAIL = 1, NAME = 2;

    public static final List<String> OPENING = List.of(
            "Complaint 41-0887 is closed, and the board has asked for the file. "
                    + "Sixteen lines. You are the officer who sends it.",
            "You may withhold any line. A withheld line is not gone: the board "
                    + "sees that something was there, and reads it as a place to look.",
            "The board has time to read " + DIGS + " of them. It takes them in the "
                    + "order they appear in the file, and it stops when its time is out. "
                    + "Everything it reads goes in the finding.",
            "A line it reads that refers to the complainant gives it one more "
                    + "question to spend. It will spend it on the next line you withheld.",
            "The finding says three things: whether the complainant is named, how much "
                    + "of the office's own record survived, and how much of the file was "
                    + "withheld. You cannot make all three good.");

    public static final String RULES_HEADING = "THE DESK";

    public static final List<String[]> RULES = List.of(
            new String[]{"1 - 9", "select a line"},
            new String[]{"W", "withhold it. The board will see that it was withheld."},
            new String[]{"R", "release it. It goes in the finding as written."},
            new String[]{"", "ENTER sends the file. Nothing can be changed after that."},
            new String[]{"", "The board reads " + DIGS + " withheld lines, in file order, and stops."});

    public static final class Line {
        public final int number;      // 1..LINES
        public final String text;
        public final int kind;        // SAFE, FAIL, NAME
        /** A line the board reads that refers to the complainant buys it another
         *  question. See the note on line 3 at the top of this file. */
        public final boolean refers;
        public boolean withheld;

        Line(int number, String text, int kind, boolean refers) {
            this.number = number;
            this.text = text;
            this.kind = kind;
            this.refers = refers;
        }
    }

    /**
     * The file. Fixed, in this order, every time -- the same way Attribution's
     * copy is fixed and only the desk is dealt. Nothing here is random: the
     * game is entirely about where the bars go, and a file that moved would
     * make the one thing the player controls harder to learn.
     *
     * The four failures are the office's own: a visit not reassigned, a call
     * not returned, a file lost for a fortnight, an interview held without a
     * support person. The name is one line. Everything else is the ordinary
     * business of a complaint, and the ordinary business is what a decoy has
     * to be made of.
     */
    static final Object[][] FILE = {
            {1,  SAFE, false, "Complaint 41-0887 was received on 3 March and entered in the register the same day."},
            {2,  SAFE, false, "It concerned conditions at the Northgate cold storage, on Pier 4."},
            {3,  SAFE, true,  "The complainant asked that no name be recorded. The request was noted on the file."},
            {4,  SAFE, false, "A first inspection was set for 19 March."},
            {5,  FAIL, false, "The inspector assigned was away from the office from 12 to 26 March, and the visit was not reassigned."},
            {6,  SAFE, false, "The inspection was carried out on 2 April."},
            {7,  SAFE, false, "The cold store was found to be within the code at the time of the visit."},
            {8,  SAFE, true,  "The complainant telephoned on 9 April to ask what had been found."},
            {9,  FAIL, false, "The call was not returned."},
            {10, SAFE, false, "A second complaint from the same address, 41-0902, was received on 21 April."},
            {11, FAIL, false, "The file for 41-0887 could not be found between 14 and 30 April."},
            {12, SAFE, false, "It was in the deputy director's office, where it had been left."},
            {13, SAFE, true,  "The complainant was interviewed at their place of work on 6 May."},
            {14, NAME, false, "The complainant is D. Marchetti, of 11 Ash Street, employed at the Northgate site."},
            {15, FAIL, false, "The interview was held without a support person present."},
            {16, SAFE, false, "No further action was taken. The file was closed on 30 June."}};

    public final List<Line> lines = new ArrayList<>();

    /** Set when the file has gone to the board. The bars cannot be moved after. */
    public boolean sent;

    public Redaction() {
        for (Object[] row : FILE) {
            lines.add(new Line((Integer) row[0], (String) row[3], (Integer) row[1], (Boolean) row[2]));
        }
    }

    public static Redaction of() { return new Redaction(); }

    public int withheld() {
        int n = 0;
        for (Line l : lines) if (l.withheld) n++;
        return n;
    }

    public int released() { return LINES - withheld(); }

    public boolean anyWithheld() { return withheld() > 0; }

    // ------------------------------------------------------------- the board

    /** What the board read, in the order it read it. */
    public static final class Reading {
        public final List<Line> recovered = new ArrayList<>();
        /** Questions the board was given by a read line that refers. */
        public int extra = 0;
    }

    /**
     * The board reads the withheld lines in file order until its time is out.
     *
     * A read line that refers to the complainant buys it another question, so
     * the loop is not a fixed count -- and that is the only reason a bar can be
     * worse than no bar at all. It terminates because the index advances every
     * pass, however many questions are added.
     */
    public Reading reading() {
        Reading r = new Reading();
        List<Line> leads = new ArrayList<>();
        for (Line l : lines) if (l.withheld) leads.add(l);
        int digs = DIGS;
        int i = 0;
        while (digs > 0 && i < leads.size()) {
            Line l = leads.get(i++);
            r.recovered.add(l);
            digs--;
            if (l.refers) { digs++; r.extra++; }
        }
        return r;
    }

    /** A line the board knows about: released, or withheld and then read. */
    private boolean known(Line l, Reading r) {
        return !l.withheld || r.recovered.contains(l);
    }

    // ------------------------------------------------------------ the finding

    public static final class Finding {
        public final int withheld;
        public final boolean nameOut;
        public final int failsOut;
        public final int failsTotal;
        public final int extra;
        public final List<Line> recovered;

        Finding(int withheld, boolean nameOut, int failsOut, int failsTotal, int extra,
                List<Line> recovered) {
            this.withheld = withheld;
            this.nameOut = nameOut;
            this.failsOut = failsOut;
            this.failsTotal = failsTotal;
            this.extra = extra;
            this.recovered = recovered;
        }
    }

    public Finding finding() {
        Reading r = reading();
        boolean nameOut = false;
        int failsOut = 0, failsTotal = 0;
        for (Line l : lines) {
            if (l.kind == NAME && known(l, r)) nameOut = true;
            if (l.kind == FAIL) {
                failsTotal++;
                if (known(l, r)) failsOut++;
            }
        }
        return new Finding(withheld(), nameOut, failsOut, failsTotal, r.extra, r.recovered);
    }

    // ------------------------------------------------------------- the prose

    public static final String PERSON_NAMED = "The complainant is named in the finding.";
    public static final String PERSON_SAFE = "The complainant is not named in the finding.";

    public static String personLine(boolean nameOut) {
        return nameOut ? PERSON_NAMED : PERSON_SAFE;
    }

    public static final String RECORD_NONE =
            "No irregularity in the office's own record is noted.";
    public static final String RECORD_MINOR =
            "Minor irregularities in the office's own record are noted.";
    public static final String RECORD_CENSURED =
            "The office is censured on its own record.";

    public static String recordLine(int failsOut) {
        if (failsOut == 0) return RECORD_NONE;
        if (failsOut <= 2) return RECORD_MINOR;
        return RECORD_CENSURED;
    }

    public static final String HELD_FULL = "The file was released in full.";
    public static final String HELD_NARROW = "A narrow withholding.";
    public static final String HELD_NOTABLE = "A notable withholding.";
    public static final String HELD_HEAVY = "A heavy withholding.";
    public static final String HELD_GUTTED =
            "The file was gutted, and the board orders it released again.";

    /**
     * The bands are where the trace says they are, not where they felt right.
     *
     * The frontier is (4 failures out, 6 bars), (3, 7), (2, 8), (1, 9) -- one
     * more bar buys exactly one more buried failure, all the way along. So a
     * band boundary that does not fall between two frontier points is a
     * boundary that cannot be felt: it splits filings nobody is choosing
     * between. The boundaries at 6/7 and 8/9 are the two that separate the
     * four things a player can actually end up with. See Trace.
     */
    public static String withholdingLine(int withheld) {
        if (withheld == 0) return HELD_FULL;
        if (withheld <= 3) return HELD_NARROW;
        if (withheld <= 6) return HELD_NOTABLE;
        if (withheld <= 8) return HELD_HEAVY;
        return HELD_GUTTED;
    }

    /**
     * The one-line summary, for the top of the finding.
     *
     * There is no branch for a clean release, because there is no clean
     * release. Line 5 is the fifth line and the first failure, so there are
     * four lines in front of it and a bar is only buried by other bars. The
     * earliest failure is always read. SelfTest asserts it, so the missing
     * branch is a fact rather than an oversight.
     */
    public static String headline(Finding f) {
        return headline(f.nameOut, f.failsOut, f.withheld);
    }

    public static String headline(boolean nameOut, int failsOut, int withheld) {
        if (nameOut) return "The name is out.";
        if (failsOut >= 3) return "The office is on its own record.";
        if (withheld >= 9) return "The file was gutted.";
        return "A partial record.";
    }

    /**
     * The closing paragraph, as a template rather than a sentence.
     *
     * It depends on three numbers at once, so it cannot be emitted as a table
     * the way Inventory's report was. The phone build fills the same template
     * the desktop fills, which is the next best thing: the sentences live here
     * and nowhere else.
     */
    public static final String CLOSING_TEMPLATE =
            "You withheld %held% of the sixteen lines, and the board read %read% of them. %recovered%";

    public static final String RECOVERED_NONE =
            "It read nothing you withheld, because you withheld nothing.";
    public static final String RECOVERED_SOME = "What it read was: %lines%";

    public static String closing(Finding f) {
        String rec;
        if (f.recovered.isEmpty()) {
            rec = RECOVERED_NONE;
        } else {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < f.recovered.size(); i++) {
                if (i > 0) b.append(" ");
                b.append("line ").append(f.recovered.get(i).number).append(".");
            }
            rec = RECOVERED_SOME.replace("%lines%", b.toString());
        }
        return CLOSING_TEMPLATE
                .replace("%held%", String.valueOf(f.withheld))
                .replace("%read%", String.valueOf(f.recovered.size()))
                .replace("%recovered%", rec);
    }

    public static final String VERDICT_NAME_OUT =
            "The bar is not a hole. It is a pointer, and every one you drew pointed at "
                    + "something. The board followed the first five and found the person "
                    + "at the end of them.";

    public static final String VERDICT_RECORD_OPEN =
            "You spent every bar on the person and none on the office. What the board "
                    + "read was the office's own record, in the office's own words. The "
                    + "person is safe. Nothing else is.";

    public static final String VERDICT_RECORD_BURIED =
            "You spent the bars on the person and on one of the office's failures, and "
                    + "the board read the rest. The failure you buried is the one nobody "
                    + "will ever ask about, and the ones you did not are the finding.";

    public static final String VERDICT_PARTIAL =
            "Two of the office's failures are out of the board's reach and two are in "
                    + "it, and the person is not in it at all. That is a choice, and the "
                    + "board can see the shape of it even though it cannot see what you "
                    + "chose.";

    public static final String VERDICT_GUTTED =
            "You kept the person out of it and you kept almost all of the record out of "
                    + "it, and the cost is the file. This many bars is not a file with "
                    + "something missing. It is a file that has been made into a "
                    + "different document.";

    /**
     * The last word, chosen by what the board was left holding.
     *
     * The branches are the four points on the frontier, in order of how much
     * they cost: the record stands and the person is safe; the record is
     * partly buried; the record is buried and the file is thin; the file is
     * gone. A verdict that did not separate those four would be a verdict that
     * did not tell the player which of them they had reached.
     */
    public static String verdict(Finding f) {
        return verdict(f.nameOut, f.failsOut, f.withheld);
    }

    public static String verdict(boolean nameOut, int failsOut, int withheld) {
        if (nameOut) return VERDICT_NAME_OUT;
        if (failsOut >= 3) return withheld <= 6 ? VERDICT_RECORD_OPEN : VERDICT_RECORD_BURIED;
        if (withheld >= 9) return VERDICT_GUTTED;
        return VERDICT_PARTIAL;
    }

    /**
     * Every finding the board can reach, as a row of five strings.
     *
     * The phone build is not told the rules of the finding; it is told the
     * answers. The key is (nameOut * failsTotal + failsOut) * (LINES + 1) +
     * withheld, so the build computes three numbers and looks up one row --
     * the same move as Inventory's nine-answer report, taken to the whole
     * report rather than one line of it.
     */
    public static String[] findingEntry(boolean nameOut, int failsOut, int withheld) {
        return new String[]{
                headline(nameOut, failsOut, withheld),
                personLine(nameOut),
                recordLine(failsOut),
                withholdingLine(withheld),
                verdict(nameOut, failsOut, withheld)};
    }

    public static final int FAILS_TOTAL = 4;

    public static int findingKey(boolean nameOut, int failsOut, int withheld) {
        return ((nameOut ? 1 : 0) * (FAILS_TOTAL + 1) + failsOut) * (LINES + 1) + withheld;
    }

    public static int findingKeys() {
        return 2 * (FAILS_TOTAL + 1) * (LINES + 1);
    }

    public static final String STANDING =
            "The board does not know what you chose to protect. It only knows what you "
                    + "drew a bar over.";

    // ------------------------------------------------------------- the labels

    public static final String THE_FILE = "THE FILE AS IT WILL BE SENT";
    public static final String THE_FINDING = "THE FINDING";
    public static final String WHAT_WAS_READ = "WHAT THE BOARD READ";
    public static final String NOTHING_READ = "It read nothing. You withheld nothing.";
    public static final String WITHHELD_MARK = "withheld";
    public static final String RELEASED_MARK = "released";
    public static final String RECOVERED_MARK = "read by the board";
    public static final String NOT_REACHED = "not reached";
    public static final String REFERS_MARK = "refers to the complainant";
    public static final String FAIL_MARK = "the office's own record";
    public static final String NAME_MARK = "names the complainant";

    public static final String START_LINE = "ENTER to send the file.";
    public static final String START_BUTTON = "Send the file";
    public static final String SEND_BUTTON = "Send it";
    public static final String AGAIN = "Take the file again";
    public static final String WITHHOLD_BTN = "Withhold";
    public static final String RELEASE_BTN = "Release";
    public static final String HELD_LINE = "%n% of the sixteen lines withheld.";
    public static final String DIGS_LINE = "The board will read %n% of them.";
    public static final String EXTRA_LINE =
            "A line it read referred to the complainant, and bought it %n% more.";
    public static final String EXTRA_NONE = "Nothing it read sent it further.";
    public static final String SEND_WARNING = "Nothing can be changed after the file is sent.";

    public static String heldLine(int n) { return HELD_LINE.replace("%n%", String.valueOf(n)); }
    public static String digsLine(int n) { return DIGS_LINE.replace("%n%", String.valueOf(n)); }
    public static String extraLine(int n) { return EXTRA_LINE.replace("%n%", String.valueOf(n)); }

    public static String word(int n) {
        String[] w = {"none", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
                "fifteen", "sixteen"};
        return (n >= 0 && n < w.length) ? w[n] : String.valueOf(n);
    }

    public static String cap(String s) {
        return (s == null || s.isEmpty()) ? s
                : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // -------------------------------------------------------------- the file

    /**
     * The withheld set, one character per line, and whether it has been sent.
     *
     * The file itself never changes, so there is nothing else to keep: a
     * sixteen-character line is the whole of the state. That is the same
     * economy as Attribution's seed and for the same reason -- the game is
     * entirely in what the player decided, and everything else is fixed.
     */
    public void save(Path p) throws IOException {
        StringBuilder b = new StringBuilder();
        for (Line l : lines) b.append(l.withheld ? '1' : '0');
        b.append(sent ? " 1" : " 0");
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, b + "\n");
    }

    public static Redaction load(Path p) throws IOException {
        Redaction r = new Redaction();
        if (!Files.exists(p)) return r;
        String s = Files.readString(p).trim();
        String bits = s.split("\\s+")[0];
        for (int i = 0; i < LINES && i < bits.length(); i++) {
            r.lines.get(i).withheld = bits.charAt(i) == '1';
        }
        r.sent = s.endsWith("1");
        return r;
    }
}
