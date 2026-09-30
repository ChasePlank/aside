package aside.games.fnaf2;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** Pick a night. Five of them, and the fifth is the one that matters. */
public class NightSelect extends UiScreen {

    static final int NIGHTS = MouseMap.NIGHTS;
    int focus = 0;

    public NightSelect(UiManager ui) {
        super(ui);
        Audio.A.music("room_tone");
    }

    @Override public Parent getRoot() { return root; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case UP -> { focus = (focus - 1 + NIGHTS) % NIGHTS; e.consume(); }
            case DOWN -> { focus = (focus + 1) % NIGHTS; e.consume(); }
            case ENTER, SPACE -> {
                start();
                e.consume();
            }
            case ESCAPE -> { ui.pop(); e.consume(); }
            default -> { }
        }
    }

    /**
     * Hover moves the selection, click starts the night -- the same shape
     * Roxanne gave the library, so the two menus behave alike. The row that
     * lights up is the row that starts, because both come from
     * {@link MouseMap#nightRow}.
     */
    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        int i = MouseMap.nightAt(x, y);
        if (i < 0) return;
        focus = i;
        if (pressed) start();
    }

    void start() {
        Audio.A.sfx("choice_select");
        ui.replace(new GameScreen(ui, focus + 1));
    }

    @Override
    public void tick(double dt) {
        gc.setFill(Color.web("#07070C"));
        gc.fillRect(0, 0, W, H);

        Assets a = Assets.A;
        if (a != null && a.office != null) {
            gc.setGlobalAlpha(0.35);
            gc.drawImage(a.office, 0, (H - 768.0 * W / 1600) / 2, W, 768.0 * W / 1600);
            gc.setGlobalAlpha(1);
        }
        gc.setFill(Color.rgb(0, 0, 0, 0.45));
        gc.fillRect(0, 0, W, H);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 46));
        gc.fillText("FIVE NIGHTS AT FREDDY'S 2", W / 2, 110);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 16));
        gc.fillText("No doors. A mask, a flashlight, and a music box you cannot stop winding.",
                W / 2, 146);

        for (int i = 0; i < NIGHTS; i++) {
            boolean on = i == focus;
            double[] row = MouseMap.nightRow(i);
            if (on) {
                gc.setFill(Color.rgb(233, 69, 96, 0.16));
                gc.fillRect(row[0], row[1], row[2], row[3]);
                gc.setStroke(Color.rgb(233, 69, 96, 0.5));
                gc.setLineWidth(1);
                gc.strokeRect(row[0], row[1], row[2], row[3]);
            }
            gc.setFill(on ? Color.web("#FFD700") : Color.web("#CCCCCC"));
            gc.setFont(Font.font("Arial", on ? 30 : 26));
            gc.fillText((on ? "▶  " : "   ") + "Night " + (i + 1), W / 2,
                    row[1] + row[3] / 2 + 11);
        }

        gc.setFill(Color.web("#7777AA"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText("click a light      Q hall      Z / C vents      M mask", W / 2, H - 130);
        gc.fillText("click a camera      SPACE monitor      W wind (CAM 11)", W / 2, H - 106);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText("click a night or ENTER to start      ESC back to the library",
                W / 2, H - 62);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
