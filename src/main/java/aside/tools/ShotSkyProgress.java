package aside.tools;

import aside.games.fruitjump.GameplayScreen;
import aside.ui.UiManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The run's sky at several levels, captured so the change can be looked at rather than argued about.
 *
 * <p>The sun sinks and the sky deepens as a run goes on (see {@code LEVELS_TO_DUSK} in GameplayScreen), and that
 * is a claim about what the game LOOKS like - which is the class of claim this project keeps finding made in
 * comments and never checked. One frame per level, taken from the same starting state, so the difference between
 * the images is the difference the level number makes.
 *
 *   java ... aside.tools.ShotSkyProgress   (shotdir=..., levels=1,20,40)
 */
public class ShotSkyProgress extends Application {

    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        String dir = System.getProperty("shotdir", "/root/downloads");
        new java.io.File(dir).mkdirs();
        String[] levels = System.getProperty("levels", "1,20,40").split(",");

        int[] done = {0};
        for (String spec : levels) {
            int level = Integer.parseInt(spec.trim());
            UiManager ui = new UiManager(".");
            // false = generated, which is the run this is about; the tutorial keeps the canonical sunset.
            GameplayScreen screen = new GameplayScreen(ui, level, false);
            ui.push(screen);
            Scene scene = new Scene(new StackPane(ui.getContainer()), 1600, 1200, Color.BLACK);
            stage.setScene(scene);
            stage.show();
            for (int t = 0; t < 8; t++) ui.tick(1.0 / 60);   // let it settle, then take the frame
            var img = scene.snapshot(null);
            String name = "sky-level-" + level + ".png";
            javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                    "png", new java.io.File(dir, name));
            System.out.println("wrote " + dir + "/" + name);
            done[0]++;
        }
        System.out.println("captured " + done[0] + " level(s)");
        Platform.exit();
    }

    public static void main(String[] args) { launch(args); }
}
