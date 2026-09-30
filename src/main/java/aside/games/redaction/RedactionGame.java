package aside.games.redaction;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * redaction, as a game module.
 *
 * No script file, like the other ten. The file is fixed and the bars are the
 * player's, so the whole of the state is sixteen characters.
 */
public class RedactionGame implements Game {

    static final String SAVE = "redaction.state";

    @Override public String id() { return "redaction"; }

    @Override public String title() { return "Redaction"; }

    @Override public String blurb() {
        return Redaction.cap(Redaction.word(Redaction.LINES)) + " lines go to the board, "
                + "and every black bar is a place they will look.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new RedactionScreen(ui, Redaction.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the file at " + save + ": " + e.getMessage(), e);
        }
    }
}
