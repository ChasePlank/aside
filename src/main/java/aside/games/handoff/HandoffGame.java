package aside.games.handoff;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * Handoff, as a game module.
 *
 * Like Residue, Ledger and Testimony there is no script file. Nothing here
 * is authored for a player to read ahead of; the content is Bell's four
 * lines and the sea, and the only thing that changes between playthroughs
 * is what you decided a night needed.
 */
public class HandoffGame implements Game {

    static final String SAVE = "orders.state";

    @Override public String id() { return "handoff"; }

    @Override public String title() { return "Handoff"; }

    @Override public String blurb() {
        return "Keep five watches under someone else's orders, then write the orders for whoever comes next.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        return new HandoffScreen(ui, Handoff.load(save), save);
    }
}
