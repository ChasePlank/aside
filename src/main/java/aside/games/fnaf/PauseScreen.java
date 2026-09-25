package aside.games.fnaf;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** Pause overlay — Canvas-drawn scrim + text over the frozen office. */
public class PauseScreen extends UiScreen {
    static final int W = 1280, H = 720;

    private static final String[] ITEMS = {"Resume", "Quit to Menu"};
    private int focusIndex = 0;
    private boolean drawRequested = false;

    public PauseScreen(UiManager ui, UiScreen game) {
        super(ui);
        root.getStyleClass().add("pause-overlay");
    }
    @Override public void enter() { drawRequested = true; }

    @Override
    public void handleKey(KeyEvent e) {
        if (e.getCode() == KeyCode.UP) {
            focusIndex = (focusIndex - 1 + ITEMS.length) % ITEMS.length;
            drawRequested = true;
        } else if (e.getCode() == KeyCode.DOWN) {
            focusIndex = (focusIndex + 1) % ITEMS.length;
            drawRequested = true;
        } else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
            if (focusIndex == 0) { ui.pop(); }
            else { ui.pop(); ui.replace(new MainMenu(ui)); }
            e.consume();
        } else if (e.getCode() == KeyCode.ESCAPE) {
            ui.pop();
            e.consume();
        }
    }

    public void tick(double dt) {
        if (drawRequested) { render(); drawRequested = false; }
    }

    void render() {
        // Scrim
        gc.setFill(Color.rgb(5, 5, 10, 0.75));
        gc.fillRect(0, 0, W, H);

        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 52));
        gc.fillText("Paused", W/2 - 80, 200);

        for (int i = 0; i < ITEMS.length; i++) {
            boolean focused = (i == focusIndex);
            gc.setFill(focused ? Color.web("#FFD700") : Color.web("#CCCCCC"));
            gc.setFont(Font.font("Arial", 22));
            gc.fillText((focused ? "▶ " : "  ") + ITEMS[i], W/2 - 60, 300 + i * 45);
        }

        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText("ESC resume", W/2 - 30, H - 60);
    }
}
