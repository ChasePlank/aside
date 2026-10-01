package aside.games.interval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * interval, the model.
 *
 * The twelfth game, and the twelfth verb: the other eleven are about what you
 * send, what you keep, or what you compare. This one is about the time in
 * between, which is the only thing in the library that nobody has had to do
 * yet. Waiting is not keeping -- Vigil keeps, and keeping is work. Waiting is
 * what you do when there is no work that would help.
 *
 * THE SITUATION. You keep a signal station. A ship is out. She will signal on
 * one day, or she will not come at all. Twelve days, and a lamp, and a glass.
 *
 * THE RULE THE WHOLE THING RESTS ON. You cannot stand watch three nights
 * running. Two nights on is the most a person does; the third night you sleep,
 * whether or not you want to. So the twelve days are not twelve chances -- they
 * are six, and they have to be spent on days that are not adjacent in runs of
 * three. The two likeliest days of the crossing are next to each other, and
 * you cannot be awake for both of them. That is the game: not "when will she
 * come", but "which of the days she might come on can I afford to be present
 * for".
 *
 * WHY IT IS FAIR. The shape of the wait is not hidden. The station's own
 * record is on the wall: the last twelve ships, and the day each one
 * signalled, or the fact that she never did. The record is dealt from the same
 * distribution as the ship you are waiting for, so reading it is the whole of
 * the skill -- and it is a sample, not the truth, which is the whole of the
 * difficulty. See aside.games.interval.SelfTest: it checks that the record and
 * the arrival are drawn from one table, that the watch rule is what the prose
 * says it is, and that the report cannot say something the arithmetic does not
 * support.
 *
 * WHAT IS FIXED AND WHAT IS NOT. The twelve days, the twelve log lines, and
 * the table are fixed. What the seed decides is the record on the wall, the
 * day she comes, and which of the twelve lines each day gets.
 */
public final class Interval {

    public static final String WORDMARK = "Interval";
    public static final String WHERE = "the signal station";
    public static final String WHERE_RECORD = "the record on the wall";
    public static final String WHERE_REPORT = "the season's end";

    /** The twelve days of the wait. */
    public static final int DAYS = 12;
    /** The most watches a person can stand in a season. */
    public static final int WATCHES = 6;
    /** How many past ships the station has a record of. */
    public static final int RECORD = 12;
    /** Two nights on is the most; the third night you sleep. */
    public static final int MAX_RUN = 2;

    /**
     * The shape of the crossing, as weights.
     *
     * Index 0 is "she never came"; 1..DAYS are the day she signalled. This one
     * table is the whole of the game's information: the record on the wall is
     * twelve draws from it, and so is the ship you are waiting for. A crossing
     * cannot be shorter than three days or longer than ten, which is why the
     * ends of the table are zero -- the zeros are not decoration, they are the
     * reason a player who watches the first two days has already made a
     * mistake.
     */
    static final int[] WEIGHT = { 3, 0, 0, 1, 2, 3, 7, 4, 3, 2, 1, 0, 0 };

    static final int TOTAL;
    static {
        int t = 0;
        for (int w : WEIGHT) t += w;
        TOTAL = t;
    }

    // ------------------------------------------------------------- the deal

    /**
     * Counter-based, not a stream: every number is a pure function of
     * (seed, salt), so there is no RNG position to carry and the phone build
     * can compute the same season without porting java.util.Random. Same
     * arithmetic as drift and corroboration, on purpose -- one mixing function
     * in the library is one thing to check.
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

    /**
     * One draw from the table: 0 for "never", 1..DAYS for the day.
     *
     * Cumulative rather than a rejection loop, so the outcome is exact by
     * construction and the phone can reproduce it with the same walk.
     */
    public static int draw(long seed, int salt) {
        int r = pick(seed, salt, TOTAL);
        int acc = 0;
        for (int i = 0; i < WEIGHT.length; i++) {
            acc += WEIGHT[i];
            if (r < acc) return i;
        }
        return WEIGHT.length - 1;
    }

    /** The station's record: the last RECORD ships, and when each signalled. */
    public static List<Integer> record(long seed) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < RECORD; i++) out.add(draw(seed, 100 + i));
        return out;
    }

    // ------------------------------------------------------------ the state

    public final long seed;
    /** 0 means she never came; 1..DAYS is the day she signalled. */
    public final int arrival;
    public final List<Integer> record;
    /** Which days the keeper stood watch. */
    public final boolean[] watched = new boolean[DAYS];
    /** Which days the keeper has decided. Days are decided in order. */
    public int decided;
    public boolean reported;

    Interval(long seed) {
        this.seed = seed;
        this.arrival = draw(seed, 1);
        this.record = record(seed);
    }

    public static Interval of(long seed) { return new Interval(seed); }
    public static Interval of() { return new Interval(System.nanoTime()); }

    // ------------------------------------------------------------- the rule

    /** How many days in a row were watched immediately before this one. */
    public int runBefore(int day) {
        int n = 0;
        for (int i = day - 1; i >= 0 && watched[i]; i--) n++;
        return n;
    }

    /** The days the keeper may still stand watch on, given the rule. */
    public boolean canWatch(int day) {
        if (day < 0 || day >= DAYS || day < decided) return false;
        if (watchesUsed() >= WATCHES) return false;
        return runBefore(day) < MAX_RUN;
    }

    public boolean canSleep(int day) {
        return day >= 0 && day < DAYS && day >= decided;
    }

    public int watchesUsed() {
        int n = 0;
        for (boolean b : watched) if (b) n++;
        return n;
    }

    /** Decide the current day. Returns false if the choice was not allowed. */
    public boolean watch(int day) {
        if (!canWatch(day)) return false;
        watched[day] = true;
        decided = day + 1;
        return true;
    }

    public boolean sleep(int day) {
        if (!canSleep(day)) return false;
        decided = day + 1;
        return true;
    }

    public boolean done() { return decided >= DAYS; }

    // ---------------------------------------------------------- the outcome

    public enum End { CAUGHT, MISSED, NEVER }

    public End end() {
        if (arrival == 0) return End.NEVER;
        return watched[arrival - 1] ? End.CAUGHT : End.MISSED;
    }

    /** The watches that were not the day she came. */
    public int wasted() {
        if (arrival == 0) return watchesUsed();
        return watchesUsed() - (watched[arrival - 1] ? 1 : 0);
    }

    // -------------------------------------------------------------- the file

    /**
     * The season, as two lines of plain text: the seed, then the decisions.
     *
     * The seed is the whole season, so a season can be picked up exactly where
     * it was left -- and a player can read the file and see which days they
     * stood watch, which is the same joke the other plain-text saves make.
     */
    public void save(Path p) throws IOException {
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < DAYS; i++) {
            d.append(i < decided ? (watched[i] ? '1' : '0') : '-');
        }
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, seed + "\n" + d + "\n" + (reported ? 1 : 0) + "\n");
    }

    public static Interval load(Path p) throws IOException {
        if (!Files.exists(p)) return of();
        List<String> lines = Files.readAllLines(p);
        if (lines.isEmpty()) return of();
        long seed;
        try { seed = Long.parseLong(lines.get(0).trim()); } catch (Exception e) { return of(); }
        Interval it = of(seed);
        if (lines.size() > 1) {
            String d = lines.get(1).trim();
            for (int i = 0; i < DAYS && i < d.length(); i++) {
                char c = d.charAt(i);
                if (c == '-') { it.decided = i; break; }
                it.watched[i] = c == '1';
                it.decided = i + 1;
            }
        }
        if (lines.size() > 2) it.reported = lines.get(2).trim().equals("1");
        return it;
    }

    // -------------------------------------------------------------- the prose

    public static final List<String> OPENING = List.of(
            "You keep a signal station. A ship is out, and she will signal on one "
                    + "day, or she will not come at all.",
            "Twelve days. A lamp, a glass, and a wall with the station's record on "
                    + "it: the last twelve ships, and the day each one signalled.",
            "You cannot stand watch three nights running -- two nights on is the "
                    + "most a person does, and the third night you sleep whether you "
                    + "want to or not. And six watches is all you have in a season. "
                    + "So the twelve days are six nights you can be awake for, and "
                    + "the two likeliest days of the crossing are next to each other.");

    public static final String RULES_HEADING = "THE STATION";

    public static final List<String[]> RULES = List.of(
            new String[] { "W", "stand watch. You will see her if she signals today." },
            new String[] { "S", "sleep. You will not." },
            new String[] { "", "Six watches in the twelve days, and never three "
                    + "nights running." },
            new String[] { "", "The record on the wall is twelve past ships. It is "
                    + "the only thing you know about when she comes." });

    public static final String RECORD_HEAD = "THE RECORD ON THE WALL";
    public static final String RECORD_NEVER = "never signalled";
    public static final String JOURNAL_HEAD = "YOUR JOURNAL";
    public static final String JOURNAL_BLANK = "asleep";
    public static final String WATCH = "Watch";
    public static final String SLEEP = "Sleep";
    public static final String START_LINE = "ENTER to take the first night.";
    public static final String START_BUTTON = "Take the watch";
    public static final String AGAIN = "Another season";
    public static final String HINT = "W watch    S sleep    ESC the library";
    public static final String HINT_REPORT = "ENTER another season    ESC the library";
    public static final String CANNOT_WATCH = "Three nights running is more than a person does.";
    public static final String NO_WATCHES_LEFT = "You have stood every watch you have in you.";
    public static final String MUST_CHOOSE = "The night does not wait.";

    public static final String REPORT_HEAD = "THE SEASON'S END";
    public static final String WATCHED_HEAD = "THE WATCHES YOU STOOD";
    public static final String MISSED_HEAD = "THE DAYS YOU SLEPT";
    public static final String NOTHING_MISSED = "You were awake for all of them.";
    public static final String NOTHING_WATCHED = "You slept through the season.";

    /** The twelve days, each with the line the keeper would have written. */
    public static final List<String> LOG = List.of(
            "The glass is empty and the horizon is a line. Nothing has happened yet.",
            "Wind north-east, steady. The lamp is trimmed and the wick is good.",
            "A gull sat on the rail for an hour. I have started talking to it.",
            "The barometer is falling. If she is out there she is running before it.",
            "Rain since noon. The glass is useless in rain and I stood at it anyway.",
            "The swell has gone long and low. A wake does that when it has spread.",
            "Clear again. I have begun to count the ships that are not her.",
            "I have learned every noise this station makes. None of them is a ship.",
            "The wick is short. I trimmed it too far, and there is no spare.",
            "The horizon is a line, and I have looked at it long enough to answer.",
            "I woke at dusk certain she had come. She had not. It took a while to go.",
            "The relief is due. Whatever the season was, the log is what is left.");

    public static final List<String> NAMES = List.of(
            "the Kestrel", "the Marrow", "the Anselm", "the Petrel", "the Verity",
            "the Osprey", "the Tarn", "the Sabine", "the Cormorant", "the Hollis",
            "the Meridian", "the Auk");

    // ------------------------------------------------------------ the report

    /** What the season was, said plainly, one line per ending. */
    public static String outcomeLine(End end, int arrival) {
        return switch (end) {
            case CAUGHT -> "She signalled on the " + ordinal(arrival)
                    + ". You were at the glass.";
            case MISSED -> "She signalled on the " + ordinal(arrival)
                    + ". You were asleep, and you found the mark on the glass "
                    + "the next morning.";
            case NEVER -> "She never signalled. Twelve days, and the horizon "
                    + "stayed a line.";
        };
    }

    public static String watchedLine(int n) {
        if (n == 0) return NOTHING_WATCHED;
        return "You stood " + word(n) + (n == 1 ? " watch." : " watches.");
    }

    /**
     * What the watches cost, which is the number the game is actually about.
     *
     * A watch spent on a day she did not come is a day of your life that went
     * into the glass. The report says it out loud rather than letting the
     * player add it up, because the whole point of the game is that the sum is
     * the thing you were spending.
     */
    public static String wastedLine(End end, int wasted) {
        if (end == End.NEVER) {
            return wasted == 0 ? "You spent nothing on her."
                    : "Every one of them went into the glass.";
        }
        if (wasted == 0) return "Not one of them was wasted.";
        return cap(word(wasted)) + (wasted == 1 ? " of them was a day she was not there."
                : " of them were days she was not there.");
    }

    /**
     * The closing sentence. One per ending, and the missed one is not the same
     * as the never one -- a season you spent on nothing and a season you spent
     * on the wrong day are different failures, and a game that said the same
     * thing at both would not be reading the season back.
     */
    public static String closing(End end, int wasted) {
        return switch (end) {
            case CAUGHT -> wasted == 0
                    ? "You read the record, you picked the day, and you were right. "
                            + "That is the whole of it, and it almost never happens."
                    : "You were there when she came. The days you spent getting "
                            + "there are the price, and you paid it, and she came.";
            case MISSED -> wasted >= WATCHES
                    ? "You stood every watch you had and she came on a night you "
                            + "could not have been awake for. That is not a mistake. "
                            + "That is the arithmetic."
                    : "She came on a day you slept, and you had watches left. The "
                            + "record was on the wall the whole time.";
            case NEVER -> wasted == 0
                    ? "You slept, and she never came, and you lost nothing. It is "
                            + "the only way to be sure, and it is not a way to be "
                            + "there."
                    : "She never came. The watches are gone and there is nothing to "
                            + "show for them, which is what waiting is when it does "
                            + "not work, and it mostly does not work.";
        };
    }

    /** "7" -> "7th". The game never needs more than twelve. */
    public static String ordinal(int n) {
        String suffix = (n % 100 >= 11 && n % 100 <= 13) ? "th"
                : switch (n % 10) {
                    case 1 -> "st";
                    case 2 -> "nd";
                    case 3 -> "rd";
                    default -> "th";
                };
        return n + suffix;
    }

    /** "6" -> "six". Small numbers only; the game never needs more. */
    public static String word(int n) {
        String[] w = { "no", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve" };
        return n >= 0 && n < w.length ? w[n] : String.valueOf(n);
    }

    public static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The record, as one line of prose, for the report and the phone. */
    public static String recordLine(int outcome) {
        return outcome == 0 ? RECORD_NEVER : "signalled on the " + ordinal(outcome);
    }
}
