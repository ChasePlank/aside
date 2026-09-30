package aside.games.attribution;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * attribution, as a game module.
 *
 * No script file, like the other nine. The copy is fixed and the desk is dealt
 * from a seed, so the night is different every time and the same every time
 * you reload it.
 */
public class AttributionGame implements Game {

    static final String SAVE = "attribution.state";

    @Override public String id() { return "attribution"; }

    @Override public String title() { return "Attribution"; }

    @Override public String blurb() {
        return Attribution.cap(Attribution.word(Attribution.CALLS)) + " calls for "
                + Attribution.word(Attribution.ITEMS) + " lines. The edition does not say "
                + "which of them you checked.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new AttributionScreen(ui, Attribution.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the desk at " + save + ": " + e.getMessage(), e);
        }
    }
}
