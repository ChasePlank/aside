package aside.tools;

import aside.games.fruitjump.GameplayScreen;
import aside.ui.UiManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The water lesson, captured.
 *
 * <p>The tutorial teaches the spider, the bomb, the arrow, the bat, the pickups and the spikes by hand, and level
 * 9 teaches water and piranhas the same way. This is how it was looked at rather than assumed - the same rule
 * that found the bats and the splash particles invisible.
 *
 *   java ... aside.tools.ShotTutorialWater   (SHOTDIR=... to choose the directory)
 */
public class ShotTutorialWater extends Application {
    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        String dir = System.getProperty("shotdir", "/root/downloads");
        new java.io.File(dir).mkdirs();

        UiManager ui = new UiManager(".");
        GameplayScreen screen = new GameplayScreen(ui, 9, true);   // true = tutorial
        ui.push(screen);
        StackPane root = new StackPane(ui.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();

        // Walk right for a while so the water and the piranhas are on screen, then shoot.
        new javafx.animation.AnimationTimer() {
            int t = 0;
            @Override public void handle(long now) {
                t++;
                for (int i = 0; i < 3; i++) ui.tick(1.0 / 60);
                if (t == 240) {
                    javafx.scene.image.WritableImage img = scene.snapshot(null);
                    try {
                        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                                "png", new java.io.File(dir, "tutorial-9-water.png"));
                        System.out.println("wrote " + dir + "/tutorial-9-water.png");
                    } catch (Exception e) {
                        System.out.println("FAIL " + e);
                    }
                    Platform.exit();
                }
            }
        }.start();
    }
    public static void main(String[] args) { launch(args); }
}
