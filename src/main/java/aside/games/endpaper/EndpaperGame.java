package aside.games.endpaper;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Endpaper, as a game module.
 *
 * <p>A bindery over a shop that sells paper and envelopes, twenty-two years of trade, and a Book of Common Prayer
 * from 1901 whose front pastedown has lifted at one corner. Under the lifted corner there is writing in pencil, and
 * the trade's answer is that a pastedown is replaced with the boards - because that is what rebinding means.
 *
 * <p>Five endings, and they are five answers to whether a repair is allowed to lose things.
 */
public class EndpaperGame implements Game {

    static final String STORY = "stories/the-endpaper.aside";

    @Override public String id() { return "endpaper"; }

    @Override public String title() { return "The Endpaper"; }

    @Override public String blurb() {
        return "A rebinding, and nine lines in pencil under the pastedown.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-endpaper");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
