package aside.games.plot;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Plot, as a game module.
 *
 * <p>A sexton, a grave open for a burial on Thursday, and a headstone by the gate that has come loose at the base -
 * which is the worst of the two places for it to move. The diocese's rule says an unstable memorial is made safe,
 * and the usual way is to lay it down flat, and the family is coming on Thursday and will walk past it.
 *
 * <p>Four endings, and they are four answers to who the decision belongs to when the rule is right.
 */
public class PlotGame implements Game {

    static final String STORY = "stories/the-plot.aside";

    @Override public String id() { return "plot"; }

    @Override public String title() { return "The Plot"; }

    @Override public String blurb() {
        return "A leaning headstone, a rule about safety, and a burial on Thursday.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-plot");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
