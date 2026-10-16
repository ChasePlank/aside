package aside.games.asmalllump;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * A Small Lump, as a game module.
 *
 * <p>A vet of eleven years, an appointment at ten past four, and a lurcher of about thirteen called Bramble. The lump is
 * on the inside of the back leg: not a fatty lump, fixed to the muscle, about the size of a walnut. The honest answer
 * costs a hundred and forty pounds before anybody has decided anything, and the man who came on the bus with the dog's
 * basket under his chair is eighty-one.
 *
 * <p>Four choices, three endings, and they turn on a question nobody asks in the right order - "which one of those is
 * for him?" - and on the answer, which is that none of them are.
 */
public class ASmallLumpGame implements Game {

    static final String STORY = "stories/a-small-lump.aside";

    @Override public String id() { return "a-small-lump"; }

    @Override public String title() { return "A Small Lump"; }

    @Override public String blurb() {
        return "A vet, an old dog, and an owner who cannot afford the answer.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "a-small-lump");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
