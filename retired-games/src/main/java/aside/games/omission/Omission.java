package aside.games.omission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * omission, the model.
 *
 * The sixteenth game, and the sixteenth verb: forgetting. The other fifteen are
 * about what you send, what you keep, what you compare, what you wait for, or
 * what you teach. This one is about the verb the whole library has been walking
 * around: what happens to a thing you did not keep.
 *
 * THE SITUATION. You have lived in this house for nine years and you are
 * leaving it tonight. You know twelve things about it. One bag, and five things
 * fit. You cannot take the house. You can take five things you know about it.
 *
 * THE RULE THE WHOLE THING RESTS ON. What you leave behind is not gone. It is
 * replaced by what you would have assumed -- and the assumption is right or
 * wrong depending on one thing only: **whether you ever said it out loud.** A
 * thing you said has been said back to you at least once, by somebody who was
 * there, and so it has been checked against the world. A thing you never said
 * has only ever been in your own head, and nothing has ever corrected it.
 *
 * THE TRAP, AND IT IS THE WHOLE GAME. Every one of the twelve has a weight --
 * how much it was worth to you -- and the obvious move is to spend the five
 * slots on the five heaviest. That move loses, and it loses for a reason that
 * is true outside the game: **the things that mattered most are the things you
 * said out loud.** You have been telling people about the boiler and the clock
 * and the tree for nine years. The things nobody knows are the small ones --
 * the stair that creaks on the left, the door you have to lift. A player who
 * keeps what mattered keeps what was already safe and loses what was not.
 *
 * WHY IT IS FAIR. The signal is in the material, not hidden: every detail
 * carries a line saying whether you ever said it, and the rule in the opening
 * says what to do with that line. The weights are declared. And the arithmetic
 * is checked rather than asserted -- see aside.games.omission.SelfTest, which
 * proves that a thing you said is remembered as it was, that a thing you did
 * not say is remembered as the assumption and not as the truth, that keeping by
 * weight is worse than keeping by silence on every seed, and that there are
 * always more silent things than slots.
 *
 * WHAT IS FIXED AND WHAT IS NOT. The eighteen details, their weights, their
 * lines and their assumptions are fixed. What the seed decides is which twelve
 * of the eighteen are on the list, and in what order.
 */
public final class Omission {

    public static final String WORDMARK = "Omission";
    public static final String WHERE = "the house";
    public static final String WHERE_LIST = "what you know";
    public static final String WHERE_REPORT = "what you kept";

    /** How many things about the house you know. */
    public static final int LIST = 12;
    /** How many of them fit in the one bag. */
    public static final int SLOTS = 5;

    // ------------------------------------------------------------ the detail

    /**
     * One thing you know about the house.
     *
     * `told` is the whole game. `said` is how the player learns it. `assumed`
     * is what the detail becomes when it is left behind and nobody ever said it
     * back -- and for a thing you did say, the assumption is the truth, which
     * is what makes saying it the thing that saves it.
     */
    public record Detail(String id, String text, String said, int matters,
                         boolean told, String assumed) {}

    static Detail said(String id, String text, String said, int matters) {
        return new Detail(id, text, said, matters, true, text);
    }

    static Detail unsaid(String id, String text, String said, int matters, String assumed) {
        return new Detail(id, text, said, matters, false, assumed);
    }

    /**
     * The eighteen things you know, six of which you have said out loud.
     *
     * Six and not more, on purpose: it is what guarantees that any twelve of
     * the eighteen contains at least six things nobody else knows, so the five
     * slots can never cover them and the night always costs something. The
     * weights are skewed the other way -- the said things average heavier than
     * the unsaid ones -- because that is the trap, and it is not a rigged trap:
     * it is what happens when you talk about the things you care about.
     *
     * The one deliberate exception is the bedroom door, the heaviest thing on
     * the list and one you never told anybody about. A trap with no exception
     * in it is a rule, and a player who kept by weight should get *something*
     * right, so that the report can show them what the strategy actually cost
     * rather than telling them it was worthless.
     */
    public static final List<Detail> POOL = List.of(
            // ---- the six you said out loud ----
            said("door-green", "The front door was green.",
                    "You told the neighbours, and the man who fixed the gutter, "
                            + "and the woman at the estate agent's.", 2),
            said("clock-fast", "The kitchen clock ran four minutes fast.",
                    "You said it at the fence, twice, to two different people.", 3),
            said("boiler-october", "The boiler was serviced every October.",
                    "You put it in writing, to the people who bought the house.", 3),
            said("house-hill", "The house stood at the top of a hill.",
                    "You said it every time you gave anybody directions, and you "
                            + "never once had to be asked.", 1),
            said("cherry-tree", "There was a cherry tree at the end of the garden.",
                    "You mentioned the tree to everyone who asked about the garden.", 2),
            said("tap-drip", "The bathroom tap dripped all night.",
                    "You complained about it at work for a year, and never got it fixed.", 2),

            // ---- the twelve you never said to anybody ----
            unsaid("stair-left", "The third stair creaked on the left.",
                    "You never said this to anybody.",
                    1, "The third stair creaked on the right."),
            unsaid("drawer-stick", "The second drawer in the dresser stuck unless you lifted it.",
                    "You never mentioned the drawer.",
                    1, "The second drawer slid out easily."),
            unsaid("back-key", "The back door key turned twice.",
                    "Nobody else ever used the back door.",
                    2, "The back door key turned once."),
            unsaid("hall-light", "The hall light took four seconds to come on.",
                    "You never said it out loud.",
                    2, "The hall light came on at once."),
            unsaid("small-window", "The window in the small room faced east.",
                    "You never told anyone which way it faced.",
                    1, "The window in the small room faced west."),
            unsaid("cold-water", "The water in the morning ran cold for a long time.",
                    "It did not come up.",
                    1, "The water in the morning ran hot in a moment."),
            unsaid("three-steps", "There were three steps down to the garden.",
                    "You never counted them out loud.",
                    1, "There were two steps down to the garden."),
            unsaid("high-shelf", "The pantry shelf was too high to reach without the stool.",
                    "You never said so.",
                    2, "The pantry shelf was at eye level."),
            unsaid("wall-phone", "The telephone was on the wall by the door.",
                    "You never described it to anyone.",
                    1, "The telephone was on the table in the hall."),
            unsaid("sloped-floor", "The floor in the front room sloped toward the window.",
                    "You were not sure it was true, so you said nothing.",
                    2, "The floor in the front room was level."),
            unsaid("gate-out", "The gate at the end of the path opened outward.",
                    "You never mentioned the gate.",
                    1, "The gate at the end of the path opened inward."),
            unsaid("bedroom-door", "The bedroom door had to be lifted to close.",
                    "You never told anyone about the door.",
                    3, "The bedroom door closed with a push."));

    // -------------------------------------------------------------- the deal

    /**
     * Counter-based, not a stream: every number is a pure function of
     * (seed, salt), so there is no RNG position to carry and the phone build
     * can deal the same list without porting java.util.Random. The same mixing
     * function as drift, corroboration, interval and lesson, on purpose -- one
     * mixing function in the library is one thing to check.
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
     * The twelve things on the list, in the order they are read.
     *
     * Dealt rather than laid out, so the six said things are not clustered and
     * a player cannot find them by position. The order is the deal's order and
     * nothing else -- sorting the list by anything would hand over part of the
     * answer.
     */
    public static List<Detail> dealList(long seed) {
        int[] idx = deal(seed, 100, POOL.size(), LIST);
        List<Detail> out = new ArrayList<>();
        for (int i : idx) out.add(POOL.get(i));
        return out;
    }

    // ------------------------------------------------------------- the state

    public final long seed;
    public final List<Detail> list;
    /** Indices into the list that went in the bag, in the order chosen. */
    public final List<Integer> kept = new ArrayList<>();
    public boolean left;

    Omission(long seed) {
        this.seed = seed;
        this.list = dealList(seed);
    }

    public static Omission of(long seed) { return new Omission(seed); }
    public static Omission of() { return new Omission(System.nanoTime()); }

    public int slotsLeft() { return SLOTS - kept.size(); }

    public boolean canKeep(int index) {
        return !left && index >= 0 && index < list.size() && !kept.contains(index)
                && slotsLeft() > 0;
    }

    public boolean keep(int index) {
        if (!canKeep(index)) return false;
        kept.add(index);
        return true;
    }

    /** Take it back out of the bag. Only before you leave. */
    public boolean drop(int index) {
        if (left) return false;
        return kept.remove((Integer) index);
    }

    public boolean ready() { return slotsLeft() == 0; }

    public void leave() { left = true; }

    // ------------------------------------------------------------ the memory

    /**
     * What you remember about a thing, after the bag is closed.
     *
     * A thing in the bag you remember as it was. A thing you left behind you
     * remember as it was only if you had said it out loud -- otherwise you
     * remember the assumption, which is a different thing wearing the same
     * shape.
     */
    public String believed(int index) {
        Detail d = list.get(index);
        if (kept.contains(index) || d.told()) return d.text();
        return d.assumed();
    }

    /** Did you come out of the house with this one right? */
    public boolean right(int index) {
        return believed(index).equals(list.get(index).text());
    }

    /** How many of the twelve you got right, as a count. */
    public int rightCount() {
        int n = 0;
        for (int i = 0; i < list.size(); i++) if (right(i)) n++;
        return n;
    }

    /** What you kept, weighted by what it was worth. */
    public int score() {
        int n = 0;
        for (int i = 0; i < list.size(); i++) if (right(i)) n += list.get(i).matters();
        return n;
    }

    /** Everything there was to keep, weighted. */
    public int total() {
        int n = 0;
        for (Detail d : list) n += d.matters();
        return n;
    }

    /**
     * The most that was available to a player who knew the rule.
     *
     * Everything you said comes back to you for free, so it is never worth a
     * slot; the slots go to the heaviest things nobody else knew. This is the
     * number the verdict measures you against, and it is derived rather than
     * declared -- a build that changed the rule would move it.
     */
    public int best() {
        int n = 0;
        List<Integer> silent = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).told()) n += list.get(i).matters();
            else silent.add(list.get(i).matters());
        }
        silent.sort((a, b) -> b - a);
        for (int i = 0; i < Math.min(SLOTS, silent.size()); i++) n += silent.get(i);
        return n;
    }

    /** How many things nobody else knew were on the list. */
    public int silentCount() {
        int n = 0;
        for (Detail d : list) if (!d.told()) n++;
        return n;
    }

    /** The heaviest thing you left behind that nobody else knew, or null. */
    public Detail heaviestLost() {
        Detail best = null;
        for (int i = 0; i < list.size(); i++) {
            Detail d = list.get(i);
            if (d.told() || kept.contains(i)) continue;
            if (best == null || d.matters() > best.matters()) best = d;
        }
        return best;
    }

    /**
     * The heaviest thing you spent a slot on that would have come back anyway,
     * or null. A slot spent on something you had already said is the whole
     * mistake the game is about, so the report names it.
     */
    public Detail heaviestWasted() {
        Detail best = null;
        for (int i : kept) {
            Detail d = list.get(i);
            if (!d.told()) continue;
            if (best == null || d.matters() > best.matters()) best = d;
        }
        return best;
    }

    /** How many slots went to things you had already said out loud. */
    public int wastedSlots() {
        int n = 0;
        for (int i : kept) if (list.get(i).told()) n++;
        return n;
    }

    // -------------------------------------------------------------- the file

    /**
     * The bag, as three lines of plain text: the seed, what went in, and
     * whether you have left.
     *
     * The seed is the whole list, so a night can be picked up exactly where it
     * was left -- and a player can read the file and see what they took, which
     * is the same joke the other plain-text saves make.
     */
    public void save(Path p) throws IOException {
        StringBuilder d = new StringBuilder();
        for (int i : kept) { if (d.length() > 0) d.append(','); d.append(i); }
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, seed + "\n" + d + "\n" + (left ? 1 : 0) + "\n");
    }

    public static Omission load(Path p) throws IOException {
        if (!Files.exists(p)) return of();
        List<String> lines = Files.readAllLines(p);
        if (lines.isEmpty()) return of();
        long seed;
        try { seed = Long.parseLong(lines.get(0).trim()); } catch (Exception e) { return of(); }
        Omission o = of(seed);
        if (lines.size() > 1) {
            String d = lines.get(1).trim();
            if (!d.isEmpty()) {
                for (String part : d.split(",")) {
                    try { o.keep(Integer.parseInt(part.trim())); } catch (Exception ignored) { }
                }
            }
        }
        if (lines.size() > 2 && lines.get(2).trim().equals("1")) o.leave();
        return o;
    }

    // -------------------------------------------------------------- the prose

    public static final List<String> OPENING = List.of(
            "You have lived in this house for nine years. You are leaving it "
                    + "tonight, and you are not coming back.",
            "You cannot take the house. You can take five things you know about it. "
                    + "There are twelve on the list, and one bag.",
            "What you leave behind is not gone. You will remember it. You will just "
                    + "remember it the way you would have guessed, and you will not be "
                    + "able to tell which ones those are.");

    public static final String RULES_HEADING = "THE ONE THING THAT SAVES A MEMORY";

    public static final List<String[]> RULES = List.of(
            new String[] { "SAID", "A thing you said out loud has been said back to you "
                    + "at least once, by somebody who was there. It has been checked "
                    + "against the world, and it comes back to you on its own." },
            new String[] { "UNSAID", "A thing you never said has only ever been in your "
                    + "own head. Nothing has ever corrected it, and what you remember "
                    + "will be what you would have assumed." },
            new String[] { "", "Five things fit in the bag. Spend them on what nobody "
                    + "else knows." });

    public static final String LIST_HEAD = "WHAT YOU KNOW";
    public static final String BAG_HEAD = "THE BAG";
    public static final String WORTH_HEAD = "HOW MUCH IT MATTERED";
    public static final String SAID_TAG = "said";
    public static final String UNSAID_TAG = "never said";
    public static final String SLOTS_LEFT = "left in the bag";
    public static final String SLOT = "slot";
    public static final String SLOTS_WORD = "slots";
    public static final String START_LINE = "ENTER to start filling the bag.";
    public static final String START_BUTTON = "Start filling the bag";
    public static final String TAKE = "Put it in the bag";
    public static final String TAKE_BACK = "Take it back out";
    public static final String FULL = "The bag is full.";
    public static final String NOTHING_YET = "Nothing in the bag yet.";
    public static final String LEAVE_LINE = "The bag is full. Close it and go.";
    public static final String LEAVE_KEY = "Close the bag and go";
    public static final String LEAVE_BUTTON = "Close the bag and go";
    public static final String NOT_YET = "The bag is not full yet.";
    public static final String AGAIN = "Another house";
    public static final String HINT = "UP/DOWN choose    ENTER in or out    ESC the library";
    public static final String HINT_REPORT = "ENTER another house    ESC the library";

    public static final String REPORT_HEAD = "WHAT YOU KEPT";
    public static final String READ_BACK_HEAD = "WHAT YOU REMEMBER";
    public static final String KEPT_TAG = "in the bag";
    public static final String CAME_BACK_TAG = "left, and it came back";
    public static final String LOST_TAG = "left, and you have it wrong";
    public static final String RIGHT = "right";
    public static final String WRONG = "wrong";
    public static final String YOU_BELIEVE = "you remember it as";
    public static final String SCORE_HEAD = "WHAT YOU KEPT, WEIGHTED";

    /** A weight, said as a word, because a number in a column reads as a score. */
    public static String worth(int m) {
        return switch (m) {
            case 3 -> "most";
            case 2 -> "some";
            default -> "little";
        };
    }

    /** "6" -> "six". Small numbers only; the list is twelve. */
    public static String word(int n) {
        String[] w = { "no", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve" };
        return n >= 0 && n < w.length ? w[n] : String.valueOf(n);
    }

    /**
     * The verdict. One line, and it is about the slots rather than about the
     * memory -- because the memory did exactly what memory does, and the slots
     * were the only thing the player ever chose.
     */
    public static String verdict(int score, int best, Detail wasted, Detail lost) {
        if (score >= best) {
            return "Every slot went to something nobody else knew. There was "
                    + "nothing better to do with five.";
        }
        if (wasted != null && (lost == null || wasted.matters() >= lost.matters())) {
            return "You spent a slot on something you had already said out loud. "
                    + "It would have come back to you on its own.";
        }
        if (lost != null) {
            return "You left behind the heaviest thing nobody else knew, and you "
                    + "will remember it wrong.";
        }
        return "You kept less than you could have.";
    }

    /**
     * The closing. What the five slots were worth, said without flattering the
     * player: the slots are the thing that was spent, and the report says so
     * whether or not they were spent well.
     */
    public static String closing(int score, int best, int silent, int wasted) {
        if (score >= best) {
            return "Five slots, and " + word(silent) + " things nobody else knew. "
                    + "You spent every one of them on the part of the house that "
                    + "would not have come back.";
        }
        if (wasted > 0) {
            return word(wasted) + (wasted == 1 ? " slot went" : " slots went")
                    + " to something you had said out loud, and the thing you had "
                    + "said out loud was never in danger. That is the whole of it: "
                    + "you kept what was already safe.";
        }
        return "You spent the slots on the things nobody else knew and you still "
                + "came out short, because there were more of them than there was "
                + "room. That is not a mistake. That is the arithmetic.";
    }

    /** The line under the score, so the weighted number is not the only one. */
    public static String scoreLine(int count, int list, int score, int total) {
        return word(count) + " of " + word(list) + " things, worth " + score
                + " of the " + total + " that was there.";
    }

    /** The best a player who knew the rule could have done, said plainly. */
    public static String bestLine(int best) {
        return "The most that was there to keep: " + best + ".";
    }

}
