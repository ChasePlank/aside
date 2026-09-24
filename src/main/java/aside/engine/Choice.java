package aside.engine;

import java.util.ArrayList;
import java.util.List;

/** One option in a choice beat, with an optional gate and effects. */
public class Choice {
    public String text;
    public String target;
    /** Condition expression, or null if always available. */
    public String condition;
    public List<String> effects = new ArrayList<>();
    public int line;

    public Choice(String text, String target) {
        this.text = text;
        this.target = target;
    }

    @Override public String toString() {
        return "* " + text + " -> " + target + (condition != null ? " if " + condition : "");
    }
}
