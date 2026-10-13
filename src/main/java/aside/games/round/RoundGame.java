package aside.games.round;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Round, as a game module.
 *
 * <p>A night cleaner, a card in a cupboard with fourteen rooms on it in their own handwriting, and one office that
 * has been cleared - so the list and the building no longer agree, and the list is the only thing that says what the
 * job is.
 *
 * <p>Four endings, and the question under them is the one this library keeps returning to in different clothes:
 * what do you do with a procedure when the reason for it has gone? Tick it anyway, cross it out, keep it and stop
 * opening the door, or come back at half past four and find it already done.
 */
public class RoundGame implements Game {

    static final String STORY = "stories/the-round.aside";

    @Override public String id() { return "round"; }

    @Override public String title() { return "The Round"; }

    @Override public String blurb() {
        return "Fourteen rooms, four hours, six years, and one office the list still asks for.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-round");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
