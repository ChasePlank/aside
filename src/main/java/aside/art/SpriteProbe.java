package aside.art;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reports, for every image in a folder, the things that decide whether
 * it can be used as a visual-novel sprite:
 *
 *   alpha        does it already have transparency?
 *   border       is the outer edge one flat colour (removable), or is
 *                the subject boxed in by scenery/text (not removable)?
 *   subject bbox where the actual figure sits, and what fraction of the
 *                frame it fills
 *   framing      bust / three-quarter / full / letterboxed screenshot
 *
 * Usage:  java aside.art.SpriteProbe <folder>
 */
public class SpriteProbe extends Application {

    @Override
    public void start(Stage stage) {
        String dir = getParameters().getRaw().isEmpty()
                ? "art/raw" : getParameters().getRaw().get(0);
        File folder = new File(dir);
        if (!folder.isDirectory()) {
            System.out.println("not a folder: " + dir);
            Platform.exit();
            return;
        }
        List<File> files = new ArrayList<>();
        for (File f : folder.listFiles()) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) files.add(f);
        }
        files.sort(Comparator.comparing(File::getName));

        System.out.printf("%-34s %-11s %-6s %-9s %-22s %-7s %s%n",
                "file", "size", "alpha", "border", "corner", "subject", "framing");
        System.out.println("-".repeat(120));
        for (File f : files) {
            try {
                report(f);
            } catch (Throwable t) {
                System.out.printf("%-34s ERROR %s%n", f.getName(), t);
            }
        }
        Platform.exit();
    }

    static void report(File f) {
        Image img = new Image(f.toURI().toString());
        if (img.isError()) {
            System.out.printf("%-34s DECODE FAILED: %s%n", f.getName(), img.getException());
            return;
        }
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        var pr = img.getPixelReader();

        // --- alpha ---
        boolean hasAlpha = false;
        for (int y = 0; y < h && !hasAlpha; y += 3) {
            for (int x = 0; x < w; x += 3) {
                if (pr.getColor(x, y).getOpacity() < 0.98) { hasAlpha = true; break; }
            }
        }

        // --- border uniformity ---
        // Sample the outer ring. If it's all within tolerance of the
        // corner colour, the background is flat and can be keyed out.
        Color corner = pr.getColor(0, 0);
        int samples = 0, matching = 0;
        for (int x = 0; x < w; x += Math.max(1, w / 200)) {
            samples += 2;
            if (near(pr.getColor(x, 0), corner)) matching++;
            if (near(pr.getColor(x, h - 1), corner)) matching++;
        }
        for (int y = 0; y < h; y += Math.max(1, h / 200)) {
            samples += 2;
            if (near(pr.getColor(0, y), corner)) matching++;
            if (near(pr.getColor(w - 1, y), corner)) matching++;
        }
        double borderRatio = samples == 0 ? 0 : matching / (double) samples;
        String border = borderRatio > 0.92 ? "flat" : (borderRatio > 0.6 ? "partial" : "busy");

        // --- subject bounding box (non-background pixels) ---
        int minX = w, minY = h, maxX = -1, maxY = -1, subjectPx = 0, total = 0;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                Color c = pr.getColor(x, y);
                total++;
                boolean isBg = hasAlpha ? c.getOpacity() < 0.5 : near(c, corner);
                if (!isBg) {
                    subjectPx++;
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        double fill = total == 0 ? 0 : subjectPx / (double) total;

        // --- framing guess ---
        double aspect = h / (double) w;
        String framing;
        if (aspect < 0.75) framing = "WIDE/screenshot?";
        else if (aspect < 1.05) framing = "square/bust";
        else if (aspect < 1.45) framing = "three-quarter";
        else if (aspect < 1.9) framing = "full body";
        else framing = "TALL/screenshot?";
        if (maxX >= 0 && (minX <= 1 || maxX >= w - 2)) framing += " [clipped]";

        String bbox = maxX < 0 ? "-" : String.format("%d,%d %dx%d",
                minX, minY, maxX - minX, maxY - minY);

        System.out.printf("%-34s %-11s %-6s %-9s %-22s %-7s %s%n",
                f.getName(), w + "x" + h,
                hasAlpha ? "yes" : "NO",
                border,
                hex(corner) + (borderRatio > 0.92 ? "" : String.format("(%.0f%%)", borderRatio * 100)),
                String.format("%.0f%%", fill * 100),
                framing);
    }

    static boolean near(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) < 0.09
                && Math.abs(a.getGreen() - b.getGreen()) < 0.09
                && Math.abs(a.getBlue() - b.getBlue()) < 0.09;
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    public static void main(String[] args) { launch(args); }
}
