package aside.tools;

import aside.games.fruitjump.MainMenu;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * ShotFruitJump - the two screens of the platformer that have never been looked at.
 *
 * Every other view of this game has been captured and looked at: the levels, the game-over card, the rooms
 * mode, the water, the tutorial. The screen that opens it had not, and neither had the one where you pick
 * the climber. Both are drawn entirely by hand on a Canvas - no layout engine checks them, and the engine's
 * own test suite cannot see a coordinate - so "it compiles" is the whole of what is known about them.
 *
 * It drives the screens directly rather than through a display timer, the same way ShotWater and ShotLibrary
 * do: the host ticks the top screen, so ticking here is the player's frame path minus the clock.
 *
 * Run through tools/run-headless.sh:
 *   tools/run-headless.sh java -cp out aside.tools.ShotFruitJump
 */
public class ShotFruitJump extends Application {

    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        String dir = System.getProperty("shotdir", "/root/downloads");
        // The first run of this failed every write with "Can't create an ImageOutputStream" because the directory
        // did not exist. The tool reported each failure, which is why it took one run to find rather than a
        // silent set of empty pngs.
        new java.io.File(dir).mkdirs();

        UiManager ui = new UiManager(".");
        MainMenu menu = new MainMenu(ui);
        ui.push(menu);

        StackPane root = new StackPane(ui.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();

        double dt = 1.0 / 60;
        for (int i = 0; i < 4; i++) ui.tick(dt);
        shoot(scene, dir + "/fj-menu.png", "main menu");

        // Walk down to "The Climber" and open it. The label is read from the menu
        // itself rather than counted: the item list is built at construction and grows
        // a "Continue" entry when an autosave exists, so a fixed number of presses is
        // wrong on any machine that has played before. The first version pressed a
        // fixed eight times and reported landing on the climber screen from a
        // five-item menu - which is not a thing that can happen, and was the tool
        // lying, not the menu.
        int presses = 0;
        while (presses < 12 && !"The Climber".equals(selected(menu))) {
            key(ui, KeyCode.DOWN);
            presses++;
        }
        System.out.println("  items: " + items(menu));
        System.out.println("  presses to select The Climber: " + presses
                + "   selected: " + selected(menu));
        key(ui, KeyCode.ENTER);
        for (int i = 0; i < 4; i++) ui.tick(dt);
        System.out.println("  top screen now: " + topName(ui));
        shoot(scene, dir + "/fj-climber.png", "the climber / customise screen");
        key(ui, KeyCode.ESCAPE);
        for (int i = 0; i < 4; i++) ui.tick(dt);

        // AND THE THREE THE DOCSTRING ABOVE SAYS HAVE ALREADY BEEN LOOKED AT.
        //
        // It claims "the levels, the game-over card, the rooms mode, the water, the tutorial" have all been
        // captured. Those files are gone - the sandbox reboots and /root/downloads with it - so the claim is
        // unverifiable, and a claim in a docstring is not evidence. The three that can be reached from the menu
        // are captured here, in the same run, so the next person has the pictures rather than the sentence.
        for (String[] target : new String[][]{
                {"Rooms Mode", "fj-rooms.png", "the rooms mode"},
                {"Tutorial", "fj-tutorial.png", "tutorial level 1"},
                {"New Game", "fj-level.png", "a generated level"}}) {
            open(ui, target[0]);
            for (int i = 0; i < 30; i++) ui.tick(dt);       // let it settle and draw a few frames
            System.out.println("  " + target[0] + " -> " + topName(ui));
            shoot(scene, dir + "/" + target[1], target[2]);
            key(ui, KeyCode.ESCAPE);
            for (int i = 0; i < 4; i++) ui.tick(dt);
        }

        javafx.application.Platform.exit();
    }

    /**
     * Put a fresh menu on top and walk it to a label, then press ENTER.
     *
     * <p>Not by backing out of whatever is open. ESC inside a game opens the PAUSE screen rather than leaving, so
     * "press ESC until the menu is on top" lands on a pause menu and then walks THAT - which is how the first
     * version reported "no menu on the stack for Tutorial" and then "Tutorial -> PauseScreen". This tool is for
     * capturing views, not for testing the back-path, so it starts from a menu rather than negotiating one.
     */
    private static void open(UiManager ui, String label) {
        ui.replace(new MainMenu(ui));
        MainMenu menu = (MainMenu) ui.peek();
        int presses = 0;
        while (presses < 12 && !label.equals(selected(menu))) { key(ui, KeyCode.DOWN); presses++; }
        key(ui, KeyCode.ENTER);
    }

    /** The menu's own item list and selection, by reflection, so the names cannot go stale. */
    @SuppressWarnings("unchecked")
    private static java.util.List<String> items(MainMenu menu) {
        try {
            var f = MainMenu.class.getDeclaredField("items");
            f.setAccessible(true);
            return (java.util.List<String>) f.get(menu);
        } catch (Exception ex) { return java.util.List.of("<unreadable: " + ex + ">"); }
    }

    private static String selected(MainMenu menu) {
        try {
            var fi = MainMenu.class.getDeclaredField("index");
            fi.setAccessible(true);
            int i = fi.getInt(menu);
            var list = items(menu);
            return i >= 0 && i < list.size() ? list.get(i) : "<index " + i + " out of range>";
        } catch (Exception ex) { return "<unreadable: " + ex + ">"; }
    }

    private static void key(UiManager ui, KeyCode code) {
        UiScreen top = ui.peek();
        if (top != null) {
            top.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
        }
    }

    private static String topName(UiManager ui) {
        UiScreen top = ui.peek();
        return top == null ? "none" : top.getClass().getSimpleName();
    }

    private static void shoot(Scene scene, String path, String what) {
        try {
            var img = scene.snapshot(null);
            int w = (int) img.getWidth(), h = (int) img.getHeight();
            var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var pr = img.getPixelReader();
            int nonBlack = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int argb = pr.getArgb(x, y);
                bi.setRGB(x, y, argb);
                if ((argb & 0x00FFFFFF) != 0) nonBlack++;
            }
            javax.imageio.ImageIO.write(bi, "png", new java.io.File(path));
            System.out.println("wrote " + path + " (" + what + ") nonBlack=" + nonBlack
                    + " of " + (w * h));
        } catch (Exception ex) {
            System.out.println("FAIL " + path + ": " + ex);
        }
    }

    public static void main(String[] args) { launch(args); }
}
