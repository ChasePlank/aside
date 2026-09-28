package aside.games.fnaf;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * ShotScare - capture a jumpscare.
 *
 * The payoff of the whole game, and the one screen with fiddly art handling behind it:
 * the jumpscare images are photographs with backgrounds, and the comment in the draw code
 * says two of them "cannot be keyed out", so a crop plus a vignette does the work instead.
 * A crop that misses is the difference between a face filling the screen and a photo of a
 * room - and nothing but a frame can tell.
 *
 * Deterministic because the seed is now forceable: -Dfnaf.seed=42 dies at hour 4.
 */
public class ShotScare extends Application {
    @Override public void start(Stage stage) {
        int night = Integer.getInteger("night", 1);
        Assets.load();
        aside.ui.UiManager ui = new aside.ui.UiManager(".");
        GameScreen screen = new GameScreen(ui, night);
        ui.push(screen);
        StackPane root = new StackPane(screen.getRoot());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        screen.enter();

        var t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
        t.setOnFinished(e -> {
            // Play until it goes wrong, then a few more ticks so the scare is on screen.
            int ticks = 0;
            for (; ticks < 60 * 60 * 6; ticks++) {
                ui.tick(1.0 / 60);
                if (screen.game.status == aside.games.fnaf.engine.Game.Status.JUMPSCARED) break;
            }
            for (int i = 0; i < 30; i++) ui.tick(1.0 / 60);
            System.out.println("DIAG status=" + screen.game.status + " after " + ticks
                    + " ticks (" + String.format("%.1f", ticks / 60.0) + "s), killer="
                    + (screen.game.jumpscareBy != null ? screen.game.jumpscareBy.name : "?"));

            WritableImage img = scene.snapshot(null);
            try {
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var pr = img.getPixelReader();
                int nonBlack = 0;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int argb = pr.getArgb(x, y);
                    bi.setRGB(x, y, argb);
                    if ((argb & 0xFFFFFF) != 0) nonBlack++;
                }
                javax.imageio.ImageIO.write(bi, "png", new java.io.File(
                        System.getProperty("shot", "/root/downloads/fnaf-scare.png")));
                System.out.println("PASS: wrote " + System.getProperty("shot", "/root/downloads/fnaf-scare.png")
                        + " (" + w + "x" + h + ", " + nonBlack + " non-black pixels)");
            } catch (Exception ex) {
                System.out.println("FAIL: " + ex);
            }
            Platform.exit();
            System.exit(0);
        });
        t.play();
    }
}
