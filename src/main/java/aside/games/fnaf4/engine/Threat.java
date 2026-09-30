package aside.games.fnaf4.engine;

/**
 * One thing in the dark, walking toward one place.
 *
 * Every threat in FNAF 4 works the same way and differs only in where it
 * is going and how fast. It has a <b>home</b> -- the one station it is
 * coming for -- and a <b>distance</b> counted in moves. When the distance
 * reaches zero it is standing there, and from that moment it is a clock:
 * the player has {@link Game#grace()} seconds to be at that station with
 * the light, or the night is over.
 *
 * Two consequences fall out of that, and both are the design:
 *
 *  - **A threat is not a dice roll, it is a countdown you can hear.** The
 *    breath plays the moment it arrives, so the player is never guessing
 *    whether something is there -- only whether they can get there.
 *  - **Being at the wrong station is the same as not looking.** The flash
 *    only reaches what is in front of it, so the distance between where
 *    you are and where it is *is* the cost of the mistake.
 *
 * Fredbear is the exception and is not one of these: he does not walk, he
 * is simply somewhere, and he has to be found. See {@link Game}.
 */
public final class Threat {

    /** What the screen calls it. */
    public final String name;
    /** The short key the audio cue is built from. */
    public final String key;
    /** The one station it is coming for. */
    public final Room.Where home;
    /**
     * How much faster or slower this one is than the night's baseline.
     *
     * Four threats on one interval would arrive in lockstep, which reads as
     * a metronome rather than as a room with things in it. The spread is
     * small on purpose: it staggers the arrivals without letting one of
     * them be the only one that matters.
     */
    public final double pace;

    /** Moves left before it is here. Zero means it is here. */
    public int distance;
    /** Seconds since the last move. */
    public double timer;
    /** Seconds it has been standing here. Only meaningful at distance 0. */
    public double hereFor;
    /** Seconds since the last breath, so it keeps announcing itself. */
    public double sinceBreath;

    public Threat(String name, String key, Room.Where home, double pace, int startDistance) {
        this.name = name;
        this.key = key;
        this.home = home;
        this.pace = pace;
        this.distance = startDistance;
    }

    /** True when it is standing at its station, waiting. */
    public boolean here() {
        return distance == 0;
    }

    public void update(double dt, Game g) {
        if (g.status != Game.Status.PLAYING) return;

        if (here()) {
            hereFor += dt;
            sinceBreath += dt;
            // It keeps breathing while it waits. One cue on arrival would
            // be enough to be fair and not enough to be frightening: the
            // thing that wears a player down is being told again.
            if (sinceBreath >= Game.BREATH_EVERY) {
                sinceBreath = 0;
                g.cue("breath_" + key);
            }
            if (hereFor >= g.grace()) g.jumpscare(this);
            return;
        }

        timer += dt;
        if (timer < g.interval(this)) return;
        timer = 0;
        distance--;
        if (distance == 0) {
            hereFor = 0;
            sinceBreath = 0;
            g.cue("breath_" + key);
            g.arrived(this);
        } else if (distance == 1) {
            // One move out, and it says which one of them it is. That is
            // the cue the whole pre-empt half of the game runs on: without
            // it a player would know something was close and not what, and
            // "go and look" would be a guess rather than a decision.
            g.cue("step_" + key);
        } else {
            g.cue("footstep");
        }
    }

    /**
     * Sent all the way back to where it started.
     *
     * This is what the light does to something that is already standing in
     * the doorway, and it is the good outcome: four moves of quiet for one
     * flash. See {@link #push()} for the other one.
     */
    public void repel() {
        distance = Game.DIST_MAX;
        timer = 0;
        hereFor = 0;
        sinceBreath = 0;
    }

    /**
     * Turned back from one move out -- two moves of quiet, not four.
     *
     * The asymmetry is the whole reason the flashlight is a decision
     * rather than a button. Answering a breath is efficient and late;
     * catching something in the hall is safe and half as good, so a player
     * who pre-empts everything spends twice the flashes and fills the room
     * with twice the noise. Neither is right on its own, and which one you
     * should be doing changes as the night gets faster.
     */
    public void push() {
        distance = Game.PUSH_TO;
        timer = 0;
        hereFor = 0;
        sinceBreath = 0;
    }
}
