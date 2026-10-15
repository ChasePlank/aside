package aside.games.flue;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Flue, as a game module.
 *
 * <p>A sweep, four chimneys, and the one that has not been done since the old man died. Behind the calendar in the
 * cupboard on the landing the flue bends, and in the bend of a brick there is a gap the size of a fist and a tobacco
 * tin with a perished rubber band round it.
 *
 * <p>Four endings, and they are four answers to a question the job does not cover: what you do with a thing somebody
 * hid, when the only person who could have hidden it is seven years dead.
 */
public class FlueGame implements Game {

    static final String STORY = "stories/the-flue.aside";

    @Override public String id() { return "flue"; }

    @Override public String title() { return "The Flue"; }

    @Override public String blurb() {
        return "A sweep, four chimneys, and a tobacco tin in the bend of the brickwork.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-flue");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
