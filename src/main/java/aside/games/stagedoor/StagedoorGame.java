package aside.games.stagedoor;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Stage Door, as a game module.
 *
 * <p>A doorkeeper, a book with a line for everybody who comes in, and the last night of a run. The rule is the whole
 * job and it is the only one they have ever needed: name, time, who they are here to see, written in their presence.
 *
 * <p>THE FIRST STORY HERE THAT BRANCHES WITHOUT A VARIABLE. Every earlier one records a flag in a `~` beat and reads
 * it in a choice's `[if ...]`. This one needed to branch AFTER a shared scene, and the engine has no author syntax
 * for a conditional jump - `Beat.Kind.IFGOTO` exists and is never produced by the parser, so a line like
 * `-> ending [if flag]` is parsed as PROSE and shows up in the story as narration. Found by probing the parsed beats
 * rather than by reading the script. The three branches therefore each carry the night to their own ending, which is
 * plainer and needed no flag at all.
 */
public class StagedoorGame implements Game {

    static final String STORY = "stories/the-stage-door.aside";

    @Override public String id() { return "stage-door"; }

    @Override public String title() { return "The Stage Door"; }

    @Override public String blurb() {
        return "A doorkeeper, a book with a line for everyone who comes in, and the last night of a run.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-stage-door");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
