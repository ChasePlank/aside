package aside.tools;

import aside.engine.Script;
import aside.games.overtime.VnScreen;
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
 * Usage:  DISPLAY=:99 java -cp out aside.tools.ShotVn [story-name]     (default: overtime)
 *
 * Run from the repo root: Assets resolves art/backgrounds and art/sprites relatively.
 *
 * VERIFIED (Sept 28): Monty drawn on the office background, keyed and cropped onto the
 * shared floor line, text box reading "Monty / Ne" - the typewriter caught mid-line by the
 * three-tick capture. So aside.art.SpriteProcess output renders correctly, which until now
 * was only known from its own quality report.
 *
 * Two things it took, both worth keeping:
 *   1. Three ticks, not ninety. One frame draws; ninety of them re-decoded the large sprite
 *      PNGs per tick and spent two minutes hitting the timeout without reaching the snapshot.
 *   2. DISPLAY=:99 on the command. Without it JavaFX dies with "Unable to open DISPLAY", and
 *      a pgrep for Xvfb does not tell you whether the display you are about to use is live.
 */
public class ShotVn extends Application {
    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        aside.ui.UiManager ui = new aside.ui.UiManager(".");
        // The story is an argument now, defaulting to overtime. It used to be hard-coded, and it lived in
        // the overtime game's package - which is why I forgot it existed and nearly wrote it again. A tool
        // for the engine does not belong inside one game.
        String story = getParameters().getRaw().isEmpty() ? "overtime" : getParameters().getRaw().get(0);
        Script script = Script.load(Path.of("stories", story + ".aside"));
        Vn vn = new Vn(script);
        // Where to stop. "sprite" waits for someone to be standing in the scene; "choice" stops the moment the
        // story offers the player a decision, which is the screen a reader spends the most time looking at and
        // the one view of this presenter I had never seen.
        String stopAt = getParameters().getRaw().size() > 1 ? getParameters().getRaw().get(1) : "sprite";
        int steps = 0;
        if ("choice".equals(stopAt)) {
            while (vn.mode != Vn.Mode.CHOOSING && steps++ < 400) vn.advance();
        } else {
            while (vn.shown.isEmpty() && steps++ < 400) {
                if (vn.mode == Vn.Mode.CHOOSING) vn.choose(0);
                else vn.advance();
            }
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
            // Three ticks, not ninety. One frame is enough to draw, and this screen decodes
            // large sprite PNGs per tick - ninety of them is how the first attempt spent two
            // minutes and hit its timeout without ever reaching the snapshot.
            for (int i = 0; i < 3; i++) ui.tick(1.0 / 60);
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
                javax.imageio.ImageIO.write(bi, "png", new java.io.File("/root/downloads/" + story + "-vn.png"));
                System.out.println("PASS wrote " + story + "-vn-" + stopAt + ".png non-black=" + nonBlack);
            } catch (Exception ex) {
                System.out.println("FAIL: " + ex);
            }
            Platform.exit();
            System.exit(0);
        });
        t.play();
    }
}
