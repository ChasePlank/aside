package aside.games.fnaf3.engine;

/**
 * Springtrap: the only thing in the building that is trying to reach you.
 *
 * FNAF 1 and FNAF 2 both gave the player a way to *stop* something -- a
 * door, a mask, a light. FNAF 3 gives you none of those. You cannot block
 * Springtrap, you cannot hide from him, and you cannot make him leave the
 * building. The only verb you have is <b>redirection</b>: an audio lure
 * that draws him to a room you choose, which is a way of spending his
 * distance from you rather than removing it.
 *
 * That is the whole design. He is always coming; the question is only
 * whether you have bought enough room to do the other things the night
 * asks for.
 *
 * He is not a dice roll either. He walks a graph, one room per move, and
 * the graph is a tree rooted at the office -- so where he is, where the
 * lure is, and how many moves you have bought are all countable.
 */
public final class Springtrap {

    /** Where he is: 1..10. Meaningless while {@link #atOffice}. */
    public int room;

    /** True once he has stepped out of room 1 and into the office. */
    public boolean atOffice;
    /** Which way in he came. The office has two: a window and a vent. */
    public boolean atWindow;

    /** Seconds since his last move. */
    public double moveTimer;
    /** Seconds he has been standing in the office. */
    public double officeTimer;

    public Springtrap(int startRoom) {
        this.room = startRoom;
    }

    /**
     * One frame.
     *
     * The office branch is checked first and returns, because once he is
     * inside there is no graph left -- there is only the lure, and the
     * clock on how long you have to use it.
     */
    public void update(double dt, Game g) {
        if (g.status != Game.Status.PLAYING) return;

        if (atOffice) {
            // The lure is the only thing that reaches him in here. It does
            // not need to be played in a clever room -- he is standing in
            // the office and can hear a sound anywhere in the building --
            // but it does have to be *available*, which is what the
            // cooldown and the audio system are for.
            if (g.lureRoom > 0) {
                leave(g);
                return;
            }
            officeTimer += dt;
            if (officeTimer >= g.officeGrace()) g.jumpscare(this);
            return;
        }

        moveTimer += dt;
        if (moveTimer < g.moveInterval()) return;
        moveTimer = 0;
        step(g);
    }

    /** One move along the graph. */
    void step(Game g) {
        int lure = g.lureRoom;
        int target = lure > 0 ? lure : 1;

        if (room == 1 && lure == 0) {
            // The last room before the office. He does not walk past it;
            // he either comes in or waits.
            if (g.rng.nextDouble() < g.enterChance()) enter(g);
            return;
        }

        if (room == target) {
            // Already where the sound is. He stays and listens.
            return;
        }

        int[] cands = House.closerTo(room, target);
        if (cands.length == 0) return;
        room = cands[g.rng.nextInt(cands.length)];
        g.cue("footstep");

        if (room == 1 && lure == 0 && g.rng.nextDouble() < g.enterChance()) enter(g);
    }

    void enter(Game g) {
        atOffice = true;
        atWindow = g.rng.nextBoolean();
        officeTimer = 0;
        g.cue("at_door");
    }

    /** Drawn back out by the lure. He returns to the last room, not to the far end. */
    void leave(Game g) {
        atOffice = false;
        room = 1;
        moveTimer = 0;
        officeTimer = 0;
        g.cue("door_close");
    }

    /** How far he is from the office, in moves. 0 while he is inside it. */
    public int distanceToOffice() {
        return atOffice ? 0 : House.TO_OFFICE[room];
    }

    public String where() {
        return atOffice ? "Office" : House.NAME[room];
    }
}
