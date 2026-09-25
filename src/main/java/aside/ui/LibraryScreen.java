package aside.ui;

import aside.game.Game;
import aside.game.Games;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * The library: everything the engine can start.
 *
 * The list is built from Games.all(), so adding a game is one entry in
 * one registry and it appears here with no other change.
 */
public class LibraryScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 52);
    static final Font F_SUB = Font.font("Arial", 15);
    static final Font F_ITEM = Font.font("Georgia", 26);
    static final Font F_BLURB = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);

    final List<Game> games = new ArrayList<>(Games.all());
    int index = 0;
    String notice = "";
    double noticeTimer = 0;

    /** Index == games.size() means the Quit row. */
    int quitRow() { return games.size(); }
    int rows() { return games.size() + 1; }

    public LibraryScreen(UiManager ui) {
        super(ui);
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.UP) index = (index - 1 + rows()) % rows();
        else if (c == KeyCode.DOWN) index = (index + 1) % rows();
        else if (c == KeyCode.ENTER || c == KeyCode.SPACE) select();
        e.consume();
    }

    void select() {
        if (index == quitRow()) { javafx.application.Platform.exit(); return; }
        Game g = games.get(index);
        try {
            ui.replace(g.create(ui));
        } catch (Exception ex) {
            notice = "could not start " + g.title() + ": " + ex.getMessage();
            noticeTimer = 4.0;
        }
    }

    @Override
    public void tick(double dt) {
        if (noticeTimer > 0) noticeTimer -= dt;
        draw();
    }

    void draw() {
        gc.setFill(Color.web("#0A0A12"));
        gc.fillRect(0, 0, W, H);

        var cover = Assets.A == null ? null : Assets.A.background("title");
        if (cover != null) {
            gc.setGlobalAlpha(0.22);
            drawCover(cover);
            gc.setGlobalAlpha(1);
            gc.setFill(Color.rgb(8, 8, 14, 0.55));
            gc.fillRect(0, 0, W, H);
        }

        gc.setFill(Color.web("#F2C14E"));
        gc.setFont(F_TITLE);
        gc.fillText("Aside", 70, 100);
        gc.setFill(Color.web("#7A7A90"));
        gc.setFont(F_SUB);
        gc.fillText("a small engine, and the games built on it", 72, 128);

        double y = 214;
        for (int i = 0; i < rows(); i++) {
            boolean sel = i == index;
            boolean isQuit = i == quitRow();
            String title = isQuit ? "Quit" : games.get(i).title();
            String blurb = isQuit ? "" : games.get(i).blurb();

            if (sel) {
                gc.setFill(Color.rgb(30, 30, 46, 0.85));
                gc.fillRoundRect(60, y - 36, W - 120, 70, 10, 10);
            }
            gc.setFill(sel ? Color.web("#F2C14E") : Color.web("#B9B9C6"));
            gc.setFont(F_ITEM);
            gc.fillText((sel ? ">  " : "   ") + title, 90, y);
            if (!blurb.isEmpty()) {
                gc.setFill(Color.web("#6E6E86"));
                gc.setFont(F_BLURB);
                gc.fillText(blurb, 122, y + 22);
            }
            y += 88;
        }

        if (noticeTimer > 0) {
            gc.setFill(Color.web("#E94560"));
            gc.setFont(F_SUB);
            gc.fillText(notice, 72, H - 60);
        }
        gc.setFill(Color.web("#6E6E86"));
        gc.setFont(F_TINY);
        gc.fillText("up/down select    ENTER start", 72, H - 28);
    }
}