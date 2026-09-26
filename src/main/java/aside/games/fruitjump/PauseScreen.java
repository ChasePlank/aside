package aside.games.fruitjump;

import aside.ui.LibraryScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Pause overlay. Drawn on its own canvas, so it covers the frozen game
 * underneath rather than showing it through.
 *
 * The game beneath stops advancing for free: the host ticks only the top
 * screen on the stack.
 */
public class PauseScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Arial", 56);
    static final Font F_ITEM = Font.font("Arial", 28);
    static final Font F_TINY = Font.font("Arial", 13);

    static final String[] ITEMS = {"Resume", "Quit to menu"};
    int index = 0;

    public PauseScreen(UiManager ui, UiScreen game) {
        super(ui);
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.UP) index = (index - 1 + ITEMS.length) % ITEMS.length;
        else if (c == KeyCode.DOWN) index = (index + 1) % ITEMS.length;
        else if (c == KeyCode.ESCAPE) ui.pop();
        else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
            if (index == 0) ui.pop();
            else ui.replace(new MainMenu(ui));
        }
        e.consume();
    }

    @Override
    public void tick(double dt) {
        gc.setFill(Color.web("#1a1a2e"));
        gc.fillRect(0, 0, W, H);

        gc.setFill(Color.web("#f5a623"));
        gc.setFont(F_TITLE);
        gc.fillText("Paused", 120, 220);

        double y = 330;
        for (int i = 0; i < ITEMS.length; i++) {
            boolean sel = i == index;
            gc.setFill(sel ? Color.web("#f5a623") : Color.web("#c9c9d6"));
            gc.setFont(F_ITEM);
            gc.fillText((sel ? ">  " : "   ") + ITEMS[i], 160, y);
            y += 52;
        }
        gc.setFill(Color.web("#6e6e86"));
        gc.setFont(F_TINY);
        gc.fillText("ESC resume    up/down select    ENTER confirm", 120, H - 40);
    }
}