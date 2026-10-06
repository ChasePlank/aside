package aside.games.bellcodes;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Bell Codes, as a game module.
 *
 * <p>A signalman works the last night in a mechanical signal box before the panel at the regional centre takes
 * the line. He is not sad about the levers. What goes when the box closes is not the labour - it is the
 * courtesy: the five beats that were never in the book, because the book records codes and the men had manners.
 *
 * <p>The seventh register. The first six are Overtime (horror and performance), The Lamp Room (warm, about
 * tending), The Discrepancy (quiet and wry), The Water Line (elegiac and practical), Two of Everything (two
 * people and a house), and Night Shift (the demo). This one is technical and warm, and it is the first that
 * insists on nothing supernatural: the only thing that happens in it is that a bell rings twice.
 *
 * <p>THREE BACKGROUNDS and no sprites - there is nobody else in the story to draw, which is the same reason
 * every story but Two of Everything has none. The three are the box at night, the box's book on the desk, and
 * the box at first light, and they are the same room because the room is the whole set.
 */
public class BellCodesGame implements Game {

    static final String STORY = "stories/bell-codes.aside";

    @Override public String id() { return "bell-codes"; }

    @Override public String title() { return "Bell Codes"; }

    @Override public String blurb() {
        return "The last night in a signal box, and a bell code that is not in the book.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "bell-codes");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
