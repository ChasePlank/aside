package aside.games.lastcrossing;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Last Crossing, as a game module.
 *
 * <p>A ferryman, a rope ferry, and the last three nights before a bridge opens and makes him unnecessary. Nothing
 * is broken and nobody is cruel; the work was good for nineteen years and it is ending.
 *
 * <p>THE ELEVENTH REGISTER AND THE FIRST THAT OFFERS THE SAME DECISION MORE THAN ONCE. Every story before this
 * moves forward - a choice leads to a scene and the reader is always further along than they were. This one
 * circles: he is asked the same question at the same waterline three nights running, and the only thing that
 * changes between them is how many times he has said not yet. The flags are the entire engine of the story, and
 * the auditor shows it as a shape rather than a claim - <b>28 scenes, 4 endings</b>, where Ninety Days - a hub -
 * reaches its endings by how many items you bothered to look at rather than by which one.
 *
 * <p>FOUR BACKGROUNDS AND NO SPRITES. There is one other person in it and she is described rather than drawn,
 * which is how the two stories about people who do not get looked at directly are both staged.
 *
 * <p><b>THE ART IS BORROWED.</b> river_jetty, far_bank, dawn_river and dawn_bridge are the lamp room's coast and
 * a dawn, copied into this story's folder so the phone build carries art rather than four blank panels. Drawing
 * this story's own river is the remaining work, and it is why the README's "each has its own art" is not yet
 * true of this one.
 */
public class LastCrossingGame implements Game {

    static final String STORY = "stories/the-last-crossing.aside";

    @Override public String id() { return "last-crossing"; }

    @Override public String title() { return "The Last Crossing"; }

    @Override public String blurb() {
        return "A rope ferry, three nights, and a bridge that opens on Monday.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-last-crossing");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
