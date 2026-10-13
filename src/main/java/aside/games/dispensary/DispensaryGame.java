package aside.games.dispensary;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Dispensary, as a game module.
 *
 * <p><b>IT HAD NONE, AND THAT IS WHY IT WAS NOT IN THE LIBRARY AT ALL.</b> `LibraryScreen` builds its list from
 * `Games.all()`, so a story with no module is not merely missing its art - it cannot be opened. MEASURED on
 * 2026-10-13, before this file existed: the library listed 31 games against 17 story files, and the stories absent
 * from it were the-bothy, the-dispensary, the-harbour, the-last-route and the-lookout (night-shift is the suite's
 * FIXTURE with planted faults, and stays out on purpose).
 *
 * <p>SIX STORIES IN ONE RELEASE'S NOTES, FIVE OF THEM UNREACHABLE. The audit ran over them, the shelf counted
 * them, and nothing asked whether a player could open one - which is the same shape as the rest of this week: a
 * check that reports on the thing and not about the thing.
 *
 * <p>The id follows the convention that `the-lamp-room` set: the game id is the SQUASHED story name
 * (`lamp-room`), while `Assets.loadStory` is given the STORY's name, because the art lives under the story's own
 * directory. Getting this the other way round for The Exchange cost five passing checks and a written-down wrong
 * turn; see `gameIdFor` in SelfTest.
 */
public class DispensaryGame implements Game {

    static final String STORY = "stories/the-dispensary.aside";

    @Override public String id() { return "dispensary"; }

    @Override public String title() { return "The Dispensary"; }

    @Override public String blurb() {
        return "The back half of a shop that shut at six, and the answerphone that still rings in it.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-dispensary");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
