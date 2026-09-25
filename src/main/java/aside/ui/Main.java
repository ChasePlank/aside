package aside.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Aside — entry point.
 *
 * Window fits the screen's work area and is resizable; the drawing
 * surface stays a fixed 1280x720 and is scaled to fit. Region
 * backgrounds are avoided entirely (they don't paint on this GPU), so
 * every screen draws itself onto a Canvas.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        String root = System.getProperty("aside.root", ".");
        Assets.load(root);
        Audio.load(root);

        for (String note : Assets.A.notes) System.out.println("[assets] " + note);
        for (String note : Audio.A.notes) System.out.println("[audio] " + note);

        UiManager ui = new UiManager(root);

        StackPane host = new StackPane(ui.getContainer());
        host.setStyle("-fx-background-color: #000000;");

        javafx.geometry.Rectangle2D area =
                javafx.stage.Screen.getPrimary().getVisualBounds();
        double w = Math.min(1280, area.getWidth());
        double h = Math.min(720, area.getHeight());

        Scene scene = new Scene(host, w, h, Color.BLACK);
        scene.setOnKeyPressed(ui::handleKey);

        // Screenshot mode: render N frames, write the frame to a file,
        // exit. Screen-grabbing the desktop proved unreliable (the
        // window can composite to white on this GPU), but asking
        // JavaFX for its own frame always tells the truth.
        String shotPath = System.getProperty("aside.snapshot");
        String shotKeys = System.getProperty("aside.keys");

        AnimationTimer loop = new AnimationTimer() {
            long last = -1;
            long frames = 0;
            @Override public void handle(long now) {
                double dt = last < 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
                last = now;
                ui.tick(dt);
                frames++;

                // Feed synthetic key presses, then snapshot.
                if (shotKeys != null && frames == 30) {
                    for (String k : shotKeys.split(",")) {
                        javafx.scene.input.KeyCode code;
                        try {
                            code = javafx.scene.input.KeyCode.valueOf(k.trim().toUpperCase());
                        } catch (Exception ex) { continue; }
                        ui.handleKey(new javafx.scene.input.KeyEvent(
                                javafx.scene.input.KeyEvent.KEY_PRESSED,
                                "", "", code, false, false, false, false));
                    }
                }

                if (shotPath != null && frames == 90) {
                    try {
                        var img = host.snapshot(null, null);
                        java.awt.image.BufferedImage bi =
                                javafx.embed.swing.SwingFXUtils.fromFXImage(img, null);
                        javax.imageio.ImageIO.write(bi, "png", new java.io.File(shotPath));
                        System.out.println("[snapshot] wrote " + shotPath
                                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
                    } catch (Exception ex) {
                        System.out.println("[snapshot] failed: " + ex);
                    }
                    javafx.application.Platform.exit();
                }
            }
        };
        loop.start();

        stage.setTitle("Aside — visual novel engine");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();

        ui.push(new TitleScreen(ui));
    }

    public static void main(String[] args) { launch(args); }
}
