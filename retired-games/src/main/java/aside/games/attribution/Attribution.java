package aside.games.attribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * attribution, the model.
 *
 * The tenth game, and the first one in this library you can be *good* at.
 * Every other game I have written is a situation you read and answer once.
 * This one is scored, it is different every night, and the score is a
 * confusion matrix.
 *
 * THE RULE THE WHOLE THING RESTS ON. You cannot check a claim. You can only
 * check who filed it. A line of copy is one sentence about something you did
 * not see, and the only thing standing behind it is a byline. So the question
 * the game asks is not "is this true" -- you cannot answer that -- but "how
 * much is this byline worth", and you have five calls to find out.
 *
 * WHAT MAKES IT A GAME RATHER THAN ARITHMETIC. Five calls, six stringers,
 * twenty-four lines. You cannot learn a desk in one night. Two calls on one
 * stringer tells you a rate; one call on each of six tells you almost nothing,
 * because a single true line is what a liar files four times out of five. So
 * the real decision is *direct* against *structural*: spend a call on the line
 * you are about to run, or on the byline you know nothing about. Those are
 * different bets and the game does not tell you which one it wants.
 *
 * THE SCORE. Accuracy over all twenty-four lines: what you ran that was true,
 * plus what you spiked that was false. Filing everything scores the desk's
 * average -- about fifty-four per cent -- and spiking everything scores about
 * forty-six. An oracle who knew every byline would score about eighty-five.
 * That is the gradient, and the five calls are what you buy of it.
 *
 * WHAT IS FIXED AND WHAT IS NOT. The copy is fixed: the same twenty-four
 * lines come in every night, in the same order. What shuffles is the desk --
 * which byline carries which reliability. That is deliberate. The stories are
 * not the variable; who filed them is.
 */
public class Attribution {

    public static final String WORDMARK = "Attribution";
    public static final String WHERE_OPEN = "the wire desk";
    public static final String WHERE_NIGHT = "the night's copy";
    public static final String WHERE_REPORT = "the edition";

    public static final int STRINGERS = 6;
    public static final int ROUNDS = 6;
    public static final int PER_ROUND = 4;
    public static final int ITEMS = ROUNDS * PER_ROUND;   // 24
    public static final int CALLS = 5;

    public static final List<String> OPENING = List.of(
            "Copy comes in all night. Six stringers, four lines each, and every "
                    + "line is a claim about something you did not see.",
            "You can call and check a line. You have five calls for the night, "
                    + "and a call tells you one thing: whether that line is true.",
            "Everything else you file on the strength of who filed it. Run it and "
                    + "it is in the edition. Spike it and it is not.",
            "The same copy comes in every night, in the same order. What changes "
                    + "is who filed it.",
            "The edition is what survives. It does not say which of it you checked.");

    public static final String RULES_HEADING = "THE DESK";

    public static final List<String[]> RULES = List.of(
            new String[]{"1 - 4", "select a line"},
            new String[]{"C", "call and check it. One call, and it tells you whether that line is true."},
            new String[]{"R", "run it. It goes in the edition."},
            new String[]{"S", "spike it. It does not."},
            new String[]{"", "The round is filed when all four lines are decided. A line you have filed cannot be called."},
            new String[]{"", "Five calls, twenty-four lines. You will not check most of them."});

    /** The desk. Names and beats only; the reliability is dealt underneath. */
    static final String[][] DESK = {
            {"Halloran", "the city desk"},
            {"Pryce", "the courts"},
            {"Nkemdi", "the docks"},
            {"Vasquez", "the scanner"},
            {"Auerbach", "the county"},
            {"Sandoval", "the obituaries"}};

    /**
     * The six reliabilities, dealt one to each stringer at random.
     *
     * The middle two are the point. 0.65 and 0.45 are close enough to each
     * other that a handful of calls cannot separate them, and far enough from
     * 0.5 that the right answer is still worth finding. An oracle runs the top
     * three and spikes the bottom three; the average is 0.5417, so a player who
     * files everything scores the same as a coin that lands on the desk's mean.
     */
    static final double[] RELIABILITY = {0.95, 0.85, 0.65, 0.45, 0.25, 0.10};

    /** The night's copy. Fixed, in this order, every night. */
    public static final List<String> COPY = List.of(
            "Two men were held after a break-in at the cold storage on Pier 4.",
            "The county has approved repaving on Route 9 for the spring.",
            "A fire at the mill was ruled accidental by the fire marshal.",
            "The harbor master has resigned, effective the end of the month.",
            "A body was recovered from the river below the dam on Tuesday.",
            "The school board voted to keep the fourth-grade class intact.",
            "Three arrests were made at the depot after the Saturday fight.",
            "The water main on Ash Street will be shut off from eight until noon.",
            "A jury took four hours to acquit the driver in the March collision.",
            "The mill has laid off forty workers and expects more in the fall.",
            "A dog was destroyed after a second complaint from the same street.",
            "The council has tabled the zoning change until the next session.",
            "A man was treated for burns after a kitchen fire on Bell Street.",
            "The ferry will run a reduced schedule through the end of October.",
            "A stolen truck was found abandoned on the county road near the quarry.",
            "The library has extended its hours on Thursdays to nine o'clock.",
            "A woman was cited for leaving the scene of a fender-bender on Main.",
            "The old cannery has been sold to a buyer from out of state.",
            "A gas leak closed the block around Fifth and Vine for two hours.",
            "The coroner has ruled the death at the motel a homicide.",
            "Two dozen head of cattle were lost when the barn came down.",
            "The school bus route to the north end has been suspended.",
            "A fire at the paper plant burned through the night and is out.",
            "The city has hired a consultant to study the parking downtown.");

    // ------------------------------------------------------------ the pieces

    public static final class Stringer {
        public final String name;
        public final String beat;
        final double reliability;

        Stringer(String name, String beat, double reliability) {
            this.name = name;
            this.beat = beat;
            this.reliability = reliability;
        }
    }

    public static final class Item {
        public final int number;    // 1..24
        public final int round;     // 0..5
        public final String text;
        public final int stringer;  // index into stringers
        public final boolean truth; // never shown until the edition
        public int filed;           // 0 unfiled, 1 run, 2 spike
        public int called;          // 0 not called, 1 called and true, 2 called and false

        Item(int number, int round, String text, int stringer, boolean truth) {
            this.number = number;
            this.round = round;
            this.text = text;
            this.stringer = stringer;
            this.truth = truth;
        }

        public boolean filed() { return filed != 0; }
        public boolean ran() { return filed == 1; }
        public boolean spiked() { return filed == 2; }
        public boolean calledIt() { return called != 0; }
        public boolean calledTrue() { return called == 1; }
        public boolean calledFalse() { return called == 2; }
        public boolean right() { return (ran() && truth) || (spiked() && !truth); }
    }

    // ------------------------------------------------------------- the night

    public final List<Stringer> stringers = new ArrayList<>();
    public final List<Item> items = new ArrayList<>();
    public final long seed;
    public int callsLeft = CALLS;

    Attribution(long seed) { this.seed = seed; }

    /** A night, dealt from the seed. Same seed, same desk, same copy. */
    public static Attribution of(long seed) {
        Random rng = new Random(seed);
        Attribution a = new Attribution(seed);

        List<Double> rel = new ArrayList<>();
        for (double d : RELIABILITY) rel.add(d);
        Collections.shuffle(rel, rng);
        for (int i = 0; i < STRINGERS; i++) {
            a.stringers.add(new Stringer(DESK[i][0], DESK[i][1], rel.get(i)));
        }

        // Four lines each, dealt at random across the night.
        List<Integer> byline = new ArrayList<>();
        for (int s = 0; s < STRINGERS; s++) {
            for (int k = 0; k < PER_ROUND; k++) byline.add(s);
        }
        Collections.shuffle(byline, rng);

        for (int i = 0; i < ITEMS; i++) {
            int s = byline.get(i);
            boolean truth = rng.nextDouble() < a.stringers.get(s).reliability;
            a.items.add(new Item(i + 1, i / PER_ROUND, COPY.get(i), s, truth));
        }
        return a;
    }

    public static Attribution of() { return of(System.nanoTime()); }

    // ------------------------------------------------------------- the round

    /** The round being filed, or ROUNDS when the night is over. */
    public int round() {
        for (Item it : items) if (!it.filed()) return it.round;
        return ROUNDS;
    }

    public boolean finished() { return round() >= ROUNDS; }

    public List<Item> roundItems() {
        List<Item> out = new ArrayList<>();
        int r = round();
        for (Item it : items) if (it.round == r) out.add(it);
        return out;
    }

    /** Spend a call on a line. Fails on a filed line, a called line, or an empty purse. */
    public boolean call(Item it) {
        if (callsLeft <= 0 || it.filed() || it.calledIt()) return false;
        callsLeft--;
        it.called = it.truth ? 1 : 2;
        return true;
    }

    public boolean file(Item it, boolean run) {
        if (it.filed()) return false;
        it.filed = run ? 1 : 2;
        return true;
    }

    // ------------------------------------------------------------- the score

    public int ran() { return count(i -> i.ran()); }
    public int ranTrue() { return count(i -> i.ran() && i.truth); }
    public int ranFalse() { return count(i -> i.ran() && !i.truth); }
    public int spiked() { return count(i -> i.spiked()); }
    public int spikedTrue() { return count(i -> i.spiked() && i.truth); }
    public int spikedFalse() { return count(i -> i.spiked() && !i.truth); }

    /** Lines that came out right: run and true, or spiked and false. */
    public int correct() { return ranTrue() + spikedFalse(); }

    public double accuracy() { return (double) correct() / ITEMS; }

    /** How many of the twenty-four were true at all. The desk's own number. */
    public int trueLines() { return count(i -> i.truth); }

    public int callsMade() { return CALLS - callsLeft; }

    public int filedBy(int s) { return count(i -> i.stringer == s && i.filed()); }
    public int dealtBy(int s) { return count(i -> i.stringer == s); }
    public int trueBy(int s) { return count(i -> i.stringer == s && i.truth); }
    public int calledBy(int s) { return count(i -> i.stringer == s && i.calledIt()); }
    public int calledTrueBy(int s) { return count(i -> i.stringer == s && i.calledTrue()); }

    private int count(java.util.function.Predicate<Item> p) {
        int n = 0;
        for (Item it : items) if (p.test(it)) n++;
        return n;
    }

    // ------------------------------------------------------------- the prose

    public String headline() { return headline(correct()); }

    public static String headline(int correct) {
        double a = (double) correct / ITEMS;
        if (a >= 0.90) return "A clean edition.";
        if (a >= 0.75) return "A good edition.";
        if (a >= 0.60) return "An ordinary edition.";
        if (a >= 0.45) return "A poor edition.";
        return "A bad edition.";
    }

    /**
     * The verdict, as a template rather than a sentence.
     *
     * It depends on five numbers at once, so it cannot be emitted as a table
     * the way Inventory's three-sentence report was. The phone build fills the
     * same template the desktop fills, which is the next best thing: the
     * sentences live here and nowhere else.
     */
    public static final String VERDICT_TEMPLATE =
            "You ran %ran% of the twenty-four lines, and %ranTrue% of those were true.\n"
                    + "You spiked %spiked%, and %spikedTrue% of those were true, and are "
                    + "not in the edition.\n"
                    + "%correct% of the twenty-four came out right.";

    public String verdict() { return verdict(ran(), ranTrue(), spiked(), spikedTrue(), correct()); }

    public static String verdict(int ran, int ranTrue, int spiked, int spikedTrue, int correct) {
        return VERDICT_TEMPLATE
                .replace("%ranTrue%", String.valueOf(ranTrue))
                .replace("%spikedTrue%", String.valueOf(spikedTrue))
                .replace("%ran%", String.valueOf(ran))
                .replace("%spiked%", String.valueOf(spiked))
                .replace("%correct%", String.valueOf(correct));
    }

    public String closing() { return closing(correct()); }

    public static String closing(int correct) {
        double a = (double) correct / ITEMS;
        if (a >= 0.85) {
            return "You spent the calls where they were worth spending. Most of what "
                    + "you ran was true and most of what you killed deserved it. The "
                    + "desk will not remember how you knew.";
        }
        if (a >= 0.65) {
            return "Some of it was judgement and some of it was the shape of a byline "
                    + "you had learned to trust. The edition is better than chance and "
                    + "worse than it reads.";
        }
        if (a >= 0.50) {
            return "You ran about as well as the desk does. " + cap(word(CALLS)) + " calls is "
                    + "not enough to learn " + word(STRINGERS) + " people, and the lines "
                    + "you could not vouch for went out anyway, because a night does not "
                    + "wait for you to be sure.";
        }
        return "You ran what you could not vouch for, and the edition carries it. "
                + "Nothing on the page says so. That is the whole of it.";
    }

    /** The standing line, printed under every edition. */
    public static final String STANDING =
            "Nobody downstream can tell which lines you called. The edition reads the "
                    + "same either way.";

    /** "You made five calls", indexed by how many were made. */
    public static String callsLineMade(int made) {
        if (made == 0) return "You made no calls";
        return "You made " + word(made) + (made == 1 ? " call" : " calls");
    }

    public static final String CALLS_LINE_ALL =
            ", and there is no one on the desk you did not call.";

    public static final String CALLS_LINE_NEVER = ". You never called %s.";

    public static final String OR = " or ";

    /** "You made five calls. You never called Auerbach or Pryce." */
    public String callsLine() {
        List<String> never = new ArrayList<>();
        for (int i = 0; i < stringers.size(); i++) {
            if (calledBy(i) == 0) never.add(stringers.get(i).name);
        }
        String made = callsLineMade(callsMade());
        if (never.isEmpty()) return made + CALLS_LINE_ALL;
        return made + String.format(CALLS_LINE_NEVER, joinOr(never));
    }

    /** "A or B or C" -- the phone joins the same way, from the same word. */
    public static String joinOr(List<String> xs) {
        if (xs.isEmpty()) return "";
        if (xs.size() == 1) return xs.get(0);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < xs.size() - 1; i++) {
            if (i > 0) b.append(", ");
            b.append(xs.get(i));
        }
        return b.append(OR).append(xs.get(xs.size() - 1)).toString();
    }

    public static String word(int n) {
        String[] w = {"none", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
                "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
                "twenty-one", "twenty-two", "twenty-three", "twenty-four"};
        return (n >= 0 && n < w.length) ? w[n] : String.valueOf(n);
    }

    /** A spelled number at the start of a sentence still needs a capital. */
    public static String cap(String s) {
        return (s == null || s.isEmpty()) ? s
                : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String roundWhere(int r) { return "round " + (r + 1) + " of " + ROUNDS; }

    public static String callsWhere(int left) {
        return left == 1 ? "1 call left" : left + " calls left";
    }

    public static final String START_LINE = "ENTER to take the desk.";
    public static final String START_BUTTON = "Take the desk";
    public static final String AGAIN = "Start another night";
    public static final String NO_CALLS = "The desk has no calls left.";
    public static final String ALREADY_CALLED = "That line has already been called.";
    public static final String ALREADY_FILED = "That line is filed.";
    public static final String WHO_YOU_CALLED = "WHO YOU HAVE CALLED";
    public static final String WHO_FILES_HERE = "WHO FILES HERE";
    public static final String NOBODY_CALLED = "Nobody yet.";
    public static final String THE_EDITION = "THE EDITION";
    public static final String THE_DESK = "THE DESK";
    public static final String FILED = "filed";
    public static final String TRUE_OF = "true";
    public static final String CALLED = "called";
    public static final String NOT_CALLED = "not called";
    public static final String RUN = "run";
    public static final String SPIKE = "spike";
    public static final String CALL_BTN = "Call";
    public static final String NO_CALL = "no call";
    public static final String WAS_TRUE = "true";
    public static final String WAS_FALSE = "false";
    public static final String ROUND_FILED = "The round is filed.";

    // -------------------------------------------------------------- the file

    /**
     * The night, as three lines of plain text. The seed is the whole desk and
     * the whole copy, so a night can be picked up exactly where it was left.
     */
    public void save(Path p) throws IOException {
        StringBuilder filed = new StringBuilder();
        StringBuilder called = new StringBuilder();
        for (Item it : items) {
            if (filed.length() > 0) filed.append(',');
            filed.append(it.filed);
            if (called.length() > 0) called.append(',');
            called.append(it.called);
        }
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, seed + " " + callsLeft + "\n" + filed + "\n" + called + "\n");
    }

    public static Attribution load(Path p) throws IOException {
        if (!Files.exists(p)) return of();
        List<String> lines = Files.readAllLines(p);
        if (lines.size() < 3) return of();
        String[] head = lines.get(0).trim().split("\\s+");
        if (head.length < 2) return of();
        Attribution a = of(Long.parseLong(head[0]));
        a.callsLeft = Math.max(0, Math.min(CALLS, Integer.parseInt(head[1])));
        String[] filed = lines.get(1).trim().split(",");
        String[] called = lines.get(2).trim().split(",");
        for (int i = 0; i < ITEMS; i++) {
            if (i < filed.length) a.items.get(i).filed = clamp(parse(filed[i]), 0, 2);
            if (i < called.length) a.items.get(i).called = clamp(parse(called[i]), 0, 2);
        }
        return a;
    }

    static int parse(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
