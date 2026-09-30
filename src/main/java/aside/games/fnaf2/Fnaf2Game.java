package aside.games.fnaf2;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Five Nights at Freddy's 2, as a game module.
 *
 * The survival engine underneath (fnaf2.engine.*) is pure logic with no
 * UI dependency, which is why it can be played by a bot at 60fps with no
 * display -- and why the difficulty was tuned by sweeping the bot rather
 * than by playing twenty nights by hand.
 *
 * Commissioned by Chase on 2026-09-29: "same monitor and light system, no
 * doors, 2 vents with lights, 1 hall with light in front, music box
 * winding, mask to protect."
 */
public class Fnaf2Game implements Game {

    @Override public String id() { return "fnaf2"; }

    @Override public String title() { return "Five Nights at Freddy's 2"; }

    @Override public String blurb() {
        return "No doors. A mask, a flashlight, and a music box you cannot stop winding.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Assets.load();
        return new NightSelect(ui);
    }
}
