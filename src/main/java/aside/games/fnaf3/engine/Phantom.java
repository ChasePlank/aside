package aside.games.fnaf3.engine;

/**
 * A phantom: a hallucination, and the reason the ventilation matters.
 *
 * Phantoms are not a threat. They cannot kill you and there is nothing to
 * do about them -- they appear, they stand there, and when they go they
 * take a system with them. The damage is always to the *night*, not to
 * you: a dead camera, a dead lure, or dead air.
 *
 * That is what makes them the honest punishment for a failed ventilation
 * system. The failure is not "you take damage"; it is "you now have one
 * fewer thing to think with, and the thing you lost is chosen for you."
 *
 * Each phantom is tied to a slot in the office so the screen has somewhere
 * to draw it and the mouse layer has somewhere to *not* put a button.
 */
public final class Phantom {

    /** Where in the office it stands. */
    public enum Slot { WINDOW, VENT, DESK, CORNER }

    public final String name;
    public final Slot slot;
    /** Which system it will take when it goes. */
    public final Game.System takes;
    /** Seconds left before it vanishes. */
    public double life;
    /** True once it has taken its system, so it can only do it once. */
    public boolean spent;

    public Phantom(String name, Slot slot, Game.System takes, double life) {
        this.name = name;
        this.slot = slot;
        this.takes = takes;
        this.life = life;
    }

    /**
     * One frame. Returns true when this phantom has just finished and the
     * caller should apply {@link #takes}.
     *
     * The system is taken on the way *out*, not on the way in, so the
     * player gets the whole hallucination as a warning and the cost lands
     * after it. A phantom that took the cameras the instant it appeared
     * would be a jump-scare; one that takes them as it leaves is a bill.
     */
    public boolean update(double dt) {
        if (spent) return false;
        life -= dt;
        if (life > 0) return false;
        spent = true;
        return true;
    }
}
