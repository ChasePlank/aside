package aside.games.fnaf7;

import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** Pause overlay: a scrim over the frozen office. */
public class PauseScreen extends UiScreen {

    static final String[] ITEMS = {"Resume", "Quit to Menu"};
    int focus = 0;
    boolean dirty = true;

    public PauseScreen(UiManager ui, UiScreen game) {
        super(ui);
    }

    @Override public void enter() { dirty = true; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case UP -> { focus = (focus - 1 + ITEMS.length) % ITEMS.length; dirty = true; e.consume(); }
            case DOWN -> { focus = (focus + 1) % ITEMS.length; dirty = true; e.consume(); }
            case ENTER, SPACE -> {
                if (focus == 0) ui.pop();
                else { ui.pop(); ui.replace(new NightSelect(ui)); }
                e.consume();
            }
            case ESCAPE -> { ui.pop(); e.consume(); }
            default -> { }
        }
    }

    @Override
    public void tick(double dt) {
        if (!dirty) return;
        dirty = false;
        gc.setFill(Color.rgb(3, 3, 6, 0.80));
        gc.fillRect(0, 0, W, H);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 52));
        gc.fillText("Paused", W / 2, 250);
        for (int i = 0; i < ITEMS.length; i++) {
            boolean on = i == focus;
            gc.setFill(on ? Color.web("#FFD700") : Color.web("#CCCCCC"));
            gc.setFont(Font.font("Arial", 22));
            gc.fillText((on ? "\u25B6 " : "  ") + ITEMS[i], W / 2, 340 + i * 46);
        }
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText("ESC resume", W / 2, H - 60);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
