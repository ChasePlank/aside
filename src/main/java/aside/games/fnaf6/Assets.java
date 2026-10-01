package aside.games.fnaf6;

import javafx.scene.image.Image;

import java.io.InputStream;

/**
 * Art for FNAF 6, loaded from resources/fnaf6/images.
 *
 * <p>Two kinds of picture, and the split is the game:
 *
 * <pre>
 *   room.jpg            the salvage bay, empty, in the dark.
 *   unit_&lt;key&gt;.png     the thing in the chair, full body, transparent.
 *   scare_&lt;key&gt;.jpg    what fills the screen when it gets you.
 * </pre>
 *
 * <p>There is one room and one picture per unit, and <b>there is no
 * picture per pose.</b> The pose is drawn: the unit is scaled up and
 * raised out of the chair as it climbs, and tilted while it is still
 * slumped. That is a deliberate choice rather than a shortcut. Five
 * photographs of the same animatronic in five poses would be five
 * photographs that have to agree about lighting, angle and scale, and the
 * one thing the player has to be able to read at a glance -- <i>is it
 * higher than it was</i> -- is a fact about position, which the engine
 * already knows exactly.
 *
 * <p>Every scene is 1280x720, the engine's canvas, because the crop is an
 * art decision and it lives in tools/fnaf6-art.py where it can be re-run.
 */
public class Assets {
    public static Assets A;

    /** The bay, empty. */
    public Image room;

    /**
     * The desk, with alpha, drawn <b>after</b> the unit.
     *
     * <p>The thing in the chair is behind the desk, and the one thing the
     * player has to read at a glance is how much of it is above the desk
     * line. A unit drawn on top of the desk is a unit standing on the
     * table; a unit whose legs are hidden is a unit that is getting up.
     */
    public Image desk;

    /** One per unit key. */
    public final java.util.Map<String, Image> unit = new java.util.HashMap<>();
    /** One per unit key. */
    public final java.util.Map<String, Image> scare = new java.util.HashMap<>();

    public static void load() {
        A = new Assets();
        A.room = load("room");
        A.desk = load("desk");
        for (String key : new String[]{"scraptrap", "scrapbaby", "moltenfreddy", "lefty"}) {
            A.unit.put(key, load("unit_" + key));
            A.scare.put(key, load("scare_" + key));
        }
    }

    /** The thing in the chair, or null if the art is missing. */
    public Image unit(String key) {
        return key == null ? null : unit.get(key);
    }

    /** The face to fill the screen with when this one gets you. */
    public Image scare(String key) {
        Image img = key == null ? null : scare.get(key);
        return img != null ? img : scare.get("scraptrap");
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
                    .getResourceAsStream("fnaf6/images/" + stem + ext)) {
                if (in == null) continue;
                Image img = new Image(in);
                if (!img.isError()) return img;
            } catch (Exception ignored) { }
        }
        return null;
    }
}
