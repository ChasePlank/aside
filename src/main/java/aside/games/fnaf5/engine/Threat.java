package aside.games.fnaf5.engine;

/**
 * One thing in the building, and the rule that moves it.
 *
 * Every threat in FNAF 1 to 4 walked toward a <i>place</i>: a door, a
 * station, a side of the room. They differed in speed and in which place,
 * and the player's job was to be at the right place in time. Here they
 * walk toward three different <b>kinds of thing</b>, and that is the whole
 * difference:
 *
 * <pre>
 *   SOUND      walks toward the last sound.        You move, it comes.
 *   ATTENTION  walks toward the camera.            You look, it comes.
 *   PURSUIT    walks toward you.                   You wait, it comes.
 * </pre>
 *
 * The three demands contradict each other, and that is the design. Freddy
 * punishes standing still. Ballora punishes moving. Foxy punishes looking.
 * A player cannot satisfy all three, so the night is spent choosing which
 * one to be wrong about, and the controlled shock is the only thing that
 * answers Freddy -- which is why there are so few of them, and why the
 * other two have to be answered by their own counters. See
 * {@link Game#shock} for why the shock stopped being a universal answer on
 * 2026-10-01, and {@link Game#FEED_PACE} for the other half of that change.
 *
 * Two of the three can be held off by a player who understands them, and
 * that is deliberate. Ballora loses you if the building stays quiet long
 * enough, and Foxy stops dead if the monitor goes down. Neither of those is
 * a winning strategy on its own, because Freddy does not care what you do,
 * and because the camera is the only way to know which room is safe to walk
 * into. The counters exist so that the player has something to learn;
 * Freddy exists so that learning it is not enough.
 */
public final class Threat {

    /** What moves it. Three rules, three counters, and they disagree. */
    public enum Rule {
        /** Follows the last sound made in the building. Ballora. */
        SOUND,
        /** Follows the camera feed. Funtime Foxy. */
        ATTENTION,
        /** Follows you, and is never distracted. Funtime Freddy. */
        PURSUIT
    }

    /** What the screen calls it. */
    public final String name;
    /** The short key every cue for this one is built from. */
    public final String key;
    /** What moves it. */
    public final Rule rule;
    /** Where it starts, and where the report says it belongs. */
    public final Room.Where home;
    /**
     * How much faster or slower this one is than the night's baseline.
     *
     * Three threats on one interval arrive in lockstep and read as a
     * metronome rather than as a building with things in it. The spread is
     * small on purpose: it staggers them without letting one of them be
     * the only one that matters.
     */
    public final double pace;

    /** Which room it is standing in. */
    public Room.Where room;
    /** Seconds since its last move. */
    public double timer;
    /**
     * How long it will wait before its next move.
     *
     * Not the same as {@link Game#interval}: the wait is drawn once, when
     * the timer resets, with {@link Game#INTERVAL_JITTER} around it. Drawing
     * it per frame instead would bias every move early, because the
     * effective wait would be the smallest draw seen so far.
     */
    public double wait;
    /**
     * Seconds it has been in the room you are standing in.
     *
     * <b>This is the clock that ends most nights, and it does not reset
     * when a threat and the player change rooms together.</b> If it steps
     * into the room you are walking into while you are still mid-move, its
     * clock is already running -- it was in your room a moment ago -- and
     * you arrive with the grace partly spent. Measured 2026-10-01: reset
     * the clock on every step and the game goes to 100% for every policy
     * on nights 1 to 3, because that carry is the only thing that ever
     * catches a player who keeps walking. So it is load-bearing rather
     * than a bug, but it is also fragile: the whole week's difficulty
     * rests on a race between two moves landing in the same frame. Worth
     * a redesign rather than a guard.
     */
    public double hereFor;
    /** Seconds since it last announced itself from your room. */
    public double sinceCue;
    /** How many times it has walked into the room you were standing in. */
    public int visits;
    /**
     * Seconds this one has no target no matter what it hears.
     *
     * Only Ballora ever has one, and it is what makes her counter
     * *profitable* rather than merely necessary: she gives up when the
     * building has been quiet long enough, and this is the stretch after
     * that in which silence has bought you something. See
     * {@link Game#BALLORA_COOLDOWN}.
     */
    public double deaf;

    public Threat(String name, String key, Rule rule, Room.Where home,
                  double pace, Room.Where start) {
        this.name = name;
        this.key = key;
        this.rule = rule;
        this.home = home;
        this.pace = pace;
        this.room = start;
    }

    /** True when it is standing in the room you are standing in. */
    public boolean inYourRoom(Game g) {
        return room == g.where;
    }

    /** True when it is one room away, which is as close as a sound gets. */
    public boolean nextDoor(Game g) {
        return Room.adjacent(room, g.where);
    }

    public void update(double dt, Game g) {
        if (g.status != Game.Status.PLAYING) return;
        if (deaf > 0) deaf = Math.max(0, deaf - dt);

        if (inYourRoom(g)) {
            hereFor += dt;
            sinceCue += dt;
            // It keeps announcing itself while it waits. One cue on arrival
            // would be enough to be fair and not enough to be frightening:
            // what wears a player down is being told again.
            if (sinceCue >= Game.CUE_EVERY) {
                sinceCue = 0;
                g.cue("here_" + key);
            }

            // Ballora is blind. The room she is standing in tells her
            // nothing -- only sound does -- so if nothing has made a noise
            // for as long as she has been here, she gives up and walks
            // off. This is the one threat in the franchise that is
            // survived by doing nothing, and it is why she is the one
            // that makes moving expensive.
            //
            // Both halves of that test are load-bearing. The building
            // being quiet is not enough on its own -- a player who has
            // been standing still for a minute has not earned anything,
            // she simply has not found them yet -- and her having been
            // here a while is not enough on its own either, because a
            // player who walks into the room she is standing in has just
            // made a noise, and that noise is hers to follow.
            if (rule == Rule.SOUND
                    && hereFor >= Game.BALLORA_PATIENCE
                    && g.soundAge >= Game.BALLORA_PATIENCE) {
                g.cue("lost_" + key);
                room = Room.stepAway(room, g.where);
                // She has lost you, and it takes her a while to find you
                // again. This is the whole reward for standing still: the
                // stop is not just a way to survive the next few seconds,
                // it is the only thing in the building that buys time.
                deaf = Game.BALLORA_COOLDOWN;
                hereFor = 0;
                sinceCue = 0;
                timer = 0;
                // She is a seven-foot animatronic and she does not leave
                // silently. This used to move her without telling the
                // player anything at all, which made the one moment the
                // game is supposed to reward -- you were quiet, and she
                // gave up -- the one moment you could not hear. Measured
                // 2026-10-01: the bot walked straight into the room she
                // had just left, on every seed, because the only channel
                // that says "something is next door" was never fired.
                if (nextDoor(g)) {
                    g.cue("step_" + key);
                    g.heardNextDoor(this);
                } else {
                    g.cue("footstep");
                }
                return;
            }

            if (hereFor >= g.graceFor(this)) g.jumpscare(this);
            // NOTE: no return here, and that is the whole reason the
            // counters work. A thing standing in your room is still
            // walking -- it is just also a clock. Funtime Foxy is following
            // the camera, so the moment you put the camera somewhere else
            // it turns and goes, and the grace is the time you have to do
            // that in. Funtime Freddy is already where it was going, so it
            // does not move at all and only the shock answers it. If this
            // returned early instead, a thing in your room would be frozen
            // there until it killed you, and two of the three counters
            // would be decoration.
        } else {
            hereFor = 0;
        }

        timer += dt;
        if (wait <= 0) wait = g.interval(this);
        if (timer < wait) return;
        timer = 0;
        // The interval carries the randomness the die runs out of. See the
        // note on Game.INTERVAL_JITTER: at the top of the week the roll is
        // 20-in-20, so without this the last night is a script.
        wait = g.interval(this) * (1.0 + Game.INTERVAL_JITTER * (g.rng.nextDouble() - 0.5));

        // The die. Every other game in the franchise rolls one -- FNAF 1,
        // 2 and 3 all read `rng.nextInt(20) < aiLevel` before an
        // animatronic takes its step -- and FNAF 5 shipped without it.
        // `Game.aiLevel` was defined and never called, so the building was
        // a metronome: every threat moved on its interval, every time, and
        // the seed did nothing at all. The sweep is how it showed up. A
        // night with no randomness in it cannot produce a difficulty
        // curve, and FNAF 5's sweep returned 0% or 100% on every night
        // with nothing in between, at every grace value and every pace
        // tried, because there was nothing to average.
        //
        // A failed roll is a hesitation, not a retreat: the threat stays
        // where it is and tries again on its next interval.
        if (g.rng.nextInt(20) >= Game.aiLevel(g.night)) return;

        step(g);
    }

    /**
     * One room toward whatever this one is following.
     *
     * The cue is chosen by how close the move leaves it, and the gradient
     * is the whole information model of the game: a footstep somewhere in
     * the building, a named step when it is one door away, and its own
     * voice when it is in the room with you. A player who learns the three
     * voices can play this with their eyes shut, which is the point --
     * because the camera is pointed at somewhere else.
     */
    void step(Game g) {
        Room.Where target = target(g);
        if (target == null) return;
        Room.Where next = Room.stepToward(room, target);
        if (next == room) return;
        room = next;

        if (inYourRoom(g)) {
            hereFor = 0;
            sinceCue = 0;
            visits++;
            g.cue("here_" + key);
            g.arrived(this);
        } else if (nextDoor(g)) {
            g.cue("step_" + key);
            g.heardNextDoor(this);
        } else {
            g.cue("footstep");
        }
    }

    /**
     * What it is walking toward right now, or null if nothing.
     *
     * Null is not a failure state -- it is the two ways a player can make
     * one of these harmless. Ballora has no target once the building has
     * been quiet longer than she can remember a sound, and Foxy has no
     * target at all while the monitor is down, because it is following
     * the feed and there is no feed to follow.
     */
    Room.Where target(Game g) {
        return switch (rule) {
            case PURSUIT -> g.where;
            case ATTENTION -> {
                if (!g.monitorOn || g.camera == null) yield null;
                // It goes to what you are watching, and once it is there
                // it turns around and comes for you. That two-stage rule
                // is what stops "park the camera on the far end" from
                // being a way to keep it busy forever.
                yield room == g.camera ? g.where : g.camera;
            }
            // Deaf is not the same as out of range: past SOUND_MEMORY she
            // has simply forgotten the last noise, and the next one brings
            // her straight back. Deaf is the stretch she has given up in.
            case SOUND -> (deaf > 0 || g.soundAge > Game.SOUND_MEMORY)
                    ? null : g.lastSound;
        };
    }

    /** Sent to one end of the building, by a controlled shock. */
    void banished(Room.Where to) {
        room = to;
        timer = 0;
        hereFor = 0;
        sinceCue = 0;
    }
}
