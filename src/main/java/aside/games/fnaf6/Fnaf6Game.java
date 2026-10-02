package aside.games.fnaf6;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Five Nights at Freddy's 6, as a game module.
 *
 * <p>The sixth one, and the one that takes away the desk, the building and
 * the monitor, and leaves a chair.
 *
 * <p>FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 gave you
 * a body that can only be in one place. FNAF 5 gave you a building and
 * took the room you were standing in. FNAF 6 gives you <b>one chair, one
 * lamp, and one shock</b> -- and then takes away the thing every game
 * before it assumed, which is that a look is free.
 *
 * <pre>
 *   THE RULE: the lamp is the only way to see it, and the lamp is what it
 *             is waiting for.
 * </pre>
 *
 * <p>There is one unit in the room and it is either going to get up or it
 * is not, and there is no way to tell the difference except by watching --
 * and watching is the one thing that makes either of them move. A night
 * with nothing in the chair is a night you can lose by being careful. A
 * night with something in it is a night you can lose by being patient.
 * Those are opposite instructions and you do not know which night you are
 * in, which is the whole game.
 *
 * <p>Chase's line for the franchise was "each gets harder than the last",
 * and the difficulty table ends at the top of the six. The engine
 * underneath (fnaf6.engine.*) is pure logic with no UI dependency, which
 * is how the week was tuned -- by sweeping a bot over two hundred seeds a
 * night rather than by playing twenty nights by hand.
 */
public class Fnaf6Game implements Game {

    @Override public String id() { return "fnaf6"; }

    @Override public String title() { return "Five Nights at Freddy's 6"; }

    @Override public String blurb() {
        return "Five nights with one lamp and one shock. The lamp is the only way to see the chair, and the lamp is what wakes it.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Assets.load();
        return new NightSelect(ui);
    }
}
