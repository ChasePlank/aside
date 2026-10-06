package aside.games.twoofeverything;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Two of Everything, as a game module.
 *
 * <p>A brother and a sister clearing out their mother's house in a weekend. Fifth register: the other four are one
 * person alone with a task, and this one is two people talking to each other instead of to the reader.
 *
 * <p>The thing it is about: a house with two of everything is a house where two people disagreed and neither of
 * them ever threw anything away. Six scenes, three endings, and the ending that costs the most is the one where
 * they leave both boxes on the table.
 */
public class TwoOfEverythingGame implements Game {

    static final String STORY = "stories/two-of-everything.aside";

    @Override public String id() { return "two-of-everything"; }

    @Override public String title() { return "Two of Everything"; }

    @Override public String blurb() {
        return "A brother, a sister, and a house where nothing was ever thrown away.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "two-of-everything");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
