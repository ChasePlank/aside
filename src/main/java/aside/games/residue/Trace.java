package aside.games.residue;

/** One thing left in the room, and how long ago. */
public final class Trace {

    public final Thing thing;
    /** Which gesture was used — see {@link Thing.Gesture}. */
    public final String gesture;
    /** The visit number this was left on. */
    public final int bornVisit;
    /** True if this player left it, on an earlier visit. */
    public final boolean mine;
    /** Visits since it was left. 0 means it was left just now. */
    public int age;

    public Trace(Thing thing, String gesture, int bornVisit, boolean mine, int age) {
        this.thing = thing;
        this.gesture = gesture;
        this.bornVisit = bornVisit;
        this.mine = mine;
        this.age = age;
    }

    /** The words for this, as it looks right now. Null once it is gone. */
    public String describe() { return thing.describe(gesture, age); }

    /** The words for this, as it will look after one more visit away. */
    public String describeNext() { return thing.describe(gesture, age + 1); }

    public boolean gone() { return describe() == null; }

    /** Who left it, in a word. */
    public String author() { return mine ? "yours" : "someone"; }
}
