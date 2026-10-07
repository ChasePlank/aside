package aside.games.a440;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * A440, as a game module.
 *
 * <p>A piano tuner, forty years of other people's instruments, and the one piano they could not finish. The fork
 * goes on the bench first, because it is the only thing in the room that is not an opinion.
 *
 * <p>THE NINTH REGISTER AND THE FIRST THAT IS NOT A CHOICE TREE. Every story before this branches: you pick, and
 * the ending is whichever branch you took. This one does not branch at all until its last scene, because it is a
 * RECOLLECTION and the past is not a thing you choose. There is one choice in the whole script, at the end, and it
 * is not about what happened - it is about which memory the narrator settles on.
 *
 *
 * <p>(This said TENTH until the count was checked: there are nine stories, Bell Codes is the seventh and Ninety
 * Days the eighth. A number in a comment is a claim, which is the thing this project keeps relearning.)
 * <p>That shows up in the auditor as a number: <b>paths explored: 3</b>, where Ninety Days - a hub - has 48. The
 * traversal count is a signature of the shape.
 *
 * <p>ONE BACKGROUND and no sprites. The story never leaves the bench, so a second background would be scenery
 * rather than a place, and there is nobody else in it to draw.
 */
public class A440Game implements Game {

    static final String STORY = "stories/a440.aside";

    @Override public String id() { return "a440"; }

    @Override public String title() { return "A440"; }

    @Override public String blurb() {
        return "Forty years of other people's pianos, and the one that was never finished.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "a440");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
