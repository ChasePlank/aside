package aside.tools;

import aside.games.fruitjump.VictoryScreen;
import aside.ui.UiManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The end of a run, captured.
 *
 * <p>The ending is a claim about what the game LOOKS like - "the same horizon as the game over screen, with the
 * climber standing on the rock" - and that is the class of claim this project keeps finding made in comments and
 * never checked. One frame, so the likeness can be compared with the game-over screen's rather than asserted.
 *
 *   java ... aside.tools.ShotVictory   (shotdir=..., levels=..., time=...)
 */
public class ShotVictory extends Application {

    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        String dir = System.getProperty("shotdir", "/root/downloads");
        new java.io.File(dir).mkdirs();
        int levels = Integer.parseInt(System.getProperty("levels", "40"));
        double time = Double.parseDouble(System.getProperty("time", "1284.5"));

        UiManager ui = new UiManager(".");
        ui.push(new VictoryScreen(ui, levels, time));
        Scene scene = new Scene(new StackPane(ui.getContainer()), 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        for (int t = 0; t < 8; t++) ui.tick(1.0 / 60);

        var img = scene.snapshot(null);
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                "png", new java.io.File(dir, "victory.png"));
        System.out.println("wrote " + dir + "/victory.png");
        Platform.exit();
    }

    public static void main(String[] args) { launch(args); }
}
