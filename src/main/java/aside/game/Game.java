package aside.game;

import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * A game the engine can host.
 *
 * Deliberately small. Everything a game needs from the host is already
 * available as a static service -- Audio.A for sound, its own asset
 * loading for art, Games.saveDir(id) for persistence -- so this
 * interface only has to answer "who are you" and "give me something to
 * put on screen".
 *
 * The test of an abstraction is whether two genuinely different things
 * fit it. This one has to hold both a branching visual novel and a
 * real-time survival game, so it stays this thin on purpose.
 */
public interface Game {

    /** Stable identifier, used for the save directory and for
     *  addressing the game from the command line. */
    String id();

    String title();

    /** One line for the library list. */
    String blurb();

    /** Build the game's entry screen. Called fresh each time it starts. */
    UiScreen create(UiManager ui);
}