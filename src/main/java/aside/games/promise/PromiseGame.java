package aside.games.promise;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * promise, as a game module.
 *
 * No script file, like the other thirteen. There is nothing to deal: the eight
 * asks and the five nights of water are the same every season, and the only
 * thing that changes between one run and the next is what the player says.
 */
public class PromiseGame implements Game {

    static final String SAVE = "promise.state";

    @Override public String id() { return "promise"; }

    @Override public String title() { return "Promise"; }

    @Override public String blurb() {
        return "Five nights, eight people asking, and water that takes some of every "
                + "night. Saying yes costs you nothing \u2014 which is the whole trouble.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new PromiseScreen(ui, Promise.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the crossing at " + save
                    + ": " + e.getMessage(), e);
        }
    }
}
