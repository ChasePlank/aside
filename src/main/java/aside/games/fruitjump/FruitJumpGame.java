package aside.games.fruitjump;

import aside.game.Game;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Tropical Punch, as a game module.
 *
 * The playable Java version of the project: Fruit Jump, a side-view
 * platformer, plus the top-down room-crawling mode underneath it. Both
 * share the same headless engine (physics, level generation, enemy AI,
 * the AI director), which is why it ported the same way the survival
 * game did.
 */
public class FruitJumpGame implements Game {

    @Override public String id() { return "fruitjump"; }

        // THE LIBRARY SHELF NAME, which is a SECOND place the display name lives - MainMenu.TITLE is the first, and
    // its comment used to claim the title was "here and nowhere else", which was not true. Both say Holdfast.
    //
    // The class name, the package and the save file keep saying "fruitjump". Those are identifiers rather than
    // display: renaming them touches every import and every save path for no gain a player can see.
    @Override public String title() { return "Holdfast"; }

    @Override public String blurb() {
        // "Still needs a real name" was true until 2026-10-04 and is not any more. The first sentence stays
        // exactly as it was: it is the premise, and it was already right.
        return "A climber, a sunset, and a way home.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        return new MainMenu(ui);
    }
}