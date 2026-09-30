package aside.games.vigil;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * vigil, as a game module.
 *
 * No script file, like the seven before it. The house is in the code and it is
 * the same house every time, because the thing the player is playing is not
 * the five things in it. It is twelve days of arithmetic that does not work
 * out, and what they decide to do about that.
 */
public class VigilGame implements Game {

    static final String SAVE = "vigil.state";

    @Override public String id() { return "vigil"; }

    @Override public String title() { return "Vigil"; }

    @Override public String blurb() {
        return "Twelve days, five things, and less of you every day.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        return new VigilScreen(ui, Vigil.load(save), save);
    }
}
