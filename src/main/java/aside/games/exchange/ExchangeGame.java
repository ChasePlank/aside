package aside.games.exchange;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * The Exchange, as a game module.
 *
 * <p><b>IT HAD NO GAME MODULE, AND THAT IS WHY ITS BACKGROUND NEVER LOADED.</b> A story's art lives in
 * {@code art/stories/<id>/backgrounds/}, and the only thing that reads that directory is {@code Assets.loadStory} -
 * which is called from a game module's {@code create}. This story shipped without one, so when it was opened its
 * scene asked for {@code booth_desk}, nothing had loaded it, and THE FRAME SHOWED SOMEBODY ELSE'S ROOM. Rendered and
 * looked at, not inferred: the projection booth the scene asks for against the FNAF office that appeared instead.
 *
 * <p>Seven stories were in that state - the ones added fastest - and the audit knew: it prints "backgrounds with no
 * image yet" per story and reported the library clean, because the count was never part of its verdict.
 *
 * <p>One room, one night, and a voice you are not supposed to recognise. The exchange is being replaced on Friday.
 */
public class ExchangeGame implements Game {

    static final String STORY = "stories/the-exchange.aside";

    @Override public String id() { return "exchange"; }

    @Override public String title() { return "The Exchange"; }

    @Override public String blurb() {
        return "Eleven years of other people's calls, and one you are not supposed to know.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "the-exchange");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
