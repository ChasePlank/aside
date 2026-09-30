package aside.games.drift;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * drift, as a game module.
 *
 * No script file, like the other ten. The log is fixed and the night is dealt
 * from a seed, so the same copy is different every time and the same every time
 * you reload it.
 */
public class DriftGame implements Game {

    static final String SAVE = "drift.state";

    @Override public String id() { return "drift"; }

    @Override public String title() { return "Drift"; }

    @Override public String blurb() {
        return "Two copies of one record. " + Drift.cap(Drift.word(Drift.CHANGED))
                + " lines differ. " + Drift.cap(Drift.word(Drift.REWORDED))
                + " of them say the same thing.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new DriftScreen(ui, Drift.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the log at " + save + ": " + e.getMessage(), e);
        }
    }
}
