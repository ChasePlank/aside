package aside.games.handoff;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handoff, the whole game, with nothing drawn.
 *
 * You are the relief keeper at a light on a rock. Bell kept the watch
 * before you and left four standing orders. You keep five watches under
 * those orders, and the sea does not care what they say. Then you write
 * the orders for whoever comes after you -- and you watch him keep his
 * first night using nothing but what you wrote.
 *
 * The idea the whole thing is built on: **a standing order is a judgment
 * with the judge taken out of it.** Bell's lines were right when Bell
 * wrote them. By your fifth watch the situation has moved and following
 * them exactly is the dangerous thing. So the game is not about whether
 * you obey; it is about what you can put in writing for a person who is
 * not you, on a night you have not seen.
 *
 * The mechanic that carries it is the specificity tradeoff. Bell's lines
 * are general: one signal each, so they fire often and misfire often. The
 * lines you earn are specific: two signals each, so they are right when
 * they fire and silent when they do not. You hold three lines. A player
 * who judged well on all five watches ends up with five sharp lines and
 * three slots, and the three they pick are the three about their own
 * nights.
 *
 * Pure Java, no JavaFX. The window onto it lives in {@link HandoffScreen};
 * the standalone repo runs this file headless.
 */
public final class Handoff {

    /**
     * How many lines the standing orders hold. Three, and there are three
     * things that can go wrong on a single night. The gap is the game.
     */
    public static final int ORDERS_HELD = 3;

    // ------------------------------------------------------------- signals

    /**
     * Which part of the station an order is about. One action per concern
     * per night: a keeper cannot raise the wick and trim it.
     */
    public enum Concern { LAMP, DOOR, VESSEL, STAIR }

    /** Something you can see from the lamp room door. */
    public static final class Signal {
        public final String id;
        public final String text;
        Signal(String id, String text) { this.id = id; this.text = text; }
    }

    static final Map<String, Signal> SIGNALS = new LinkedHashMap<>();

    static Signal sig(String id, String text) {
        Signal s = new Signal(id, text);
        SIGNALS.put(id, s);
        return s;
    }

    static {
        sig("lamp_low",     "the lamp is burning low");
        sig("wick_short",   "the wick is burnt down to a stub");
        sig("oil_low",      "the drum is down to the last of it");
        sig("fog",          "fog is coming in from the sea");
        sig("door_open",    "the outer door will not stay shut");
        sig("wind_west",    "the wind is from the west and getting up");
        sig("vessel_light", "a light is showing off the reef");
        sig("vessel_wrong", "the light is on the wrong side of the reef");
        sig("stair_dark",   "the stair lamp has gone out");
        sig("tide_turning", "the tide is turning");
    }

    public static Signal signal(String id) { return SIGNALS.get(id); }

    public static String signalText(String id) {
        Signal s = SIGNALS.get(id);
        return s == null ? id : s.text;
    }

    // -------------------------------------------------------------- orders

    /**
     * One line of standing orders.
     *
     * {@code requires} is the whole condition language: every signal listed
     * must be present for the line to apply. That is deliberately smaller
     * than a rule engine, because the thing being modelled is not logic --
     * it is how much of a situation a sentence can carry. One signal is a
     * general line. Two is a line about one particular night.
     */
    public static final class Order {
        public final String id;
        public final String text;
        public final Set<String> requires;
        public final String action;
        public final Concern concern;
        /** Bell's line, inherited, as opposed to one you earned. */
        public final boolean bell;
        /** Where it came from, in the words the game would use. */
        public final String origin;

        Order(String id, String text, Set<String> requires, String action,
              Concern concern, boolean bell, String origin) {
            this.id = id;
            this.text = text;
            this.requires = requires;
            this.action = action;
            this.concern = concern;
            this.bell = bell;
            this.origin = origin;
        }

        /** Does this line apply to a night with these signals in it? */
        public boolean applies(Set<String> present) {
            return present.containsAll(requires);
        }

        /** How much of a situation the sentence carries. */
        public int breadth() { return requires.size(); }
    }

    static Order o(String id, String text, Set<String> requires, String action,
                   Concern concern, boolean bell, String origin) {
        return new Order(id, text, requires, action, concern, bell, origin);
    }

    /** Bell's four. General on purpose: one signal each. */
    public static final List<Order> BELL = List.of(
        o("B1", "When the lamp burns low, raise the wick.",
          Set.of("lamp_low"), "raise_wick", Concern.LAMP, true, "Bell's"),
        o("B2", "When the outer door is unlatched, bolt it.",
          Set.of("door_open"), "bolt_door", Concern.DOOR, true, "Bell's"),
        o("B3", "When a vessel shows a light, log her name.",
          Set.of("vessel_light"), "log_vessel", Concern.VESSEL, true, "Bell's"),
        o("B4", "Never leave the stair unlit.",
          Set.of("stair_dark"), "light_stair", Concern.STAIR, true, "Bell's"));

    // -------------------------------------------------------------- nights

    /**
     * What you can do about a night. Three ways, and only one of them is
     * written down anywhere.
     */
    public enum Kind {
        /** Do what the line says. */
        FOLLOW,
        /** Do what the night actually needs. */
        JUDGE,
        /** Do nothing. */
        HOLD
    }

    public static final class Option {
        public final String text;
        public final Kind kind;
        public final String outcome;
        /** A line you earn by taking this option, or null. */
        public final Order unlock;
        Option(String text, Kind kind, String outcome, Order unlock) {
            this.text = text; this.kind = kind; this.outcome = outcome; this.unlock = unlock;
        }
    }

    public static final class Watch {
        public final String heading;
        public final String title;
        public final String scene;
        public final List<String> signals;
        public final List<Option> options;
        Watch(String heading, String title, String scene,
              List<String> signals, List<Option> options) {
            this.heading = heading; this.title = title; this.scene = scene;
            this.signals = signals; this.options = options;
        }
        public Set<String> signalSet() { return new LinkedHashSet<>(signals); }
    }

    static Option op(String text, Kind kind, String outcome, Order unlock) {
        return new Option(text, kind, outcome, unlock);
    }

    /**
     * The lines you can earn. Specific on purpose: two signals each, so
     * each one is about exactly one night -- the night you had.
     */
    static final Order E1 = o("E1",
        "When the lamp burns low and the oil is low, look to the oil before the wick.",
        Set.of("lamp_low", "oil_low"), "check_oil", Concern.LAMP, false,
        "yours, from the first watch");
    static final Order E2 = o("E2",
        "When the wind is from the west and the door is unlatched, bolt it.",
        Set.of("door_open", "wind_west"), "bolt_door", Concern.DOOR, false,
        "yours, from the second watch");
    static final Order E3 = o("E3",
        "In fog, with little oil, burn low and light the standby.",
        Set.of("fog", "oil_low"), "burn_low", Concern.LAMP, false,
        "yours, from the third watch");
    static final Order E4 = o("E4",
        "When a light shows on the wrong side of the reef, answer it with the shutter.",
        Set.of("vessel_light", "vessel_wrong"), "shutter_answer", Concern.VESSEL, false,
        "yours, from the fourth watch");
    static final Order E5 = o("E5",
        "When the oil is low, the stair stays dark and the light stays lit.",
        Set.of("stair_dark", "oil_low"), "rope_stair", Concern.STAIR, false,
        "yours, from the fifth watch");

    public static final List<Watch> WATCHES = List.of(

        new Watch("First watch", "The wick",
            "The light has been going since four. By nine it is burning low, and the "
          + "drum is light in your hand when you lift it. The wick is burnt down to a stub.",
            List.of("lamp_low", "wick_short", "oil_low"),
            List.of(
                op("Raise the wick, as the orders say.", Kind.FOLLOW,
                   "You raise the wick. It catches and burns bright and ugly, and for two "
                 + "hours the light is the best it has been all week. Then the drum is dry, "
                 + "and the light goes out, and you sit with a dead lamp until the boat comes.",
                   null),
                op("Trim the wick back and top up the drum from the spare.", Kind.JUDGE,
                   "You trim the wick and fill the drum from the spare, and the light burns "
                 + "steady and low all night. It is not a good light. It is a light.",
                   E1),
                op("Leave it. It is burning.", Kind.HOLD,
                   "You leave it. It burns low all night, and twice you hear a horn out in "
                 + "the dark, asking where the light is.",
                   null))),

        new Watch("Second watch", "The door",
            "The outer door will not stay shut. The wind is from the west and getting up, "
          + "and every few minutes the door walks itself open and the room fills with cold.",
            List.of("door_open", "wind_west"),
            List.of(
                op("Bolt it, as the orders say.", Kind.FOLLOW,
                   "You bolt it. The wind works at it all night and does not get in. In the "
                 + "morning the bolt is still there, and so are you.",
                   null),
                op("Wedge it open a hand's width to air the room.", Kind.JUDGE,
                   "You wedge it open to air the room, and by midnight the floor is wet and "
                 + "the salt is on everything, and you spend the rest of the night mopping.",
                   E2),
                op("Leave it. It is only wind.", Kind.HOLD,
                   "You leave it. It bangs all night, and by morning the latch has worked "
                 + "itself loose in the frame.",
                   null))),

        new Watch("Third watch", "The fog",
            "Fog comes in from the sea at eleven and does not lift. The lamp is burning low "
          + "again and the drum is down to the last of it. The boat is not due for three days.",
            List.of("lamp_low", "oil_low", "fog"),
            List.of(
                op("Raise the wick, as the orders say.", Kind.FOLLOW,
                   "You raise the wick. For an hour the fog is lit up orange and you can see "
                 + "the light doing its work. Then the drum is dry, and the fog is still there, "
                 + "and there is nothing you can do about it.",
                   null),
                op("Trim it low and steady, and light the standby beside it.", Kind.JUDGE,
                   "You trim the lamp down and set the standby burning beside it. Two small "
                 + "lights instead of one big one, and neither of them uses much. The fog "
                 + "keeps them both, all night.",
                   E3),
                op("Wait. Fog lifts.", Kind.HOLD,
                   "You wait. The fog does not lift. At two in the morning you hear a horn, "
                 + "very close, and then further off, and then not at all.",
                   null))),

        new Watch("Fourth watch", "The light off the reef",
            "A light shows off the reef at half past one. It is not a vessel's light. It is "
          + "on the wrong side of the reef, low down, and it does not move.",
            List.of("vessel_light", "vessel_wrong"),
            List.of(
                op("Log her name, as the orders say.", Kind.FOLLOW,
                   "You take down the book and write the hour and the bearing and the word "
                 + "unidentified, and you go back to the light. The light off the reef is "
                 + "still there at four. In the morning there is nothing on the reef at all.",
                   null),
                op("Answer it with the shutter -- three short.", Kind.JUDGE,
                   "You take the shutter and give it three short. The light off the reef goes "
                 + "out. Nothing comes of it, and you never learn what it was, and that is "
                 + "the whole of the reward.",
                   E4),
                op("Nothing. It is not yours.", Kind.HOLD,
                   "You do nothing. It burns until first light and then it is gone, and you "
                 + "find out later that a dory went onto the reef at Sarrow Point the same night.",
                   null))),

        new Watch("Fifth watch", "The stair",
            "The stair lamp has gone out. The oil for it is kept at the foot of the stair, "
          + "and the stair is unlit, and the tide is turning -- the causeway will be under "
          + "in an hour.",
            List.of("stair_dark", "oil_low", "tide_turning"),
            List.of(
                op("Light the stair, as the orders say.", Kind.FOLLOW,
                   "You go down the stair in the dark to fetch the oil. You get it, and you "
                 + "light the lamp, and you come back up with a wrenched ankle and the drum "
                 + "in the lamp room half empty. The light upstairs burns low for the rest "
                 + "of the night.",
                   null),
                op("Leave the stair dark. Keep the oil for the light, and rope the stair off.",
                   Kind.JUDGE,
                   "You rope the stair off at the top and leave it dark, and the oil stays in "
                 + "the lamp room where the light can use it. Nobody comes up the stair in "
                 + "the night, because nobody comes.",
                   E5),
                op("Nothing. Nobody uses the stair at night.", Kind.HOLD,
                   "You leave it. Nobody comes, and the stair is dark, and the order sits in "
                 + "the back of your mind all night like a bill you have not paid.",
                   null))));

    // ---------------------------------------------------------- succession

    /**
     * The night after you. Fixed, and you do not get to see it before you
     * write -- that is the whole point of writing.
     */
    public static final String SUCCESSION_HEADING = "The night after you";
    public static final String SUCCESSION_TITLE = "His first night";
    public static final String SUCCESSION_SCENE =
        "The next keeper's first night on the rock. He has your orders and nothing else: "
      + "no watch of his own, no idea what your nights were like.";
    public static final List<String> SUCCESSION_SIGNALS = List.of(
        "lamp_low", "oil_low", "fog", "door_open", "vessel_light", "vessel_wrong");

    /** What each action does on his night, and whether it was right. */
    static final class Act {
        final String text;
        final boolean good;
        Act(String text, boolean good) { this.text = text; this.good = good; }
    }

    static final Map<String, Act> ACTS = new LinkedHashMap<>();

    static {
        ACTS.put("raise_wick", new Act(
            "He raised the wick, because that is what the orders said to do with a low lamp. "
          + "It burned bright for an hour and then the last of the oil was gone, and the "
          + "light was out by two.", false));
        ACTS.put("check_oil", new Act(
            "He looked to the oil before the wick, and found enough in the drum for the night, "
          + "and trimmed the lamp to make it last. The light burned steady until morning.", true));
        ACTS.put("burn_low", new Act(
            "He trimmed the lamp low and lit the standby beside it, and the fog took both "
          + "lights and kept them all night.", true));
        ACTS.put("bolt_door", new Act(
            "He bolted the outer door against the weather. It held.", true));
        ACTS.put("log_vessel", new Act(
            "He wrote a name in the book -- unidentified -- and went back to the light. The "
          + "light off the reef stayed where it was until four.", false));
        ACTS.put("shutter_answer", new Act(
            "He answered the light off the reef with the shutter, three short. It went out "
          + "and did not come back.", true));
        ACTS.put("light_stair", new Act(
            "He went down the stair in the dark for the oil.", false));
        ACTS.put("rope_stair", new Act(
            "He roped off the stair and kept the oil for the light.", true));
    }

    static final Map<Concern, String> UNMET = new LinkedHashMap<>();

    static {
        UNMET.put(Concern.LAMP,
            "The lamp burned low all night and no line of yours told him to touch it.");
        UNMET.put(Concern.DOOR,
            "The outer door was unlatched all night and no line of yours told him to bolt it.");
        UNMET.put(Concern.VESSEL,
            "A light showed off the reef and no line of yours told him to answer it.");
        UNMET.put(Concern.STAIR,
            "The stair was dark and no line of yours told him what to do about it.");
    }

    /** One thing that happened on his night. */
    public static final class Event {
        public final Concern concern;
        /** The line that produced it, or null when nothing applied. */
        public final Order by;
        public final String text;
        public final boolean good;
        Event(Concern concern, Order by, String text, boolean good) {
            this.concern = concern; this.by = by; this.text = text; this.good = good;
        }
    }

    /** The result of handing your orders to somebody else. */
    public static final class Succession {
        public final List<Event> events = new ArrayList<>();
        public int good;
        public int bad;
        public int unmet;
        public int raised;
        public boolean allBell;
        public String closing;
        public String verdict;
    }

    /**
     * Play the next keeper's night against a set of written orders.
     *
     * The rules, and they are the whole of the simulation:
     *   - a line applies if every signal it names is present;
     *   - he reads the orders in the order you wrote them, and for each
     *     concern he does the first thing that applies;
     *   - a concern nothing applies to is a concern he does nothing about.
     *
     * No judgment, no adaptation, no asking what you meant. He cannot ask.
     */
    public static Succession succeed(List<Order> written) {
        Set<String> present = new LinkedHashSet<>(SUCCESSION_SIGNALS);
        Succession s = new Succession();

        Map<Concern, Order> chosen = new LinkedHashMap<>();
        for (Order ord : written) {
            if (ord.applies(present) && !chosen.containsKey(ord.concern)) {
                chosen.put(ord.concern, ord);
            }
        }

        for (Concern c : Concern.values()) {
            Order ord = chosen.get(c);
            if (ord == null) continue;
            Act a = ACTS.get(ord.action);
            if (a == null) continue;
            s.events.add(new Event(c, ord, a.text, a.good));
            if (a.good) s.good++; else s.bad++;
        }

        Set<Concern> raised = raisedConcerns(present);
        for (Concern c : Concern.values()) {
            if (!raised.contains(c) || chosen.containsKey(c)) continue;
            String t = UNMET.get(c);
            if (t == null) continue;
            s.events.add(new Event(c, null, t, false));
            s.unmet++;
        }

        s.raised = raised.size();
        s.allBell = !written.isEmpty();
        for (Order ord : written) if (!ord.bell) s.allBell = false;

        s.verdict = s.good + " of the " + s.raised
                  + " things the night raised were met well.";

        if (s.allBell) {
            s.closing = "You gave him Bell's night. It was not Bell's night.";
        } else if (s.good >= 3) {
            s.closing = "Nothing went wrong on his night. He will think the light is easy, "
                      + "and he will write you a shorter note than you wrote him.";
        } else if (s.good == 2) {
            s.closing = "One thing went wrong on his night, and it was the thing you had no "
                      + "line for. You can only write for the night you had.";
        } else if (s.good == 1) {
            s.closing = "Two things went wrong on his night. He will not know which of them "
                      + "to blame you for, so he will blame you for both.";
        } else {
            s.closing = "Everything went wrong on his night. He will write his own orders, "
                      + "and they will be about the things that happened to him.";
        }
        return s;
    }

    /**
     * Which parts of the station the night actually raised a question
     * about.
     *
     * A concern is raised when some line about it applies -- not when some
     * signal it happens to mention is present. The difference matters: a
     * night with low oil and no stair must not be counted as raising the
     * stair, or the game would charge you for a thing that never happened.
     */
    public static Set<Concern> raisedConcerns(Set<String> present) {
        Set<Concern> out = new LinkedHashSet<>();
        for (Order ord : allOrders()) {
            if (ord.applies(present)) out.add(ord.concern);
        }
        return out;
    }

    /** Every line that exists, Bell's and earned. */
    public static List<Order> allOrders() {
        List<Order> all = new ArrayList<>(BELL);
        all.add(E1); all.add(E2); all.add(E3); all.add(E4); all.add(E5);
        return all;
    }

    public static Order orderById(String id) {
        for (Order o : allOrders()) if (o.id.equals(id)) return o;
        return null;
    }

    /** The lines that apply to a night, in the order they are written. */
    public static List<Order> firing(List<Order> orders, Set<String> present) {
        List<Order> out = new ArrayList<>();
        for (Order o : orders) if (o.applies(present)) out.add(o);
        return out;
    }

    // --------------------------------------------------------------- state

    public enum Phase { WATCH, OUTCOME, WRITING, SUCCESSION, DONE }

    public Phase phase = Phase.WATCH;
    public int watchIndex = 0;
    /** The option taken on each watch, by index into that watch's options. */
    public final List<Integer> taken = new ArrayList<>();
    public final List<Order> unlocked = new ArrayList<>();
    /** The orders you leave him, in the order you wrote them. */
    public final List<Order> written = new ArrayList<>();
    public int lastOption = -1;

    public Watch watch() { return WATCHES.get(Math.min(watchIndex, WATCHES.size() - 1)); }

    /** Bell's four, which is what you are keeping the watch under. */
    public List<Order> standing() { return BELL; }

    /** The line of Bell's that applies to the current watch. */
    public Order applying() {
        List<Order> f = firing(BELL, watch().signalSet());
        return f.isEmpty() ? null : f.get(0);
    }

    /** Everything you may write, Bell's first, then what you earned. */
    public List<Order> pool() {
        List<Order> p = new ArrayList<>(BELL);
        p.addAll(unlocked);
        return p;
    }

    /** Take one of the three ways through the current watch. */
    public void choose(int option) {
        if (phase != Phase.WATCH) return;
        Watch w = watch();
        if (option < 0 || option >= w.options.size()) return;
        Option opt = w.options.get(option);
        lastOption = option;
        taken.add(option);
        if (opt.unlock != null && !unlocked.contains(opt.unlock)) unlocked.add(opt.unlock);
        phase = Phase.OUTCOME;
    }

    /** Leave the outcome behind and go on to the next watch, or to writing. */
    public void advance() {
        if (phase != Phase.OUTCOME) return;
        lastOption = -1;
        if (watchIndex + 1 < WATCHES.size()) {
            watchIndex++;
            phase = Phase.WATCH;
        } else {
            phase = Phase.WRITING;
        }
    }

    /** Add a line to the orders you are leaving. Order of writing matters. */
    public boolean write(Order o) {
        if (phase != Phase.WRITING) return false;
        if (written.size() >= ORDERS_HELD) return false;
        if (written.contains(o)) return false;
        written.add(o);
        return true;
    }

    public void unwrite() {
        if (phase != Phase.WRITING || written.isEmpty()) return;
        written.remove(written.size() - 1);
    }

    /** Done writing. The night after you plays out. */
    public boolean seal() {
        if (phase != Phase.WRITING || written.size() != ORDERS_HELD) return false;
        phase = Phase.SUCCESSION;
        return true;
    }

    public Succession succession() { return succeed(written); }

    public void finish() {
        if (phase == Phase.SUCCESSION) phase = Phase.DONE;
    }

    public void reset() {
        phase = Phase.WATCH;
        watchIndex = 0;
        taken.clear();
        unlocked.clear();
        written.clear();
        lastOption = -1;
    }

    /** The option taken on a watch, or null if it has not been kept yet. */
    public Option takenOn(int watch) {
        if (watch < 0 || watch >= taken.size()) return null;
        int i = taken.get(watch);
        List<Option> opts = WATCHES.get(watch).options;
        return (i < 0 || i >= opts.size()) ? null : opts.get(i);
    }

    // --------------------------------------------------------- persistence

    /**
     * Plain text, one record per line, so a player can open the file and
     * read exactly what they left him. The record of the record is itself
     * readable -- same joke as the ledger.
     */
    public String toText() {
        StringBuilder b = new StringBuilder();
        b.append("phase ").append(phase).append('\n');
        b.append("watch ").append(watchIndex).append('\n');
        for (int i = 0; i < taken.size(); i++) {
            b.append("took ").append(i).append(' ').append(taken.get(i)).append('\n');
        }
        for (Order o : unlocked) b.append("unlocked ").append(o.id).append('\n');
        for (Order o : written) b.append("wrote ").append(o.id).append('\n');
        return b.toString();
    }

    public void save(Path p) throws Exception {
        Files.writeString(p, toText(), StandardCharsets.UTF_8);
    }

    /** Tolerant by design: an unknown line is skipped, not fatal. */
    public static Handoff load(Path p) {
        Handoff h = new Handoff();
        if (p == null || !Files.exists(p)) return h;
        List<String> lines;
        try {
            lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return h;
        }
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            try {
                switch (parts[0]) {
                    case "phase" -> h.phase = Phase.valueOf(parts[1]);
                    case "watch" -> h.watchIndex =
                            clamp(Integer.parseInt(parts[1]), 0, WATCHES.size() - 1);
                    case "took" -> {
                        int idx = Integer.parseInt(parts[1]);
                        int opt = Integer.parseInt(parts[2]);
                        while (h.taken.size() <= idx) h.taken.add(0);
                        h.taken.set(idx, opt);
                    }
                    case "unlocked" -> {
                        Order o = orderById(parts[1]);
                        if (o != null && !o.bell && !h.unlocked.contains(o)) h.unlocked.add(o);
                    }
                    case "wrote" -> {
                        Order o = orderById(parts[1]);
                        if (o != null && !h.written.contains(o)
                                && h.written.size() < ORDERS_HELD) {
                            h.written.add(o);
                        }
                    }
                    default -> { }
                }
            } catch (Exception ignored) {
                // a corrupt record is skipped; the rest of the file still stands
            }
        }
        // An interrupted outcome beat goes back to the watch it belongs to.
        if (h.phase == Phase.OUTCOME) h.phase = Phase.WATCH;
        // A game that says it is writing but already holds three lines is
        // really at the night after you.
        if (h.phase == Phase.WRITING && h.written.size() >= ORDERS_HELD) {
            h.phase = Phase.SUCCESSION;
        }
        return h;
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
