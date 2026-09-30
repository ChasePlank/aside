package aside.games.outside;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * outside, the whole game, with nothing drawn.
 *
 * You are on the ridge. Below you is the haul road, the river, and the yard,
 * and in the yard there is a dispatcher in a room with no window. It routes
 * trucks. It cannot see anything. Everything it has ever known about the
 * world it knows because somebody on the ridge told it.
 *
 * Seven days. One report a day, one thing, or nothing. Each day it acts on
 * what it has, and you watch.
 *
 * The rule that makes it a game rather than a memory test: <b>it acts on the
 * first thing it ever heard about each thing.</b> It has no clock. A fact
 * from day one and a fact from day seven are the same age to it, and when
 * they disagree, the older one wins, because the older one is the one it
 * has believed longest.
 *
 * So a report is not a correction. It is one more thing it now believes,
 * ranked behind everything you said before it. The only edit available to
 * you is silence: a thing you never mention keeps its slot open, and
 * whatever you say about it later will be the first thing it heard.
 *
 * Which is the whole cost. Silence is the only edit, and silence is a day
 * the truck does not run.
 *
 * This class is pure Java and holds no JavaFX. The window onto it lives in
 * {@link OutsideScreen}.
 */
public final class Outside {

    // ----------------------------------------------------------- vocabulary

    /** The three things the dispatcher has to decide. */
    public enum Concern {
        CROSSING("the crossing"),
        LOAD("the load"),
        HOUR("the hour");

        public final String label;
        Concern(String label) { this.label = label; }
    }

    /** The words a decision can be made in. */
    public static String valueLabel(String value) {
        return switch (value) {
            case "ford"   -> "cross at the ford";
            case "bridge" -> "cross by the bridge";
            case "wait"   -> "do not cross";
            case "full"   -> "take the full load";
            case "light"  -> "take a light load";
            case "empty"  -> "go down empty";
            case "dawn"   -> "go at first light";
            case "noon"   -> "go at midday";
            case "dark"   -> "go after dark";
            default       -> value;
        };
    }

    // ------------------------------------------------------------- the voice

    /**
     * Every fixed line the game says, in one place.
     *
     * There are two builds now -- the JavaFX screen and the phone build -- and
     * a sentence kept in the screen is a sentence the phone build does not
     * have. So the model holds the writing and the screens hold the layout,
     * which is the same split ledger, residue, testimony and vigil were moved
     * to, and SelfTest enforces it: the screen may not hold a second copy of
     * any line in here.
     *
     * The lines that depend on state are methods rather than constants, and
     * they take the state as arguments rather than reading it. That is not
     * tidiness -- the phone build has its own copy of the state, and the only
     * way to be sure it prints the same sentence is for the sentence to be
     * built from numbers on both sides.
     */

    public static final String THE_RIDGE = "THE RIDGE";
    public static final String RULES_HEADING = "HOW IT DECIDES";
    public static final String WHAT_YOU_CAN_SEE = "WHAT YOU CAN SEE";
    public static final String SAY_NOTHING = "Say nothing today.";
    public static final String WHAT_IT_HOLDS = "WHAT IT HOLDS";
    public static final String NOTHING = "nothing";
    public static final String FIRST_LINE_NOTE =
            "The first line under each heading is the one it acts on.";
    /** The phone's version of the same note, where there is one line per heading. */
    public static final String STRIP_NOTE =
            "It acts on the first thing it ever heard about each of these.";
    public static final String THE_DISPATCHER = "THE DISPATCHER";
    public static final String TRUCK_WENT = "The truck went down.";
    public static final String TRUCK_DID_NOT_GO = "The truck did not go.";
    public static final String THE_WORLD = "the world";
    public static final String IT_DID = "it did";
    public static final String NO_REPORT = "no report";
    public static final String NOTHING_ROUTED = "Nothing was routed today.";
    public static final String WEEK_OVER = "The week is over.";
    public static final String BELIEVED_HEADING = "WHAT IT BELIEVED, AND WHEN YOU SAID IT";
    public static final String NEVER_SAID = "never said";
    public static final String NO_CLOCK_LINE = "It has no clock. Everything you ever told it is "
            + "the same age, and the first thing is the loudest.";
    public static final String THE_RIDGE_LOG = "THE RIDGE LOG";
    public static final String NOTHING_WAS_EVER_SAID = "nothing was ever said";
    public static final String WEEK_IS_OVER = "the week is over";
    public static final String SAID_NOTHING_TODAY = "You said nothing today.";
    public static final String ACTED_ON_WHAT_IT_HAD =
            "It acted on what it already had, which is all it ever does.";

    /** The premise, once, before the first day. */
    public static final List<String> OPENING = List.of(
            "There is a haul road down the ridge to the river, and a yard at the "
          + "bottom of it, and in the yard a dispatcher sits in a room with no window.",

            "It routes the trucks. It cannot see the road, or the water, or the weather. "
          + "Everything it has ever known about the world outside that room, it knows "
          + "because somebody on the ridge told it.",

            "That is you. You get one report a day: one thing, or nothing at all. Then "
          + "it acts on what it has, and you watch what it does.");

    /** The one thing the player has to understand before the first day. */
    public static final List<String> RULES = List.of(
            "It has no clock. A thing it heard on the first day and a thing it heard "
          + "on the last are the same age to it.",

            "For each thing it has to decide, it does what the FIRST thing it ever "
          + "heard about that thing said.",

            "So a new report is not a correction. It is one more thing it believes, "
          + "ranked behind everything you said before it.",

            "The only way to keep a decision open is to say nothing about it.");

    /** The header's right-hand line, on both builds. */
    public static String where(int day, boolean finished) {
        return finished ? WEEK_IS_OVER
                : "day " + Math.min(day + 1, DAYS.size()) + " of " + DAYS.size();
    }

    public static String dayLabel(int day) { return "day " + day; }

    public static String fromDay(int day) { return "from day " + day; }

    public static String rightToday(int n) { return n + " of 3 right today."; }

    public static String saidToday(String words) {
        return "You said today: \"" + words + "\"";
    }

    public static String actedOnEarlier(int day) {
        return "It acted on day " + day + " instead, because day " + day + " got there first.";
    }

    public static String reachedIt(Concern c) {
        return "That one reached it, because nothing had been said about " + c.label + " before.";
    }

    /** The week, as a sentence. */
    public static String scoreLine(int correct, int ranDays) {
        return correct + " of " + DECISIONS + " decisions came out right. The truck ran on "
                + ranDays + " of " + DAYS.size() + " days.";
    }

    /** How many things were ever visible, and how many of them were said. */
    public static String visibleLine(int said) {
        return DAYS.size() * 4 + " things were visible. " + said + " of them were ever said.";
    }

    /**
     * The closing line, chosen from how the week actually went.
     *
     * Takes the two numbers instead of reading the state, so the phone build
     * can ask the same question of its own week and get the same answer.
     */
    public static String closing(int correct, int said) {
        if (said == 0) {
            return "You never told it anything. It never moved. A room with no window "
                 + "and no reports is not careful, it is empty.";
        }
        if (correct >= bestPossible()) {
            return "You held things back until they were worth saying, and it still "
                 + "could not tell this week from last week. That is the best week "
                 + "there is, and it is not a good one.";
        }
        if (said >= DAYS.size()) {
            return "You told it everything you saw, every day. It kept all of it, and "
                 + "it acted on the first of it. Nothing you said after the first day "
                 + "could reach the decisions you had already made for it.";
        }
        if (correct >= 8) {
            return "You said most of the right things in most of the right order. The "
                 + "days it got wrong are the days it was still holding your earliest "
                 + "words about something that had moved.";
        }
        if (correct >= 5) {
            return "It was right about as often as it was wrong, which is what a room "
                 + "with no window and a week of secondhand weather looks like.";
        }
        return "It spent the week acting on things that were true when you said them. "
             + "None of them were true by the end. That is not carelessness on either "
             + "of your parts. It is what a fixed mind does with a moving world.";
    }

    // --------------------------------------------------------------- content

    /** One thing you can see, in the words you would use to say it. */
    public static final class Fact {
        public final String id;
        public final Concern concern;
        public final String value;
        public final String words;
        /** The day it was offered, 1-based. Stamped once, in the static block. */
        public int day;

        Fact(String id, Concern concern, String value, String words) {
            this.id = id;
            this.concern = concern;
            this.value = value;
            this.words = words;
        }
    }

    /** One day on the ridge: what you can see, and what is actually true. */
    public static final class Day {
        public final int index;                  // 0-based
        public final String heading;             // "Day one"
        public final String title;
        public final String scene;
        public final List<Fact> facts;
        public final Map<Concern, String> truth;

        Day(int index, String heading, String title, String scene,
            Map<Concern, String> truth, List<Fact> facts) {
            this.index = index;
            this.heading = heading;
            this.title = title;
            this.scene = scene;
            this.truth = truth;
            this.facts = facts;
        }
    }

    static Fact f(String id, Concern c, String value, String words) {
        return new Fact(id, c, value, words);
    }

    static Map<Concern, String> truth(String crossing, String load, String hour) {
        Map<Concern, String> m = new EnumMap<>(Concern.class);
        m.put(Concern.CROSSING, crossing);
        m.put(Concern.LOAD, load);
        m.put(Concern.HOUR, hour);
        return m;
    }

    static Day day(int i, String heading, String title, String scene,
                   Map<Concern, String> truth, Fact... facts) {
        return new Day(i, heading, title, scene, truth, List.of(facts));
    }

    public static final List<Day> DAYS = List.of(

        day(0, "Day one", "First light",
            "You are on the ridge before the sun. Below you the haul road runs down "
          + "through the cut to the river, and the yard is a lit square at the bottom "
          + "of it. The dispatcher's window has been boarded since the yard was built. "
          + "It will hear whatever you say to it, and nothing else.",
            truth("ford", "full", "dawn"),
            f("d1a", Concern.CROSSING, "ford",   "The river is down to the shingle. A truck could cross at the ford."),
            f("d1b", Concern.CROSSING, "bridge", "The bridge planks are sound. The survey was done in spring."),
            f("d1c", Concern.LOAD,     "full",   "The truck is loaded to the boards and the driver is fresh."),
            f("d1d", Concern.HOUR,     "dawn",   "The light comes up clean and the road down is dry.")),

        day(1, "Day two", "Rain upstream",
            "It rained hard in the hills in the night, but the river has not turned "
          + "yet. The road down is greasy where the cut shadows it.",
            truth("ford", "full", "dawn"),
            f("d2a", Concern.HOUR,     "noon",  "The road is greasy in the shade until the sun is high."),
            f("d2b", Concern.CROSSING, "wait",  "The river is carrying colour and the first of the driftwood. It will be over the shingle by tomorrow."),
            f("d2c", Concern.LOAD,     "full",  "The truck is loaded to the boards and the driver is fresh."),
            f("d2d", Concern.CROSSING, "ford",  "The river is up a foot. The shingle is still there under it.")),

        day(2, "Day three", "The river comes up",
            "The ford is gone. The water is over the shingle and still climbing, and "
          + "the bridge is taking the whole of it.",
            truth("wait", "light", "noon"),
            f("d3a", Concern.CROSSING, "wait",   "The ford is gone and the river is still rising."),
            f("d3b", Concern.CROSSING, "bridge", "The bridge is the only way over and it is holding."),
            f("d3c", Concern.LOAD,     "light",  "The cut face moved in the night. The ore is coming off wet and heavy."),
            f("d3d", Concern.HOUR,     "dawn",   "The road is dry again at first light.")),

        day(3, "Day four", "The bridge",
            "There is a sound in the bridge you can hear from the ridge, a plank "
          + "working. The cut has started running trucks of its own.",
            truth("wait", "light", "noon"),
            f("d4a", Concern.LOAD,     "light",  "The cut is sending wet ore down and the road is taking it, half a truck at a time."),
            f("d4b", Concern.LOAD,     "empty",  "The truck's springs are gone. It should go down light or not at all."),
            f("d4c", Concern.CROSSING, "wait",   "The bridge is moving in the current. Nobody should be on it."),
            f("d4d", Concern.HOUR,     "dark",   "The road is only clear of the cut's traffic after dark.")),

        day(4, "Day five", "Half loads",
            "The cut is working again and half loads are going down all day. The river "
          + "has not fallen.",
            truth("wait", "light", "noon"),
            f("d5a", Concern.HOUR,     "noon",  "The sun is high and the shade is off the road."),
            f("d5b", Concern.LOAD,     "light", "The cut is sending half loads down and the road is taking them."),
            f("d5c", Concern.CROSSING, "wait",  "The river is still over the shingle and the bridge is still working."),
            f("d5d", Concern.HOUR,     "dark",  "The cut's trucks are still on the road until dark, and they take the middle of it.")),

        day(5, "Day six", "The last report",
            "Frost on the road until the sun is on it. The yard has been asking for "
          + "ore for four days.",
            truth("wait", "light", "noon"),
            f("d6a", Concern.CROSSING, "wait",  "The river has not fallen. It will not fall tonight."),
            f("d6b", Concern.LOAD,     "light", "The yard needs what the truck can bring safely and no more."),
            f("d6c", Concern.HOUR,     "noon",  "The road is at its best at midday and the light holds until six."),
            f("d6d", Concern.HOUR,     "dawn",  "There is frost on the road until the sun is on it.")),

        day(6, "Day seven", "The river falls",
            "In the night the river came down. The shingle is showing, the bridge has "
          + "stopped working, and the frost is off the road. You are still on the "
          + "ridge, and you have one more report to make.",
            truth("bridge", "full", "dawn"),
            f("d7a", Concern.CROSSING, "bridge", "The river has fallen. The bridge is quiet. It can cross."),
            f("d7b", Concern.CROSSING, "ford",   "The ford is passable again, but the bridge is the better line today."),
            f("d7c", Concern.LOAD,     "full",   "The springs have been replaced. The truck can take a full load."),
            f("d7d", Concern.HOUR,     "dawn",   "The frost is gone and the first light is clean.")));

    static {
        for (Day d : DAYS) for (Fact x : d.facts) x.day = d.index + 1;
    }

    /** How many decisions the week contains: seven days, three each. */
    public static final int DECISIONS = 21;

    // --------------------------------------------------------------- routing

    /** One of the dispatcher's calls, and why it made it. */
    public static final class Decision {
        public final Concern concern;
        /** What the world actually is that day. */
        public final String truth;
        /** What it did, or null if it had nothing to act on. */
        public final String did;
        /** The day it learned the fact it acted on, or 0 if it had none. */
        public final int fromDay;

        Decision(Concern concern, String truth, String did, int fromDay) {
            this.concern = concern;
            this.truth = truth;
            this.did = did;
            this.fromDay = fromDay;
        }

        public boolean right() { return did != null && did.equals(truth); }
    }

    /** Everything the dispatcher did on one day. */
    public static final class Routing {
        public final int day;                       // 1-based
        public final List<Decision> decisions;
        /** The truck only goes if it knows how to cross. */
        public final boolean ran;

        Routing(int day, List<Decision> decisions, boolean ran) {
            this.day = day;
            this.decisions = decisions;
            this.ran = ran;
        }

        public int correct() {
            // A day the truck does not go is a day of nothing, whatever the
            // dispatcher happened to be right about in its head. The yard
            // measures the week in ore, not in opinions.
            if (!ran) return 0;
            int n = 0;
            for (Decision d : decisions) if (d.right()) n++;
            return n;
        }
    }

    // ----------------------------------------------------------------- state

    /** Everything it has ever heard, in the order it heard it. */
    public final List<Fact> heard = new ArrayList<>();

    /** Which day is on the ridge. 0-based, 0..6. */
    public int day = 0;

    /** The routing for the day just filed, or null. */
    public Routing last;

    public int correct = 0;
    public int ranDays = 0;
    public boolean finished = false;

    // ------------------------------------------------------------- the rule

    /**
     * The first thing it ever heard about a concern.
     *
     * This single line is the game. Everything else is presentation.
     */
    public Fact earliest(Concern c) {
        return earliest(c, Integer.MAX_VALUE);
    }

    /**
     * The first thing it ever heard about a concern, as of the end of a day.
     *
     * The day matters. Asking what it believes on day three has to ignore
     * the things you have not said yet, or the game would let a later report
     * travel backwards and fix a day it was never present for. The whole
     * point is that it cannot.
     */
    public Fact earliest(Concern c, int upToDay) {
        for (Fact x : heard) if (x.concern == c && x.day <= upToDay) return x;
        return null;
    }

    public Day currentDay() { return day < DAYS.size() ? DAYS.get(day) : null; }

    /** What it would do right now, without filing anything. */
    public Routing route(int dayIndex) {
        Day d = DAYS.get(dayIndex);
        int upTo = dayIndex + 1;
        List<Decision> ds = new ArrayList<>();
        for (Concern c : Concern.values()) {
            Fact x = earliest(c, upTo);
            ds.add(new Decision(c, d.truth.get(c),
                    x == null ? null : x.value,
                    x == null ? 0 : x.day));
        }
        return new Routing(dayIndex + 1, ds, earliest(Concern.CROSSING, upTo) != null);
    }

    /** Report one thing. It is now believed, permanently, and ranked last. */
    public void report(Fact x) {
        if (x == null || heard.contains(x)) return;
        heard.add(x);
    }

    /** File the day: it acts on what it has, and the day is scored. */
    public Routing file() {
        last = route(day);
        correct += last.correct();
        if (last.ran) ranDays++;
        return last;
    }

    /** Move on. After the seventh day there is no eighth. */
    public void next() {
        day++;
        if (day >= DAYS.size()) { day = DAYS.size(); finished = true; }
    }

    /** True when the report just made was not the one it acted on. */
    public boolean lastReportIgnored(Fact x) {
        if (x == null || last == null) return false;
        return earliest(x.concern) != x;
    }

    /** Every fact in the game, for lookup. */
    public static Fact fact(String id) {
        for (Day d : DAYS) for (Fact x : d.facts) if (x.id.equals(id)) return x;
        return null;
    }

    /**
     * The best week available, by brute force.
     *
     * Cached, and it has to be: the end screen asks for this every frame to
     * decide which closing line to print, and the search is 5^7. Uncached it
     * turned the last screen of the game into a slideshow -- found by reading
     * the draw path, not by watching it, which is the only reason it did not
     * ship.
     */
    private static int bestCache = -1;

    public static int bestPossible() {
        if (bestCache < 0) bestCache = best(0, new ArrayList<>());
        return bestCache;
    }

    static int best(int dayIndex, List<Fact> chosen) {
        if (dayIndex >= DAYS.size()) {
            Outside o = new Outside();
            o.heard.addAll(chosen);
            int n = 0;
            for (int i = 0; i < DAYS.size(); i++) n += o.route(i).correct();
            return n;
        }
        int best = best(dayIndex + 1, chosen);          // say nothing
        for (Fact x : DAYS.get(dayIndex).facts) {
            chosen.add(x);
            best = Math.max(best, best(dayIndex + 1, chosen));
            chosen.remove(chosen.size() - 1);
        }
        return best;
    }

    // --------------------------------------------------------------- storage

    /**
     * The ridge log. One report per line, in the order it was made, because
     * the order is the only thing the dispatcher understands.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("# outside v1\n");
        sb.append("day\t").append(day).append('\n');
        sb.append("correct\t").append(correct).append('\n');
        sb.append("ran\t").append(ranDays).append('\n');
        sb.append("finished\t").append(finished ? 1 : 0).append('\n');
        for (Fact x : heard) sb.append("heard\t").append(x.id).append('\n');
        return sb.toString();
    }

    public static Outside deserialize(String data) {
        Outside o = new Outside();
        for (String raw : data.split("\r?\n")) {
            if (raw.isBlank() || raw.startsWith("#")) continue;
            String[] p = raw.split("\t", -1);
            switch (p[0]) {
                case "day"      -> o.day = num(p[1], 0);
                case "correct"  -> o.correct = num(p[1], 0);
                case "ran"      -> o.ranDays = num(p[1], 0);
                case "finished" -> o.finished = num(p[1], 0) == 1;
                case "heard"    -> { Fact x = fact(p[1]); if (x != null && !o.heard.contains(x)) o.heard.add(x); }
                default -> { }
            }
        }
        if (o.day > DAYS.size()) o.day = DAYS.size();
        if (o.correct > DECISIONS) o.correct = DECISIONS;
        if (o.ranDays > DAYS.size()) o.ranDays = DAYS.size();
        return o;
    }

    static int num(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, serialize(), StandardCharsets.UTF_8);
    }

    public static Outside load(Path file) throws IOException {
        if (!Files.exists(file)) return new Outside();
        return deserialize(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** A fresh observer with nothing said yet. */
    public static Outside fresh() { return new Outside(); }
}
