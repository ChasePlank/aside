package aside.games.calledhome;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Called Home, as a game module.
 *
 * <p>A bell tower, a ringer of fifty-four years who has died, and the question in the notice book: whether anybody in
 * the band can call a course of Plain Bob Major, half-muffled, at a funeral, in front of a family, without it coming
 * apart. You have rung here since you were fourteen and have never called anything in your life; knowing the method is
 * not calling it.
 *
 * <p>THE FIRST STORY HERE WHERE STATE CHANGES A SCENE IN THE MIDDLE as well as the ending: whether you said yes to
 * calling splits the last practice into two different evenings, and the ending routes on that and on whether the bells
 * went muffled or open. Written against a measurement - 26 of the 39 stories before it open by inventorying something
 * and then drawing an inference from it, so this one opens inside a conversation.
 */
public class CalledHomeGame implements Game {

    static final String STORY = "stories/called-home.aside";

    @Override public String id() { return "called-home"; }

    @Override public String title() { return "Called Home"; }

    @Override public String blurb() {
        return "A bell tower, a ringer who has died, and a method nobody else can call.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "called-home");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
