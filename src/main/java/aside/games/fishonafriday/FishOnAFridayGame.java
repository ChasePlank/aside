package aside.games.fishonafriday;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Fish on a Friday, as a game module.
 *
 * <p>A chip shop, twenty-two years of frying, and the oil in the second fryer that is due out today and is now eleven
 * o'clock and dark at the edges. Doing it now costs a lunch and four hundred pounds; doing it Monday costs a Friday
 * and a Saturday on oil that has gone.
 *
 * <p>THE FIRST STORY HERE WHERE STATE CHANGES WHICH OPTIONS THE READER IS OFFERED. All three ways through the evening
 * reach the same late decision, and one of its four options - doing the night properly, shutting at nine and changing
 * the oil before going home - APPEARS ONLY IF YOU DID NOT CHANGE IT THIS MORNING. The auditor's `never-offered picks`
 * is what says the conditional option is really offered on some path rather than dead.
 */
public class FishOnAFridayGame implements Game {

    static final String STORY = "stories/fish-on-a-friday.aside";

    @Override public String id() { return "fish-on-a-friday"; }

    @Override public String title() { return "Fish on a Friday"; }

    @Override public String blurb() {
        return "A chip shop, oil due out today, and an option that only appears if you did not change it.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "fish-on-a-friday");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
