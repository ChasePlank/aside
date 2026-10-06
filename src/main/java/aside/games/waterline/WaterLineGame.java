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
 * <p>It has ONE background - the archive basement - and no sprites, which is not an oversight: there is nobody
 * else in this story to draw. Every story before this one is a single person alone, and the two after it kept
 * that shape. The only story in the engine with character sprites is Two of Everything, and it has them because
 * it is the only one with two people in it.
 *
 * <p>(This paragraph used to say it had no art at all. That was true when it was written and stopped being true
 * when the basement was drawn, and nothing updated it - which is the same thing that happened to the water
 * probe's comment about splashes.)
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
