package aside.games.fnaf2;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.InputStream;

/**
 * Art for FNAF 2, loaded from resources/fnaf2/images.
 *
 * The office is <b>four whole views</b>, not one picture with lit
 * rectangles drawn on top. In FNAF 2 turning a light on swaps the entire
 * view: the hall light shows the office with the hall lit, each vent
 * light its own variant. Chase caught the overlay version on the first
 * build -- "the hallway office is different from the camera office" --
 * and the same rule applies here.
 *
 * The vent-lit views are synthesised from the base office (see
 * tools/vent-light.py) because the wiki only hosts vent-lit office
 * images with a character already standing in the vent, and a light that
 * always shows Balloon Boy is worse than no art at all.
 *
 * Camera feeds are drawn fitted, not covered: they are 1600x768 against
 * a 1280x720 canvas, and cropping 17% off the sides reads as zoomed in.
 */
public class Assets {
    public static Assets A;

    /** Character order, shared by the figure and jumpscare tables. */
    public static final String[] NAMES = {
        "Toy Freddy", "Withered Foxy", "Toy Bonnie", "Toy Chica",
        "Mangle", "Withered Bonnie", "Balloon Boy", "The Puppet"
    };
    public static final int TOY_FREDDY = 0, FOXY = 1, TOY_BONNIE = 2,
            TOY_CHICA = 3, MANGLE = 4, WITHERED_BONNIE = 5,
            BALLOON_BOY = 6, PUPPET = 7;

    // ---- The office, one whole view per light state ----
    public Image office, officeHall, officeVentL, officeVentR;
    public Image mask;

    /** Camera feeds, 1..11. */
    public Image[] room = new Image[12];

    /** Figures drawn inside an opening. */
    public Image[] figure = new Image[NAMES.length];
    /** Jumpscare frames. */
    public Image[] jumpscare = new Image[NAMES.length];

    public static void load() {
        A = new Assets();
        A.office     = load("office");
        A.officeHall = load("office.hall");
        A.officeVentL = load("office.ventL");
        A.officeVentR = load("office.ventR");
        A.mask = loadKeyed("mask");

        for (int i = 1; i <= 11; i++) A.room[i] = load("room" + i);

        A.figure[TOY_FREDDY]      = loadKeyed("toyfreddy");
        A.figure[FOXY]            = loadKeyed("witheredfoxy");
        A.figure[TOY_BONNIE]      = loadKeyed("toybonnie");
        A.figure[TOY_CHICA]       = loadKeyed("toychica");
        A.figure[MANGLE]          = loadKeyed("mangle");
        A.figure[WITHERED_BONNIE] = loadKeyed("witheredbonnie");
        A.figure[BALLOON_BOY]     = loadKeyed("balloonboy");
        A.figure[PUPPET]          = loadKeyed("puppet");

        A.jumpscare[TOY_FREDDY]      = load("js.toyfreddy");
        A.jumpscare[FOXY]            = load("js.witheredfoxy");
        A.jumpscare[TOY_BONNIE]      = load("js.toybonnie");
        A.jumpscare[TOY_CHICA]       = load("js.toychica");
        A.jumpscare[MANGLE]          = load("js.mangle");
        A.jumpscare[WITHERED_BONNIE] = load("js.witheredbonnie");
        A.jumpscare[BALLOON_BOY]     = load("js.balloonboy");
        A.jumpscare[PUPPET]          = load("js.puppet");
    }

    /** Index into the tables for an animatronic name, or -1. */
    public static int index(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        return -1;
    }

    /**
     * Load by stem, trying the extensions in turn.
     *
     * The opaque art (office views, camera feeds, jumpscares) is JPEG
     * because it is photographic and PNG stores it at roughly five times
     * the size for no benefit. Anything that needs alpha -- the figures
     * and the mask -- is PNG. The caller should not have to know which.
     */
    static Image load(String stem) {
        for (String ext : new String[]{".png", ".jpg"}) {
            try (InputStream in = Assets.class.getClassLoader()
                    .getResourceAsStream("fnaf2/images/" + stem + ext)) {
                if (in == null) continue;
                Image img = new Image(in);
                if (!img.isError()) return img;
            } catch (Exception ignored) { }
        }
        return null;
    }

    /**
     * Load and remove a near-white background by flood-filling inward
     * from the image border -- the same trick the FNAF 1 assets use.
     * Flood fill rather than a global threshold so white *inside* a
     * character (teeth, eyes, highlights) survives unless it is actually
     * connected to the outside. An image that is already transparent is
     * left alone: a fully transparent pixel counts as background, so the
     * fill cannot escape into the figure.
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

    /** Generous white test -- the sources have soft grey gradients near
     *  the feet, so a strict 255 check leaves a visible halo. */
    static boolean isNearWhite(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return true;
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        return r > 224 && g > 224 && b > 224;
    }
}
