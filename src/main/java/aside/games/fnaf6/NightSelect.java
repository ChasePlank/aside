package aside.games.fnaf6;

import aside.games.fnaf6.engine.Unit;
import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** Pick a night. Five of them, and each one is a different thing in the chair. */
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
        gc.setFill(Color.web("#05060A"));
        gc.fillRect(0, 0, W, H);

        Assets a = Assets.A;
        if (a != null && a.room != null) {
            gc.setGlobalAlpha(0.22);
            gc.drawImage(a.room, 0, 0, W, H);
            gc.setGlobalAlpha(1);
        }
        gc.setFill(Color.rgb(0, 0, 0, 0.66));
        gc.fillRect(0, 0, W, H);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 44));
        gc.fillText(Voice.TITLE, W / 2, 104);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 16));
        gc.fillText(Voice.SUBTITLE, W / 2, 140);

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
            Unit u = Unit.forNight(i);
            gc.setFill(on ? Color.web("#FFD700") : Color.web("#CCCCCC"));
            gc.setFont(Font.font("Arial", on ? 24 : 21));
            gc.fillText((on ? "\u25B6  " : "   ")
                            + String.format(Voice.NIGHT_ROW, i, u.name()),
                    W / 2, row[1] + 26);
            gc.setFill(Color.web(on ? "#9A9AAE" : "#555566"));
            gc.setFont(Font.font("Arial", 12));
            gc.fillText(u.note(), W / 2, row[1] + 43);
        }

        gc.setFill(Color.web("#7777AA"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText(Voice.SELECT_HELP_1, W / 2, H - 140);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText(Voice.SELECT_HELP_2, W / 2, H - 110);
        gc.fillText(Voice.SELECT_HELP_3, W / 2, H - 88);
        gc.fillText(Voice.SELECT_HELP_4, W / 2, H - 46);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
