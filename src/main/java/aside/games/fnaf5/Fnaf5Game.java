package aside.games.fnaf5;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Five Nights at Freddy's 5, as a game module.
 *
 * The fifth one, and the one that takes away the room you are standing in.
 * FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 gave you
 * a body that can only be in one place. FNAF 5 gives you <b>a building</b>
 * -- five rooms, a monitor, and three things walking around in the dark --
 * and then takes away the one thing every game before it assumed, which is
 * that you can see where you are.
 *
 * <pre>
 *   THE RULE: the camera cannot see the room you are in.
 * </pre>
 *
 * So the monitor is always pointed somewhere else, and every decision to
 * move is a decision made on a picture of where you are going, taken from
 * another room, and already a second old by the time you get there. The
 * information and the safety are the same resource and you cannot hold
 * both.
 *
 * Chase's line for the franchise was "each gets harder than the last", so
 * the difficulty table starts above FNAF 4's and ends at the top. The
 * engine underneath (fnaf5.engine.*) is pure logic with no UI dependency,
 * which is how the week was tuned -- by sweeping a bot over sixty seeds a
 * night rather than by playing twenty nights by hand.
 */
public class Fnaf5Game implements Game {

    @Override public String id() { return "fnaf5"; }

    @Override public String title() { return "Five Nights at Freddy's 5"; }

    @Override public String blurb() {
        return "Five rooms, one monitor, and the camera cannot see the room "
                + "you are standing in.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Assets.load();
        return new NightSelect(ui);
    }
}
