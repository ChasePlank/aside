package aside.games.outofseason;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Out of Season, as a game module.
 *
 * <p>A seaside guest house holding eleven, down to three by the middle of October, and a man in room four who pays a
 * week at a time in cash and does not leave. Marion has run this house for nineteen years and has never said no to a
 * booking in October; the last three weeks of the year are the boiler service, the gutters, and her own front room.
 * Three weeks of one guest is three hundred and fifteen pounds.
 *
 * <p>THE FIRST STORY HERE TOLD IN THE THIRD PERSON, and the first with a conditional option written correctly - the
 * option to tell him the truth about the front room is offered only if she asked him how long he was staying. Told in
 * the third person because one of the forty-one stories before it was; every other one is second-person and puts the
 * reader inside the trade, and this one makes the reader an observer of somebody at work.
 */
public class OutOfSeasonGame implements Game {

    static final String STORY = "stories/out-of-season.aside";

    @Override public String id() { return "out-of-season"; }

    @Override public String title() { return "Out of Season"; }

    @Override public String blurb() {
        return "A guest house in the October, and a man who does not leave.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "out-of-season");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
