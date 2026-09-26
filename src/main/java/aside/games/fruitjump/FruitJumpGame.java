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

    @Override public String title() { return "Tropical Punch"; }

    @Override public String blurb() {
        return "Fruit Jump, and the room-crawling dungeon underneath it.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        return new MainMenu(ui);
    }
}