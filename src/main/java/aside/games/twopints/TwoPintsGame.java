package aside.games.twopints;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Two Pints, as a game module.
 *
 * <p>A milk round of three hundred and forty bottles, and two pints at the end of Chandlers Lane that have been on the
 * book since before he had the round. The bottles have stopped coming back empty - they are coming back FULL, which is
 * what happens at a house where nobody is drinking the milk and nobody has told the dairy.
 *
 * <p>Four endings, and they are four answers to how far a job reaches: ring the office, knock, ring the number she left
 * in a bottle, or keep leaving them and say nothing to anybody.
 */
public class TwoPintsGame implements Game {

    static final String STORY = "stories/two-pints.aside";

    @Override public String id() { return "two-pints"; }

    @Override public String title() { return "Two Pints"; }

    @Override public String blurb() {
        return "A milk round, and a doorstep that is still being paid for.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "two-pints");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
