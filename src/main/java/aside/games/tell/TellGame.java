package aside.games.tell;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * tell, as a game module.
 *
 * No script file, like the other sixteen. The five nights and the read
 * arithmetic are fixed and the house is dealt from a seed, so the same house
 * is different every time and the same every time you reload it.
 */
public class TellGame implements Game {

    static final String SAVE = "tell.state";

    @Override public String id() { return "tell"; }

    @Override public String title() { return "Tell"; }

    @Override public String blurb() {
        return "Something in the house cannot see you and is counting which doors you use. It shows you what it expects.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new TellScreen(ui, Tell.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the house at " + save + ": " + e.getMessage(), e);
        }
    }
}
