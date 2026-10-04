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

    /**
     * THE TITLE ON THE TITLE SCREEN. It is NOT the only place the name lives - the library shelf has its own
     * in FruitJumpGame.title(), and this comment claimed "and nowhere else" for a day, which was not true.
     *
     * It said "TROPICAL PUNCH" - the wrong name, belonging to a different and as-yet-unbuilt game. Then it was
     * "Fruit Jump", a working title with a note from Kinger (Sept 28) that the game "is its own thing and still
     * needs a real name", and that the name was mine to give rather than his.
     *
     * HOLDFAST, chosen 2026-10-04. A holdfast is the root-like base a seaweed grips rock with, and "hold fast"
     * is what a climber does - which is the whole game: a climber, a hookshot, and water that keeps arriving.
     * The word is the two halves of this thing in one, and it is the kind of title a platformer can carry
     * without explaining itself.
     *
     * The old note said renaming the title screen is this one constant, and that is still true - if this is
     * wrong, it is wrong in one place.
     */
    static final String TITLE = "HOLDFAST";
    // The subtitle is the game's own sentence, and it was already written - in the library blurb, which said
    // "A climber, a sunset, and a way home. Still needs a real name." I nearly invented a second premise
    // ("a climber, a hookshot, and rising water") before reading it. The existing one is better and it is the
    // voice this game has, so the title screen borrows it rather than competing with it.
    static final String SUBTITLE = "a climber, a sunset, and a way home";

    final List<String> items = new ArrayList<>();
    int index = 0;
    String notice = "";
    double noticeTimer = 0;

    public MainMenu(UiManager ui) {
        super(ui);
        items.add("New Game");
        if (new File(SAVE_FILE).exists()) items.add("Continue");
        items.add("Rooms Mode");
        items.add("Tutorial");
        items.add("The Climber");
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
            case "Tutorial" -> ui.replace(new GameplayScreen(ui, 1, true));
            case "The Climber" ->
                    ui.replace(new CustomizeScreen(ui, CharacterConfig.load(), this));
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
        gc.fillText(TITLE, 90, 170);

        gc.setFill(Color.web("#8a8aa0"));
        gc.setFont(F_SUB);
        gc.fillText(SUBTITLE, 94, 206);

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
