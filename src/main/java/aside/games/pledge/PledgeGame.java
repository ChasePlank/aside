package aside.games.pledge;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Pledge, as a game module.
 *
 * <p>A pawnbroker, a christening mug pledged for less than it is worth, and an engraving that names somebody who is
 * not on the ticket. Three hundred and sixty days is the whole of the trade and it fits on the ticket; the question
 * underneath it is what a person does with a thing they have noticed about a thing they are holding.
 *
 * <p>Three endings: he answers and it goes in pencil on the shop's half, or nobody says the name and the mug is
 * forfeit in the window with a card, or you write the ticket as it is and never find out.
 */
public class PledgeGame implements Game {

    static final String STORY = "stories/the-pledge.aside";

    @Override public String id() { return "pledge"; }

    @Override public String title() { return "The Pledge"; }

    @Override public String blurb() {
        return "A pawnbroker, three hundred and sixty days, and an engraving that names somebody else.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-pledge");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
