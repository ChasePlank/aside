package aside.tools;

import aside.games.fruitjump.GameplayScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The boss lesson, captured.
 *
 * <p>Level 10 teaches the one thing nine levels did not: that the ground can move. This looks at it rather than
 * assuming it drew, which is the rule that found the invisible bats, the invisible splash and the snake that was a
 * second spider.
 *
 * <p>THE CAPTURES ARE BOUNDED TO THE FIGHT, and that is because this tool LOSES it. It walks for five seconds and
 * then stops, the boss closes and kills a player who never fires back, and the tutorial's last level sends them
 * back to the menu - so frames taken after that are the menu, which is a picture of nothing. The window below is
 * the approach and the first exchange; the earlier version ran on to 900 ticks and captured three menu frames.
 *
 * <p>The rim says whether the boss is dangerous and the mark says whether damage counts, so the frames are close
 * together rather than spread out: a state that lasts under a second is missed by a slow sweep.
 *
 *   java ... aside.tools.ShotTutorialBoss   (shotdir=... to choose the directory)
 */
public class ShotTutorialBoss extends Application {
    private static int captured = 0;

    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        String dir = System.getProperty("shotdir", "/root/downloads");
        new java.io.File(dir).mkdirs();

        UiManager ui = new UiManager(".");
        GameplayScreen screen = new GameplayScreen(ui, 11, true);   // true = tutorial
        ui.push(screen);
        StackPane root = new StackPane(ui.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        System.out.println("DEBUG top screen after push: " + ui.peek().getClass().getName());

        // Hold RIGHT so the boss is on screen: it stands at x 960..1024 and the player spawns at x 64, and a
        // boss the camera never reaches is a boss this tool cannot report on.
        new javafx.animation.AnimationTimer() {
            int ticks = 0;
            @Override public void handle(long now) {
                UiScreen top = ui.peek();
                for (int i = 0; i < 3; i++) {
                    // WALK, THEN STOP AND LET IT COME. Holding RIGHT the whole way walks past the boss and out
                    // of the level through the exit - which is what the first version of this tool did, and it
                    // captured a boss that was off the right-hand edge of the canvas. A player who stops is what
                    // makes the boss close the distance, and that is the frame worth having.
                    if (top != null && ticks < 300) {
                        top.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT,
                                false, false, false, false));
                    }
                    ui.tick(1.0 / 60);
                    ticks++;
                }
                if (ticks % 45 == 0 && ticks >= 300 && ticks <= 465) {
                    try {
                        var img = scene.snapshot(null);
                        String name = "tutorial-11-boss-" + ticks + ".png";
                        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                                "png", new java.io.File(dir, name));
                        System.out.println("wrote " + dir + "/" + name);
                        captured++;
                    } catch (Exception e) {
                        System.out.println("FAIL " + e);
                    }
                }
                if (ticks >= 480) {
                    System.out.println("captured " + captured + " frame(s)");
                    Platform.exit();
                }
            }
        }.start();
    }

    public static void main(String[] args) { launch(args); }
}
