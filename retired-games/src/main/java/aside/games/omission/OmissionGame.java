package aside.games.omission;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * omission, as a game module.
 *
 * No script file, like the other thirteen. The list is dealt from a seed, so
 * the same house is a different house every time and the same every time you
 * reload it.
 */
public class OmissionGame implements Game {

    static final String SAVE = "omission.state";

    @Override public String id() { return "omission"; }

    @Override public String title() { return "Omission"; }

    @Override public String blurb() {
        return "Five things fit in the bag. What you never said out loud, you "
                + "will remember wrong.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new OmissionScreen(ui, Omission.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the house at " + save
                    + ": " + e.getMessage(), e);
        }
    }
}
