package aside.games.fnaf5;

import aside.games.fnaf5.engine.Room;

import javafx.scene.image.Image;

import java.io.InputStream;

/**
 * Art for FNAF 5, loaded from resources/fnaf5/images.
 *
 * Every scene is already 1280x720 -- the engine's canvas -- because the
 * crop is an art decision and it lives in tools/fnaf5-art.py where it can
 * be re-run, not here where it would be invisible.
 *
 * The whole game is seen through a security monitor, and the art is
 * treated accordingly: each room is desaturated, darkened and pushed
 * toward the green of a cheap camera, so five photographs from five
 * different places in the franchise read as five feeds from one building.
 * The scanlines and the timestamp are drawn live, on top, because they
 * should move.
 *
 * Two states per room, and the second is the game:
 *
 *   EMPTY   the feed is up and the room is clear.
 *   HERE    the feed is up and something is standing in it, close to the
 *           lens, which is what a camera sees when it has been found.
 *
 * There is no third state, and that is the rule of the game: the monitor
 * cannot show the room you are standing in, so the room you are standing
 * in has no picture at all.
 */
public class Assets {
    public static Assets A;

    /** What the camera is showing. */
    public enum State { EMPTY, HERE }

    /** [room][state], indexed by {@link Room.Where#ordinal()}. */
    public final Image[][] scene = new Image[Room.COUNT][State.values().length];

    /** One per threat key. */
    public Image scareBallora, scareFoxy, scareFreddy;

    public static void load() {
        A = new Assets();
        for (Room.Where w : Room.ALL) {
            A.scene[Room.index(w)][State.EMPTY.ordinal()] = load("room" + Room.index(w));
        }
        A.scene[Room.Where.BALLORA.ordinal()][State.HERE.ordinal()] = load("here_ballora");
        A.scene[Room.Where.AUDITORIUM.ordinal()][State.HERE.ordinal()] = load("here_foxy");
        A.scene[Room.Where.PARTS.ordinal()][State.HERE.ordinal()] = load("here_freddy");

        A.scareBallora = load("scare_ballora");
        A.scareFoxy = load("scare_foxy");
        A.scareFreddy = load("scare_freddy");
    }

    /** The face to fill the screen with when this one gets you. */
    public Image scare(String killer) {
        if (killer == null) return scareFreddy;
        if (killer.contains("Ballora")) return scareBallora;
        if (killer.contains("Foxy")) return scareFoxy;
        return scareFreddy;
    }

    /** The feed for a room in a state, or null if the art is missing. */
    public Image scene(Room.Where w, State s) {
        if (w == null) return null;
        return scene[Room.index(w)][s.ordinal()];
    }

    /**
     * The feed for a room, with whatever is standing in it.
     *
     * The "here" art is per character, so a room with something in it
     * shows that thing -- and a room with nothing in it falls back to the
     * empty frame, which is the common case and must never be missing.
     */
    public Image feed(Room.Where w, String who) {
        if (w == null) return null;
        if (who != null) {
            Image here = here(w, who);
            if (here != null) return here;
        }
        return scene(w, State.EMPTY);
    }

    /** The frame for a room with a particular character in it. */
    Image here(Room.Where w, String who) {
        if (w == Room.Where.BALLORA && who.equals("ballora")) {
            return scene[w.ordinal()][State.HERE.ordinal()];
        }
        if (w == Room.Where.AUDITORIUM && who.equals("foxy")) {
            return scene[w.ordinal()][State.HERE.ordinal()];
        }
        if (w == Room.Where.PARTS && who.equals("freddy")) {
            return scene[w.ordinal()][State.HERE.ordinal()];
        }
        return null;
    }

    /**
     * Load by stem, trying the extensions in turn.
     *
     * Every scene is opaque and photographic, so it is JPEG. The caller
     * should not have to know which.
     */
    static Image load(String stem) {
        for (String ext : new String[]{".jpg", ".png"}) {
            try (InputStream in = Assets.class.getClassLoader()
                    .getResourceAsStream("fnaf5/images/" + stem + ext)) {
                if (in == null) continue;
                Image img = new Image(in);
                if (!img.isError()) return img;
            } catch (Exception ignored) { }
        }
        return null;
    }
}
