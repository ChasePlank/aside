package aside.games.fnaf8;

import javafx.scene.image.Image;

import java.io.InputStream;

/**
 * Art for FNAF 8, loaded from resources/fnaf8/images.
 *
 * <p>Two kinds of picture, and the split is the game:
 *
 * <pre>
 *   room.jpg            the office, empty, in the dark.
 *   unit_&lt;key&gt;.png     one of them, full body, transparent.
 *   scare_&lt;key&gt;.jpg    what fills the screen when they find each other.
 * </pre>
 *
 * <p>There is one room and one picture per unit, and <b>there is no
 * picture per hall and no picture per distance.</b> A unit is drawn into
 * whichever doorway the engine says it is standing in, at whatever size
 * the engine says it is at. That is a deliberate choice rather than a
 * shortcut, and it is the same one FNAF 6 and 7 made: the one thing the
 * player has to read at a glance is <i>how far up the hall is it</i>, and
 * that is a fact about position, which the engine already knows exactly.
 * Five photographs of the same animatronic at five distances would be five
 * photographs that have to agree about lighting, angle and scale, and they
 * would not.
 *
 * <p>Every scene is 1280x720, the engine's canvas, because the crop is an
 * art decision and it lives in tools/fnaf8-art.py where it can be re-run.
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
        for (aside.games.fnaf8.engine.Unit u
                : aside.games.fnaf8.engine.Unit.all()) {
            A.unit.put(u.key(), load("unit_" + u.key()));
            A.scare.put(u.key(), load("scare_" + u.key()));
        }
    }

    /** The thing in the hall, or null if the art is missing. */
    public Image unit(String key) {
        return key == null ? null : unit.get(key);
    }

    /** The face to fill the screen with when they meet. */
    public Image scare(String key) {
        Image img = key == null ? null : scare.get(key);
        return img != null ? img : scare.get("bonnie");
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
                    .getResourceAsStream("fnaf8/images/" + stem + ext)) {
                if (in == null) continue;
                Image img = new Image(in);
                if (!img.isError()) return img;
            } catch (Exception ignored) { }
        }
        return null;
    }
}
