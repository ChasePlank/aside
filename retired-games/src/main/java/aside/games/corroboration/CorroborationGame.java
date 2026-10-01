package aside.games.corroboration;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * corroboration, as a game module.
 *
 * No script file, like the rest of them. The night is in the code, and the
 * seed is in the save, so a night half-asked is a night you can come back to
 * with the questions you have already spent still spent.
 */
public class CorroborationGame implements Game {

    static final String SAVE = "night.state";

    @Override public String id() { return "corroboration"; }

    @Override public String title() { return "Corroboration"; }

    @Override public String blurb() {
        return "Two people saw it. Neither of them is an instrument.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            Corroboration c = Corroboration.load(save);
            if (c == null) c = Corroboration.newNight((int) (System.nanoTime() & 0x7fffffff));
            return new CorroborationScreen(ui, c, save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the log at " + save + ": " + e.getMessage(), e);
        }
    }
}
