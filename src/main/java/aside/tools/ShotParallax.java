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
 * The two background ridges, captured twice, so their scroll rates can be MEASURED rather than asserted.
 *
 * <p>The engine side of this is arithmetic and can be checked anywhere, but arithmetic that is right and a draw
 * call that ignores it look identical from the engine. What this catches is the whole path: that the layers reach
 * the screen at all, and that the far ridge really does move at half the near one's rate.
 *
 * <p>Two frames, 180 ticks apart, while the player walks right so the camera actually travels. The ridges occupy
 * separate vertical bands (the far ridge 744..864, the near one 876..960 on a 1200px canvas) and do not overlap,
 * so a row inside each band sees exactly one ridge and its leading edge can be found by colour.
 *
 *   java ... aside.tools.ShotParallax   (shotdir=... to choose the directory)
 */
public class ShotParallax extends Application {
    private static final int FIRST = 120, SECOND = 300;

    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        String dir = System.getProperty("shotdir", "/root/downloads");
        new java.io.File(dir).mkdirs();

        UiManager ui = new UiManager(".");
        GameplayScreen screen = new GameplayScreen(ui, 1, true);   // level 1: flat walk, the camera just moves
        ui.push(screen);
        StackPane root = new StackPane(ui.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();

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
                if (ticks == FIRST || ticks == SECOND) {
                    try {
                        var img = scene.snapshot(null);
                        String name = "parallax-" + ticks + ".png";
                        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                                "png", new java.io.File(dir, name));
                        System.out.println("wrote " + dir + "/" + name);
                    } catch (Exception e) {
                        System.out.println("FAIL " + e);
                    }
                }
                if (ticks > SECOND + 30) { System.out.println("done"); Platform.exit(); }
            }
        }.start();
    }

    public static void main(String[] args) { launch(args); }
}
