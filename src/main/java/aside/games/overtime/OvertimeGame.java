package aside.games.overtime;

import aside.engine.Audit;
import aside.engine.Script;
import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;
import java.nio.file.Path;

/** Overtime, as a game module. */
public class OvertimeGame implements Game {

    static final String STORY = "stories/overtime.aside";

    @Override public String id() { return "overtime"; }

    @Override public String title() { return "Overtime"; }

    @Override public String blurb() {
        return "A night shift at the pizzeria, from the other side of the desk.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            File f = new File(STORY);
            Script s = Script.load(f.toPath());
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }

    /** Convenience for tooling: the same script the game runs. */
    public static Path storyPath() { return Path.of(STORY); }
}