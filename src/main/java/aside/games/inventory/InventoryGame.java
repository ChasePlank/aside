package aside.games.inventory;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.nio.file.Path;

/**
 * inventory, as a game module.
 *
 * No script file, like Residue, Ledger, Testimony, Handoff, Outside and
 * Bearings. The collection is in the code and it is the same every time,
 * because the thing the player is actually playing is not the eight objects.
 * It is the moment of writing a card, when the object is still in front of
 * you and the card is the only thing that will survive it.
 */
public class InventoryGame implements Game {

    static final String SAVE = "inventory.state";

    @Override public String id() { return "inventory"; }

    @Override public String title() { return "Inventory"; }

    @Override public String blurb() {
        return "The catalogue is what the collection is to whoever gets it.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        Path save = Games.saveDir(id()).resolve(SAVE);
        try {
            return new InventoryScreen(ui, Inventory.load(save), save);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the inventory at " + save + ": " + e.getMessage(), e);
        }
    }
}
