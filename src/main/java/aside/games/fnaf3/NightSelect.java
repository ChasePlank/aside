package aside.games.fnaf3;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** Pick a night. Five of them, and the fifth is the one that matters. */
public class NightSelect extends UiScreen {

    static final int NIGHTS = MouseMap.NIGHTS;
    int focus = 1;

    public NightSelect(UiManager ui) {
        super(ui);
        Audio.A.music("room_tone");
    }

    @Override public Parent getRoot() { return root; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case UP -> { focus = focus == 1 ? NIGHTS : focus - 1; e.consume(); }
            case DOWN -> { focus = focus == NIGHTS ? 1 : focus + 1; e.consume(); }
            case ENTER, SPACE -> { start(); e.consume(); }
            case ESCAPE -> { ui.pop(); e.consume(); }
            default -> { }
        }
    }

    /**
     * Hover moves the selection, click starts the night -- the same shape
     * Roxanne gave the library, so the two menus behave alike. The row
     * that lights up is the row that starts, because both come from
     * {@link MouseMap#nightRow}.
     */
    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        int i = MouseMap.nightAt(x, y);
        if (i < 1) return;
        focus = i;
        if (pressed) start();
    }

    void start() {
        Audio.A.sfx("choice_select");
        ui.replace(new GameScreen(ui, focus));
    }

    @Override
    public void tick(double dt) {
        gc.setFill(Color.web("#07070C"));
        gc.fillRect(0, 0, W, H);

        Assets a = Assets.A;
        if (a != null && a.office != null) {
            gc.setGlobalAlpha(0.32);
            double h = a.office.getHeight() * W / a.office.getWidth();
            gc.drawImage(a.office, 0, (H - h) / 2, W, h);
            gc.setGlobalAlpha(1);
        }
        gc.setFill(Color.rgb(0, 0, 0, 0.5));
        gc.fillRect(0, 0, W, H);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 44));
        gc.fillText("FIVE NIGHTS AT FREDDY'S 3", W / 2, 108);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 16));
        gc.fillText("No doors, no mask. A speaker, a screwdriver, and one thing "
                + "that will not stop walking.", W / 2, 144);

        for (int i = 1; i <= NIGHTS; i++) {
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
            gc.fillText((on ? "\u25B6  " : "   ") + "Night " + i, W / 2,
                    row[1] + row[3] / 2 + 11);
        }

        gc.setFill(Color.web("#7777AA"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText("SPACE monitor      1-0 cameras      Q play the lure in that room",
                W / 2, H - 132);
        gc.fillText("click a panel to reboot it      A audio      V air      C cameras",
                W / 2, H - 108);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText("click a night or ENTER to start      ESC back to the library",
                W / 2, H - 62);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
