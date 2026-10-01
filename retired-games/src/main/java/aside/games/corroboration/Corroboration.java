package aside.games.corroboration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * corroboration -- the model.
 *
 * A hill station, two observers, and one night that has to be written down.
 *
 * The entry has four lines: the hour, the bearing, the height, the motion.
 * Each has three possible values. Two people were awake for it, both have
 * already told you what they saw, and neither of them is an instrument.
 *
 * The rule the game is built on: <b>an observer who is wrong is wrong the
 * same way every time.</b> They do not misread the night. They report what
 * they expected to see, and they report it on every night, in the same voice,
 * with the same confidence. So the account is not noise around the truth that
 * could be averaged away. It is a fixed value wearing the truth's clothes.
 *
 * That has a consequence the whole game turns on: <b>a clean check is not
 * proof.</b> You check a person by asking them about the eleventh, which is
 * in the log and which you can read. If they answer something else, they are
 * caught -- but catching them tells you only that they are stuck, never what
 * the fourteenth was. If they answer the eleventh correctly, they are either
 * straight, or stuck on a value that happens to be the eleventh's. The check
 * cannot tell you which, and the second case is the one that ends up in the
 * log.
 *
 * The arithmetic is small on purpose. Four lines, two observers, seven
 * questions. One line both observers are stuck on -- there is no answer to it
 * that is not somebody's expectation, so it can only be left blank. The other
 * three cost one question each if you check the right person first and two if
 * you do not, and you cannot know which until you have spent it.
 *
 * Nothing here is drawn. The separation is the point: the rule can be tested
 * without a screen, which is what SelfTest does.
 */
public final class Corroboration {

    // ------------------------------------------------------------- the shape

    public enum Axis { TIME, BEARING, HEIGHT, MOTION }

    public static final Axis[] AXES = Axis.values();
    public static final int N = AXES.length;
    public static final int VALUES = 3;

    public static final String[] OBSERVERS = { "Hallam", "Voss" };

    /**
     * Eight questions.
     *
     * Four lines, two observers. Two checks a line is what it costs to be
     * sure: one to see what a person says, and one to see whether the first
     * answer could have been otherwise. Four lines at two checks is eight,
     * exactly -- so a player who never wastes a question can settle every
     * line that can be settled, and a player who trusts a single clean check
     * has spent the same eight and knows less.
     */
    public static final int BUDGET = 8;

    /** A line the player declined to establish. Not a failure; a record. */
    public static final int BLANK = -1;

    public static final String NIGHT = "the fourteenth";
    public static final String KNOWN_NIGHT = "the eleventh";

    public static String axisName(int a) {
        return switch (AXES[a]) {
            case TIME -> "the hour";
            case BEARING -> "the bearing";
            case HEIGHT -> "the height";
            case MOTION -> "the motion";
        };
    }

    public static String valueName(int a, int v) {
        if (v == BLANK) return "--";
        return switch (AXES[a]) {
            case TIME -> new String[] { "22:00", "23:00", "00:00" }[v];
            case BEARING -> new String[] { "north-east", "east", "south-east" }[v];
            case HEIGHT -> new String[] { "low", "half", "high" }[v];
            case MOTION -> new String[] { "fixed", "drifting", "crossing" }[v];
        };
    }

    // ------------------------------------------------------------ the night

    public final int seed;

    /** What actually happened. The player never sees this until the end. */
    public final int[] truth = new int[N];

    /** The eleventh, already in the log, and readable. This is the ruler. */
    public final int[] logged = new int[N];

    /** [observer][axis] -- is this person stuck on this line? */
    public final boolean[][] stuck = new boolean[2][N];

    /**
     * [observer][axis] -- the value they report when stuck.
     *
     * Not an offset. An offset can be measured and subtracted, and a witness
     * you can correct is not a witness, it is an instrument. This is the value
     * itself, and it is the same on every night, which is exactly what makes
     * it undetectable from one clean check.
     */
    public final int[][] expected = new int[2][N];

    // -------------------------------------------------------- the questions

    public int left = BUDGET;
    public final boolean[][] asked = new boolean[2][N];
    public final int[][] answer = new int[2][N];

    // ------------------------------------------------------------ the filing

    public final int[] filed = { BLANK, BLANK, BLANK, BLANK };
    public boolean filedDone = false;

    private Corroboration(int seed) {
        this.seed = seed;
        for (int o = 0; o < 2; o++) {
            for (int a = 0; a < N; a++) answer[o][a] = BLANK;
        }
    }

    // ------------------------------------------------------------- the draw

    /**
     * A pure function of (seed, salt).
     *
     * Not a stream. There is no RNG position to carry, so a seed replays a
     * night identically anywhere -- which is what lets a phone build run this
     * same model rather than a second copy of it, and what lets a trace
     * compare the two.
     */
    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static int draw(int seed, int salt, int n) {
        long h = mix((long) seed * 0x100000001B3L + (long) salt * 0xC2B2AE3D27D4EB4FL);
        // remainderUnsigned, NOT floorMod. The hash is a 64-bit value and half
        // the time its top bit is set, so as a signed long it is negative --
        // and floorMod on a negative long is the signed residue, which differs
        // from the unsigned one by (2^64 mod n). The phone build takes the
        // unsigned remainder, because a BigInt has no sign to lose, so the two
        // builds disagreed about the night on their first trace. The draw is
        // defined on the 64 bits, not on the number Java reads them as.
        return (int) Long.remainderUnsigned(h, n);
    }

    public static Corroboration newNight(int seed) {
        Corroboration c = new Corroboration(seed);
        for (int a = 0; a < N; a++) {
            c.truth[a] = draw(seed, 100 + a, VALUES);
            c.logged[a] = draw(seed, 200 + a, VALUES);
        }

        // One line both observers are stuck on. Nobody can settle it, and it
        // is the line that has to be left blank -- which is the only part of
        // the entry that will still be true in a year.
        int shared = draw(seed, 300, N);

        int[] others = new int[N - 1];
        int k = 0;
        for (int a = 0; a < N; a++) if (a != shared) others[k++] = a;
        int hallamPrivate = others[draw(seed, 301, N - 1)];

        int[] rest = new int[N - 2];
        k = 0;
        for (int a : others) if (a != hallamPrivate) rest[k++] = a;
        int vossPrivate = rest[draw(seed, 302, N - 2)];

        c.stuck[0][shared] = true;
        c.stuck[0][hallamPrivate] = true;
        c.stuck[1][shared] = true;
        c.stuck[1][vossPrivate] = true;

        for (int o = 0; o < 2; o++) {
            for (int a = 0; a < N; a++) {
                if (c.stuck[0][a] && c.stuck[1][a]) {
                    // Nobody was watching this line. Their expectation cannot
                    // be the log's own value, or the check would come back
                    // clean on both of them and the one line that cannot be
                    // settled would look settled.
                    int e = draw(seed, 400 + o * 10 + a, VALUES - 1);
                    if (e >= c.logged[a]) e++;
                    c.expected[o][a] = e;
                } else {
                    c.expected[o][a] = draw(seed, 400 + o * 10 + a, VALUES);
                }
            }
        }
        return c;
    }

    // ------------------------------------------------------------- the rule

    /**
     * What an observer says a value was.
     *
     * A straight observer says what happened. The other one says what they
     * expected -- and says it about the eleventh too, which is how they are
     * caught, and says it about the fourteenth, which is how the log gets
     * written wrong.
     */
    public int report(int obs, int axis, int actual) {
        return stuck[obs][axis] ? expected[obs][axis] : actual;
    }

    /** The account of the fourteenth. Free: two people told you what they saw. */
    public int claim(int obs, int axis) { return report(obs, axis, truth[axis]); }

    // -------------------------------------------------------- the questions

    public boolean canCheck(int obs, int axis) {
        return asked[obs][axis] || left > 0;
    }

    /**
     * Spend a question: one observer, one line, against the eleventh.
     *
     * Returns what they said. Checking the same thing twice is free and
     * returns what it returned the first time -- a question you have already
     * paid for is not a question you have to pay for again.
     */
    public int check(int obs, int axis) {
        if (asked[obs][axis]) return answer[obs][axis];
        if (left <= 0) return BLANK;
        asked[obs][axis] = true;
        left--;
        answer[obs][axis] = report(obs, axis, logged[axis]);
        return answer[obs][axis];
    }

    // --------------------------------------------------------- what is known

    /** The check came back clean: they said the eleventh's own value. */
    public boolean cleared(int obs, int axis) {
        return asked[obs][axis] && answer[obs][axis] == logged[axis];
    }

    /** The check came back dirty: they said something that was not the eleventh. */
    public boolean caught(int obs, int axis) {
        return asked[obs][axis] && answer[obs][axis] != logged[axis];
    }

    /**
     * The check was worth something.
     *
     * A clean check proves a person straight only if it could have come back
     * dirty. A stuck observer answers with their expected value, so a clean
     * check on a stuck observer means their expected value happens to be the
     * eleventh's -- and then their account of the fourteenth is that same
     * value, which is why a clean check on it says nothing.
     *
     * Which means the test is visible from the outside: a person is proved
     * straight when they came back clean AND their account of the night is
     * not the eleventh's own value. If they were stuck, those two would be
     * the same number.
     */
    public boolean decisive(int obs, int axis) {
        return cleared(obs, axis) && claim(obs, axis) != logged[axis];
    }

    /** Did a clean check lie? The case the whole game is about. */
    public boolean falseClear(int obs, int axis) {
        return cleared(obs, axis) && stuck[obs][axis];
    }

    /**
     * The line, as the player has earned the right to state it.
     *
     * One way only: a person came back clean on a check that could have
     * failed, so they are straight and their account is the night.
     *
     * There is deliberately no second way. Two accounts that disagree are
     * not a bracket around the truth, because an expectation is not an
     * offset -- it is a value the observer was going to say whatever
     * happened, so two of them disagreeing says nothing about what did. The
     * line nobody was watching stays unsettled no matter how many questions
     * are spent on it, and the only thing left to write is a blank.
     */
    public int established(int axis) {
        for (int o = 0; o < 2; o++) if (decisive(o, axis)) return claim(o, axis);

        // Neither check proved anything on its own, and together they do.
        // The line nobody saw is the only line both observers are stuck on,
        // and there both of them are caught. So a clean check on this line
        // cannot be a stuck observer's -- which means whoever came back clean
        // is straight, and the night is the value they named.
        if (asked[0][axis] && asked[1][axis] && (cleared(0, axis) || cleared(1, axis))) {
            return logged[axis];
        }
        return BLANK;
    }

    /** Can the player state this line as fact? */
    public boolean knows(int axis) { return established(axis) != BLANK; }

    // ---------------------------------------------------------- the verdict

    public enum Verdict { KNOWN, LUCKY, WRONG, BLANK, WITHHELD }

    public Verdict verdict(int axis) {
        if (filed[axis] == BLANK) return knows(axis) ? Verdict.WITHHELD : Verdict.BLANK;
        if (filed[axis] != truth[axis]) return Verdict.WRONG;
        return knows(axis) ? Verdict.KNOWN : Verdict.LUCKY;
    }

    public int tally(Verdict v) {
        int n = 0;
        for (int a = 0; a < N; a++) if (verdict(a) == v) n++;
        return n;
    }

    // ------------------------------------------------------------- the prose

    public static final String OPEN_HEAD = "CORROBORATION";

    public static final String[] OPEN = {
        "A hill station. Two observers awake for the night of the fourteenth,",
        "and one of them has reported a sighting.",
        "",
        "The entry has four lines: the hour, the bearing, the height, the",
        "motion. Whatever you write becomes what happened.",
        "",
        "Both observers have already told you what they saw. What you do not",
        "have is any reason to believe either of them, because neither of them",
        "is an instrument.",
        "",
        "An observer who is wrong is wrong the same way every time. They report",
        "what they expected to see, and they report it on every night, in the",
        "same voice.",
        "",
        "They were not watching the same part of the sky. One line of the entry",
        "neither of them could see, and each of them is wrong about one other.",
        "",
        "You have eight questions. A question is one check: one observer, one",
        "line, against the eleventh, which is in the log. A check that comes",
        "back clean is not proof. It is proof only if it could have come back",
        "dirty.",
        "",
        "A line may be left blank. A blank is a record of what was not",
        "established. It is not a lie, and it is not a failure.",
    };

    // ---- every fixed line the game says, kept here and not in the screen.
    // The phone build reads these too; a label written twice is a label that
    // will disagree with itself eventually.

    public static final String OPEN_SUB = "THE LOG, CLOSED FOR THE NIGHT";
    public static final String ASK_HEAD = "THE FOURTEENTH";
    public static final String FILE_HEAD = "THE ENTRY";
    public static final String END_HEAD = "THE LOG, CLOSED";
    public static final String FILE_LINE = "Whatever you write becomes what happened.";
    public static final String LABEL_SAYS = "says";
    public static final String LABEL_CHECK = "check";
    public static final String LABEL_WROTE = "wrote";
    public static final String LABEL_WAS = "was";
    public static final String CHECK_NONE = "--";
    public static final String CHECK_CLEAN = "clean";
    public static final String CHECK_CAUGHT = "caught";
    public static final String LOG_HEAD = "THE ELEVENTH, AS IT STANDS IN THE LOG";
    public static final String LOG_ORDER = "(the hour, the bearing, the height, the motion)";
    public static final String BTN_BEGIN = "open the log";
    public static final String BTN_FILE = "write the entry";
    public static final String BTN_BACK = "back to the questions";
    public static final String BTN_END = "close the entry";
    public static final String BTN_AGAIN = "another night";
    public static final String BTN_LIBRARY = "the library";
    public static final String NO_QUESTIONS = "that was the last question";

    /** The two closings that are whole paragraphs rather than a tally. */
    public static final String CLOSING_EMPTY =
        "Four lines, all of them blank. A blank is the only honest thing to write "
      + "about a line nobody could settle, and it is the only part of an entry that "
      + "will still be true in a year. It is also not an entry. The station will have "
      + "to be asked again, and it will not be asked by you.";

    public static final String CLOSING_BEST =
        "Three lines, three checks that could have failed, and one left open on "
      + "purpose. That is as good as this entry gets: everything in it was paid "
      + "for, and the one thing that could not be established says so.";

    public static final String V_KNOWN = "known";
    public static final String V_LUCKY = "right, and unchecked";
    public static final String V_WRONG = "wrong";
    public static final String V_BLANK = "not established";
    public static final String V_WITHHELD = "known, and not written";

    public static String verdictWord(Verdict v) {
        return switch (v) {
            case KNOWN -> V_KNOWN;
            case LUCKY -> V_LUCKY;
            case WRONG -> V_WRONG;
            case BLANK -> V_BLANK;
            case WITHHELD -> V_WITHHELD;
        };
    }

    /** "N QUESTIONS LEFT", and the singular nobody remembers to write. */
    public static String questionsLeft(int n) {
        return n + (n == 1 ? " QUESTION LEFT" : " QUESTIONS LEFT");
    }

    public static final String ASK_HINT =
            "up/down the lines    enter to check that person against the eleventh";

    public static final String FILE_HINT =
            "left/right to set a line    up/down to move    enter to file the entry";

    public static final String END_HINT =
            "enter for the library    r for another night";

    // The phone's own hints, because a hint is written for the thing that
    // reads it. The desktop's says "up/down the lines" and "enter"; a phone
    // has no keys, and a hint that names keys it does not have is worse than
    // no hint. (Bearings shipped a button labelled "for the library" that
    // actually sailed again, because the label was a regex strip of a
    // keyboard hint. Same mistake, caught earlier.)
    public static final String ASK_HINT_PHONE =
            "tap a line to check that person against the eleventh";
    public static final String FILE_HINT_PHONE =
            "tap a value for each line, then close the entry";
    public static final String END_HINT_PHONE = "";

    /** The four lines of the eleventh, as they stand in the log. */
    public String logLine() {
        StringBuilder s = new StringBuilder();
        for (int a = 0; a < N; a++) {
            if (a > 0) s.append("   ");
            s.append(valueName(a, logged[a]));
        }
        return s.toString();
    }

    /**
     * The closing paragraph.
     *
     * A function of the tally rather than one sentence with holes in it,
     * because the interesting thing is not how many lines were right. It is
     * the difference between the lines that were paid for and the lines that
     * merely came out right.
     */
    public String closing() {
        int known = tally(Verdict.KNOWN), lucky = tally(Verdict.LUCKY);
        int wrong = tally(Verdict.WRONG), blank = tally(Verdict.BLANK);
        int withheld = tally(Verdict.WITHHELD);

        if (known + lucky + wrong + withheld == 0) return CLOSING_EMPTY;

        StringBuilder s = new StringBuilder();
        if (wrong > 0) {
            s.append(plural(wrong, "One line went in", wrong + " lines went in"))
             .append(" on the strength of a check that came back clean, and it was wrong. ")
             .append("Nothing in the log will show that. The entry is written in the same hand ")
             .append("as the rest of it, and the fourteenth is now whatever you said it was. ");
        }
        if (lucky > 0) {
            s.append(plural(lucky, "One line came out right", lucky + " lines came out right"))
             .append(" without being checked, which is not the same as being known, and which ")
             .append("will not happen in the same place twice. ");
        }
        if (withheld > 0) {
            s.append(plural(withheld, "One line was established", withheld + " lines were established"))
             .append(" and then left out of the entry. That is the one kind of gap that is ")
             .append("somebody's decision rather than the arithmetic's. ");
        }
        if (blank > 0) {
            s.append(plural(blank, "One line is blank", blank + " lines are blank"))
             .append(". A blank is the only honest thing to write about a line nobody could ")
             .append("settle, and it is the only part of this entry that will still be true in ")
             .append("a year. ");
        }
        if (known == N - 1 && blank + withheld == 1) {
            s.append(CLOSING_BEST);
        } else if (known > 0) {
            s.append(plural(known, "One line was paid for", known + " lines were paid for"))
             .append(". That is the whole of what you know, and it is worth more than the rest ")
             .append("of the entry put together.");
        }
        return s.toString();
    }

    static String plural(int n, String one, String many) { return n == 1 ? one : many; }

    // -------------------------------------------------------------- storage

    /**
     * The night, in a form both builds can read.
     *
     * Plain text and one line per fact, because a phone build writes the same
     * file and a format that needs a parser to disagree with itself is a
     * format that will.
     */
    public String serialize() {
        StringBuilder s = new StringBuilder();
        s.append("seed ").append(seed).append('\n');
        s.append("left ").append(left).append('\n');
        s.append("asked ").append(bits(asked)).append('\n');
        s.append("filed");
        for (int a = 0; a < N; a++) s.append(' ').append(filed[a]);
        s.append('\n');
        s.append("done ").append(filedDone ? 1 : 0).append('\n');
        return s.toString();
    }

    static String bits(boolean[][] m) {
        StringBuilder s = new StringBuilder();
        for (int o = 0; o < 2; o++) for (int a = 0; a < N; a++) s.append(m[o][a] ? '1' : '0');
        return s.toString();
    }

    static void readBits(String s, boolean[][] m) {
        for (int i = 0; i < s.length() && i < 2 * N; i++) m[i / N][i % N] = s.charAt(i) == '1';
    }

    /** Rebuild a night from its seed and the questions already spent on it. */
    public static Corroboration load(Path p) throws IOException {
        if (!Files.exists(p)) return null;
        int seed = 0, left = BUDGET;
        boolean[][] asked = new boolean[2][N];
        int[] filed = { BLANK, BLANK, BLANK, BLANK };
        boolean done = false;
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            String[] f = line.trim().split("\\s+");
            if (f.length == 0 || f[0].isEmpty()) continue;
            switch (f[0]) {
                case "seed" -> seed = Integer.parseInt(f[1]);
                case "left" -> left = Integer.parseInt(f[1]);
                case "asked" -> readBits(f[1], asked);
                case "filed" -> { for (int a = 0; a < N; a++) filed[a] = Integer.parseInt(f[1 + a]); }
                case "done" -> done = "1".equals(f[1]);
                default -> { }
            }
        }
        Corroboration c = newNight(seed);
        c.left = left;
        for (int o = 0; o < 2; o++) {
            for (int a = 0; a < N; a++) {
                if (asked[o][a]) { c.asked[o][a] = true; c.answer[o][a] = c.report(o, a, c.logged[a]); }
            }
        }
        System.arraycopy(filed, 0, c.filed, 0, N);
        c.filedDone = done;
        return c;
    }

    public void save(Path p) throws IOException {
        Files.write(p, new ArrayList<>(List.of(serialize().split("\n"))), StandardCharsets.UTF_8);
    }
}
