package aside.games.fnaf;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Five Nights at Freddy's - Glamrock Edition, as a game module.
 *
 * The survival engine underneath (fnaf.engine.*) is pure logic with no
 * UI dependency, which is why it ported without a single change. Only
 * the screens had to be adapted.
 */
public class FnafGame implements Game {

    /** Shared progress (nights beaten, characters met). */
    public static Progress progress;

    @Override public String id() { return "fnaf"; }

    @Override public String title() { return "Five Nights at Freddy's - Glamrock"; }

    @Override public String blurb() {
        return "Five nights in the office. Shut the doors, watch the halls, and do not run the power out before 6 AM.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Assets.load();
        if (progress == null) progress = Progress.load();
        return new MainMenu(ui);
    }
}