package aside.games.fnaf4;

import aside.games.fnaf4.engine.Room;

import javafx.scene.image.Image;

import java.io.InputStream;

/**
 * Art for FNAF 4, loaded from resources/fnaf4/images.
 *
 * Every scene is already 1280x720 -- the engine's canvas -- because the
 * crop is an art decision and it lives in tools/fnaf4-art.py where it can
 * be re-run, not here where it would be invisible. So this class does no
 * scaling at all, which is the point: there is exactly one place that
 * decides what part of a photograph you are looking at.
 *
 * Each of the four stations has three states, and the third is the game:
 *
 *   DARK   the flashlight is off. You cannot see the room.
 *   LIT    the flashlight is on and the place is empty.
 *   HERE   the flashlight is on and something is standing in it.
 *
 * Fredbear is not a scene. He is a sprite drawn over whatever station he
 * has turned up in, because he is the one thing that is not tied to a
 * place -- which is exactly what makes him the one you have to go looking
 * for.
 */
public class Assets {
    public static Assets A;

    /** What the light is doing at a station. */
    public enum State { DARK, LIT, HERE }

    /** [station][state], indexed by {@link Room.Where#ordinal()}. */
    public final Image[][] scene = new Image[Room.ALL.length][State.values().length];

    /** Fredbear, drawn over a lit scene when he is in it. */
    public Image fredbear;

    /** One per threat key, plus Fredbear. */
    public Image scareBonnie, scareChica, scareFoxy, scareFreddy, scareFredbear;

    public static void load() {
        A = new Assets();
        A.scene[Room.Where.BED.ordinal()][State.DARK.ordinal()] = load("bed_dark");
        A.scene[Room.Where.BED.ordinal()][State.LIT.ordinal()] = load("bed_lit");
        A.scene[Room.Where.BED.ordinal()][State.HERE.ordinal()] = load("bed_here");
        A.scene[Room.Where.LEFT.ordinal()][State.DARK.ordinal()] = load("left_dark");
        A.scene[Room.Where.LEFT.ordinal()][State.LIT.ordinal()] = load("left_lit");
        A.scene[Room.Where.LEFT.ordinal()][State.HERE.ordinal()] = load("left_here");
        A.scene[Room.Where.RIGHT.ordinal()][State.DARK.ordinal()] = load("right_dark");
        A.scene[Room.Where.RIGHT.ordinal()][State.LIT.ordinal()] = load("right_lit");
        A.scene[Room.Where.RIGHT.ordinal()][State.HERE.ordinal()] = load("right_here");
        A.scene[Room.Where.CLOSET.ordinal()][State.DARK.ordinal()] = load("closet_dark");
        A.scene[Room.Where.CLOSET.ordinal()][State.LIT.ordinal()] = load("closet_lit");
        A.scene[Room.Where.CLOSET.ordinal()][State.HERE.ordinal()] = load("closet_here");

        A.fredbear = load("fredbear");
        A.scareBonnie = load("scare_bonnie");
        A.scareChica = load("scare_chica");
        A.scareFoxy = load("scare_foxy");
        A.scareFreddy = load("scare_freddy");
        A.scareFredbear = load("scare_fredbear");
    }

    /** The face to fill the screen with when this one gets you. */
    public Image scare(String killer) {
        if (killer == null) return scareFreddy;
        if (killer.contains("Fredbear")) return scareFredbear;
        if (killer.contains("Bonnie")) return scareBonnie;
        if (killer.contains("Chica")) return scareChica;
        if (killer.contains("Foxy")) return scareFoxy;
        return scareFreddy;
    }

    /** The scene for a station in a state, or null if the art is missing. */
    public Image scene(Room.Where w, State s) {
        return scene[w.ordinal()][s.ordinal()];
    }

    /**
     * Load by stem, trying the extensions in turn.
     *
     * Every scene is opaque and photographic, so it is JPEG; Fredbear needs
     * alpha and is PNG. The caller should not have to know which.
     */
    static Image load(String stem) {
        for (String ext : new String[]{".jpg", ".png"}) {
            try (InputStream in = Assets.class.getClassLoader()
                    .getResourceAsStream("fnaf4/images/" + stem + ext)) {
                if (in == null) continue;
                Image img = new Image(in);
                if (!img.isError()) return img;
            } catch (Exception ignored) { }
        }
        return null;
    }
}
