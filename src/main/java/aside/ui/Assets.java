package aside.ui;

import javafx.scene.image.Image;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backgrounds and character sprites for the presenter.
 *
 * Looks on the classpath first (so a bundled jar works) then the
 * project folder (so art can be swapped without a rebuild).
 *
 * Sprite lookup falls back rather than failing: a pose that hasn't
 * been drawn yet uses the character's neutral, or whatever pose does
 * exist. A missing pose should never break a scene — it should just
 * look slightly less specific than intended, and be reported.
 */
public class Assets {
    public static Assets A;

    static final String[] CHARACTERS = {"monty", "roxanne", "chica", "freddy"};

    private final Map<String, Image> backgrounds = new LinkedHashMap<>();
    private final Map<String, Image> sprites = new LinkedHashMap<>();
    /** Poses the script asked for that no image exists for. */
    public final Set<String> missingPoses = new LinkedHashSet<>();
    public final List<String> notes = new ArrayList<>();

    public static void load(String root) {
        A = new Assets();
        File bgDir = new File(root, "art/backgrounds");
        File spDir = new File(root, "art/sprites");

        if (bgDir.isDirectory()) {
            for (File f : list(bgDir)) {
                String key = stripExt(f.getName());
                Image img = loadImage(f, "backgrounds/" + f.getName());
                if (img != null) A.backgrounds.put(key, img);
            }
        } else {
            A.notes.add("no background folder at " + bgDir);
        }

        if (spDir.isDirectory()) {
            for (File f : list(spDir)) A.sprites.put(stripExt(f.getName()), loadImage(f, null));
        } else {
            A.notes.add("no sprite folder at " + spDir);
        }

        A.notes.add("loaded " + A.backgrounds.size() + " backgrounds, " + A.sprites.size() + " sprites");
    }

    static List<File> list(File dir) {
        List<File> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) out.add(f);
        }
        out.sort((a, b) -> a.getName().compareTo(b.getName()));
        return out;
    }

    static Image loadImage(File f, String classpath) {
        try {
            if (classpath != null) {
                InputStream in = Assets.class.getClassLoader().getResourceAsStream(classpath);
                if (in != null) {
                    Image img = new Image(in);
                    if (!img.isError()) return img;
                }
            }
            Image img = new Image(f.toURI().toString());
            if (img.isError()) {
                System.err.println("bad image " + f + ": " + img.getException());
                return null;
            }
            return img;
        } catch (Exception e) {
            System.err.println("failed to load " + f + ": " + e);
            return null;
        }
    }

    static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? name : name.substring(0, i);
    }

    // ---------- lookups ----------

    public Image background(String name) {
        if (name == null) return null;
        Image img = backgrounds.get(name);
        if (img == null && !"none".equals(name)) missingPoses.add("bg:" + name);
        return img;
    }

    /** Pose for a character, with graceful fallback. */
    public Image sprite(String character, String pose) {
        if (character == null) return null;
        String ch = character.toLowerCase();
        String p = pose == null ? "neutral" : pose.toLowerCase();

        Image img = sprites.get(ch + "-" + p);
        if (img != null) return img;

        // Fall back to this character's neutral, then any pose at all
        img = sprites.get(ch + "-neutral");
        if (img == null) {
            for (String fallback : new String[]{"idle", "smirk", "warm", "bright", "quiet"}) {
                img = sprites.get(ch + "-" + fallback);
                if (img != null) break;
            }
        }
        if (img != null) missingPoses.add(ch + "-" + p);
        return img;
    }

    public int spriteCount() { return sprites.size(); }
    public int backgroundCount() { return backgrounds.size(); }
    public Set<String> spriteKeys() { return sprites.keySet(); }
}
