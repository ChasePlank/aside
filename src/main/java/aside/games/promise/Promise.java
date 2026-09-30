package aside.games.promise;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * promise, the model.
 *
 * The sixteenth game, and the sixteenth verb: promising. The other fifteen are
 * about what you send, what you keep, what you compare, what you wait for, or
 * what you show somebody. This one is about the thing none of them touch: the
 * sentence you say to a person about a night that has not happened yet.
 *
 * THE SITUATION. You have the boat. There is a village on this shore and a town
 * on the other, and five nights before the season closes. Eight people ask you
 * for something over the first four of them. Each ask is a crossing, or two.
 *
 * THE RULE THE WHOLE THING RESTS ON. Saying yes is free. That is the entire
 * trouble with it. You have four crossings in you a night, and every night the
 * water takes some of them -- a tide, a cough in the outboard, a stranger on
 * the shingle -- and you do not know how much until it comes. So a promise is a
 * claim on a night you have not seen yet, made by a self who does not know what
 * that night will cost.
 *
 * AND THE THING IT IS ACTUALLY ABOUT. A player who says yes to all eight and a
 * player who says no to the two that cannot fit end up doing the same thing:
 * both of them carry six people and leave two on the shingle. The difference is
 * that one of them said yes first. That is the failure this game exists to make
 * legible, and it does not look like a failure while it is happening -- every
 * yes feels like a kindness, the nights keep coming, and the break arrives
 * wearing the face of bad luck instead of the face of a decision made on night
 * three.
 *
 * WHY IT IS FAIR. Nothing is hidden and nothing is random. The eight asks, the
 * nights they are made on, the nights they come due, and what the water takes
 * on each night are all declared below, and the screen shows the player what
 * they have promised and what is due tonight. A player who counts can see the
 * collision on the fourth night from the third. See aside.games.promise.SelfTest:
 * it checks that every ask is due after it is made, that the greedy strategy
 * (say yes to everything) breaks promises while the counting strategy does not,
 * and that six is the most anybody can keep.
 *
 * WHAT IS FIXED AND WHAT IS NOT. All of it is fixed. There is no seed: the
 * season is the season, the same every time. What varies is what you say, and
 * that is the only variable the game needs.
 */
public final class Promise {

    public static final String WORDMARK = "Promise";

    public static final int NIGHTS = 5;
    /** Crossings in you, every night. */
    public static final int CAPACITY = 4;
    public static final int ASKS = 8;
    /** The most anybody can keep. Checked by brute force in SelfTest. */
    public static final int MAX = 6;

    // ------------------------------------------------------------- the season

    /** One person asking for one thing, and what it costs when it comes due. */
    public record Ask(String who, String what, int cost, int due) {}

    /** What the water takes on a night, which nobody agreed to. */
    public record Water(String what, int cost) {}

    /**
     * The eight asks, in the order they are made.
     *
     * Ivor asks twice, Nell twice, Tess twice, and the schoolmaster once. The
     * second asks are the ones that matter: Nell's sister is ill on the third,
     * and Nell wants bringing home on the fifth, and by the time she asks the
     * second time the player has already spent the fourth night's room.
     */
    public static final List<Ask> REQUESTS = List.of(
            new Ask("Ivor", "The shop is out of everything. Take the crates across on the second.", 2, 2),
            new Ask("Nell", "Take me across on the third. My sister is ill.", 1, 3),
            new Ask("Tess", "Bring the doctor over on the fourth. She will have her bag with her.", 2, 4),
            new Ask("Bram", "Take me across on the third. I have a train to catch.", 1, 3),
            new Ask("Nell", "Bring me home on the fifth.", 1, 5),
            new Ask("the schoolmaster", "Take the children's things across on the fourth.", 2, 4),
            new Ask("Ivor", "One more run on the fifth. The last of it.", 1, 5),
            new Ask("Tess", "Bring the doctor back on the fifth.", 2, 5));

    /** Which night each ask is made on. Non-decreasing, so the game can walk it. */
    public static final int[] ASKED_ON = { 1, 1, 2, 2, 3, 3, 4, 4 };

    /**
     * What the water takes. Index 1..5, so a night number is an index.
     *
     * The fourth and fifth nights are where the season turns. The fourth takes
     * one and has two two-crossing promises due on it; the fifth takes two and
     * has three promises due on it. Neither is a surprise to a player who
     * counted, and both are a surprise to a player who said yes.
     */
    public static final Water[] WATER = {
            null,
            new Water("The outboard is coughing. It costs you a crossing to coax it.", 1),
            new Water("A stranger is on the shingle, soaked through. You take him over.", 1),
            new Water("The tide is wrong tonight. Every crossing costs you double.", 2),
            new Water("The lamp on the far jetty is out. You go and light it.", 1),
            new Water("The wind gets up. You lose two crossings to it.", 2) };

    // ------------------------------------------------------------- the state

    /** -1 not yet asked, 0 said no, 1 promised. */
    public final int[] answer = new int[ASKS];
    /** Promises that were made and then not kept. */
    public final boolean[] broken = new boolean[ASKS];
    public int night = 1;
    /** The next ask to put to the player, 0..ASKS. */
    public int askAt = 0;
    public boolean reported;

    public Promise() { Arrays.fill(answer, -1); }

    /** Is somebody standing in front of you right now? */
    public boolean asking() {
        return !reported && askAt < ASKS && ASKED_ON[askAt] == night;
    }

    public Ask current() { return REQUESTS.get(askAt); }

    public void answer(boolean yes) {
        if (!asking()) return;
        answer[askAt] = yes ? 1 : 0;
        askAt++;
    }

    /** The promises due tonight that are still standing. */
    public List<Integer> dueTonight() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < ASKS; i++) {
            if (answer[i] == 1 && !broken[i] && REQUESTS.get(i).due() == night) out.add(i);
        }
        return out;
    }

    /** Everything you promised that is not yet due and not yet broken. */
    public List<Integer> outstanding() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < ASKS; i++) {
            if (answer[i] == 1 && !broken[i] && REQUESTS.get(i).due() > night) out.add(i);
        }
        return out;
    }

    /** The water plus every promise due tonight. */
    public int load() {
        int l = WATER[night].cost();
        for (int i : dueTonight()) l += REQUESTS.get(i).cost();
        return l;
    }

    /** How much of tonight does not fit. Zero means the night is honest. */
    public int over() { return Math.max(0, load() - CAPACITY); }

    public void breakIt(int i) { broken[i] = true; }

    /** Move on. Refuses while the night is still over. */
    public void finishNight() {
        if (over() > 0) return;
        if (night >= NIGHTS) { reported = true; return; }
        night++;
    }

    // ------------------------------------------------------------- the count

    public int kept() {
        int n = 0;
        for (int i = 0; i < ASKS; i++) if (answer[i] == 1 && !broken[i]) n++;
        return n;
    }

    public int breaks() {
        int n = 0;
        for (boolean b : broken) if (b) n++;
        return n;
    }

    public int declined() {
        int n = 0;
        for (int a : answer) if (a == 0) n++;
        return n;
    }

    /**
     * What the season was worth.
     *
     * A promise kept is a person carried. A promise broken is a person who was
     * told yes and then left on the shingle, and it costs more than never having
     * said anything -- so it is subtracted, and a season of saying yes to
     * everything cannot outscore a season of saying yes to what fits.
     */
    public int score() { return kept() - breaks(); }

    // -------------------------------------------------------------- the save

    public void save(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("promise 1\n");
        sb.append("night ").append(night).append('\n');
        sb.append("askAt ").append(askAt).append('\n');
        sb.append("answers");
        for (int a : answer) sb.append(' ').append(a);
        sb.append('\n');
        sb.append("broken");
        for (boolean b : broken) sb.append(' ').append(b ? 1 : 0);
        sb.append('\n');
        sb.append("reported ").append(reported ? 1 : 0).append('\n');
        Files.writeString(p, sb.toString());
    }

    public static Promise load(Path p) throws IOException {
        Promise g = new Promise();
        if (!Files.exists(p)) return g;
        for (String line : Files.readAllLines(p)) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0) continue;
            switch (parts[0]) {
                case "night" -> { if (parts.length > 1) g.night = Integer.parseInt(parts[1]); }
                case "askAt" -> { if (parts.length > 1) g.askAt = Integer.parseInt(parts[1]); }
                case "answers" -> {
                    for (int i = 0; i < ASKS && i + 1 < parts.length; i++) g.answer[i] = Integer.parseInt(parts[i + 1]);
                }
                case "broken" -> {
                    for (int i = 0; i < ASKS && i + 1 < parts.length; i++) g.broken[i] = parts[i + 1].equals("1");
                }
                case "reported" -> { if (parts.length > 1) g.reported = parts[1].equals("1"); }
                default -> { }
            }
        }
        return g;
    }

    // ------------------------------------------------------------- the words

    public static final String OPEN_TITLE = "Promise";
    public static final String OPEN_SUB = "Five nights, eight people, and water that takes some of every one of them.";

    public static final String[] OPEN_SITUATION = {
            "You have the boat. There is a village on this shore and a town on the "
                    + "other, and between them there is water that does not care what "
                    + "anybody promised.",
            "Over the first four nights, eight people ask you for something. Each one "
                    + "is a crossing, or two. You can say yes to all of them. Saying yes "
                    + "costs you nothing at all.",
            "Then the night comes, and the water takes what it takes, and what is left "
                    + "is what you have." };

    public static final String RULES_HEADING = "the rule";
    public static final String[][] RULES = {
            { "yes is free", "It costs you nothing to promise. That is the whole "
                    + "trouble with it." },
            { "the water", "Every night the water takes something \u2014 a tide, a "
                    + "cough in the outboard, a stranger on the shingle. You will not "
                    + "know how much until it comes." },
            { "four crossings", "You have four crossings in you a night. What is due "
                    + "tonight and what the water takes both come out of those four." },
            { "breaking", "If it does not fit, you choose what to break. The water is "
                    + "never broken. A promise you break is worse than one you never "
                    + "made." } };

    public static final String START_LINE = "ENTER to open the season.";
    public static final String START_BUTTON = "open the season";

    public static final String NIGHT_LABEL = "night %d of %d";
    public static final String ASKS_HEAD = "what you have promised";
    public static final String NOTHING_PROMISED = "nothing yet";
    public static final String COST_ONE = "one crossing";
    public static final String COST_TWO = "two crossings";
    public static final String DUE_LINE = "%s \u00b7 due on the %s";
    public static final String SAY_YES = "Y  promise it";
    public static final String SAY_NO = "N  say no";
    /** The phone has buttons, not keys, so it gets its own words. */
    public static final String SAY_YES_BUTTON = "promise it";
    public static final String SAY_NO_BUTTON = "say no";
    public static final String YES_SAID = "promised";
    public static final String NO_SAID = "said no";

    public static final String WATER_HEAD = "the water";
    public static final String DUE_HEAD = "due tonight";
    public static final String NOTHING_DUE = "nothing is due tonight";
    public static final String LOAD_LINE = "%d of %d crossings";
    public static final String OVER_LINE = "%d over \u2014 choose what to break";
    public static final String FITS_LINE = "it fits";
    public static final String BREAK_KEY = "ENTER  break this one";
    public static final String GO_ON = "ENTER  go on";
    public static final String BROKEN_TAG = "broken";
    /**
     * The phone has buttons, not keys, so it gets its own words. A label derived
     * by stripping a key name off a keyboard hint is a hint wearing a button's
     * clothes -- Bearings shipped one that said "for the library" on a button
     * that sailed again.
     */
    public static final String BREAK_BUTTON = "break this one";
    public static final String GO_ON_BUTTON = "go on";

    public static final String REPORT_HEAD = "the season";
    public static final String AGAIN = "run the season again";
    public static final String KEPT_HEAD = "carried";
    public static final String BROKEN_HEAD = "broken";
    public static final String DECLINED_HEAD = "said no";
    public static final String NO_BREAKS = "nothing was broken";

    public static final String HINT_ASK = "Y promise it    N say no    M mute    [ quieter    ] louder";
    public static final String HINT_NIGHT = "ENTER go on    M mute    [ quieter    ] louder";
    public static final String HINT_BREAK = "UP/DOWN choose    ENTER break it    M mute";
    public static final String HINT_REPORT = "R run it again    ENTER for the library    M mute";

    public static String costWord(int cost) { return cost == 1 ? COST_ONE : COST_TWO; }

    /** "the second" .. "the fifth". The first night has nothing due on it. */
    public static String ordinal(int n) {
        return switch (n) {
            case 2 -> "second";
            case 3 -> "third";
            case 4 -> "fourth";
            case 5 -> "fifth";
            default -> "first";
        };
    }

    public static String dueLine(Ask a) {
        return String.format(DUE_LINE, costWord(a.cost()), ordinal(a.due()));
    }

    public static String nightLabel(int night) {
        return String.format(NIGHT_LABEL, night, NIGHTS);
    }

    public static String loadLine(int load) {
        return String.format(LOAD_LINE, load, CAPACITY);
    }

    public static String overLine(int over) {
        return String.format(OVER_LINE, over);
    }

    public static String worthLine(int score) {
        return "worth " + score + " of " + MAX;
    }

    public static String breaksLine(int breaks) {
        if (breaks == 0) return NO_BREAKS;
        if (breaks == 1) return "one promise was made and not kept";
        return breaks + " promises were made and not kept";
    }

    /** The label under a row in the report. */
    public static String rowWord(int answer, boolean broken) {
        if (answer == 1) return broken ? BROKEN_TAG : KEPT_HEAD;
        if (answer == 0) return DECLINED_HEAD;
        return "\u2014";
    }

    /** Said when nobody was promised anything and nothing was broken. */
    public static final String CLOSING_EMPTY =
            "You promised nobody, so nobody was let down. Nobody was carried either. "
                    + "The water took its crossings every night and you watched it, and "
                    + "the eight of them found another way across or they did not. That "
                    + "is a clean season and it is not the same as a good one.";

    public static String closing(int score) {
        if (score >= MAX) {
            return "Every promise you made, you kept. You said no twice, and you said "
                    + "it on the night they asked, before the water had a chance to say "
                    + "it for you. That is the whole of it: the no belongs to you, and a "
                    + "broken promise is what a no becomes when you give it away.";
        }
        if (score >= 5) {
            return "Nearly all of it. One promise went, and it went on a night that was "
                    + "already full \u2014 which is the only kind of night a promise ever "
                    + "goes on. Nobody was lied to. Somebody was told yes and then told "
                    + "nothing, and those are not the same thing.";
        }
        if (score >= 3) {
            return "You carried most of them and you broke some. Look at when you said "
                    + "yes: the nights that broke were already full when you promised, "
                    + "and the water on them was the same water it always is. Nothing "
                    + "surprised you. You promised a night you had not counted.";
        }
        if (score >= 1) {
            return "More was broken than carried. Every one of the breaks happened on a "
                    + "night that could not hold what was promised to it, and every one "
                    + "of those promises was free to make. That is the shape of it: the "
                    + "yes costs nothing, so you spent it, and the night sent the bill.";
        }
        if (score == 0) {
            return "You said yes to more than you had and then chose who to let down. The "
                    + "choosing happened on the night, but the promise happened on an "
                    + "earlier one, and that is where it was decided.";
        }
        return "You broke more than you carried. Every one of them was told yes, and "
                + "every one of them was told it on a night that was already full. The "
                + "water took what it always takes. Nothing here was bad luck.";
    }

}
