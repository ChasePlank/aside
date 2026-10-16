package aside.games.undercover;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Under Cover, as a game module.
 *
 * <p>Twelve pitches under cover, eleven of them the same eleven people for years, and a new van in the third pitch with
 * a board that says EVERYTHING A POUND. He is two along, and he is selling what you sell.
 *
 * <p>Four endings, and they are four answers to what the trade asks of you when a price walks in and stands two
 * pitches down.
 */
public class UnderCoverGame implements Game {

    static final String STORY = "stories/under-cover.aside";

    @Override public String id() { return "under-cover"; }

    @Override public String title() { return "Under Cover"; }

    @Override public String blurb() {
        return "A market pitch, and a neighbour who sells what you sell for a pound.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "under-cover");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
