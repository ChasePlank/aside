package aside.games.forge;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Forge, as a game module.
 *
 * <p>A farrier at the end of a lane who can hear a hoof on tarmac from the yard and tell which foot it is, a horse
 * that comes off a ramp with its near hind toe just touching the ground, and a man who wants new shoes on it before
 * a photograph on Saturday.
 *
 * <p>Four endings, and they are four answers to who the job is done for: the man, the horse, the buyer, or the
 * fortnight it actually needs.
 */
public class ForgeGame implements Game {

    static final String STORY = "stories/the-forge.aside";

    @Override public String id() { return "forge"; }

    @Override public String title() { return "The Forge"; }

    @Override public String blurb() {
        return "A farrier, a horse with a hot foot, and a sale on Saturday.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-forge");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
