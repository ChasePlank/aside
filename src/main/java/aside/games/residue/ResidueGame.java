package aside.games.residue;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * Residue, as a game module.
 *
 * Unlike the other three, this one has no script file. Its content is not
 * written down anywhere — it is generated from the room's state, which is
 * the point. There is nothing to author and nothing to read ahead of; the
 * only thing that changes between playthroughs is what the player did
 * last time.
 */
public class ResidueGame implements Game {

    static final String SAVE = "room.state";

    @Override public String id() { return "residue"; }

    @Override public String title() { return "Residue"; }

    @Override public String blurb() {
        return "A room that ages while you are gone. Leave one thing.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            Room room = Room.load(save);
            // Age the room on arrival, before anything is drawn. This is
            // in-memory only -- the room is written back when the player
            // actually leaves something. Walking in and walking out again
            // therefore costs nothing, and reopening this screen twice
            // gives the same room both times.
            room.advance();
            return new ResidueScreen(ui, room, save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the room at " + save + ": " + e.getMessage(), e);
        }
    }
}
