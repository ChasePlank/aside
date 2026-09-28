package aside.games.fnaf;

import aside.games.fnaf.engine.Game;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * ShotScreens - capture the screens nobody has looked at.
 *
 * One process, several frames. Every visual check this session has found something the
 * code could not: the water drawn under the sky, the jumpscare shaken off-centre so a
 * strip of the room showed, the seed label, the swapped buttons. The camera monitor is
 * where a player spends most of a night and it had never been seen at all.
 */
public class ShotScreens extends Application {
    static aside.ui.UiManager ui;
    static Stage stage;

    @Override public void start(Stage st) {
        stage = st;
        Assets.load();
        ui = new aside.ui.UiManager(".");
        String which = System.getProperty("screen", "cams");
        switch (which) {
            case "menu" -> grab("fnaf-menu.png", () -> {
                MainMenu m = new MainMenu(ui);
                ui.push(m);
                return m.getRoot();
            }, 30);
            case "custom" -> grab("fnaf-custom.png", () -> {
                CustomNight c = new CustomNight(ui);
                ui.push(c);
                return c.getRoot();
            }, 30);
            case "blackout" -> grab("fnaf-blackout.png", () -> {
                GameScreen s = new GameScreen(ui, 1);
                ui.push(s);
                s.game.status = Game.Status.POWER_OUT;   // the lights are gone
                return s.getRoot();
            }, 180);
            case "win" -> grab("fnaf-win.png", () -> {
                GameScreen s = new GameScreen(ui, 1);
                ui.push(s);
                s.game.time = Game.HOUR_SECONDS * Game.NIGHT_HOURS - 1.0;
                return s.getRoot();
            }, 120);
            default -> grab("fnaf-cams.png", () -> {
                GameScreen s = new GameScreen(ui, 1);
                ui.push(s);
                s.game.cameraUp = true;              // the monitor, up
                s.game.setCam(1);
                return s.getRoot();
            }, 90);
        }
    }

    static void grab(String file, java.util.function.Supplier<Parent> build, int ticks) {
        StackPane root = new StackPane(build.get());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        var t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
        t.setOnFinished(e -> {
            for (int i = 0; i < ticks; i++) ui.tick(1.0 / 60);
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
                javax.imageio.ImageIO.write(bi, "png", new java.io.File("/root/downloads/" + file));
                System.out.println("PASS " + file + "  non-black=" + nonBlack);
            } catch (Exception ex) {
                System.out.println("FAIL " + file + ": " + ex);
            }
            Platform.exit();
            System.exit(0);
        });
        t.play();
    }
}
