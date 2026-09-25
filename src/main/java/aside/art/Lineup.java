package aside.art;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contact sheet: every sprite for one character in a row, all drawn at
 * the same figure height on the same baseline.
 *
 * This exists because a number can't tell whether a set is consistent.
 * The width-at-equal-height metric in SpriteProbe treats a side profile
 * as a camera-distance outlier, because a profile genuinely IS narrower
 * -- the metric can't know that, but your eye can, instantly.
 *
 * So: look at it. If the figures read as the same person at the same
 * size, they are.
 *
 * Usage: java aside.art.Lineup <spriteDir> <outPng>
 */
public class Lineup extends Application {

    static final int FIG_H = 300;        // figure height for every sprite
    static final int ROW_H = FIG_H + 46; // + label space
    static final int PAD = 14;
    static final int LABEL_W = 110;

    static final String[][] GROUPS = {
        {"monty",   "monty-neutral", "monty-tired", "monty-flat", "monty-quiet"},
        {"roxanne", "roxanne-smirk", "roxanne-laughs", "roxanne-flat",
                    "roxanne-cold", "roxanne-warm"},
        {"freddy",  "freddy-idle", "freddy-warm", "freddy-wry"},
        {"chica",   "chica-neutral", "chica-bright", "chica-flat", "chica-small"},
    };

    @Override
    public void start(Stage stage) throws Exception {
        List<String> args = getParameters().getRaw();
        File dir = new File(args.isEmpty() ? "art/sprites" : args.get(0));
        File out = new File(args.size() > 1 ? args.get(1) : "art/lineup.png");

        // Load what exists
        Map<String, Image> imgs = new LinkedHashMap<>();
        int rows = 0, widest = 0;
        for (String[] g : GROUPS) {
            int w = LABEL_W;
            boolean any = false;
            for (int i = 1; i < g.length; i++) {
                File f = new File(dir, g[i] + ".png");
                if (!f.exists()) continue;
                Image img = new Image(f.toURI().toString());
                if (img.isError()) continue;
                imgs.put(g[i], img);
                w += img.getWidth() * (FIG_H / 900.0) + PAD;
                any = true;
            }
            if (any) { rows++; widest = Math.max(widest, w); }
        }
        if (rows == 0) { System.out.println("no sprites found in " + dir); Platform.exit(); return; }

        int W = widest + PAD, H = rows * ROW_H + PAD;
        Canvas canvas = new Canvas(W, H);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(Color.web("#1A1A22"));
        gc.fillRect(0, 0, W, H);

        int row = 0;
        for (String[] g : GROUPS) {
            double baseline = PAD + row * ROW_H + FIG_H;
            gc.setFill(Color.web("#F2C14E"));
            gc.setFont(Font.font("Arial", 20));
            gc.fillText(g[0], PAD + 6, baseline - 6);

            // Guide line: the shared floor every figure stands on
            gc.setStroke(Color.web("#3A3A4A"));
            gc.setLineWidth(1);
            gc.strokeLine(LABEL_W, baseline, W - PAD, baseline);

            double x = LABEL_W + PAD;
            for (int i = 1; i < g.length; i++) {
                Image img = imgs.get(g[i]);
                if (img == null) continue;
                double s = FIG_H / 900.0;
                double dw = img.getWidth() * s, dh = img.getHeight() * s;
                double dy = baseline - dh + (24 * s);   // 24px transparent margin
                gc.drawImage(img, x, dy, dw, dh);
                gc.setFill(Color.web("#9A9AAE"));
                gc.setFont(Font.font("Arial", 11));
                String label = g[i].substring(g[i].indexOf('-') + 1);
                gc.fillText(label, x + 4, baseline + 20);
                x += dw + PAD;
            }
            row++;
        }

        Image rendered = canvas.snapshot(new SnapshotParameters(), null);
        java.awt.image.BufferedImage bi =
                javafx.embed.swing.SwingFXUtils.fromFXImage(rendered, null);
        ImageIO.write(bi, "png", out);
        System.out.println("wrote " + out + "  (" + W + "x" + H + ")");
        Platform.exit();
    }

    public static void main(String[] args) { launch(args); }
}
