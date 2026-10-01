package aside.games.discrepancy;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Discrepancy, as a game module.
 *
 * Note on the import: VnScreen lives in the overtime package, so every other story has to reach into it. That is
 * a smell rather than a bug - the presenter belongs to the engine, not to the first game that needed one - and it
 * is left alone here rather than moved, because moving it touches every story at once for no behaviour change.
 */
public class DiscrepancyGame implements Game {

    static final String STORY = "stories/the-discrepancy.aside";

    @Override public String id() { return "discrepancy"; }

    @Override public String title() { return "The Discrepancy"; }

    @Override public String blurb() {
        return "A night audit that does not balance, and the line in the ledger that should not be there.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
