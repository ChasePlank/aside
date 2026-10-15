package aside.games.chart;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Chart, as a game module.
 *
 * <p>An optician, twenty minutes, a man of eighty-three whose daughter booked the appointment and drove him in - and
 * a line on a chart that he can still read in one eye, in good light, if he takes his time. There is a form, and a box
 * on it called VISUAL STANDARD FOR DRIVING, and the box has two options and neither of them is "nearly".
 *
 * <p>Four endings, and the question is who a result belongs to when the chart does not care what anybody wants.
 */
public class ChartGame implements Game {

    static final String STORY = "stories/the-chart.aside";

    @Override public String id() { return "chart"; }

    @Override public String title() { return "The Chart"; }

    @Override public String blurb() {
        return "A sight test, and a number that has a legal consequence.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-chart");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
