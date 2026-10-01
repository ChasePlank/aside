package aside.games.fnaf7;

import javafx.scene.image.Image;

import java.io.InputStream;

/**
 * Art for FNAF 7, loaded from resources/fnaf7/images.
 *
 * <p>Two kinds of picture, and the split is the game:
 *
 * <pre>
 *   room.jpg            the office, empty, in the dark.
 *   unit_&lt;key&gt;.png     the thing in the hall, full body, transparent.
 *   scare_&lt;key&gt;.jpg    what fills the screen when it gets you.
 * </pre>
 *
 * <p>There is one room and one picture per unit, and <b>there is no
 * picture per hall.</b> The unit is drawn into whichever doorway the
 * engine says it is standing in. That is a deliberate choice rather than a
 * shortcut. Two photographs of the same animatronic in two doorways would
 * be two photographs that have to agree about lighting, angle and scale,
 * and the one thing the player has to be able to read at a glance --
 * <i>which side is it on</i> -- is a fact about position, which the engine
 * already knows exactly.
 *
 * <p>Every scene is 1280x720, the engine's canvas, because the crop is an
 * art decision and it lives in tools/fnaf7-art.py where it can be re-run.
 */
public class Assets {
    public static Assets A;

    /** The office, empty. */
    public Image room;

    /** One per unit key. */
    public final java.util.Map<String, Image> unit = new java.util.HashMap<>();
    /** One per unit key. */
    public final java.util.Map<String, Image> scare = new java.util.HashMap<>();

    public static void load() {
        A = new Assets();
        A.room = load("room");
        for (String key : new String[]{"baby", "ennard", "glitchtrap", "vanny", "mimic"}) {
            A.unit.put(key, load("unit_" + key));
            A.scare.put(key, load("scare_" + key));
        }
    }

    /** The thing in the hall, or null if the art is missing. */
    public Image unit(String key) {
        return key == null ? null : unit.get(key);
    }

    /** The face to fill the screen with when this one gets you. */
    public Image scare(String key) {
        Image img = key == null ? null : scare.get(key);
        return img != null ? img : scare.get("baby");
    }

    /**
     * Load by stem, trying the extensions in turn.
     *
     * <p>Opaque art is photographic and belongs in JPEG; a figure needs
     * alpha and stays PNG. The caller should not have to know which, so
     * the extension is tried rather than named.
     */
    static Image load(String stem) {
        for (String ext : new String[]{".png", ".jpg"}) {
            try (InputStream in = Assets.class.getClassLoader()
                    .getResourceAsStream("fnaf7/images/" + stem + ext)) {
                if (in == null) continue;
                Image img = new Image(in);
                if (!img.isError()) return img;
            } catch (Exception ignored) { }
        }
        return null;
    }
}
