package aside.games.fnaf7;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Five Nights at Freddy's 7, as a game module.
 *
 * <p>The seventh one, and the one that takes away the last thing every
 * game before it assumed -- that the thing coming for you does not know
 * anything about you.
 *
 * <p>FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and
 * a music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 gave
 * you a body that can only be in one place. FNAF 5 gave you a building and
 * took the room you were standing in. FNAF 6 gave you one chair, one lamp
 * and one shock, and took away the idea that a look is free. FNAF 7 gives
 * you <b>two doors, one bar and one light</b> -- and then takes away the
 * idea that you are a stranger to it.
 *
 * <pre>
 *   THE RULE: it does not have a pattern. It has yours.
 * </pre>
 *
 * <p>There is one bar and there are two doors, so exactly one of them is
 * open. The unit does not search the building and it does not walk a
 * route: <b>it comes to the side you are not looking at</b>, because it
 * has been keeping a decayed record of where your attention has been. That
 * record is drawn on the screen, in words, as {@code IT EXPECTS YOU AT:
 * LEFT} -- so the player can see what it thinks of them, and the whole
 * night is the argument between that and where the bar actually is.
 *
 * <p>Chase's line for the franchise was "each gets harder than the last",
 * and the difficulty table ends at the top of the seven. The engine
 * underneath (fnaf7.engine.*) is pure logic with no UI dependency, which
 * is how the week was tuned -- by sweeping a bot over three hundred seeds
 * a night rather than by playing twenty nights by hand.
 */
public class Fnaf7Game implements Game {

    @Override public String id() { return "fnaf7"; }

    @Override public String title() { return "Five Nights at Freddy's 7"; }

    @Override public String blurb() {
        return "Five nights with two doors and one bar. It has no pattern -- it has yours, and it comes to the side you are not watching.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Assets.load();
        return new NightSelect(ui);
    }
}
