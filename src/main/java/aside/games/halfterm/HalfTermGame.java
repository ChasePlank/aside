package aside.games.halfterm;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Half Term, as a game module.
 *
 * <p>A school caretaker of nineteen years, half term, and a boiler pump that has been on the list since 2019 with the
 * word DEFER against it twice. Whether you write it up and put it in front of the head, nurse it through the winter,
 * or ring the supplier for a price changes not the colour of the last act but WHICH LAST ACT YOU GET.
 *
 * <p>THE FIRST STORY HERE TO USE STATE: an earlier choice sets a flag and the reckoning routes on it, so
 * `term_with_the_head` is reachable only if you said something. Four endings, and the auditor's ending list is the
 * proof that the middle decision matters.
 */
public class HalfTermGame implements Game {

    static final String STORY = "stories/half-term.aside";

    @Override public String id() { return "half-term"; }

    @Override public String title() { return "Half Term"; }

    @Override public String blurb() {
        return "A school caretaker, a boiler with DEFER against it twice, and a decision that is remembered.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "half-term");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
