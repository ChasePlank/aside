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
        // ShotFruitJump had the same bug and it is worth the same line here: without this every write fails with
        // "Can't create an ImageOutputStream" and the tool reports a failure for a run that worked perfectly.
        new java.io.File(dir).mkdirs();

        UiManager ui = new UiManager(".");
        GameplayScreen screen = new GameplayScreen(ui, level);
        ui.push(screen);

        StackPane root = new StackPane(ui.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();

        // Hold RIGHT and let the climber walk into the pool.
        //
        // EVERY PHASE IS DRIVEN BY THE CLIMBER'S OWN STATE, NOT BY A FRAME COUNT. The first version used
        // measured tick numbers - 125 to the lip, 400 into the pool, 180 to dive - and they were correct
        // until the generator changed, at which point the level reshuffled, the pool moved, and the tool
        // happily wrote a png of a climber standing on dry ground and called it "submerged". A capture tool
        // whose subject is off-screen still writes a file and still prints PASS. Reading the body back out
        // costs a reflection call and cannot go stale.
        screen.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false));

        double dt = 1.0 / 60;
        int splashTick = -1, lastSplashes = 0, splashShootAt = -1;
        boolean jumped = false, shotPool = false, shotLip = false, ended = false;
        for (int i = 0; i < 900; i++) {
            // Jump just before the lip. Walking in produces no splash at all - the
            // surface is level with the walk, so the crossing happens on the first
            // frame of the fall, with vy about 20, and the system requires
            // SPLASH_MIN_V (120) of impact speed. A jump comes down into the pool with
            // speed, which is also what a missed jump looks like, and a missed jump is
            // the case the pool exists for.
            //
            // Triggered on the climber's own x, not on a frame count: the first attempt
            // jumped at frame 115 and the climber was still 190px short of the pool, so
            // it landed on the ground and the splash never happened.
            //
            // Tapped every 30 frames rather than triggered on a coordinate. The first version jumped
            // when the climber passed x=420, which was the pool's lip in one particular level layout -
            // and stopped being that the moment the generator changed. A periodic tap cannot miss: one
            // of the jumps lands in the water with speed, which is what the splash needs and what a
            // missed jump looks like.
            if (!shotPool && i % 30 == 0) {
                jumped = true;
                screen.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false));
                screen.handleKeyReleased(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.SPACE, false, false, false, false));
            }
            screen.tick(dt);
            // STOP WHEN THE LEVEL ENDS. GameplayScreen replaces itself with GameOverScreen the frame the climber
            // falls out of the world. Ticking a screen that is no longer shown still runs the physics one more
            // frame each time, so the climber's y grew to 123063 and this tool printed that as a finding. It was
            // reporting the position of a body in a level that was over.
            if (ui.current() != screen) { ended = true; break; }
            // The lip: the frame the climber stops being on the ground and has not yet reached water.
            // A jump is the only way in that splashes, and the jump is triggered on x, below.
            if (!shotLip && jumped && !inWater(screen)) {
                shotLip = true;
                shoot(scene, dir + "/water-lip.png", "airborne, on the way into the pool");
            }
            // In the pool: the first frame the water has the body at all.
            if (!shotPool && inWater(screen)) {
                shotPool = true;
                shoot(scene, dir + "/water-pool.png", "in the pool");
            }
            // Keep going after the pool is reached, until a crossing has actually been
            // recorded or the budget runs out. Breaking at the first frame in water meant the
            // periodic taps all happened on land, so the entry was a walk-in (vy ~20, under the
            // 120 threshold) and the splash frame was never produced - the tool reported
            // events=0 rather than pretending otherwise, which is the only reason it was visible.
            if (shotPool && lastSplashes > 0) break;
            // The splash is the shortest-lived thing in the system (droplets last
            // 0.25-0.6s), so catching it by picking a frame by hand would be luck.
            // The water system counts crossings; watch the counter and shoot on the
            // frame it moves.
            int now = splashEvents(screen);
            if (now > lastSplashes) {
                lastSplashes = now;
                splashTick = i;
                // Not this frame. Emitters update BEFORE bodies in World.update, so a
                // burst spawned during the body step has not been drawn yet - the
                // crossing frame shows the pool and nothing else. Three frames on, the
                // droplets are out and still inside their 0.25-0.6s lifetime.
                splashShootAt = i + 3;
            }
            if (splashShootAt >= 0 && i == splashShootAt) {
                splashShootAt = -1;
                shoot(scene, dir + "/water-splash.png", "droplets, three frames after the crossing");
            }
        }
        System.out.println("  splash registered on frame " + splashTick
                + " (" + (splashTick / 60.0) + "s in), events=" + lastSplashes);
        System.out.println("  reached water: " + shotPool + ", reached the lip shot: " + shotLip);

        // Dive. This is the half of the water system that only the controls can reach:
        // setVerticalInput is what turns a held DOWN into a stroke, and the air bar only
        // appears once it is actually draining. Ticked until the body is genuinely under,
        // not for a fixed count.
        screen.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false));
        boolean under = false;
        for (int i = 0; i < 400 && !under; i++) {
            screen.tick(dt);
            if (ui.current() != screen) { ended = true; break; }
            if (submersion(screen) >= 0.9) under = true;
        }
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
            if (ui.current() != screen) { ended = true; break; }
        }
        shoot(scene, dir + "/water-out.png", "out of the pool");
        report(screen, "after tapping UP");
        if (ended) {
            System.out.println("THE LEVEL ENDED - the climber fell out of the world. Everything above is the");
            System.out.println("position of a screen that is no longer shown, and the water it was walking to");
            System.out.println("was never reached. This run says nothing about the water system.");
        }

        javafx.application.Platform.exit();
    }

    /** Where the climber is and whether the water still has them. Reflection, so the names cannot go stale. */
    /** Is the water holding the body at all? */
    private static boolean inWater(GameplayScreen screen) {
        try {
            var f = GameplayScreen.class.getDeclaredField("player");
            f.setAccessible(true);
            return aside.games.fruitjump.engine.Physics.Body.class.getField("inWater").getBoolean(f.get(screen));
        } catch (Exception ex) { return false; }
    }

    /** How much of the body is under the surface, 0..1. */
    private static double submersion(GameplayScreen screen) {
        try {
            var f = GameplayScreen.class.getDeclaredField("player");
            f.setAccessible(true);
            return aside.games.fruitjump.engine.Physics.Body.class.getField("submersion").getDouble(f.get(screen));
        } catch (Exception ex) { return 0; }
    }

    /** How many surface crossings the water system has reported. */
    private static double playerX(GameplayScreen screen) {
        try {
            var f = GameplayScreen.class.getDeclaredField("player");
            f.setAccessible(true);
            return aside.games.fruitjump.engine.Physics.Body.class.getField("x").getDouble(f.get(screen));
        } catch (Exception ex) {
            return 0;
        }
    }

    private static int splashEvents(GameplayScreen screen) {
        try {
            var wf = GameplayScreen.class.getDeclaredField("world");
            wf.setAccessible(true);
            Object world = wf.get(screen);
            var waterField = world.getClass().getField("water");
            Object water = waterField.get(world);
            return (int) water.getClass().getMethod("splashEvents").invoke(water);
        } catch (Exception ex) {
            return 0;
        }
    }

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
