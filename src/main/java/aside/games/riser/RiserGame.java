package aside.games.riser;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Riser, as a game module.
 *
 * <p>A plumber called out at two in the morning to a leak in a block of flats, a riser cupboard with four tide lines
 * on the pipe, and a tenant who has been ringing about the damp wall since April. The job is a two-hour repair and a
 * form, and the form has one box that decides who pays: the date of the fault.
 *
 * <p>Three endings, and they are three answers to what a professional puts in writing about a fault somebody has
 * been living with for months - the truth with the tide lines dated, April because he asked for April, or tonight,
 * so that the form is the form it was always going to be.
 */
public class RiserGame implements Game {

    static final String STORY = "stories/the-riser.aside";

    @Override public String id() { return "riser"; }

    @Override public String title() { return "The Riser"; }

    @Override public String blurb() {
        return "A call-out at two in the morning, four tide lines on a pipe, and one box on the form.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-riser");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
