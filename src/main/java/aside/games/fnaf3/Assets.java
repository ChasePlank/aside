package aside.games.fnaf3;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.InputStream;

/**
 * Art for FNAF 3, loaded from resources/fnaf3/images.
 *
 * The office is one picture and stays one picture. FNAF 3 does not swap
 * the whole view per light the way FNAF 2 does, because FNAF 3 has no
 * lights -- there is nothing to switch on. What the office has instead is
 * two places somebody can be standing, and both of them are drawn on top
 * of the same photograph.
 *
 * The camera feeds are 825x650 and are drawn fitted, not covered. They are
 * already a security-camera frame; cropping one reads as a zoom, and a
 * zoom on a room you are trying to search is the wrong affordance.
 */
public class Assets {
    public static Assets A;

    /** The phantoms, in the order the engine names them. */
    public static final String[] PHANTOM_NAMES = {
        "Phantom Freddy", "Phantom Chica", "Phantom Foxy",
        "Phantom Mangle", "Phantom Puppet", "Phantom Balloon Boy",
    };

    public Image office;
    public Image springtrap;
    /** Camera feeds, 1..10. */
    public Image[] room = new Image[11];
    /** One per name in {@link #PHANTOM_NAMES}. */
    public Image[] phantom = new Image[PHANTOM_NAMES.length];

    public static void load() {
        A = new Assets();
        A.office = load("office");
        A.springtrap = loadKeyed("springtrap");
        for (int i = 1; i <= 10; i++) A.room[i] = load("room" + i);
        A.phantom[0] = loadKeyed("phantomfreddy");
        A.phantom[1] = loadKeyed("phantomchica");
        A.phantom[2] = loadKeyed("phantomfoxy");
        A.phantom[3] = loadKeyed("phantommangle");
        A.phantom[4] = loadKeyed("phantompuppet");
        A.phantom[5] = loadKeyed("phantombb");
    }

    /** Index into {@link #PHANTOM_NAMES} for a name, or -1. */
    public static int phantomIndex(String name) {
        for (int i = 0; i < PHANTOM_NAMES.length; i++) {
            if (PHANTOM_NAMES[i].equals(name)) return i;
        }
        return -1;
    }

    /**
     * Load by stem, trying the extensions in turn.
     *
     * The opaque art (the office, the camera feeds) is JPEG because it is
     * photographic and PNG stores it at roughly five times the size for no
     * benefit. Anything that needs alpha -- Springtrap and the phantoms --
     * is PNG. The caller should not have to know which.
     */
    static Image load(String stem) {
        for (String ext : new String[]{".png", ".jpg"}) {
            try (InputStream in = Assets.class.getClassLoader()
                    .getResourceAsStream("fnaf3/images/" + stem + ext)) {
                if (in == null) continue;
                Image img = new Image(in);
                if (!img.isError()) return img;
            } catch (Exception ignored) { }
        }
        return null;
    }

    /**
     * Load and remove a near-white background by flood-filling inward from
     * the image border -- the same trick the FNAF 1 and FNAF 2 assets use.
     *
     * Flood fill rather than a global threshold, so white *inside* a
     * character (teeth, eyes, the pale patches on a phantom) survives
     * unless it is actually connected to the outside. An image that is
     * already transparent is left alone: a fully transparent pixel counts
     * as background, so the fill cannot escape into the figure.
     */
    static Image loadKeyed(String stem) {
        Image src = load(stem);
        if (src == null) return null;

        int w = (int) src.getWidth(), h = (int) src.getHeight();
        int n = w * h;
        int[] argb = new int[n];
        src.getPixelReader().getPixels(0, 0, w, h,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);

        boolean[] visited = new boolean[n];
        int[] stack = new int[n];
        int sp = 0;

        for (int x = 0; x < w; x++) {
            sp = seed(argb, visited, stack, sp, x);
            sp = seed(argb, visited, stack, sp, (h - 1) * w + x);
        }
        for (int y = 0; y < h; y++) {
            sp = seed(argb, visited, stack, sp, y * w);
            sp = seed(argb, visited, stack, sp, y * w + w - 1);
        }
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

    /** Generous white test -- the sources have soft grey gradients near the
     *  feet, so a strict 255 check leaves a visible halo. */
    static boolean isNearWhite(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return true;
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        return r > 224 && g > 224 && b > 224;
    }
}
