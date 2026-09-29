package aside.games.bearings;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * bearings, as a game module.
 *
 * No script file, like Residue, Ledger, Testimony, Handoff and Outside. The
 * voyage is in the code, and it is different every time, because the thing
 * the player is actually playing is not the sixteen days. It is how long ago
 * they last looked at something that was not their own instrument.
 */
public class BearingsGame implements Game {

    static final String SAVE = "bearings.state";

    @Override public String id() { return "bearings"; }

    @Override public String title() { return "Bearings"; }

    @Override public String blurb() {
        return "Two clocks that agree are still just two clocks.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new BearingsScreen(ui, Bearings.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the ship's book at " + save + ": " + e.getMessage(), e);
        }
    }
}
