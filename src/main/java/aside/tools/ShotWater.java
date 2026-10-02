package aside.tools;

import aside.games.fruitjump.GameplayScreen;
import aside.ui.UiManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * ShotWater - put the water on a screen and count what landed there.
 *
 * The water system arrived upstream with everything except the thing a player sees: generation, physics, breath
 * meter, current, probe - and no renderer. Every line of it could be, and was, exercised headlessly. "Does water
 * appear on screen" is the one question that needs a screen, which is why the release grew its own capture tool
 * and why this one exists here now that the renderer does.
 *
 * It drives the screen directly rather than through a display timer: the host ticks the top screen, so calling
 * tick() here is the same code path a player's frame takes, minus the clock. That also makes it deterministic,
 * which a Robot-driven capture is not.
 *
 * It reports a number as well as an image. A screenshot nobody counts can look fine at a glance - and the water
 * colour is unambiguous against this level's palette (orange sky, brown rock, black bedrock, green grass, red
 * hearts), so a pixel count is a real check rather than a formality. The count that matters is the one at the
 * pool: if the renderer is wired but the rect is scaled wrong, the count still fires, so the image is what says
 * whether the water fills its pit.
 *
 * Run through tools/run-headless.sh, which starts a display if the last reboot took the old one:
 *   tools/run-headless.sh java -cp out aside.tools.ShotWater
 */
public class ShotWater extends Application {

    /** Pixels that are water and not anything else on this level. Water over the dark terrain is the only
     *  blue-dominant thing in the frame: the sky is orange, the rock brown, the grass green, the bedrock
     *  near-black, the HUD red. */
    private static int countWater(javafx.scene.image.Image img) {
        var pr = img.getPixelReader();
        int w = (int) img.getWidth(), h = (int) img.getHeight(), n = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int argb = pr.getArgb(x, y);
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            if (b > r && b > 100 && g > r) n++;
        }
        return n;
    }

    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        int level = Integer.getInteger("level", 15);   // 15 is one of the four levels that generates water
        String dir = System.getProperty("shotdir", "/root/downloads");

        UiManager ui = new UiManager(".");
        GameplayScreen screen = new GameplayScreen(ui, level);
        ui.push(screen);

        StackPane root = new StackPane(ui.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();

        // Hold RIGHT. The spawn is flat ground at column 2 and the pool starts at column 15, so the walk in
        // needs no jump: the climber reaches the lip, walks in, and swims to the far wall.
        //
        // The tick counts are measured, not guessed. The first run walked 165 frames and stopped 85px short of
        // the pool - the frame counts below are what actually puts the climber at the lip, in the water, and
        // under it. A capture tool whose subject is off-screen still writes a png and still prints PASS.
        screen.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false));

        double dt = 1.0 / 60;
        int atLip = 125, inPool = 400, dive = 180;
        for (int i = 0; i < inPool; i++) {
            screen.tick(dt);
            if (i + 1 == atLip) shoot(scene, dir + "/water-lip.png", "at the lip");
        }
        shoot(scene, dir + "/water-pool.png", "in the pool");

        // Dive. This is the half of the water system that only the controls can
        // reach: setVerticalInput is what turns a held DOWN into a stroke, and the
        // air bar only appears once it is actually draining.
        screen.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false));
        for (int i = 0; i < dive; i++) screen.tick(dt);
        shoot(scene, dir + "/water-dive.png", "submerged");
        report(screen, "after the dive");

        // Can the climber get out? The flood pass says a pool is "escapable by
        // construction: the surface sits at the path level and the pool is 64px deep,
        // so the breach hop clears the lip". That is a claim about the whole stack -
        // the stroke, the buoyancy, the breach impulse, the lip height - and nothing
        // had ever tested it end to end. A pool you cannot leave is a soft-lock, and
        // it would look exactly like a pool.
        //
        // Tapping, not holding: the breach impulse fires on a key press and the
        // stroke is the hold, so this is what a player actually does to climb out.
        screen.handleKeyReleased(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.DOWN, false, false, false, false));
        for (int i = 0; i < 420; i++) {
            if (i % 24 == 0) screen.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.UP, false, false, false, false));
            if (i % 24 == 12) screen.handleKeyReleased(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.UP, false, false, false, false));
            screen.tick(dt);
        }
        shoot(scene, dir + "/water-out.png", "out of the pool");
        report(screen, "after tapping UP");

        javafx.application.Platform.exit();
    }

    /** Where the climber is and whether the water still has them. Reflection, so the names cannot go stale. */
    private static void report(GameplayScreen screen, String when) {
        try {
            var f = GameplayScreen.class.getDeclaredField("player");
            f.setAccessible(true);
            Object p = f.get(screen);
            var body = aside.games.fruitjump.engine.Physics.Body.class;
            double x = body.getField("x").getDouble(p), y = body.getField("y").getDouble(p);
            boolean inWater = body.getField("inWater").getBoolean(p);
            double sub = body.getField("submersion").getDouble(p);
            System.out.println("  player " + when + ": x=" + (int) x + " y=" + (int) y
                    + " inWater=" + inWater + " submersion=" + String.format("%.2f", sub));
        } catch (Exception ex) {
            System.out.println("  player " + when + ": could not read (" + ex + ")");
        }
    }

    private static void shoot(Scene scene, String path, String what) {
        try {
            var img = scene.snapshot(null);
            int w = (int) img.getWidth(), h = (int) img.getHeight();
            var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var pr = img.getPixelReader();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
            javax.imageio.ImageIO.write(bi, "png", new java.io.File(path));
            System.out.println("wrote " + path + " (" + what + ") waterPixels=" + countWater(img));
        } catch (Exception ex) {
            System.out.println("FAIL " + path + ": " + ex);
        }
    }

    public static void main(String[] args) { launch(args); }
}
