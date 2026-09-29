package aside.games.ledger;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * Ledger, as a game module.
 *
 * Like Residue, there is no script file: the nights are in the code, but
 * what the player is actually playing is not the nights. It is the five
 * lines they are allowed to keep, and those are theirs.
 */
public class LedgerGame implements Game {

    static final String SAVE = "ledger.state";

    @Override public String id() { return "ledger"; }

    @Override public String title() { return "Ledger"; }

    @Override public String blurb() {
        return "Six nights. Five lines. You cannot keep it all.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new LedgerScreen(ui, Ledger.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the ledger at " + save + ": " + e.getMessage(), e);
        }
    }
}
