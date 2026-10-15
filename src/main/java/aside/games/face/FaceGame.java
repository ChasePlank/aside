package aside.games.face;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Face, as a game module.
 *
 * <p>A shot firer at a quarry with four days left, three shots a day, and eight holes that all go within a second of
 * each other - and then a click from up at the face, which is the sound a hole makes when it has not done anything
 * at all. The rule is on the wall of the magazine in his own handwriting: do not approach the face for thirty
 * minutes, and then approach it as though the charge is live.
 *
 * <p>Four endings, and they are four answers to what the rule is for when the men are in the bothy with the kettle
 * on and the owner's car is at the gate.
 */
public class FaceGame implements Game {

    static final String STORY = "stories/the-face.aside";

    @Override public String id() { return "face"; }

    @Override public String title() { return "The Face"; }

    @Override public String blurb() {
        return "Three shots a day, four days left, and a charge that clicks instead of going up.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-face");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
