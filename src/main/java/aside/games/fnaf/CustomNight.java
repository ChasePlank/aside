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

/** Custom night: set each animatronic's AI level 0-20 with arrow keys.
 *  LEFT/RIGHT adjust, UP/DOWN select animatronic, ENTER start, ESC back. */
public class CustomNight extends UiScreen {
    static final int W = 1280, H = 720;

    private final String[] NAMES = {"Monty", "Roxanne", "Chica", "Freddy"};
    private final int[] levels = {10, 10, 10, 10};
    private int focusIndex = 0;
    private boolean drawRequested = false;

    public CustomNight(UiManager ui) {
        super(ui);
    }
    @Override public void enter() { drawRequested = true; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case UP -> { focusIndex = (focusIndex - 1 + NAMES.length) % NAMES.length; drawRequested = true; }
            case DOWN -> { focusIndex = (focusIndex + 1) % NAMES.length; drawRequested = true; }
            case LEFT -> { levels[focusIndex] = Math.max(0, levels[focusIndex] - 1); drawRequested = true; }
            case RIGHT -> { levels[focusIndex] = Math.min(20, levels[focusIndex] + 1); drawRequested = true; }
            case ENTER, SPACE -> {
                ui.replace(new GameScreen(ui, levels));
                e.consume();
            }
            case ESCAPE -> { ui.pop(); e.consume(); }
            default -> {}
        }
    }

    public void tick(double dt) {
        if (drawRequested) { render(); drawRequested = false; }
    }

    void render() {
        gc.setFill(Color.web("#0A0A12"));
        gc.fillRect(0, 0, W, H);

        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 48));
        gc.fillText("Custom Night", W/2 - 140, 100);

        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 16));
        gc.fillText("Set AI levels (0-20). 20/20/20/20 is the classic.", W/2 - 190, 140);

        // Sliders
        for (int i = 0; i < NAMES.length; i++) {
            boolean focused = (i == focusIndex);
            double y = 220 + i * 80;

            // Name
            gc.setFill(focused ? Color.web("#FFD700") : Color.web("#CCCCCC"));
            gc.setFont(Font.font("Arial", 22));
            gc.fillText((focused ? "▶ " : "  ") + NAMES[i], W/2 - 200, y);

            // Level number
            gc.setFill(focused ? Color.web("#FFD700") : Color.web("#FFFFFF"));
            gc.setFont(Font.font("Monospaced", 22));
            gc.fillText(String.format("%2d", levels[i]), W/2 + 100, y);

            // Bar
            double barX = W/2 - 40, barW = 120, barH = 12;
            gc.setFill(Color.web("#1A1A2E"));
            gc.fillRect(barX, y - 14, barW, barH);
            gc.setFill(focused ? Color.web("#FFD700") : Color.web("#3CB043"));
            gc.fillRect(barX, y - 14, barW * levels[i] / 20.0, barH);
            gc.setStroke(Color.web("#555577"));
            gc.strokeRect(barX, y - 14, barW, barH);
        }

        // Start hint
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 14));
        gc.fillText("← → adjust   ↑ ↓ select   ENTER start   ESC back", W/2 - 180, H - 60);
    }
}
