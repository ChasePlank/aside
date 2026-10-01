package aside.games.fnaf9;

import aside.games.fnaf9.engine.Feed;
import aside.games.fnaf9.engine.Pair;

/**
 * Every fixed sentence the FNAF 9 office says, in one place.
 *
 * <p>No JavaFX in this file, on purpose. {@link WebFeed} has to emit the same
 * words the desktop draws, and the only way to guarantee that is for there to
 * be one copy of them -- a phone build that agrees with the desktop by luck is
 * a phone build that will disagree the first time a line is edited.
 *
 * <p>This is the same move Testimony, Handoff, Inventory, Bearings and Tell
 * each needed when they were ported: the strings were literals inside the
 * screen, and the port pushed them out into the model so both builds read
 * them. The screens still own their <i>layout</i> -- where a line sits, how
 * big it is, what colour it is -- and nothing here knows about any of that.
 *
 * <p>Templates carry {@code %d} / {@code %s} / {@code %.1f} because the two
 * builds fill them differently: the desktop uses {@code String.format} and the
 * phone uses a plain replace. A literal percent is written {@code %%} for the
 * desktop's benefit and unescaped once, in the generator, for the phone's.
 */
public final class Voice {

    // ---- the night select -------------------------------------------------

    public static final String TITLE = "FIVE NIGHTS AT FREDDY'S 9";
    public static final String SUBTITLE =
            "Two halls, one door and one monitor -- and the picture is "
                    + "as old as the time you have spent watching it.";

    /** Night 3 -- Phantom Foxy and Phantom Puppet. The arrow is the desktop's. */
    public static final String NIGHT_ROW = "Night %d  \u2014  %s and %s";
    public static final String NIGHT_ROW_PHONE = "Night %d  \u2014  %s and %s";

    public static final String HELP_1 =
            "A / D or MON to watch a hall     SPACE or HOLD to bring "
                    + "the door down";
    public static final String HELP_2 =
            "The monitor is a delay line: a glance is live and a stare "
                    + "is a photograph.";
    public static final String HELP_3 =
            "Past its own patience with a bad picture a walker is not "
                    + "drawn at all -- so the longer you watch,";
    public static final String HELP_4 =
            "the emptier the hall looks. The sensor on the door is the "
                    + "only thing here that never lies.";
    public static final String HELP_5 =
            "click a night or ENTER to start      ESC back to the library";

    /** The two lines that are about the device rather than about the night. */
    public static final String HELP_1_PHONE =
            "Tap a hall to watch it. Tap HOLD to bring the door down.";
    public static final String HELP_5_PHONE = "Tap a night to start.";

    // ---- the band ---------------------------------------------------------

    public static final String BAND_NIGHT = "NIGHT %d";
    public static final String FEED_HEAD = "THE PICTURE IS";
    public static final String SENSOR_HEAD = "DOOR SENSOR";

    /** The sensor's three words. It is one bit, so there are three of them. */
    public static final String SENSOR_NOT_DOWN = "NOT DOWN";
    public static final String SENSOR_TOUCHING = "SOMETHING AGAINST IT";
    public static final String SENSOR_CLEAR = "CLEAR";

    // ---- the monitor ------------------------------------------------------

    public static final String MON_OFF_1 = "MONITOR OFF";
    public static final String MON_OFF_2 = "nothing is being recorded";
    public static final String FADED_1 = "PICTURE TOO OLD";
    public static final String FADED_2 = "the feed is not holding anything at %.1fs";
    /** "LEFT HALL" / "RIGHT HALL", bottom left of the screen. */
    public static final String HALL = "%s HALL";

    // ---- the strip --------------------------------------------------------

    public static final String BTN_MON_LEFT = "MON LEFT";
    public static final String BTN_MON_RIGHT = "MON RIGHT";
    public static final String BTN_DARK = "DARK";
    public static final String BTN_HOLD = "HOLD DOOR";

    public static final String CIRCUIT_JAMMED =
            "the mechanism has let go -- it will not take "
                    + "another hold until it cools";
    public static final String CIRCUIT_DARK =
            "everything off -- the feed is catching up";
    public static final String CIRCUIT_WATCH =
            "watching the %s hall -- the picture is %.1fs old";
    public static final String CIRCUIT_HOLD =
            "the door is coming down -- you are blind while it is";
    public static final String KEYS =
            "A / D  watch      SPACE  dark      S  hold the door      ESC  pause";

    // ---- the two endings --------------------------------------------------

    public static final String TAKEN_HEAD = "IT WAS IN THE DOORWAY";
    public static final String TAKEN_LINE = "The picture said you had time.";
    public static final String TAKEN_HINT = "ESC to pause, then Quit to Menu";
    public static final String WIN_HEAD = "6 AM";
    public static final String WIN_LINE =
            "Nothing came through the door. The halls are empty again.";
    public static final String WIN_HINT = "ESC to pause, then Quit to Menu";

    /** The phone's way out of a finished night. */
    public static final String BACK_TO_NIGHTS = "Back to the nights";

    // ---- helpers the two builds share -------------------------------------

    /** What the one circuit is doing, as the strip says it. */
    public static String circuitLine(Feed g) {
        if (g.jammed) return CIRCUIT_JAMMED;
        return switch (g.circuit) {
            case DARK -> CIRCUIT_DARK;
            case MON_LEFT -> String.format(CIRCUIT_WATCH, "left", g.feed);
            case MON_RIGHT -> String.format(CIRCUIT_WATCH, "right", g.feed);
            case HOLD -> CIRCUIT_HOLD;
        };
    }

    /** The sensor's word, given the door's state. */
    public static String sensorWord(Feed g) {
        if (!g.blocking()) return SENSOR_NOT_DOWN;
        return g.sensor() ? SENSOR_TOUCHING : SENSOR_CLEAR;
    }

    /** A night's row, as the select screen and the phone both write it. */
    public static String nightRow(int night) {
        Pair p = Pair.forNight(night);
        return String.format(NIGHT_ROW, night, p.left().name(), p.right().name());
    }

    private Voice() {}
}
