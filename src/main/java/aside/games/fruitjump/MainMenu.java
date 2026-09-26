package aside.games.fruitjump;

import aside.games.fruitjump.engine.SaveSystem;
import aside.ui.LibraryScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Fruit Jump's main menu.
 *
 * Canvas-drawn rather than built from controls, matching the rest of the
 * engine: Region backgrounds do not paint reliably on this machine, and a
 * single drawing surface means one coordinate space for layout and
 * hit-testing.
 */
public class MainMenu extends UiScreen {

    static final String SAVE_FILE =
            System.getProperty("user.home") + "/.tropical-punch-autosave.txt";

    static final Font F_TITLE = Font.font("Arial", 68);
    static final Font F_SUB = Font.font("Arial", 20);
    static final Font F_ITEM = Font.font("Arial", 28);
    static final Font F_TINY = Font.font("Arial", 13);

    final List<String> items = new ArrayList<>();
    int index = 0;
    String notice = "";
    double noticeTimer = 0;

    public MainMenu(UiManager ui) {
        super(ui);
        items.add("New Game");
        if (new File(SAVE_FILE).exists()) items.add("Continue");
        items.add("Rooms Mode");
        items.add("Quit to library");
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.UP) index = (index - 1 + items.size()) % items.size();
        else if (c == KeyCode.DOWN) index = (index + 1) % items.size();
        else if (c == KeyCode.ENTER || c == KeyCode.SPACE) select();
        e.consume();
    }

    void select() {
        switch (items.get(index)) {
            case "New Game" -> ui.replace(new GameplayScreen(ui, 1));
            case "Rooms Mode" -> ui.replace(new RoomsScreen(ui, 1));
            case "Quit to library" -> ui.replace(new LibraryScreen(ui));
            case "Continue" -> continueSave();
            default -> { }
        }
    }

    void continueSave() {
        try {
            SaveSystem.GameState st = new SaveSystem().load(SAVE_FILE);
            int level = st.levelNum > 0 ? st.levelNum : 1;
            // The save records which mode it belongs to. Sending a rooms
            // save into the platformer was a real playtest bug once, so
            // this branches rather than guessing.
            if ("rooms".equals(st.mode)) ui.replace(new RoomsScreen(ui, level, st));
            else ui.replace(new GameplayScreen(ui, level, st));
        } catch (Exception ex) {
            notice = "could not read the save: " + ex.getMessage();
            noticeTimer = 4.0;
        }
    }

    @Override
    public void tick(double dt) {
        if (noticeTimer > 0) noticeTimer -= dt;
        draw();
    }

    void draw() {
        gc.setFill(Color.web("#1a1a2e"));
        gc.fillRect(0, 0, W, H);

        gc.setFill(Color.web("#f5a623"));
        gc.setFont(F_TITLE);
        gc.fillText("TROPICAL PUNCH", 90, 170);

        gc.setFill(Color.web("#8a8aa0"));
        gc.setFont(F_SUB);
        gc.fillText("Fruit Jump  -  a platformer and a room-crawler", 94, 206);

        double y = 300;
        for (int i = 0; i < items.size(); i++) {
            boolean sel = i == index;
            gc.setFill(sel ? Color.web("#f5a623") : Color.web("#c9c9d6"));
            gc.setFont(F_ITEM);
            gc.fillText((sel ? ">  " : "   ") + items.get(i), 130, y);
            y += 52;
        }

        if (noticeTimer > 0) {
            gc.setFill(Color.web("#e94560"));
            gc.setFont(F_SUB);
            gc.fillText(notice, 94, H - 70);
        }
        gc.setFill(Color.web("#6e6e86"));
        gc.setFont(F_TINY);
        gc.fillText("up/down select    ENTER start", 94, H - 34);
    }
}