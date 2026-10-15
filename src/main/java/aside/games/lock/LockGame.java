package aside.games.lock;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Lock, as a game module.
 *
 * <p>A lock keeper, four gates, eleven minutes a boat, and a schedule on a board by the office. A narrowboat comes
 * up the cut at ten past seven, alone, and the answer to "am I too late" has been yes four times this year.
 *
 * <p>Four endings, and they are four answers to what a schedule is for once nobody is watching it.
 */
public class LockGame implements Game {

    static final String STORY = "stories/the-lock.aside";

    @Override public String id() { return "lock"; }

    @Override public String title() { return "The Lock"; }

    @Override public String blurb() {
        return "Four gates, eleven minutes a boat, and a narrowboat that comes up after hours.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-lock");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
