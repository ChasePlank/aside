package aside.tools;

import aside.ui.LibraryScreen;
import aside.ui.UiManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * ShotLibrary - capture the library screen, the front door of the engine.
 *
 * Every other view of this engine has been captured and looked at: the text box, a sprite, the choice list, the
 * history overlay, the pause menu. The screen that opens the whole thing had not been, and it is the first thing
 * anyone sees.
 *
 * Run it through tools/run-headless.sh, which starts a display if the last reboot took the old one.
 */
public class ShotLibrary extends Application {
    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        UiManager ui = new UiManager(".");
        LibraryScreen screen = new LibraryScreen(ui);
        ui.push(screen);
        StackPane root = new StackPane(screen.getRoot());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        screen.enter();

        var t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
        t.setOnFinished(e -> {
            try {
                // Three ticks, not none. A UiScreen draws on tick, so a snapshot taken straight after enter()
                // catches an empty stage - which is exactly what the first attempt produced: non-black=0.
                // ShotVn learned this the same way and its docstring says so; this tool did not read it.
                for (int i = 0; i < 3; i++) screen.tick(1.0 / 60);
                var img = scene.snapshot(null);
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var pr = img.getPixelReader();
                int nonBlack = 0;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int argb = pr.getArgb(x, y);
                    bi.setRGB(x, y, argb);
                    if ((argb & 0x00FFFFFF) != 0) nonBlack++;
                }
                javax.imageio.ImageIO.write(bi, "png", new java.io.File("/root/downloads/library.png"));
                System.out.println("PASS wrote library.png non-black=" + nonBlack);
            } catch (Exception ex) {
                System.out.println("FAIL " + ex);
            }
            javafx.application.Platform.exit();
        });
        t.play();
    }
    public static void main(String[] args) { launch(args); }
}
