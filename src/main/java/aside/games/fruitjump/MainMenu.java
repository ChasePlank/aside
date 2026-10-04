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

    /**
     * THE SUNSET. "A climber, a sunset, and a way home" is the game's own sentence, and this screen had none of
     * it - flat dark blue with text on it, in colours (#1a1a2e, #8a8aa0) that appear nowhere else in the game.
     *
     * <p>So the backdrop is the premise, drawn in the game's own palette: a sky from the platformer's deep water
     * blue up to the gold the title is already written in, a sun going down behind a ridge, and the climber
     * standing on the right-hand rock. The sprite is the real one, built from Sprite.PLAYER, so the character on
     * the title screen is the character you play.
     *
     * <p>The left end is deliberately darkened - a horizontal fade - because the title and the menu live there
     * and a bright sky would swallow them. The art is on the right and the words are on the left, which is also
     * how the screen was already laid out.
     */
    void drawSunset() {
        // Sky: dark blue at the top, warming down to the horizon.
        int horizon = 468;
        Color[] sky = {Color.web("#0d1526"), Color.web("#1d3b5c"), Color.web("#7a5a54"), Color.web("#c9723c"),
                       Color.web("#f5a623")};
        for (int y = 0; y < horizon; y++) {
            double t = (double) y / horizon * (sky.length - 1);
            int i = Math.min((int) t, sky.length - 2);
            gc.setFill(sky[i].interpolate(sky[i + 1], t - i));
            gc.fillRect(0, y, W, 1);
        }

        // The sun, sitting on the ridge.
        double sunX = 905, sunY = horizon - 26, r = 62;
        gc.setFill(Color.web("#f5a623", 0.20));
        gc.fillOval(sunX - r * 1.9, sunY - r * 1.9, r * 3.8, r * 3.8);
        gc.setFill(Color.web("#f5c46a", 0.35));
        gc.fillOval(sunX - r * 1.35, sunY - r * 1.35, r * 2.7, r * 2.7);
        gc.setFill(Color.web("#ffe0a3"));
        gc.fillOval(sunX - r, sunY - r, r * 2, r * 2);

        // A ridge, and the rock the climber stands on. Silhouettes, so the sky does the work.
        gc.setFill(Color.web("#2a1c16"));
        gc.fillPolygon(new double[]{0, 210, 430, 700, 980, 1280, 1280, 0},
                       new double[]{horizon - 40, horizon - 96, horizon - 30, horizon - 74, horizon - 20,
                                    horizon - 58, horizon, horizon}, 8);

        // Foreground: the ground band, and one block to stand on.
        gc.setFill(Color.web("#16100c"));
        gc.fillRect(0, horizon, W, H - horizon);
        gc.setFill(Color.web("#0d0a09"));
        gc.fillRect(0, horizon + 26, W, H - horizon - 26);
        gc.setFill(Color.web("#2a1c16"));
        gc.fillRect(930, horizon - 84, 150, 90);

        // The climber, on top of it. The real sprite, built from the same grid the game plays.
        var img = Sprite.buildScaled(Sprite.PLAYER, Sprite.PAL(), Sprite.PLAYER[0].length() * 4,
                                     Sprite.PLAYER.length * 4);
        gc.drawImage(img, 972, horizon - 84 - Sprite.PLAYER.length * 4);

        // And the fade that keeps the words readable over it.
        for (int x = 0; x < 720; x++) {
            double a = 0.86 * Math.pow(1.0 - (double) x / 720, 1.6);
            gc.setFill(Color.web("#0d0a09", a));
            gc.fillRect(x, 0, 1, H);
        }
    }

    void draw() {
        drawSunset();

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
