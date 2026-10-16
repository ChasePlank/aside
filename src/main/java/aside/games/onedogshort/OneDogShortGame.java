package aside.games.onedogshort;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * One Dog Short, as a game module.
 *
 * <p>A shepherd, nine hundred ewes on two hills, and one dog left - Moss, nine and a half, going blind. His nose is
 * perfect and his ears are perfect and he works the near hill exactly as he always has, because he knows it, and
 * knowing it is not the same as seeing it and it is doing the work.
 *
 * <p>Four endings, and they are four answers to what you do about the end of a working partnership.
 */
public class OneDogShortGame implements Game {

    static final String STORY = "stories/one-dog-short.aside";

    @Override public String id() { return "one-dog-short"; }

    @Override public String title() { return "One Dog Short"; }

    @Override public String blurb() {
        return "A shepherd, a working dog going blind, and a whistle nobody else answers.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "one-dog-short");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
