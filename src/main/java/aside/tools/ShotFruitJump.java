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

        javafx.application.Platform.exit();
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
