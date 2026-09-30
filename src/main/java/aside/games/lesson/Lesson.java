package aside.games.lesson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * lesson, the model.
 *
 * The fifteenth game, and the fifteenth verb: teaching. The other fourteen are
 * about what you send, what you keep, what you compare, or what you wait for.
 * This one is about the thing that happens before any of them: somebody has to
 * be shown, and what you show them is all they get.
 *
 * THE SITUATION. You have run a boiler for eleven years. In three nights you
 * hand it to somebody else. The machine is two gauges and a vent, and the rule
 * is one line: you open the vent when the pressure is up. You have never had to
 * say it out loud, because you have never had to tell anybody.
 *
 * THE RULE THE WHOLE THING RESTS ON. You have three nights and a board of
 * twelve states the machine has sat in. Each night you walk them through one of
 * them. Then they take a shift, and the shift is three states that are not on
 * the board.
 *
 * And here is what the game is actually about: **a state only teaches one
 * thing.** The board has four corners -- pressure up or down, heat up or down
 * -- and a state in one corner tells the new keeper what to do in that corner
 * and nothing whatever about the other three. A player who shows three states
 * that all sit in one corner has taught one corner three times, and the
 * student's belief is exactly as wide after the third night as it was after the
 * first. That is the failure this game exists to make legible: **it does not
 * look like a failure while it is happening.** Every demonstration feels like
 * teaching. The student keeps saying something. The nights keep going.
 *
 * WHY IT IS FAIR. The eight rules the student considers are declared, and so is
 * the order they try them in. Every one of the eight is a function of the four
 * corners, and no two of them agree on all four -- so a demonstration in a new
 * corner always narrows the field, and a demonstration in a corner they have
 * already seen never does. The student's belief is therefore a fact about the
 * demonstrations rather than a guess about the student. See
 * aside.games.lesson.SelfTest: it checks the eight are pairwise distinct on the
 * corners, that every state on the board sits in the corner it claims, and that
 * a player who shows one state from each of the shift's corners always wins.
 *
 * WHAT IS FIXED AND WHAT IS NOT. The thresholds, the eight rules and the prose
 * are fixed. What the seed decides is the rule the machine actually follows,
 * which twelve states are on the board, and which three states the shift is.
 */
public final class Lesson {

    public static final String WORDMARK = "Lesson";
    public static final String WHERE = "the boiler house";
    public static final String WHERE_BOARD = "the board";
    public static final String WHERE_TONIGHT = "tonight";
    public static final String WHERE_REPORT = "the handover";

    /** The pressure is up at this reading and above. */
    public static final int PRESSURE_UP = 40;
    /** The heat is up at this reading and above. */
    public static final int HEAT_UP = 60;

    /** How many states the machine has sat in lately. */
    public static final int BOARD = 12;
    /** How many nights you have to show them one. */
    public static final int SHOWS = 3;
    /** How many states the shift is. */
    public static final int SHIFT = 3;

    // ------------------------------------------------------------- the rules

    /** The eight things the new keeper thinks the rule might be. */
    public enum Rule {
        P_HIGH, P_LOW, H_HIGH, H_LOW, BOTH_HIGH, EITHER_HIGH, BOTH_LOW, EITHER_LOW
    }

    /**
     * The order they try them in.
     *
     * Declared, not inferred, because the player has to be able to predict what
     * the student will do with an ambiguous set -- a game where the student
     * picked at random would be a game where the player's last night was a coin
     * toss. It is ordered by how little a rule has to name: the pressure first,
     * because the pressure gauge is the one on the door, then the heat, then
     * the two that need both readings.
     */
    public static final List<Rule> ORDER = List.of(
            Rule.P_HIGH, Rule.P_LOW, Rule.H_HIGH, Rule.H_LOW,
            Rule.BOTH_HIGH, Rule.EITHER_HIGH, Rule.BOTH_LOW, Rule.EITHER_LOW);

    /** The four corners of the board. */
    public enum Quadrant { HH, HL, LH, LL }

    /** One state of the machine: what the two gauges read. */
    public record Reading(int pressure, int heat) {
        public boolean pressureUp() { return pressure >= PRESSURE_UP; }
        public boolean heatUp() { return heat >= HEAT_UP; }
        public Quadrant quadrant() {
            if (pressureUp()) return heatUp() ? Quadrant.HH : Quadrant.HL;
            return heatUp() ? Quadrant.LH : Quadrant.LL;
        }
    }

    static Reading r(int p, int h) { return new Reading(p, h); }

    /** Does this rule open the vent in this state? */
    public static boolean opens(Rule r, Reading x) {
        boolean p = x.pressureUp(), h = x.heatUp();
        return switch (r) {
            case P_HIGH -> p;
            case P_LOW -> !p;
            case H_HIGH -> h;
            case H_LOW -> !h;
            case BOTH_HIGH -> p && h;
            case EITHER_HIGH -> p || h;
            case BOTH_LOW -> !p && !h;
            case EITHER_LOW -> !p || !h;
        };
    }

    // -------------------------------------------------------------- the board

    /**
     * The states the machine has sat in, by corner.
     *
     * Six candidates per corner and the deal takes some of them, so the board
     * is a different board every time and every board has the same shape. The
     * shape is the point: **four corners, and a corner you cannot find a state
     * in is a corner you cannot teach.** The counts are uneven on purpose --
     * the machine spends most of its life hot and under pressure, and a board
     * that was three of each would be a board that flattered the teacher.
     *
     * The near-threshold values are not accidents either. (41,61) is in the
     * corner it is in by one point on each gauge, and (38,59) is in the corner
     * below it by the same margin. A player who reads the board quickly reads
     * it wrong, and reading it wrong is the same as not having shown them.
     */
    static final Map<Quadrant, List<Reading>> BOARD_POOL = Map.of(
            Quadrant.HH, List.of(r(78, 84), r(52, 66), r(41, 61), r(90, 95), r(67, 73), r(44, 88)),
            Quadrant.HL, List.of(r(86, 22), r(63, 44), r(44, 58), r(95, 9), r(71, 31), r(49, 51)),
            Quadrant.LH, List.of(r(18, 91), r(33, 72), r(39, 62), r(7, 84), r(24, 66), r(36, 97)),
            Quadrant.LL, List.of(r(12, 15), r(27, 38), r(38, 59), r(4, 6), r(31, 22), r(19, 47)));

    /** How many states the board takes from each corner. Twelve in all. */
    static final int[] BOARD_COUNT = { 4, 4, 2, 2 };

    /** The shift's states, by corner. Disjoint from the board's, by construction. */
    static final Map<Quadrant, List<Reading>> SHIFT_POOL = Map.of(
            Quadrant.HH, List.of(r(83, 69), r(58, 92), r(46, 64), r(72, 81)),
            Quadrant.HL, List.of(r(91, 27), r(54, 36), r(42, 55), r(69, 49)),
            Quadrant.LH, List.of(r(14, 78), r(37, 88), r(29, 64), r(11, 73)),
            Quadrant.LL, List.of(r(9, 21), r(35, 41), r(22, 53), r(16, 34)));

    // ------------------------------------------------------------- the deal

    /**
     * Counter-based, not a stream: every number is a pure function of
     * (seed, salt), so there is no RNG position to carry and the phone build
     * can deal the same board without porting java.util.Random. Same mixing
     * function as drift, corroboration and interval, on purpose -- one mixing
     * function in the library is one thing to check.
     */
    static long mix(long seed, int salt) {
        long z = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static int pick(long seed, int salt, int n) {
        if (n <= 1) return 0;
        return (int) Long.remainderUnsigned(mix(seed, salt), n);
    }

    /** n distinct indices out of poolSize, in a shuffled order. */
    static int[] deal(long seed, int salt, int poolSize, int n) {
        int[] idx = new int[poolSize];
        for (int i = 0; i < poolSize; i++) idx[i] = i;
        for (int i = 0; i < n; i++) {
            int j = i + pick(seed, salt + i, poolSize - i);
            int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
        }
        int[] out = new int[n];
        System.arraycopy(idx, 0, out, 0, n);
        return out;
    }

    /**
     * The board: BOARD_COUNT states from each corner, then shuffled.
     *
     * The shuffle is not cosmetic. A board laid out four corners at a time
     * would hand the player the whole of the game's difficulty for free, which
     * is the one thing a board must not do.
     */
    public static List<Reading> dealBoard(long seed) {
        List<Reading> raw = new ArrayList<>();
        Quadrant[] qs = Quadrant.values();
        for (int q = 0; q < qs.length; q++) {
            List<Reading> pool = BOARD_POOL.get(qs[q]);
            for (int i : deal(seed, 200 + q * 20, pool.size(), BOARD_COUNT[q])) raw.add(pool.get(i));
        }
        int[] order = deal(seed, 400, raw.size(), raw.size());
        List<Reading> out = new ArrayList<>();
        for (int i : order) out.add(raw.get(i));
        return out;
    }

    /**
     * The shift: SHIFT states, each in a different corner, none of them on the
     * board. Sorted by corner so the display does not move between reloads.
     */
    public static List<Reading> dealShift(long seed) {
        Quadrant[] qs = Quadrant.values();
        int[] pickQ = deal(seed, 500, qs.length, SHIFT);
        List<Reading> out = new ArrayList<>();
        for (int k = 0; k < SHIFT; k++) {
            List<Reading> pool = SHIFT_POOL.get(qs[pickQ[k]]);
            out.add(pool.get(pick(seed, 600 + k, pool.size())));
        }
        out.sort((a, b) -> a.quadrant().ordinal() - b.quadrant().ordinal());
        return out;
    }

    // ------------------------------------------------------------ the state

    public final long seed;
    /** The rule the machine actually follows. */
    public final Rule rule;
    public final List<Reading> board;
    public final List<Reading> shift;
    /** Indices into the board that have been walked through, in order. */
    public final List<Integer> shown = new ArrayList<>();
    /** Did the last demonstration land in a corner they had already seen? */
    public boolean lastRedundant;
    public boolean handedOver;

    Lesson(long seed) {
        this.seed = seed;
        this.rule = ORDER.get(pick(seed, 1, ORDER.size()));
        this.board = dealBoard(seed);
        this.shift = dealShift(seed);
    }

    public static Lesson of(long seed) { return new Lesson(seed); }
    public static Lesson of() { return new Lesson(System.nanoTime()); }

    public int nightsLeft() { return SHOWS - shown.size(); }

    public boolean canShow(int index) {
        return !handedOver && nightsLeft() > 0
                && index >= 0 && index < board.size() && !shown.contains(index);
    }

    /** Walk them through one state. Returns false if that was not allowed. */
    public boolean show(int index) {
        if (!canShow(index)) return false;
        Set<Rule> before = standing();
        shown.add(index);
        lastRedundant = standing().equals(before);
        return true;
    }

    public boolean ready() { return nightsLeft() == 0; }

    // -------------------------------------------------------- the new keeper

    /**
     * The rules still consistent with everything they have been shown.
     *
     * This is the whole of the student. There is no hidden state, no memory,
     * no mood: what they believe is a function of the demonstrations and
     * nothing else, which is what makes the game teachable and what makes the
     * report honest.
     */
    public Set<Rule> standing() {
        Set<Rule> out = new LinkedHashSet<>();
        for (Rule r : ORDER) {
            boolean ok = true;
            for (int i : shown) {
                Reading x = board.get(i);
                if (opens(r, x) != opens(rule, x)) { ok = false; break; }
            }
            if (ok) out.add(r);
        }
        return out;
    }

    /** The one they will act on: the first on the list that still fits. */
    public Rule preferred() {
        for (Rule r : ORDER) if (standing().contains(r)) return r;
        return rule;
    }

    /** What they would do in a state, given what they believe. */
    public boolean actsOn(Reading x) { return opens(preferred(), x); }

    public int wrongCount() {
        int n = 0;
        for (Reading x : shift) if (actsOn(x) != opens(rule, x)) n++;
        return n;
    }

    public boolean safe() { return wrongCount() == 0; }

    public boolean certain() { return standing().size() == 1; }

    /** A canonical state in each corner, for asking what a rule would do there. */
    public static Reading probe(Quadrant q) {
        return switch (q) {
            case HH -> r(50, 70);
            case HL -> r(50, 50);
            case LH -> r(30, 70);
            case LL -> r(30, 50);
        };
    }

    /** The corners where the rules still standing do not agree with each other. */
    public List<Quadrant> unsure() {
        List<Rule> s = new ArrayList<>(standing());
        List<Quadrant> out = new ArrayList<>();
        for (Quadrant q : Quadrant.values()) {
            Reading p = probe(q);
            boolean first = true, val = false;
            for (Rule r : s) {
                boolean v = opens(r, p);
                if (first) { val = v; first = false; }
                else if (v != val) { out.add(q); break; }
            }
        }
        return out;
    }

    /** The corners of the shift they get wrong. Empty when the night is safe. */
    public List<Quadrant> wrongCorners() {
        Rule r = preferred();
        List<Quadrant> out = new ArrayList<>();
        for (Reading x : shift) {
            if (opens(r, x) != opens(rule, x)) {
                Quadrant q = x.quadrant();
                if (!out.contains(q)) out.add(q);
            }
        }
        return out;
    }

    // -------------------------------------------------------------- the file

    /**
     * The handover, as three lines of plain text: the seed, the nights spent,
     * and whether they have taken the shift.
     *
     * The seed is the whole board and the whole shift, so a handover can be
     * picked up exactly where it was left -- and a player can read the file and
     * see which states they showed, which is the same joke the other plain-text
     * saves make.
     */
    public void save(Path p) throws IOException {
        StringBuilder d = new StringBuilder();
        for (int i : shown) { if (d.length() > 0) d.append(','); d.append(i); }
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, seed + "\n" + d + "\n" + (handedOver ? 1 : 0) + "\n");
    }

    public static Lesson load(Path p) throws IOException {
        if (!Files.exists(p)) return of();
        List<String> lines = Files.readAllLines(p);
        if (lines.isEmpty()) return of();
        long seed;
        try { seed = Long.parseLong(lines.get(0).trim()); } catch (Exception e) { return of(); }
        Lesson l = of(seed);
        if (lines.size() > 1) {
            String d = lines.get(1).trim();
            if (!d.isEmpty()) {
                for (String part : d.split(",")) {
                    try { l.show(Integer.parseInt(part.trim())); } catch (Exception ignored) { }
                }
            }
        }
        if (lines.size() > 2) l.handedOver = lines.get(2).trim().equals("1");
        return l;
    }

    // -------------------------------------------------------------- the prose

    public static final List<String> OPENING = List.of(
            "You have run this boiler for eleven years. In three nights you hand it "
                    + "to somebody else.",
            "The machine is two gauges and a vent. When the vent is open the boiler "
                    + "breathes; when it is shut it holds. You open it when the pressure "
                    + "is up. You have never had to say why, because you have never had "
                    + "to tell anybody.",
            "The board is what the machine has been doing lately: twelve states it has "
                    + "sat in. Three nights is three states you can walk them through. "
                    + "What you do not show them, they will not know.");

    public static final String RULES_HEADING = "THE MACHINE";

    public static final List<String[]> RULES = List.of(
            new String[] { "UP", "The pressure is up at " + PRESSURE_UP + " and above. "
                    + "The heat is up at " + HEAT_UP + " and above." },
            new String[] { "OPEN", "You open the vent when the pressure is up. "
                    + "That is the rule, and it is the whole of the rule." },
            new String[] { "", "Three nights. Each night you walk them through one state "
                    + "on the board." },
            new String[] { "", "Then they take the shift. The shift is three states, and "
                    + "none of them is on the board." });

    public static final String BOARD_HEAD = "THE BOARD";
    public static final String TONIGHT_HEAD = "TONIGHT";
    public static final String KEEPER_HEAD = "THE NEW KEEPER";
    public static final String PRESSURE = "pressure";
    public static final String HEAT = "heat";
    public static final String NIGHTS_LEFT = "nights left";
    public static final String NIGHT = "night";
    public static final String OF = "of";
    public static final String SHOW = "Walk them through it";
    public static final String SHOWN = "shown";
    public static final String START_LINE = "ENTER to take the first night.";
    public static final String START_BUTTON = "Take the first night";
    public static final String AGAIN = "Another handover";
    public static final String HINT = "UP/DOWN choose    ENTER show them    ESC the library";
    public static final String HINT_REPORT = "ENTER another handover    ESC the library";
    public static final String WATCHING = "They are watching. Nobody has shown them anything yet.";
    public static final String NOTHING_NEW = "That is the same corner as before. I already had that one.";
    public static final String HAND_OVER = "Three nights gone. ENTER and they take the shift.";
    /**
     * The handover, for a screen with a button on it instead of a keyboard.
     *
     * A hint is written for a keyboard and a label is written for a button --
     * Bearings learned this the hard way, when a label derived by stripping a
     * key name off a hint came out saying the opposite of what the button did.
     */
    public static final String HAND_OVER_SHORT = "Three nights gone.";
    public static final String HAND_OVER_BUTTON = "They take the shift";
    public static final String ALREADY_SHOWN = "You have already walked them through that one.";
    public static final String NO_NIGHTS = "There are no nights left.";

    public static final String REPORT_HEAD = "THE HANDOVER";
    public static final String SHIFT_HEAD = "THE SHIFT";
    public static final String BELIEF_HEAD = "WHAT THEY BELIEVED";
    public static final String OPENED = "opened the vent";
    public static final String SHUT = "left it shut";
    public static final String RIGHT = "right";
    public static final String WRONG = "wrong";
    public static final String SHOULD_OPEN = "should have been open";
    public static final String SHOULD_SHUT = "should have been shut";
    public static final String NOTHING_SHOWN = "You showed them nothing.";

    /** What a rule says, as the clause that follows "you open it when". */
    public static String whenOf(Rule r) {
        return switch (r) {
            case P_HIGH -> "the pressure is up";
            case P_LOW -> "the pressure is down";
            case H_HIGH -> "it is hot";
            case H_LOW -> "it is cool";
            case BOTH_HIGH -> "the pressure is up and it is hot";
            case EITHER_HIGH -> "either one of them is up";
            case BOTH_LOW -> "the pressure is down and it is cool";
            case EITHER_LOW -> "either one of them is down";
        };
    }

    /** A corner, said the long way, for a report that has to name one. */
    public static String cornerName(Quadrant q) {
        return switch (q) {
            case HH -> "when the pressure is up and it is hot";
            case HL -> "when the pressure is up and it is cool";
            case LH -> "when the pressure is down and it is hot";
            case LL -> "when the pressure is down and it is cool";
        };
    }

    /**
     * What the new keeper says they believe, given the rules still standing.
     *
     * One sentence, and it is a fact about the demonstrations rather than a
     * line of dialogue: a build that made the student sound more certain than
     * the arithmetic allows would be lying to the player about the one number
     * the whole game turns on.
     */
    public static String belief(List<Rule> s) {
        if (s.isEmpty()) return "I have nothing to go on.";
        if (s.size() == 1) {
            return "You open it when " + whenOf(s.get(0)) + ". That is all of it.";
        }
        StringBuilder b = new StringBuilder("You open it when " + whenOf(s.get(0)));
        int show = Math.min(s.size(), 3);
        for (int i = 1; i < show; i++) b.append(", or when ").append(whenOf(s.get(i)));
        b.append('.');
        if (s.size() > show) {
            int more = s.size() - show;
            b.append(more == 1 ? " There is one more." : " There are " + word(more) + " more.");
        }
        b.append(" I cannot tell which.");
        return b.toString();
    }

    /** How sure they are, which is a count and not a feeling. */
    public static String confidence(int n) {
        if (n <= 0) return "nothing";
        if (n == 1) return "one rule fits";
        return word(n) + " rules fit";
    }

    /** One state, said plainly, for a row or a report line. */
    public static String readingLine(Reading x) {
        return PRESSURE + " " + x.pressure() + ", " + HEAT + " " + x.heat();
    }

    /** What they did in a state, as the report says it. */
    public static String actionLine(Reading x, boolean open) {
        return readingLine(x) + " \u2014 they " + (open ? OPENED : SHUT) + ".";
    }

    /**
     * What the vent should have been, for a state they got wrong.
     *
     * The report marks a state wrong and then says what right was, because a
     * report that only says "wrong" makes the player work out the answer from
     * the rule they already knew -- and the whole point of the report is that
     * the player knew the rule and the student did not.
     */
    public static String shouldLine(boolean open) {
        return open ? SHOULD_OPEN : SHOULD_SHUT;
    }

    /**
     * The verdict. One line, and it is about what was taught rather than about
     * whether the student was any good -- because the student was never the
     * variable.
     */
    public static String verdict(boolean safe, boolean certain, List<Quadrant> corners) {
        if (safe && certain) {
            return "They know the machine. Every corner of it, and they will not need you again.";
        }
        if (safe) {
            return "The night was safe. They are still guessing about one corner \u2014 "
                    + cornerName(corners.get(0)) + " \u2014 because you never had a night "
                    + "to show them, and tonight it did not come up.";
        }
        return "They did what you taught them. What you taught them was wrong for "
                + cornerName(corners.get(0)) + ", and that is not their mistake.";
    }

    /**
     * The closing line. What the three nights were worth, said without
     * flattering the teacher: the nights are the thing that was spent, and the
     * report says so whether or not they were spent well.
     */
    public static String closing(boolean safe, boolean certain, boolean redundant) {
        if (safe && certain && redundant) {
            return "You showed them a corner twice and it did not cost you, because "
                    + "three corners were all tonight needed. It will not always be "
                    + "three.";
        }
        if (safe && certain) {
            return "You showed them the whole board, one corner at a time, and there "
                    + "is nothing left for them to find out the hard way.";
        }
        if (safe) {
            return "You taught them what tonight needed and no more. The rest is "
                    + "theirs to find out, and they will find it out the hard way or "
                    + "not at all.";
        }
        return "Three nights, and the corner that mattered was not one of them. "
                + "A state only ever teaches its own corner, and you showed them "
                + "three states that were not the one you needed.";
    }

    /** "6" -> "six". Small numbers only; the game never needs more than eight. */
    public static String word(int n) {
        String[] w = { "no", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve" };
        return n >= 0 && n < w.length ? w[n] : String.valueOf(n);
    }

    public static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The rules in a bitmask, for the phone build's belief table. */
    public static int maskOf(List<Rule> s) {
        int m = 0;
        for (Rule r : s) m |= 1 << r.ordinal();
        return m;
    }

    static List<Rule> maskToSet(int mask) {
        List<Rule> out = new ArrayList<>();
        for (Rule r : ORDER) if ((mask & (1 << r.ordinal())) != 0) out.add(r);
        return out;
    }

}
