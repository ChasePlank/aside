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
        NAMES.put("roxy.happy",    "roxanne-laughs");
        NAMES.put("roxy.flat",     "roxanne-flat");
        NAMES.put("roxy.cold",     "roxanne-cold");
        NAMES.put("roxy.warm",     "roxanne-warm");

        NAMES.put("freddy.idle",   "freddy-idle");
        NAMES.put("freddy.warm",   "freddy-warm");
        NAMES.put("freddy.wry",    "freddy-wry");

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
        List<String> quality = new ArrayList<>();
        for (var e : NAMES.entrySet()) {
            File src = found.get(e.getKey());
            if (src == null) { failures.add(e.getValue() + " (no source file)"); continue; }
            try {
                Result r = process(src, new File(outDir, e.getValue() + ".png"));
                System.out.printf("%-28s %-18s %-13s %-11s %s%n",
                        e.getKey(), e.getValue(), r.srcW + "x" + r.srcH,
                        r.cut ? "keyed" : "had alpha",
                        r.figW + "x" + r.figH + (r.clipping.isEmpty() ? "" : "   CUT:" + r.clipping));
                if (!r.clipping.isEmpty()) {
                    quality.add(e.getValue() + " is cut off at " + r.clipping
                            + " in the source -- leave margin around the figure.");
                }
                ok++;
            } catch (Exception ex) {
                failures.add(e.getValue() + " (" + ex.getMessage() + ")");
            }
        }

        System.out.println();
        System.out.println("processed " + ok + "/" + NAMES.size() + " -> " + outDir);

        // ---- camera-distance consistency ----
        // Every sprite is normalised to the same height, so its WIDTH
        // tells us how close the camera was. Within one character that
        // should barely vary; a big spread means the shots were taken
        // from different distances and will read as different sizes
        // when they stand side by side.
        System.out.println();
        System.out.println("camera consistency (width at equal height):");
        Map<String, List<Integer>> byChar = new LinkedHashMap<>();
        Map<String, Integer> heights = new LinkedHashMap<>();
        for (var e : NAMES.entrySet()) {
            File src = found.get(e.getKey());
            if (src == null) continue;
            int dash = e.getKey().indexOf('.');
            String ch = dash < 0 ? e.getKey() : e.getKey().substring(0, dash);
            File out = new File(outDir, e.getValue() + ".png");
            if (!out.exists()) continue;
            try {
                Image im = new Image(out.toURI().toString());
                byChar.computeIfAbsent(ch, k -> new ArrayList<>()).add((int) im.getWidth());
                heights.merge(ch, (int) im.getHeight(), (a, b) -> a);
            } catch (Exception ignored) { }
        }
        for (var e : byChar.entrySet()) {
            List<Integer> ws = e.getValue();
            int min = ws.stream().min(Integer::compare).orElse(0);
            int max = ws.stream().max(Integer::compare).orElse(0);
            double ratio = min == 0 ? 0 : max / (double) min;
            String verdict = ratio <= 1.25 ? "consistent"
                    : (ratio <= 1.5 ? "slightly varied" : "INCONSISTENT");
            System.out.printf("  %-10s %-28s %.2fx   %s%n", e.getKey(), ws.toString(), ratio, verdict);
        }
        System.out.println();
        System.out.println("  (1.00x = every shot from the same distance. Aim under 1.25x.)");

        if (!quality.isEmpty()) {
            System.out.println();
            System.out.println("cropping problems:");
            for (String q : quality) System.out.println("   !! " + q);
        }
        if (!failures.isEmpty()) {
            System.out.println("MISSING:");
            for (String f : failures) System.out.println("   " + f);
        }
        Platform.exit();
    }

    record Result(int srcW, int srcH, boolean cut, int figW, int figH, String clipping) {}

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

        // Clipping: did the figure run into the source frame edge? A
        // limb cut off by the crop can never be recovered downstream.
        StringBuilder clip = new StringBuilder();
        if (minX <= 0)      clip.append("left ");
        if (maxX >= w - 1)  clip.append("right ");
        if (minY <= 0)      clip.append("top ");
        if (maxY >= h - 1)  clip.append("bottom ");

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
        return new Result(w, h, !hasAlpha, dw, dh, clip.toString().trim());
    }

    /** Soft key: flood fill the flat background, then repair the rim.
     *
     * A binary mask leaves a halo, because the edge pixels of the source
     * are the figure COMPOSITED OVER the background -- they are a blend,
     * not the figure's real colour. Treating them as opaque keeps the
     * background's contribution, which is what shows up as a white
     * fringe.
     *
     * The fix is to invert the compositing. For a pixel that is really
     * `alpha * figure + (1 - alpha) * background`, we can estimate alpha
     * from how far the pixel sits from the background colour, then solve
     * for the figure colour:
     *
     *     figure = (observed - (1 - alpha) * background) / alpha
     *
     * Applied only within a couple of pixels of the background, so
     * genuinely light areas INSIDE the figure are never touched.
     */
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

        // Pixels within RIM px of the background get the soft treatment.
        final int RIM = 2;
        int[] rimDist = new int[w * h];
        java.util.Arrays.fill(rimDist, Integer.MAX_VALUE);
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        for (int i = 0; i < argb.length; i++) {
            if (vis[i]) { rimDist[i] = 0; q.add(i); }
        }
        while (!q.isEmpty()) {
            int idx = q.poll();
            int d = rimDist[idx];
            if (d >= RIM) continue;
            int x = idx % w, y = idx / w;
            int[] nb = {
                x > 0 ? idx - 1 : -1,
                x < w - 1 ? idx + 1 : -1,
                y > 0 ? idx - w : -1,
                y < h - 1 ? idx + w : -1,
            };
            for (int n : nb) {
                if (n >= 0 && rimDist[n] > d + 1) { rimDist[n] = d + 1; q.add(n); }
            }
        }

        final int SOFT_LO = 10, SOFT_HI = 46;
        for (int i = 0; i < argb.length; i++) {
            if (vis[i]) { argb[i] = 0; continue; }
            if (rimDist[i] >= RIM) continue;              // deep inside: leave alone
            int r = (argb[i] >>> 16) & 0xFF, g = (argb[i] >>> 8) & 0xFF, b = argb[i] & 0xFF;
            int dist = Math.max(Math.abs(r - rr), Math.max(Math.abs(g - rg), Math.abs(b - rb)));
            if (dist >= SOFT_HI) continue;                 // clearly figure
            double a = Math.max(0, Math.min(1, (dist - SOFT_LO) / (double) (SOFT_HI - SOFT_LO)));
            if (a <= 0.02) { argb[i] = 0; continue; }
            // Solve for the un-composited colour
            int rn = uncomposite(r, rr, a), gn = uncomposite(g, rg, a), bn = uncomposite(b, rb, a);
            int al = (int) Math.round(a * 255);
            argb[i] = (al << 24) | (rn << 16) | (gn << 8) | bn;
        }
    }

    static int uncomposite(int observed, int bg, double a) {
        if (a <= 0) return 0;
        int v = (int) Math.round((observed - (1 - a) * bg) / a);
        return Math.max(0, Math.min(255, v));
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
