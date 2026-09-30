package aside.games.fnaf3;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Five Nights at Freddy's 3, as a game module.
 *
 * The third one, and the one that takes the most away. FNAF 1 gave you
 * doors. FNAF 2 gave you a mask. FNAF 3 gives you a speaker and a
 * screwdriver: you cannot stop Springtrap, you can only make a noise
 * somewhere he would rather be, and every second you spend fixing a
 * system is a second you are not holding the lure.
 *
 * Chase's line for the franchise was "each gets harder than the last", so
 * the difficulty table starts higher than FNAF 2's and ends at the top.
 * The engine underneath (fnaf3.engine.*) is pure logic with no UI
 * dependency, which is how the week was tuned -- by sweeping a bot over
 * 60 seeds a night rather than by playing twenty nights by hand.
 */
public class Fnaf3Game implements Game {

    @Override public String id() { return "fnaf3"; }

    @Override public String title() { return "Five Nights at Freddy's 3"; }

    @Override public String blurb() {
        return "No doors, no mask. A speaker, a screwdriver, and one thing that will not stop walking.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Assets.load();
        return new NightSelect(ui);
    }
}
