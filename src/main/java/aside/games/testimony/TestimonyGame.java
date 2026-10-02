package aside.games.testimony;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * Testimony, as a game module.
 *
 * Like Residue and Ledger there is no script file. What the player is
 * playing is not the evening either -- it is the eight answers they gave,
 * and the fact that those eight answers are now the only copy they have.
 */
public class TestimonyGame implements Game {

    static final String SAVE = "testimony.state";

    @Override public String id() { return "testimony"; }

    @Override public String title() { return "Testimony"; }

    @Override public String blurb() {
        return "See an evening once, then answer for it. Every answer becomes your memory, right or wrong.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        return new TestimonyScreen(ui, Testimony.load(save), save);
    }
}
