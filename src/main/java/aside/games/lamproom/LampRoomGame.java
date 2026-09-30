package aside.games.lamproom;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Lamp Room, as a game module.
 *
 * Note on the import: VnScreen lives in the overtime package, so every other story has to reach into it. That is
 * a smell rather than a bug - the presenter belongs to the engine, not to the first game that needed one - and it
 * is left alone here rather than moved, because moving it touches every story at once for no behaviour change.
 */
public class LampRoomGame implements Game {

    static final String STORY = "stories/the-lamp-room.aside";

    @Override public String id() { return "lamp-room"; }

    @Override public String title() { return "The Lamp Room"; }

    @Override public String blurb() {
        return "A keeper, a lamp that goes out, and the difference between doing a job correctly and doing it well.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
