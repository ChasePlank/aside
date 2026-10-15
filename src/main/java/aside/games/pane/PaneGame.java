package aside.games.pane;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Pane, as a game module.
 *
 * <p>A glazier of thirty years who can read a broken window the way other people read a face - and a back door whose
 * pane went outwards, with the pieces on a step that is under a porch, and an invoice with a line on it that says
 * CAUSE OF BREAK.
 *
 * <p>Four endings, and they are four answers to whether a fact you can see is yours to say.
 */
public class PaneGame implements Game {

    static final String STORY = "stories/the-pane.aside";

    @Override public String id() { return "pane"; }

    @Override public String title() { return "The Pane"; }

    @Override public String blurb() {
        return "A broken window, and which side the glass is on.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-pane");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
