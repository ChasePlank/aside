package aside.games.outside;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * outside, as a game module.
 *
 * No script file, like Residue and Ledger: the seven days are in the code,
 * but the thing the player is actually playing is not the days. It is the
 * order their own words arrive in, and they cannot change it once it is
 * said.
 */
public class OutsideGame implements Game {

    static final String SAVE = "outside.state";

    @Override public String id() { return "outside"; }

    @Override public String title() { return "Outside"; }

    @Override public String blurb() {
        return "You can see it. It can only hear you.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new OutsideScreen(ui, Outside.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the ridge log at " + save + ": " + e.getMessage(), e);
        }
    }
}
