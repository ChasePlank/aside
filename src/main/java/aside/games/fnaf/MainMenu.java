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

/** Main menu — Canvas-drawn (Region backgrounds don't render on this
 *  GPU). Nights are locked until the previous one is beaten; Custom
 *  Night unlocks after night 5. */
public class MainMenu extends UiScreen {
    static final int W = 1280, H = 720;

    private static final String[] ITEMS = {
        "Night 1", "Night 2", "Night 3", "Night 4", "Night 5",
        "Custom Night", "Characters", "Quit"
    };
    private int focusIndex = 0;
    private boolean drawRequested = false;
    private String notice = "";
    private double noticeTimer = 0;

    public MainMenu(UiManager ui) {
        super(ui);

        // Start focus on the first unlocked night
        Progress p = FnafGame.progress;
        if (p != null) focusIndex = Math.min(p.unlocked - 1, 4);
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
            select();
            e.consume();
        }
    }

    /** Is this menu item currently selectable? */
    boolean available(int i) {
        Progress p = FnafGame.progress;
        if (p == null) return true;
        if (i <= 4) return (i + 1) <= p.unlocked;
        if (i == 5) return p.customUnlocked();
        return true;
    }

    void select() {
        Progress p = FnafGame.progress;
        if (focusIndex <= 4) {
            if (!available(focusIndex)) {
                notice = "Night " + (focusIndex + 1) + " is locked — beat night "
                        + p.unlocked + " first.";
                noticeTimer = 2.5;
                drawRequested = true;
                return;
            }
            ui.replace(new GameScreen(ui, focusIndex + 1));
        } else if (focusIndex == 5) {
            if (!available(5)) {
                notice = "Custom Night unlocks after you beat Night 5.";
                noticeTimer = 2.5;
                drawRequested = true;
                return;
            }
            ui.replace(new CustomNight(ui));
        } else if (focusIndex == 6) {
            ui.replace(new InfoScreen(ui));
        } else if (focusIndex == 7) {
            javafx.application.Platform.exit();
        }
    }

    public void tick(double dt) {
        if (noticeTimer > 0) {
            noticeTimer -= dt;
            if (noticeTimer <= 0) { notice = ""; }
            drawRequested = true;
        }
        if (drawRequested) { render(); drawRequested = false; }
    }

    void render() {
        gc.setFill(Color.web("#0A0A12"));
        gc.fillRect(0, 0, W, H);

        // Cast strip as a dimmed backdrop (the supplied cover art)
        Assets as = Assets.A;
        if (as != null && as.cover != null) {
            double iw = as.cover.getWidth(), ih = as.cover.getHeight();
            double scale = Math.max(W / iw, H / ih);
            double dw = iw * scale, dh = ih * scale;
            gc.setGlobalAlpha(0.30);
            gc.drawImage(as.cover, (W - dw) / 2, (H - dh) / 2, dw, dh);
            gc.setGlobalAlpha(1);
            gc.setFill(Color.rgb(10, 10, 18, 0.55));
            gc.fillRect(0, 0, W, H);
        }

        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 60));
        gc.fillText("FIVE NIGHTS", W/2 - 190, 130);
        gc.fillText("AT FREDDY'S", W/2 - 185, 195);

        gc.setFill(Color.web("#CCCCCC"));
        gc.setFont(Font.font("Arial", 22));
        gc.fillText("Glamrock Edition", W/2 - 90, 238);

        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText("with Monty • Roxanne • Chica • Freddy", W/2 - 140, 265);

        // Menu items
        for (int i = 0; i < ITEMS.length; i++) {
            boolean focused = (i == focusIndex);
            boolean ok = available(i);
            String label = ITEMS[i];
            if (!ok) label += "  [locked]";

            Color col;
            if (!ok) col = focused ? Color.web("#8A6070") : Color.web("#44445A");
            else col = focused ? Color.web("#FFD700") : Color.web("#CCCCCC");

            gc.setFill(col);
            gc.setFont(Font.font("Arial", 19));
            gc.fillText((focused ? "▶ " : "  ") + label, W/2 - 150, 350 + i * 36);
        }

        // Progress summary
        Progress p = FnafGame.progress;
        if (p != null) {
            gc.setFill(Color.web("#555577"));
            gc.setFont(Font.font("Arial", 12));
            int beaten = 0;
            for (int i = 1; i <= Progress.NIGHTS; i++) if (p.beaten[i]) beaten++;
            gc.fillText("Nights beaten: " + beaten + "/5   Characters met: "
                    + metCount() + "/4", W/2 - 140, H - 70);
        }

        if (!notice.isEmpty()) {
            gc.setFill(Color.web("#E94560"));
            gc.setFont(Font.font("Arial", 15));
            gc.fillText(notice, W/2 - 200, H - 40);
        }

        gc.setFill(Color.web("#44445A"));
        gc.setFont(Font.font("Arial", 12));
        gc.fillText("↑↓ select   ENTER confirm", W/2 - 80, H - 16);
    }

    int metCount() {
        Progress p = FnafGame.progress;
        if (p == null) return 0;
        int c = 0;
        for (boolean b : p.met) if (b) c++;
        return c;
    }
}
