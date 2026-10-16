package aside.games.fourteeninches;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Fourteen Inches, as a game module.
 *
 * <p>A thatcher, a ridge that has gone nine years early, and what is underneath it: every spar notched a quarter of an
 * inch too deep, all the way along - which is not a mistake anybody makes with a chisel by accident, it is a mistake
 * you make by doing it faster.
 *
 * <p>Four endings, and they are four answers to whose job the last man's work is.
 */
public class FourteenInchesGame implements Game {

    static final String STORY = "stories/fourteen-inches.aside";

    @Override public String id() { return "fourteen-inches"; }

    @Override public String title() { return "Fourteen Inches"; }

    @Override public String blurb() {
        return "A thatcher, and another man's work found under the ridge.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "fourteen-inches");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
