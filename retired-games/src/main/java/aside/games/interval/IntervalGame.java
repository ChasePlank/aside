package aside.games.interval;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * interval, as a game module.
 *
 * No script file, like the other eleven. The twelve days and the table are
 * fixed and the season is dealt from a seed, so the same season is different
 * every time and the same every time you reload it.
 */
public class IntervalGame implements Game {

    static final String SAVE = "interval.state";

    @Override public String id() { return "interval"; }

    @Override public String title() { return "Interval"; }

    @Override public String blurb() {
        return "Twelve days, six watches, and a ship that may not come. "
                + "The record on the wall is the only thing you know.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new IntervalScreen(ui, Interval.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the station at " + save + ": " + e.getMessage(), e);
        }
    }
}
