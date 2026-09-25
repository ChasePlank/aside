package aside.games.fnaf;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.InputStream;

/**
 * Real art for the game, loaded from resources/images.
 *
 * The character renders are white-background JPGs (no alpha), so
 * `loadKeyed` removes the background by flood-filling inward from the
 * border. Flood fill (not a global white threshold) so white *inside*
 * a character — teeth, eyes, highlights — survives unless it's actually
 * connected to the outside.
 */
public class Assets {
    public static Assets A;

    // Scenes
    public Image office;
    public Image cover;
    public Image[] room = new Image[11];   // 1..10, index 8 = kitchen (null: cam disabled)
    public Image coveClosed, coveOpen;

    // Characters: [0]=Monty [1]=Roxanne [2]=Chica [3]=Freddy
    public Image[] charMain = new Image[4];
    public Image[] charAlt = new Image[4];
    public Image[] jumpscare = new Image[4];

    /** Jumpscare framing: crop rect (x, y, w, h) of the source image,
     *  chosen so the face fills a 1280x720 frame. The sources have
     *  backgrounds we can't cleanly cut (Chica's is a lit room,
     *  Roxanne's is the atrium), so the scare zooms past them and the
     *  edges fade to black instead. */
    public static final double[][] JSCARE_CROP = {
        // monty 600x338 — face already fills the frame
        {40, 0, 520, 300},
        // roxy 600x338 — head upper-left of centre
        {150, 20, 400, 300},
        // chica 469x440 — head fills most of it
        {70, 20, 340, 380},
        // freddy 1280x720 — head from roughly x200..1030, y0..570
        {200, 0, 850, 580},
    };

    public static void load() {
        A = new Assets();
        A.office = load("OfficeEmpty.png");
        A.cover  = load("cover.png");

        A.room[1]  = load("dining.png");
        A.room[2]  = load("parts and service.jpg");
        A.room[3]  = null;                       // Kid's Cove is special (curtain stages)
        A.room[4]  = load("WestHallNoCamera.png");
        A.room[5]  = load("cam.corner.left.png");
        A.room[6]  = load("closet.jpg");
        A.room[7]  = load("FNAF 1 Camera 7 Freddy in Bathroom - Brightened.png");
        A.room[8]  = null;                       // kitchen: cam disabled, audio only
        A.room[9]  = load("east.corner.jpg");
        A.room[10] = load("easthall.jpg");

        A.coveClosed = load("cove.closed.png");
        A.coveOpen   = load("cove.open.jpg");

        A.charMain[0] = loadKeyed("monty.jpg");
        A.charMain[1] = loadKeyed("roxy.jpg");
        A.charMain[2] = loadKeyed("chica.jpg");
        A.charMain[3] = loadKeyed("freddy.jpg");

        A.charAlt[0] = loadKeyed("monty2.jpg");
        A.charAlt[1] = loadKeyed("roxy2.jpg");
        A.charAlt[2] = loadKeyed("chica2.jpg");
        A.charAlt[3] = loadKeyed("freddy2.jpg");

        A.jumpscare[0] = load("monty.jumpscare.jpg");
        A.jumpscare[1] = load("roxy.jumpscare.jpg");
        A.jumpscare[2] = load("chica.jumpscare.jpg");
        A.jumpscare[3] = load("freddy.jumpscare.png");
    }

    static Image load(String name) {
        try (InputStream in = Assets.class.getClassLoader()
                .getResourceAsStream("fnaf/images/" + name)) {
            if (in == null) {
                System.err.println("Missing image: " + name);
                return null;
            }
            Image img = new Image(in);
            if (img.isError()) {
                System.err.println("Bad image " + name + ": " + img.getException());
                return null;
            }
            return img;
        } catch (Exception e) {
            System.err.println("Load failed " + name + ": " + e);
            return null;
        }
    }

    /**
     * Load and remove a near-white background by flood-filling inward
     * from the image border. Pixels reachable from the border that are
     * within tolerance of white become transparent.
     */
    static Image loadKeyed(String name) {
        Image src = load(name);
        if (src == null) return null;

        int w = (int) src.getWidth(), h = (int) src.getHeight();
        int n = w * h;
        int[] argb = new int[n];
        src.getPixelReader().getPixels(0, 0, w, h,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);

        boolean[] visited = new boolean[n];
        int[] stack = new int[n];
        int sp = 0;

        // Seed from every border pixel that looks like background
        for (int x = 0; x < w; x++) {
            sp = seed(argb, visited, stack, sp, x);
            sp = seed(argb, visited, stack, sp, (h - 1) * w + x);
        }
        for (int y = 0; y < h; y++) {
            sp = seed(argb, visited, stack, sp, y * w);
            sp = seed(argb, visited, stack, sp, y * w + w - 1);
        }

        // Flood fill, 4-connected
        while (sp > 0) {
            int idx = stack[--sp];
            int x = idx % w, y = idx / w;
            if (x > 0)     sp = push(argb, visited, stack, sp, idx - 1);
            if (x < w - 1) sp = push(argb, visited, stack, sp, idx + 1);
            if (y > 0)     sp = push(argb, visited, stack, sp, idx - w);
            if (y < h - 1) sp = push(argb, visited, stack, sp, idx + w);
        }

        WritableImage out = new WritableImage(w, h);
        PixelWriter pw = out.getPixelWriter();
        for (int i = 0; i < n; i++) {
            if (visited[i]) pw.setColor(i % w, i / w, Color.TRANSPARENT);
            else            pw.setArgb(i % w, i / w, argb[i]);
        }
        return out;
    }

    static int seed(int[] argb, boolean[] visited, int[] stack, int sp, int idx) {
        if (visited[idx] || !isNearWhite(argb[idx])) return sp;
        visited[idx] = true;
        stack[sp] = idx;
        return sp + 1;
    }

    static int push(int[] argb, boolean[] visited, int[] stack, int sp, int idx) {
        return seed(argb, visited, stack, sp, idx);
    }

    /** Generous white test — the sources have soft grey gradients near
     *  the feet, so a strict 255 check leaves a visible halo. */
    static boolean isNearWhite(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return true;                    // already transparent
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        return r > 224 && g > 224 && b > 224;
    }
}
