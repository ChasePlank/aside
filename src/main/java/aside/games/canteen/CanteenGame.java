package aside.games.canteen;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Canteen, as a game module.
 *
 * <p>A cook, a works canteen, and a strip of card on the board that says `for R. no onion.` - a standing order nobody
 * has collected since the summer. Nineteen years of a small pie, and the question the story is actually about:
 * whether to keep a habit that has outlived its reason.
 *
 * <p>Four endings, and none of them is a defeat. Stop making it and keep the card anyway. Keep making it for a
 * reason that has changed. Write it down and never deal with it. Or leave the plate out once and find it washed.
 */
public class CanteenGame implements Game {

    static final String STORY = "stories/the-canteen.aside";

    @Override public String id() { return "canteen"; }

    @Override public String title() { return "The Canteen"; }

    @Override public String blurb() {
        return "A works canteen, a standing order nobody collects any more, and nineteen years of one small pie.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-canteen");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
