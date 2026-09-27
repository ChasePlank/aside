package aside.games.fnaf;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.canvas.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * ShotOffice - capture the office, to look at the things that only a picture can check.
 *
 * Written for two changes that were made from code and could not otherwise be seen: the
 * light/door buttons being swapped so LIGHT sits above DOOR (matching Q/E above A/D on a
 * keyboard), and the seed label that makes a playtest report replayable. Same reasoning
 * as the Fruit-Jump capture tools: rendering bugs are invisible to a test and obvious in
 * a frame.
 */
public class ShotOffice extends Application {
    @Override public void start(Stage stage) {
        aside.ui.UiManager ui = new aside.ui.UiManager(".");
        GameScreen screen = new GameScreen(ui, 1);
        StackPane root = new StackPane(screen.getRoot());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        // UiScreen lifecycle: enter() is what starts the render timer. Without it the
        // canvas never draws and the snapshot is a black rectangle - which is how this
        // tool failed the first time.
        screen.enter();
        var t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1200));
        t.setOnFinished(e -> {
            WritableImage img = scene.snapshot(null);
            try {
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var pr = img.getPixelReader();
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
                javax.imageio.ImageIO.write(bi, "png", new java.io.File("/root/downloads/fnaf-office.png"));
                System.out.println("PASS: wrote /root/downloads/fnaf-office.png (" + w + "x" + h + ")");
            } catch (Exception ex) {
                System.out.println("FAIL: " + ex);
            }
            Platform.exit();
            System.exit(0);
        });
        t.play();
    }
}
