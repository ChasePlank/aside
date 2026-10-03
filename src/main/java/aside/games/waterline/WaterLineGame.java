package aside.games.waterline;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Water Line, as a game module.
 *
 * A night archivist in a city that is flooding, moving the collection up a floor every few years and deciding
 * what gets carried. Nothing supernatural happens; the whole story is a sorting problem with a person attached
 * to it. Three endings, and the one that costs the most is the one where you take nothing.
 *
 * It has no art of its own yet, which the engine is built to survive - a story with no art directory falls back
 * to the shared one rather than failing, so this reads as a story told over a room instead of in one. Worth
 * knowing rather than hiding: the three earlier stories each have their own backgrounds and this does not.
 */
public class WaterLineGame implements Game {

    static final String STORY = "stories/the-water-line.aside";

    @Override public String id() { return "water-line"; }

    @Override public String title() { return "The Water Line"; }

    @Override public String blurb() {
        return "An archivist, a chalk line, and nineteen years of deciding what gets carried.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-water-line");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
