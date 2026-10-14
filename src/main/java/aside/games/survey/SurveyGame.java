package aside.games.survey;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Survey, as a game module.
 *
 * <p>The other side of The Canteen: an eighteen-year-old with a clipboard, a summer of levels, and the only hour of
 * the week he is not being looked at - ten past two in the works canteen, the end table under the clock, a small pie
 * with no onion in it, eleven minutes.
 *
 * <p>Three endings, and they turn on whether he says anything about a kindness he was not asked for and did not
 * acknowledge at the time.
 */
public class SurveyGame implements Game {

    static final String STORY = "stories/the-survey.aside";

    @Override public String id() { return "survey"; }

    @Override public String title() { return "The Survey"; }

    @Override public String blurb() {
        return "A summer of levels, a canteen at ten past two, and a pie somebody remembered.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-survey");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
