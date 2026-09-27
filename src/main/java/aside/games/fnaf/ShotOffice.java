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
 *
 * THREE THINGS IT TOOK, and the third is the one worth remembering:
 *   1. Assets.load() first - without the art the renderer draws its backdrop and nothing else.
 *   2. Do not also push the screen onto the UiManager; the root belongs in the scene.
 *   3. **UiManager.tick(dt) IS the game loop.** Constructing a screen and calling enter()
 *      starts a timer but draws nothing: measured 0 non-black pixels in the snapshot
 *      before ticking and 33,469 after 120 ticks. Any future capture or headless harness
 *      for this game has to drive tick(), or it will stare at a black rectangle.
 */
public class ShotOffice extends Application {
    @Override public void start(Stage stage) {
        // Assets.load() first, and ui.push() rather than only putting the root in a
        // scene: without the art the renderer draws its backdrop and nothing else, which
        // is exactly the black rectangle this tool produced the first two times.
        Assets.load();
        aside.ui.UiManager ui = new aside.ui.UiManager(".");
        GameScreen screen = new GameScreen(ui, 1);
        ui.push(screen);
        StackPane root = new StackPane(screen.getRoot());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        // UiScreen lifecycle: enter() is what starts the render timer. Without it the
        // canvas never draws and the snapshot is a black rectangle - which is how this
        // tool failed the first time.
        screen.enter();
        var t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(2000));
        t.setOnFinished(e -> {
            System.out.println("DIAG assets=" + (Assets.A != null)
                    + " rootChildren=" + root.getChildren().size()
                    + " rootVisible=" + root.isVisible()
                    + " screenRoot=" + screen.getRoot().getClass().getSimpleName());
            WritableImage img = scene.snapshot(null);
            int nonBlack = 0;
            var px = img.getPixelReader();
            for (int y = 0; y < (int) img.getHeight(); y += 4)
                for (int x = 0; x < (int) img.getWidth(); x += 4) {
                    int argb = px.getArgb(x, y);
                    if ((argb & 0xFFFFFF) != 0) nonBlack++;
                }
            System.out.println("DIAG non-black sampled pixels after 0 ticks: " + nonBlack);
            // The app drives the game through UiManager.tick - that is the game loop.
            // Constructing a screen and calling enter() is not enough: nothing ticks it.
            for (int i = 0; i < 120; i++) ui.tick(1.0 / 60);
            img = scene.snapshot(null);
            px = img.getPixelReader();
            nonBlack = 0;
            for (int y = 0; y < (int) img.getHeight(); y += 4)
                for (int x = 0; x < (int) img.getWidth(); x += 4)
                    if ((px.getArgb(x, y) & 0xFFFFFF) != 0) nonBlack++;
            System.out.println("DIAG non-black sampled pixels after 120 ticks: " + nonBlack);
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
