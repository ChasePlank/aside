package aside.tools;

import aside.games.fruitjump.GameplayScreen;
import aside.ui.UiManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * An enemy dying, frame by frame.
 *
 * <p>Enemies used to vanish on the frame they were killed while the engine counted a fade nothing drew. This drives
 * the one spider in tutorial 2 into the player's feet and captures every other tick around the kill, so the fade
 * can be MEASURED rather than asserted - the question being whether the enemy is still on screen after it is dead,
 * and whether it goes.
 *
 *   java ... aside.tools.ShotEnemyDeath   (shotdir=..., from=..., to=...)
 */
public class ShotEnemyDeath extends Application {

    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        String dir = System.getProperty("shotdir", "/root/downloads");
        new java.io.File(dir).mkdirs();
        int from = Integer.parseInt(System.getProperty("from", "240"));
        int to = Integer.parseInt(System.getProperty("to", "330"));

        UiManager ui = new UiManager(".");
        // Tutorial 2 is one spider and nothing else, which is why it is the level this tool uses.
        ui.push(new GameplayScreen(ui, 2, true));
        Scene scene = new Scene(new StackPane(ui.getContainer()), 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();

        new javafx.animation.AnimationTimer() {
            int ticks = 0;
            @Override public void handle(long now) {
                for (int i = 0; i < 3; i++) {
                    var top = ui.peek();
                    if (top != null) {
                        // Right, and jumping: a spider has to be LANDED on, not walked into.
                        top.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false));
                        if (ticks % 30 < 6) {
                            top.handleKey(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.SPACE, false, false, false, false));
                        } else {
                            top.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false));
                        }
                    }
                    ui.tick(1.0 / 60);
                    ticks++;
                    if (ticks >= from && ticks <= to && ticks % 2 == 0) {
                        try {
                            var img = scene.snapshot(null);
                            javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                                    "png", new java.io.File(dir, "death-" + ticks + ".png"));
                        } catch (Exception e) { System.out.println("FAIL " + e); }
                    }
                }
                if (ticks > to + 6) { System.out.println("captured " + (to - from) / 2 + " frame(s)"); Platform.exit(); }
            }
        }.start();
    }

    public static void main(String[] args) { launch(args); }
}
