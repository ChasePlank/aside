package aside.games.overtime;

import aside.engine.Script;
import aside.engine.Vn;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.nio.file.Path;

/**
 * ShotVn - capture the visual novel mid-scene, with sprites on screen.
 *
 * The sprite pipeline is mine (aside.art.SpriteProcess, 16 poses through art/raw and
 * art/sprites) and it had only ever been verified by its own quality report. Rendering is
 * the other half: a bad crop or a wrong floor line is invisible in a file listing and
 * obvious in a frame - which is how the water drawn under the sky and the jumpscare border
 * were both found. So: advance the story to a beat that actually shows someone, then draw.
 *
 * Run from the repo root: Assets resolves art/backgrounds and art/sprites relatively.
 */
public class ShotVn extends Application {
    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        aside.ui.UiManager ui = new aside.ui.UiManager(".");
        Script script = Script.load(Path.of("stories", "overtime.aside"));
        Vn vn = new Vn(script);
        // Walk to the first beat with someone standing in it.
        int steps = 0;
        while (vn.shown.isEmpty() && steps++ < 400) {
            if (vn.mode == Vn.Mode.CHOOSING) vn.choose(0);
            else vn.advance();
        }
        System.out.println("DIAG reached a beat after " + steps + " steps: shown=" + vn.shown
                + " speaker=" + vn.speaker() + " mode=" + vn.mode);
        VnScreen screen = new VnScreen(ui, vn, script.title);
        ui.push(screen);
        StackPane root = new StackPane(screen.getRoot());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        screen.enter();

        var t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
        t.setOnFinished(e -> {
            for (int i = 0; i < 90; i++) ui.tick(1.0 / 60);
            WritableImage img = scene.snapshot(null);
            try {
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var pr = img.getPixelReader();
                int nonBlack = 0;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int a = pr.getArgb(x, y);
                    bi.setRGB(x, y, a);
                    if ((a & 0xFFFFFF) != 0) nonBlack++;
                }
                javax.imageio.ImageIO.write(bi, "png", new java.io.File("/root/downloads/overtime-vn.png"));
                System.out.println("PASS wrote overtime-vn.png non-black=" + nonBlack);
            } catch (Exception ex) {
                System.out.println("FAIL: " + ex);
            }
            Platform.exit();
            System.exit(0);
        });
        t.play();
    }
}
