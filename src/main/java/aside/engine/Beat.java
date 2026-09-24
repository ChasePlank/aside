package aside.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * One executable step of a scene.
 *
 * TEXT and CHOICE are the only kinds the player sees — everything else
 * (staging, assignments, jumps) is applied silently while stepping, so
 * the UI only ever has to deal with "show this line" or "pick one of
 * these".
 */
public class Beat {
    public enum Kind {
        TEXT,    // speaker says something (speaker == null means narration)
        CHOICE,  // the player picks
        GOTO,    // unconditional jump, ends the scene
        SET,     // variable assignment
        STAGE,   // bg / music / sfx / show / hide
        EFFECT   // a `~` with no beat above it, applied on entry
    }

    public Kind kind;
    public int line;

    // TEXT
    public String speaker;
    public String pose;
    public String text;

    // CHOICE
    public List<Choice> choices = new ArrayList<>();

    // GOTO
    public String target;

    // SET
    public String varName;
    public String value;

    // STAGE
    public String directive;   // bg | music | sfx | show | hide
    public String arg;         // background/music/sfx name, or character
    public String arg2;        // pose
    public String arg3;        // position (left/center/right)

    /** Effects applied the moment this beat is shown. */
    public List<String> effects = new ArrayList<>();

    public static Beat text(String speaker, String pose, String text, int line) {
        Beat b = new Beat();
        b.kind = Kind.TEXT;
        b.speaker = speaker;
        b.pose = pose;
        b.text = text;
        b.line = line;
        return b;
    }

    public static Beat stage(String directive, String arg, String arg2, String arg3, int line) {
        Beat b = new Beat();
        b.kind = Kind.STAGE;
        b.directive = directive;
        b.arg = arg;
        b.arg2 = arg2;
        b.arg3 = arg3;
        b.line = line;
        return b;
    }

    public static Beat set(String name, String value, int line) {
        Beat b = new Beat();
        b.kind = Kind.SET;
        b.varName = name;
        b.value = value;
        b.line = line;
        return b;
    }

    public static Beat goTo(String target, int line) {
        Beat b = new Beat();
        b.kind = Kind.GOTO;
        b.target = target;
        b.line = line;
        return b;
    }

    /** A standalone `~` — carries effects applied when reached. */
    public static Beat effect(String effect, int line) {
        Beat b = new Beat();
        b.kind = Kind.EFFECT;
        b.effects.add(effect);
        b.line = line;
        return b;
    }

    public static Beat choice(int line) {
        Beat b = new Beat();
        b.kind = Kind.CHOICE;
        b.line = line;
        return b;
    }

    @Override public String toString() {
        return switch (kind) {
            case TEXT -> (speaker == null ? "" : speaker + ": ") + text;
            case CHOICE -> "choice(" + choices.size() + ")";
            case GOTO -> "-> " + target;
            case SET -> "set " + varName + " = " + value;
            case STAGE -> directive + " " + arg;
            case EFFECT -> "~ " + String.join("; ", effects);
        };
    }
}
