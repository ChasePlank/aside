package aside.art;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the raw pose images into presenter-ready sprites.
 *
 * Three problems it solves, all of which are the artist's to cause and
 * a tool's to fix:
 *
 *  1. Backgrounds.  Some images already have alpha (segmented);
 *     others sit on a flat colour. Key the flat ones by flood-filling
 *     inward from the border, exactly like the FNAF character
 *     renders -- so white *inside* a character survives.
 *
 *  2. Scale.  Source heights run from 468px to 1675px. In a VN the
 *     cast stands side by side, so every sprite is rescaled to the
 *     same figure height and anchored at the bottom.
 *
 *  3. Margins.  Trim the transparent border to the subject, then add
 *     back a fixed margin, so nothing sits flush against the frame.
 *
 * Usage: java aside.art.SpriteProcess <rawDir> <outDir>
 */
public class SpriteProcess extends Application {

    /** On-screen figure height every sprite is normalised to. */
    static final int TARGET_H = 900;
    /** Transparent margin kept around the subject. */
    static final int MARGIN = 12;

    /** raw filename (no extension) -> canonical <character>-<pose> */
    static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put("monty.neutral", "monty-neutral");
        NAMES.put("monty.tired",   "monty-tired");
        NAMES.put("monty.still",   "monty-flat");     // script calls this flat
        NAMES.put("monty.quiet",   "monty-quiet");

        NAMES.put("roxy.smirk",    "roxanne-smirk");
        NAMES.put("roxy.happy.replacing.laugh", "roxanne-laughs");
        NAMES.put("roxy.flat",     "roxanne-flat");
        NAMES.put("roxy.cold",     "roxanne-cold");
        NAMES.put("roxy.warm",     "roxanne-warm");

        NAMES.put("freddy.idle",   "freddy-idle");
        NAMES.put("freddy.warm",   "freddy-warm");
        NAMES.put("freddy.unamused", "freddy-wry");   // closest equivalent

        NAMES.put("chica.neutral", "chica-neutral");
        NAMES.put("chica.bright",  "chica-bright");
        NAMES.put("chica.flat",    "chica-flat");
        NAMES.put("chica.small",   "chica-small");
    }

    @Override
    public void start(Stage stage) throws Exception {
        List<String> args = getParameters().getRaw();
        File rawDir = new File(args.isEmpty() ? "art/raw" : args.get(0));
        File outDir = new File(args.size() > 1 ? args.get(1) : "art/sprites");
        outDir.mkdirs();

        // Index what exists, preferring the newest file per pose name
        Map<String, File> found = new LinkedHashMap<>();
        for (String base : NAMES.keySet()) {
            File best = null;
            for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
                File f = new File(rawDir, base + ext);
                if (f.exists() && (best == null || f.lastModified() > best.lastModified())) {
                    best = f;
                }
            }
            if (best != null) found.put(base, best);
        }

        System.out.printf("%-28s %-18s %-13s %-11s %s%n",
                "raw", "-> output", "source", "cutout", "figure");
        System.out.println("-".repeat(95));

        int ok = 0;
        List<String> failures = new ArrayList<>();
        for (var e : NAMES.entrySet()) {
            File src = found.get(e.getKey());
            if (src == null) { failures.add(e.getValue() + " (no source file)"); continue; }
            try {
                Result r = process(src, new File(outDir, e.getValue() + ".png"));
                System.out.printf("%-28s %-18s %-13s %-11s %s%n",
                        e.getKey(), e.getValue(), r.srcW + "x" + r.srcH,
                        r.cut ? "keyed" : "had alpha",
                        r.figW + "x" + r.figH);
                ok++;
            } catch (Exception ex) {
                failures.add(e.getValue() + " (" + ex.getMessage() + ")");
            }
        }

        System.out.println();
        System.out.println("processed " + ok + "/" + NAMES.size() + " -> " + outDir);
        if (!failures.isEmpty()) {
            System.out.println("MISSING:");
            for (String f : failures) System.out.println("   " + f);
        }
        Platform.exit();
    }

    record Result(int srcW, int srcH, boolean cut, int figW, int figH) {}

    static Result process(File src, File out) throws Exception {
        Image img = new Image(src.toURI().toString());
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        int[] argb = new int[w * h];
        img.getPixelReader().getPixels(0, 0, w, h,
                PixelFormat.getIntArgbInstance(), argb, 0, w);

        boolean hasAlpha = false;
        for (int p : argb) if (((p >>> 24) & 0xFF) < 240) { hasAlpha = true; break; }

        if (!hasAlpha) keyFlatBackground(argb, w, h);

        // Trim to the opaque subject
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((argb[y * w + x] >>> 24) & 0xFF) > 8) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) throw new IllegalStateException("no opaque pixels");
        int sw = maxX - minX + 1, sh = maxY - minY + 1;

        // Scale so the figure is TARGET_H tall, then pad out to a
        // common canvas so every sprite shares one coordinate space.
        double scale = TARGET_H / (double) sh;
        int dw = (int) Math.round(sw * scale), dh = TARGET_H;
        int canvasW = (int) (dw + MARGIN * 2), canvasH = dh + MARGIN * 2;

        WritableImage outImg = new WritableImage(canvasW, canvasH);
        PixelWriter pw = outImg.getPixelWriter();
        for (int y = 0; y < canvasH; y++) {
            for (int x = 0; x < canvasW; x++) pw.setArgb(x, y, 0);
        }
        for (int y = 0; y < dh; y++) {
            int sy = minY + (int) (y / scale);
            if (sy > maxY) sy = maxY;
            for (int x = 0; x < dw; x++) {
                int sx = minX + (int) (x / scale);
                if (sx > maxX) sx = maxX;
                pw.setArgb(x + MARGIN, y + MARGIN, argb[sy * w + sx]);
            }
        }

        BufferedImage bi = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < canvasH; y++) {
            for (int x = 0; x < canvasW; x++) {
                int a = outImg.getPixelReader().getArgb(x, y);
                // de-fringe: kill near-transparent pixels that kept colour
                int alpha = (a >>> 24) & 0xFF;
                if (alpha < 24) a = 0;
                bi.setRGB(x, y, a);
            }
        }
        ImageIO.write(bi, "png", out);
        return new Result(w, h, !hasAlpha, dw, dh);
    }

    /** Flood fill inward from the border over the corner colour. */
    static void keyFlatBackground(int[] argb, int w, int h) {
        int ref = argb[0];
        int rr = (ref >>> 16) & 0xFF, rg = (ref >>> 8) & 0xFF, rb = ref & 0xFF;

        boolean[] vis = new boolean[w * h];
        int[] stack = new int[w * h];
        int sp = 0;
        for (int x = 0; x < w; x++) {
            sp = seed(argb, vis, stack, sp, x, rr, rg, rb);
            sp = seed(argb, vis, stack, sp, (h - 1) * w + x, rr, rg, rb);
        }
        for (int y = 0; y < h; y++) {
            sp = seed(argb, vis, stack, sp, y * w, rr, rg, rb);
            sp = seed(argb, vis, stack, sp, y * w + w - 1, rr, rg, rb);
        }
        while (sp > 0) {
            int idx = stack[--sp];
            int x = idx % w, y = idx / w;
            if (x > 0)     sp = push(argb, vis, stack, sp, idx - 1, rr, rg, rb);
            if (x < w - 1) sp = push(argb, vis, stack, sp, idx + 1, rr, rg, rb);
            if (y > 0)     sp = push(argb, vis, stack, sp, idx - w, rr, rg, rb);
            if (y < h - 1) sp = push(argb, vis, stack, sp, idx + w, rr, rg, rb);
        }
        for (int i = 0; i < argb.length; i++) if (vis[i]) argb[i] = 0;
    }

    /** Tolerant match: flat backgrounds are rarely perfectly uniform. */
    static boolean isBg(int p, int rr, int rg, int rb) {
        int r = (p >>> 16) & 0xFF, g = (p >>> 8) & 0xFF, b = p & 0xFF;
        return Math.abs(r - rr) < 26 && Math.abs(g - rg) < 26 && Math.abs(b - rb) < 26;
    }

    static int seed(int[] argb, boolean[] vis, int[] stack, int sp, int idx,
                    int rr, int rg, int rb) {
        if (vis[idx] || !isBg(argb[idx], rr, rg, rb)) return sp;
        vis[idx] = true;
        stack[sp] = idx;
        return sp + 1;
    }

    static int push(int[] argb, boolean[] vis, int[] stack, int sp, int idx,
                    int rr, int rg, int rb) {
        return seed(argb, vis, stack, sp, idx, rr, rg, rb);
    }

    public static void main(String[] args) { launch(args); }
}
