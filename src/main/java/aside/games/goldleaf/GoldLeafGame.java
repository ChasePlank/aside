package aside.games.goldleaf;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Gold Leaf, as a game module.
 *
 * <p>A signwriter of forty years, a grocer closing in March, and a fascia hand-lettered in 1948 whose gold has gone to
 * a sort of grey-brown. She wants it repainted before she goes, and she knows it will be painted over or shuttered
 * within the year - "it's for me. I've looked at that sign for fifty-one years."
 *
 * <p>Four endings, and the question is what a job is for when nobody is coming.
 */
public class GoldLeafGame implements Game {

    static final String STORY = "stories/gold-leaf.aside";

    @Override public String id() { return "gold-leaf"; }

    @Override public String title() { return "Gold Leaf"; }

    @Override public String blurb() {
        return "A shopfront closing in March, and a sign that will outlive its reason.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "gold-leaf");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
