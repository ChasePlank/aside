package aside.games.relay;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * relay, as a game module.
 *
 * No script file, like the other eleven. There is nothing to deal: the six
 * messages are the six messages every night, and the only thing that changes
 * between one run and the next is what the player does with them.
 */
public class RelayGame implements Game {

    static final String SAVE = "relay.state";

    @Override public String id() { return "relay"; }

    @Override public String title() { return "Relay"; }

    @Override public String blurb() {
        return "Two people are not speaking. Six messages cross. You choose what "
                + "each one carries \u2014 and what it drops.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new RelayScreen(ui, Relay.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the relay at " + save + ": " + e.getMessage(), e);
        }
    }
}
