package aside.games.fnaf9;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Five Nights at Freddy's 9, as a game module.
 *
 * <p>The ninth one, and the one that takes away the last thing every game
 * before it assumed -- that <i>the picture is now.</i>
 *
 * <p>FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 gave you a
 * body that can only be in one place. FNAF 5 gave you a building and took the
 * room you were standing in. FNAF 6 gave you one chair, one lamp and one
 * shock, and took away the idea that a look is free. FNAF 7 gave you two
 * doors, one bar and one light, and took away the idea that you are a stranger
 * to it. FNAF 8 gave you two doorways and one lamp, and took away the idea
 * that either of them wants you. FNAF 9 gives you <b>two halls, one door and
 * one monitor</b> -- and then takes away the idea that the monitor is showing
 * you the present.
 *
 * <pre>
 *   THE RULE: the picture is as old as the time you have spent watching it.
 * </pre>
 *
 * <p>The monitor is a delay line. A glance is live; a stare is a photograph,
 * and past each walker's own patience with a bad picture it is not a
 * photograph of anything at all. So the night is not a fight and it is not a
 * distance -- it is <b>an attention budget</b>, and the whole of it is spent
 * deciding when to look and how long for.
 *
 * <p>Chase's line for the franchise was "each gets harder than the last", and
 * the difficulty table ends at the top of the nine. The engine underneath
 * (fnaf9.engine.*) is pure logic with no UI dependency, which is how the week
 * was tuned -- by sweeping a bot over two hundred seeds a night rather than by
 * playing twenty nights by hand.
 */
public class Fnaf9Game implements Game {

    @Override public String id() { return "fnaf9"; }

    @Override public String title() { return "Five Nights at Freddy's 9"; }

    @Override public String blurb() {
        return "Two halls, one door and one monitor -- and the picture is as "
                + "old as the time you have spent watching it.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        return new NightSelect(ui);
    }
}
