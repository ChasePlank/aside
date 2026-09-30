package aside.games.lesson;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * lesson, as a game module.
 *
 * No script file, like the other twelve. The board and the shift are dealt from
 * a seed and the rule is dealt with them, so the same handover is a different
 * handover every time and the same every time you reload it.
 */
public class LessonGame implements Game {

    static final String SAVE = "lesson.state";

    @Override public String id() { return "lesson"; }

    @Override public String title() { return "Lesson"; }

    @Override public String blurb() {
        return "Three nights to show somebody how the boiler runs. "
                + "A state only ever teaches its own corner.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new LessonScreen(ui, Lesson.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the boiler house at " + save
                    + ": " + e.getMessage(), e);
        }
    }
}
