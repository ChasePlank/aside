package aside.games.fnaf;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.canvas.*;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * ShotSprites - render the four doorway pixel sprites, labelled, to one PNG.
 *
 * The doorway warning uses Sprites.monty/roxanne/chica/freddy, which are char grids
 * built at runtime. Everything else about the cast has been checked against the art
 * (portraits, alt portraits, jumpscares, the index mapping, the door sides) and all of
 * it is correct, so if a player sees "Chica" at the left door, the last place it can be
 * coming from is the grid that is supposed to be Monty.
 */
public class ShotSprites extends Application {
    @Override public void start(Stage stage) {
        Canvas canvas = new Canvas(560, 200);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#101014"));
        gc.fillRect(0, 0, 560, 200);
        javafx.scene.image.Image[] imgs = {Sprites.monty, Sprites.roxanne, Sprites.chica, Sprites.freddy};
        String[] names = {"Sprites.monty", "Sprites.roxanne", "Sprites.chica", "Sprites.freddy"};
        gc.setFont(Font.font("Arial", 13));
        for (int i = 0; i < 4; i++) {
            double x = 20 + i * 135;
            gc.drawImage(imgs[i], x, 20, 96, 96);
            gc.setFill(Color.WHITE);
            gc.fillText(names[i].replace("Sprites.", ""), x, 140);
        }
        var img = canvas.snapshot(null, null);
        try {
            int w = (int) img.getWidth(), h = (int) img.getHeight();
            var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var pr = img.getPixelReader();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
            javax.imageio.ImageIO.write(bi, "png", new java.io.File("/root/downloads/fnaf-sprites.png"));
            // COUNT PIXELS THAT ARE NOT THE BACKGROUND, AND FAIL IF THERE ARE TOO FEW. This printed
            // "PASS: wrote <file>" and nothing else, so a sprite that failed to load would have written an empty
            // canvas and said PASS anyway - the same shape as ShotVn rendering three of four stories with no art.
            //
            // NOT ShotScreens's count. That one counts non-BLACK, which works because its canvas is black; this
            // canvas is filled #101014, so counting non-black gives every pixel and proves nothing. Measured
            // before writing the check, rather than assumed from the sibling.
            int drawn = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                if ((bi.getRGB(x, y) & 0xFFFFFF) != 0x101014) drawn++;
            }
            // four 96x96 sprites would be 36864 pixels if they filled their boxes; they are pixel art with gaps,
            // so several thousand is the honest floor. Anything near zero means the sprites did not draw.
            System.out.println((drawn >= 3000 ? "PASS" : "FAIL") + ": wrote /root/downloads/fnaf-sprites.png"
                    + "  drawn=" + drawn + " (background is #101014)");
        } catch (Exception e) {
            System.out.println("FAIL: " + e);
        }
        Platform.exit();
        System.exit(0);
    }
}
