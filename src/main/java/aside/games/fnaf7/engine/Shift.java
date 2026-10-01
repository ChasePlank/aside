package aside.games.fnaf7.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FNAF 7 core loop: the office with one bar and one light.
 *
 * <p>FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 gave you
 * a body that can only be in one place. FNAF 5 gave you a building and took
 * the room you were standing in. FNAF 6 gave you one chair, one lamp and one
 * shock, and took away the idea that a look is free. FNAF 7 gives you
 * <b>two doors, one bar and one light</b> -- and then takes away the thing
 * every game before it assumed, which is that <i>the thing coming for you
 * does not know anything about you.</i>
 *
 * <pre>
 *   THE RULE: it does not have a pattern. It has yours.
 * </pre>
 *
 * <p>There is one bar and there are two doors, so exactly one of them is
 * open at any moment. The unit does not search the building and it does not
 * walk a route: <b>it comes to the side you are not looking at.</b> It
 * knows that because it has been watching the light -- not the bar, the
 * light -- and what it holds is a decayed record of where your attention
 * has been. That record is drawn on the screen, in words, as
 * {@code IT EXPECTS YOU AT: LEFT}.
 *
 * <h2>Why the readout is not a gift</h2>
 *
 * <p>A readout that said <i>where it is going</i> would be a solution. This
 * one says <i>where it thinks you are</i>, and it comes to the other side,
 * so the player has to make one inference -- and the inference is only
 * worth anything while the record is worth anything. The record decays. It
 * is a picture of your <b>habit</b>, not of your hands, so the moment you
 * move is the moment the readout starts lying, and the readout tells you
 * how fast it is lying by how far apart the two sides have got.
 *
 * <h2>The night</h2>
 *
 * <ol>
 *   <li>The unit is <b>away</b>. When it decides to move it picks a side:
 *       the side your attention is <i>not</i> on, per the record, unless it
 *       rolls {@link #explore()} and picks at random instead.</li>
 *   <li>It enters that <b>hall</b> for {@link #approach()} seconds. This is
 *       the only window in which it can be seen: the light shows a hall and
 *       it does not show a doorway, so a light that goes up late shows you
 *       nothing at all.</li>
 *   <li>It steps to the <b>door</b> and stands there for {@link #STRIKE}
 *       seconds. A barred door turns it back. An open one ends the
 *       night.</li>
 *   <li>A door that turns it back <b>knocks the bar loose</b>. The bar has
 *       to be put back, and putting it back is a move like any other.</li>
 * </ol>
 *
 * <h2>One pair of hands</h2>
 *
 * <p>The light and the bar are the same two hands. <b>You cannot move the
 * bar while the light is on.</b> That is the whole cost of looking, and it
 * is why the night is not solved by watching: to defend the side you are
 * watching you have to stop watching it, and stopping is what changes the
 * record that sent it there in the first place.
 *
 * <p>The engine is pure logic with no UI dependency, so a week can be swept
 * by a bot at 60fps with no display. That is how the difficulty table was
 * set, rather than by playing twenty nights by hand.
 */
public class Shift {

    // ---- Clock ----
    /** Seconds per in-game hour. Six of them is the night. */
    public static final double HOUR_SECONDS = 25.0;
    public static final int NIGHT_HOURS = 6;

    // ---- The light ----
    /**
     * Seconds the light has to be on before a hall is legible.
     *
     * <p>A flash is not a look. Without this the optimal play is to tap the
     * light every second, which costs nothing and turns the whole night
     * into a metronome. With it, a look has a floor price, and the floor is
     * the unit of the night.
     */
    public static final double LIGHT_WARM = 0.45;

    /**
     * Seconds the filament lasts with the light held on.
     *
     * <p><b>This is the night's budget, and without it the game is not a
     * game.</b> The first sweep of the week said so in a way that was
     * impossible to miss: the policy that watches the unbarred side and
     * swaps when it sees something read <b>100% on all five nights</b>,
     * because the light was free, so the light was always already up, so
     * the answer to every arrival was a move it had a whole hall's worth of
     * time to make. A light you can hold is a light that solves the night.
     *
     * <p>So it cannot be held. The filament burns while the light is on and
     * cools while it is off, and at the top it cuts out and will not come
     * back until it has cooled most of the way down. The record is made of
     * light, so a light that is off is a record that is drifting -- and the
     * readout is drawn from the record, so the readout drifts with it.
     */
    public static final double LIGHT_MAX = 3.2;

    /** Seconds off, from hot, to cool all the way down. */
    public static final double LIGHT_COOL = 10.0;

    /** Heat below which a cut-out filament will strike again. */
    public static final double LIGHT_RESET = 0.55;

    // ---- The bar ----
    /**
     * Seconds the bar takes to cross from one door to the other.
     *
     * <p>While it is crossing it is on neither door. That window is the
     * price of changing your mind, and it is the reason the readout is
     * dangerous: a player who follows it every time spends the night with
     * the bar in the air.
     */
    public static final double BAR_MOVE = 0.60;

    /** Seconds it stands at the door before it comes through. */
    public static final double STRIKE = 1.50;

    /** How much a second of light counts toward the record. */
    public static final double LOOK_WEIGHT = 1.0;

    /**
     * How far apart the two sides have to be before it has an opinion.
     *
     * <p>In look-seconds. Below this the readout says {@code --} and the
     * unit picks at random, which is the honest picture: a record that has
     * not separated yet is a record that does not know anything. The
     * starting state is exactly this, so the first move of every night is
     * a coin flip and the player's first look is what makes it stop being
     * one.
     */
    public static final double SURE = 0.8;

    /** The two sides of the office. */
    public enum Side {
        LEFT("LEFT", "left"), RIGHT("RIGHT", "right");

        /** As the readout shouts it. */
        public final String shout;
        /** As a sentence uses it. */
        public final String quiet;

        Side(String shout, String quiet) {
            this.shout = shout;
            this.quiet = quiet;
        }

        public Side other() { return this == LEFT ? RIGHT : LEFT; }
    }

    /** Where the unit is. */
    public enum Where {
        AWAY("away"),
        HALL_LEFT("the left hall"), HALL_RIGHT("the right hall"),
        DOOR_LEFT("the left door"), DOOR_RIGHT("the right door");

        public final String label;

        Where(String label) { this.label = label; }

        /** True while it is still in a hall, which is the only time it shows. */
        public boolean inHall() { return this == HALL_LEFT || this == HALL_RIGHT; }

        /** The side this position belongs to, or null when it is away. */
        public Side side() {
            return switch (this) {
                case HALL_LEFT, DOOR_LEFT -> Side.LEFT;
                case HALL_RIGHT, DOOR_RIGHT -> Side.RIGHT;
                default -> null;
            };
        }
    }

    public enum Status {
        /** Still playing, and the clock is still running. */
        PLAYING,
        /** It came through an open door. */
        CAUGHT,
        /** Six AM, and it never got through. */
        SURVIVED
    }

    public final int night;
    /**
     * The thing watching you.
     *
     * <p>Not final, and the only reason is the screen's dev hook: a
     * snapshot of night four with night one's unit in the hall is how the
     * art gets checked without playing four nights to get there.
     */
    public Unit unit;
    public final Random rng;

    // ---- Clock ----
    public double time = 0;
    public int hour = 0;

    // ---- The unit ----
    public Where where = Where.AWAY;
    /** The side it picked when it left. Kept while it is in a hall or at a door. */
    public Side target = Side.LEFT;
    public double timer;

    // ---- The bar ----
    /** The door the bar is on, or null while it is loose or in the air. */
    public Side barAt = Side.RIGHT;
    public boolean barMoving = false;
    public Side barTo = null;
    public double barTimer = 0;

    // ---- The light ----
    public boolean lit = false;
    public Side lightSide = Side.LEFT;
    public double litFor = 0;
    /** Filament heat, 0 to 1. Burns while lit, cools while off. */
    public double heat = 0;
    /** True while the filament is cut out and will not strike. */
    public boolean blown = false;

    // ---- The record ----
    /** Look-seconds accumulated on each side, decayed. */
    public double attendL = 0;
    public double attendR = 0;

    // ---- Outcome ----
    public Status status = Status.PLAYING;
    public String killer = null;

    // ---- Counters, for the report ----
    public int strikes = 0;
    public int repels = 0;
    public int looks = 0;
    public int moves = 0;
    public int knocked = 0;
    /** How many arrivals could be heard, and how many could not. */
    public int heard = 0;
    public int silentArrivals = 0;
    /** The last side it was heard on, or null. Cleared when it strikes. */
    public Side heardSide = null;
    public double sinceCue = 999;
    public double lastStrikeAge = 999;

    private final List<String> cues = new ArrayList<>();

    public Shift(int night, long seed) {
        this.night = night;
        this.rng = new Random(mix(seed));
        this.unit = Unit.forNight(night);
        this.timer = away();
    }

    /**
     * Scramble a seed before it reaches {@link Random}.
     *
     * <p>{@code java.util.Random} is a 48-bit LCG and the first draw from a
     * sequence of nearby seeds is strongly correlated with the seed. FNAF 6
     * shipped that bug and the sweep found it: nights 1 to 3 produced zero
     * hostile units out of two hundred seeds and nights 4 and 5 produced
     * 104 and 191, and the table that came back looked like a clean story
     * about difficulty. It was a fact about {@code Random}. A splitmix64
     * finalizer is the standard fix and costs nothing.
     */
    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Dev hook: put a different unit in the hall. */
    public void forceUnit(Unit u) {
        if (u != null) this.unit = u;
    }

    // ---- The night's tables ----
    // Every one of these is a method rather than a constant so the sweep can
    // be run against a variant without editing the file, and so a later
    // balance pass has one place to look.

    /**
     * Seconds it spends away between attempts.
     *
     * <p>This is the night's <b>tempo</b>: how many times you have to be
     * right. A short wait is a night with more decisions in it, and a night
     * with more decisions in it is a night where one bad habit costs more.
     */
    public double away() {
        double[] t = {6.0, 5.4, 4.8, 4.2, 3.6};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * Seconds it spends in the hall, which is the whole reaction window.
     *
     * <p>The window is {@code approach() - LIGHT_WARM - BAR_MOVE}: the time
     * to see it, and then to get the light out of the way and the bar
     * across. At the top of the week that is under two seconds, and under
     * two seconds is the point -- a player who has not already decided
     * which side they are on does not get to decide.
     */
    public double approach() {
        double[] t = {4.4, 4.0, 3.6, 3.2, 2.8};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * The chance it ignores its own record and picks a side at random.
     *
     * <p>This is the night's <b>unpredictability</b>, and it is the ramp.
     * A unit that always follows the record is a unit the readout solves;
     * a unit that sometimes does not is a unit that makes you keep a light
     * on the side you are not defending, which is the side you cannot
     * defend while you are looking at it.
     */
    public double explore() {
        double[] t = {0.08, 0.11, 0.14, 0.17, 0.20};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * The record's time constant, in seconds.
     *
     * <p>How long a look stays part of what it thinks of you. A long memory
     * is a stale one: it comes to the side you were not looking at a while
     * ago, which is sometimes the side you are defending now, and a stale
     * record is a record that walks into the bar. A short memory tracks
     * your hands, so the readout is right about the present and the only
     * thing that saves you is being somewhere it did not expect.
     */
    public double memory() {
        double[] t = {16.0, 14.0, 12.0, 10.0, 8.0};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    // ---- The record ----

    /** How much attention the record has on a side. */
    public double attend(Side s) {
        return s == Side.LEFT ? attendL : attendR;
    }

    /**
     * The side it thinks you are on, or null when the record has not
     * separated. This is what the readout draws, and it is the opposite of
     * where it is going.
     */
    public Side belief() {
        double d = attendL - attendR;
        if (Math.abs(d) < SURE) return null;
        return d > 0 ? Side.LEFT : Side.RIGHT;
    }

    /** How sure the record is, 0 to 1. Drawn as the readout's bar. */
    public double confidence() {
        double total = attendL + attendR;
        if (total <= 1e-6) return 0;
        return Math.min(1.0, Math.abs(attendL - attendR) / total);
    }

    /** The side it will come to, which is the one the record does not hold. */
    public Side predicted() {
        Side b = belief();
        return b == null ? null : b.other();
    }

    /** The side it picks when it leaves. */
    Side choose() {
        if (rng.nextDouble() < explore()) return coin();
        Side b = belief();
        return b == null ? coin() : b.other();
    }

    private Side coin() { return rng.nextBoolean() ? Side.LEFT : Side.RIGHT; }

    // ---- Sound ----

    public void cue(String name) { cues.add(name); }

    public List<String> drainCues() {
        if (cues.isEmpty()) return List.of();
        List<String> out = List.copyOf(cues);
        cues.clear();
        return out;
    }

    // ---- Update ----

    public void update(double dt) {
        if (status != Status.PLAYING) return;

        time += dt;
        hour = (int) Math.floor(time / HOUR_SECONDS);
        if (hour >= NIGHT_HOURS) {
            status = Status.SURVIVED;
            cue("chime_6am");
            return;
        }

        sinceCue += dt;
        lastStrikeAge += dt;

        // The light, and the filament it is burning.
        if (lit) {
            litFor += dt;
            heat = Math.min(1.0, heat + dt / LIGHT_MAX);
            if (heat >= 1.0) {
                lit = false;
                litFor = 0;
                blown = true;
                cue("light_blown");
            }
        } else {
            litFor = 0;
            heat = Math.max(0.0, heat - dt / LIGHT_COOL);
            if (blown && heat <= LIGHT_RESET) {
                blown = false;
                cue("light_ready");
            }
        }

        // The record. It reads the light and nothing else -- not the bar,
        // not the doors. What it has is a picture of where you have been
        // looking, and the bar is the one thing about you it cannot see.
        //
        // That is also what makes the readout worth drawing. The record is
        // a *decayed* integral, not a snapshot: it is fed while the light
        // is on and it bleeds away while the light is off, and the filament
        // means the light is off more often than it is on. So the readout
        // is a quantity the player cannot compute by looking at the office
        // -- it is the only place the state of the record is visible, and
        // the state of the record is the only thing that decides where the
        // unit goes.
        double k = Math.exp(-dt / memory());
        attendL *= k;
        attendR *= k;
        if (lit) {
            if (lightSide == Side.LEFT) attendL += LOOK_WEIGHT * dt;
            else attendR += LOOK_WEIGHT * dt;
        }

        // The bar, in the air.
        if (barMoving) {
            barTimer -= dt;
            if (barTimer <= 0) {
                barAt = barTo;
                barMoving = false;
                barTo = null;
                cue("bar_set");
            }
        }

        // The unit.
        timer -= dt;
        if (timer <= 0) stepUnit();
    }

    void stepUnit() {
        switch (where) {
            case AWAY -> {
                target = choose();
                where = target == Side.LEFT ? Where.HALL_LEFT : Where.HALL_RIGHT;
                timer = approach();
                // The one free channel, and it is a chance rather than a
                // promise. An arrival you can place is an arrival you can
                // answer without spending a look -- and a look is the only
                // thing keeping the record sharp, so a player who leans on
                // their ears is a player the record stops being able to
                // describe, which is the same as a player the unit stops
                // being able to predict. The ears are a way out of the
                // light, and they are a way out of the game.
                if (rng.nextDouble() < unit.tell()) {
                    heard++;
                    heardSide = target;
                    cue(target == Side.LEFT ? "step_left" : "step_right");
                } else {
                    silentArrivals++;
                    heardSide = null;
                }
            }
            case HALL_LEFT, HALL_RIGHT -> {
                where = target == Side.LEFT ? Where.DOOR_LEFT : Where.DOOR_RIGHT;
                timer = STRIKE;
                cue("at_door");
            }
            case DOOR_LEFT, DOOR_RIGHT -> {
                Side s = where.side();
                strikes++;
                if (barAt == s) {
                    // Turned back. The bar takes the hit and comes loose.
                    repels++;
                    knocked++;
                    barAt = null;
                    barMoving = false;
                    barTo = null;
                    barTimer = 0;
                    where = Where.AWAY;
                    timer = away();
                    lastStrikeAge = 0;
                    heardSide = null;
                    cue("repel");
                } else {
                    caughtAt = (int) time;
                    heardSide = null;
                    status = Status.CAUGHT;
                    killer = unit.name();
                    cue("caught");
                }
            }
        }
    }

    /** The second of the night it came through, or -1. */
    public int caughtAt = -1;

    // ---- Controls ----
    // Every one of these is a no-op once the night is over.

    /**
     * Turn the light on a side.
     *
     * <p>Aiming at the side it is already on is a no-op rather than a
     * re-look, so a player holding a key down does not spend the night
     * restarting the warm-up.
     */
    public boolean aim(Side s) {
        if (status != Status.PLAYING) return lit;
        // One pair of hands: the light cannot come up while the bar is in
        // the air, and the bar cannot go while the light is on. The two
        // rules are the same rule, and it is the only rule about your hands
        // the game has.
        if (barMoving) return lit;
        if (blown) return lit;
        if (lit && lightSide == s) return lit;
        lit = true;
        lightSide = s;
        litFor = 0;
        looks++;
        cue("light_on");
        return true;
    }

    /** Put the light out. */
    public boolean dark() {
        if (status != Status.PLAYING) return lit;
        if (!lit) return lit;
        lit = false;
        litFor = 0;
        cue("light_off");
        return false;
    }

    /**
     * Send the bar to a door.
     *
     * <p>Refused while the light is on, and that refusal is the game's one
     * rule about your hands. Refused while it is already in the air,
     * because there is one bar. Refused when it is already there, because
     * there is nothing to do.
     *
     * @return true when the bar actually started moving
     */
    public boolean moveBar(Side s) {
        if (status != Status.PLAYING) return false;
        if (lit) return false;
        if (barMoving) return false;
        if (barAt == s) return false;
        barMoving = true;
        barTo = s;
        barAt = null;
        barTimer = BAR_MOVE;
        moves++;
        cue("bar_move");
        return true;
    }

    /** True when the bar is on that door right now. */
    public boolean barred(Side s) { return barAt == s; }

    /** True when the light is up and warm. */
    public boolean revealed() { return lit && litFor >= LIGHT_WARM; }

    /** How much filament is left, 0 to 1. Drawn as the band's meter. */
    public double filament() { return 1.0 - heat; }

    /**
     * True when the unit is showing in the lit hall.
     *
     * <p>Halls only. A light that also showed a doorway would be a light
     * that answers the question after the answer is useless, and the whole
     * reason the warm-up exists is that a look has to be spent before you
     * know whether it was worth spending.
     */
    public boolean visible() {
        return revealed() && where.inHall() && where.side() == lightSide;
    }

    /** The clock, as the band draws it. */
    public String clock() {
        return switch (Math.min(hour, 6)) {
            case 0 -> "12 AM"; case 1 -> "1 AM"; case 2 -> "2 AM";
            case 3 -> "3 AM"; case 4 -> "4 AM"; case 5 -> "5 AM";
            default -> "6 AM";
        };
    }
}
