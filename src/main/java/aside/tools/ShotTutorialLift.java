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
 * The moving-platform lesson, captured.
 *
 * <p>Level 10 teaches the one thing nine levels did not: that the ground can move. This looks at it rather than
 * assuming it drew, which is the rule that found the invisible bats, the invisible splash and the snake that was a
 * second spider.
 *
 * <p>THE TICKS ARE CHOSEN FROM THE PERIOD, not guessed. The lift's cycle is 6s = 360 engine ticks, so its surface
 * is at the LEDGE (352) at tick 90 + k*360 and at the WALK (544) at tick 270 + k*360. The captures below land on
 * 990 (bottom, flush with the walk) and 1170 (top, flush with the ledge) for that reason, with a mid-travel frame
 * either side.
 *
 *   java ... aside.tools.ShotTutorialLift   (shotdir=... to choose the directory)
 */
public class ShotTutorialLift extends Application {
    private static int captured = 0;

    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        String dir = System.getProperty("shotdir", "/root/downloads");
        new java.io.File(dir).mkdirs();

        UiManager ui = new UiManager(".");
        GameplayScreen screen = new GameplayScreen(ui, 10, true);   // true = tutorial
        ui.push(screen);
        StackPane root = new StackPane(ui.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();

        // Hold RIGHT so the lift is actually on screen: it sits at x 1184 and the player spawns at x 64, and a
        // platform the camera never reaches is a platform this tool cannot report on.
        new javafx.animation.AnimationTimer() {
            int ticks = 0;
            @Override public void handle(long now) {
                UiScreen top = ui.peek();
                for (int i = 0; i < 3; i++) {
                    if (top != null) {
                        top.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT,
                                false, false, false, false));
                    }
                    ui.tick(1.0 / 60);
                    ticks++;
                }
                if (ticks == 990 || ticks == 1080 || ticks == 1170 || ticks == 1260) {
                    try {
                        var img = scene.snapshot(null);
                        String name = "tutorial-10-lift-" + ticks + ".png";
                        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                                "png", new java.io.File(dir, name));
                        System.out.println("wrote " + dir + "/" + name);
                        captured++;
                    } catch (Exception e) {
                        System.out.println("FAIL " + e);
                    }
                }
                if (ticks >= 1300) {
                    System.out.println("captured " + captured + " frame(s)");
                    Platform.exit();
                }
            }
        }.start();
    }

    public static void main(String[] args) { launch(args); }
}
