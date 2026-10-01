package aside.games.fnaf8;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Five Nights at Freddy's 8, as a game module.
 *
 * <p>The eighth one, and the one that takes away the last thing every game
 * before it assumed -- that <i>you are what it is coming for.</i>
 *
 * <p>FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 gave you
 * a body that can only be in one place. FNAF 5 gave you a building and took
 * the room you were standing in. FNAF 6 gave you one chair, one lamp and
 * one shock, and took away the idea that a look is free. FNAF 7 gave you
 * two doors, one bar and one light, and took away the idea that you are a
 * stranger to it. FNAF 8 gives you <b>two doorways and one lamp</b> -- and
 * then takes away the idea that either of them wants you.
 *
 * <pre>
 *   THE RULE: they are not coming for you. They are coming for each other.
 *             You are where they meet.
 * </pre>
 *
 * <p>Two units walk up two halls. Neither one is hunting you. Each is
 * walking toward the office because the other one is, and <b>the night ends
 * the moment the two of them get close enough to see each other</b> -- which
 * only happens in the one place in the building they can both reach, which
 * is the room you are sitting in.
 *
 * <p>So the night is not a fight. It is <b>a distance</b>, and the whole of
 * it is spent keeping that distance open with one lamp that can only be in
 * one hall at a time. Chase's line for the franchise was "each gets harder
 * than the last", and the difficulty table ends at the top of the eight.
 * The engine underneath (fnaf8.engine.*) is pure logic with no UI
 * dependency, which is how the week was tuned -- by sweeping a bot over
 * five hundred seeds a night rather than by playing twenty nights by hand.
 */
public class Fnaf8Game implements Game {

    @Override public String id() { return "fnaf8"; }

    @Override public String title() { return "Five Nights at Freddy's 8"; }

    @Override public String blurb() {
        return "Two doorways, one lamp -- and they are not coming for you, "
                + "they are coming for each other.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Assets.load();
        return new NightSelect(ui);
    }
}
