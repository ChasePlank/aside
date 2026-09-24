package aside.engine;

import java.util.ArrayList;
import java.util.List;

/** A labelled chunk of script. Scenes are the unit of routing: choices
 *  and jumps name a scene to continue into. */
public class Scene {
    public String id;
    public List<Beat> beats = new ArrayList<>();
    public int line;

    /** Set when the scene ends by falling off the end with no
     *  GOTO/CHOICE — the bot reports these as dead ends. */
    public boolean endsOpen() {
        if (beats.isEmpty()) return true;
        Beat last = beats.get(beats.size() - 1);
        return last.kind != Beat.Kind.GOTO && last.kind != Beat.Kind.CHOICE;
    }

    @Override public String toString() { return "== " + id + " == (" + beats.size() + " beats)"; }
}
