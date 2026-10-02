package aside.games.fnaf4;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Five Nights at Freddy's 4, as a game module.
 *
 * The fourth one, and the one that takes the desk away. FNAF 1 gave you
 * doors and a power meter. FNAF 2 gave you a mask and a music box. FNAF 3
 * gave you a speaker and took the doors. FNAF 4 gives you a flashlight
 * and <b>a body that can only be in one place</b>: four things are walking
 * toward four sides of the room, and the cost of being wrong about which
 * one to watch is the walk to the other one.
 *
 * Chase's line for the franchise was "each gets harder than the last", so
 * the difficulty table starts higher than FNAF 3's and ends at the top.
 * The engine underneath (fnaf4.engine.*) is pure logic with no UI
 * dependency, which is how the week was tuned -- by sweeping a bot over 60
 * seeds a night rather than by playing twenty nights by hand.
 */
public class Fnaf4Game implements Game {

    @Override public String id() { return "fnaf4"; }

    @Override public String title() { return "Five Nights at Freddy's 4"; }

    @Override public String blurb() {
        return "Five nights in a room with four sides and one body. The flashlight pushes things back and tells them where you are.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Assets.load();
        return new NightSelect(ui);
    }
}
