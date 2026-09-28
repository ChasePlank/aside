package aside.games.fnaf;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * ShotInfo - capture the character bios.
 *
 * Written to look at the reworded bio column, because a text change in a fixed layout is
 * exactly the kind of thing a compiler cannot check: the entries are drawn with wrapped
 * text at fixed offsets, so "Right door - never stalls" and "Kid's Cove - sprints" have to
 * be seen to know they fit.
 *
 * The bios only appear for characters already met, so this marks them met through
 * Progress - and Progress WRITES ITS SAVE, so this tool must be run from a scratch
 * directory (the save path is relative to the working directory) or it will edit a real
 * playthrough. Same tick() rule as ShotOffice: UiManager.tick is the game loop.
 */
public class ShotInfo extends Application {
    @Override public void start(Stage stage) {
        Assets.load();
        aside.ui.UiManager ui = new aside.ui.UiManager(".");
        // InfoScreen reads the STATIC FnafGame.progress, not Progress.load() - so marks
        // made on a fresh instance are invisible to it. That is why the first capture of
        // this screen showed "Not yet met" for all four.
        Progress p = Progress.load();
        for (String name : new String[]{"Monty", "Roxanne", "Chica", "Freddy"}) p.meet(name);
        FnafGame.progress = p;
        InfoScreen screen = new InfoScreen(ui);
        ui.push(screen);   // tick() ticks the stack; unpushed screens never draw
        StackPane root = new StackPane(screen.getRoot());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        screen.enter();

        var t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
        t.setOnFinished(e -> {
            for (int i = 0; i < 120; i++) ui.tick(1.0 / 60);
            WritableImage img = scene.snapshot(null);
            try {
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var pr = img.getPixelReader();
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
                javax.imageio.ImageIO.write(bi, "png", new java.io.File("/root/downloads/fnaf-bios.png"));
                System.out.println("PASS: wrote /root/downloads/fnaf-bios.png (" + w + "x" + h + ")");
            } catch (Exception ex) {
                System.out.println("FAIL: " + ex);
            }
            Platform.exit();
            System.exit(0);
        });
        t.play();
    }
}
