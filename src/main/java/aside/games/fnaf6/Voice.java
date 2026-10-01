package aside.games.fnaf6;

/**
 * Every fixed sentence the salvage bay says, in one place.
 *
 * <p>This exists because FNAF 6 now has two builds -- the JavaFX screen and
 * the phone page -- and the phone is generated from Java. A sentence typed
 * twice is a sentence that drifts, and the sentences that matter here are
 * the ones the player reads <i>for information</i>: what the lamp is
 * showing, whether it is up, and what the shock will reach. A phone that
 * phrased those differently would not be a port of this game, it would be
 * a second game that happens to share a chair.
 *
 * <p>The rule is the one tell's phone build wrote down: <b>the phone
 * substitutes a number into a template and never composes a sentence.</b>
 * Anything with a {@code %d} in it is a template; everything else is a
 * fixed line, and both are read from here by both builds.
 *
 * <p>No JavaFX in this file, on purpose. {@link aside.games.fnaf6.WebSalvage}
 * generates the phone page and has to be able to read these without a
 * display, which is the same reason {@link MouseMap} is pure geometry.
 */
public final class Voice {

    // ---- The night-select screen ----------------------------------------

    public static final String TITLE = "FIVE NIGHTS AT FREDDY'S 6";
    public static final String SUBTITLE =
            "One chair, one lamp, one shock -- and the lamp is what it is "
                    + "waiting for.";

    /** One night row. {@code %d} is the night, {@code %s} the unit's name. */
    public static final String NIGHT_ROW = "Night %d  \u2014  %s";

    public static final String SELECT_HELP_1 =
            "L or click LAMP to light it     SPACE or click SHOCK to discharge";
    public static final String SELECT_HELP_2 =
            "It only gets up if it is going to. The lamp is the only way to "
                    + "see it, and the lamp is what wakes it.";
    public static final String SELECT_HELP_3 =
            "The shock only reaches something that is already standing. "
                    + "You have one.";
    public static final String SELECT_HELP_4 =
            "click a night or ENTER to start      ESC back to the library";

    // ---- The phone's own fixed lines -------------------------------------
    //
    // Four lines name the controls, and the controls are the one thing the
    // two builds genuinely do not share: a phone has no L, no SPACE and no
    // ESC, and a page that told a player to press them would be a page
    // describing a different machine. They live here rather than in the
    // template for the same reason everything else does -- so there is one
    // place to read the bay's words from.

    public static final String SELECT_HELP_1_PHONE =
            "tap LAMP to light it     tap SHOCK to discharge";
    public static final String SELECT_HELP_4_PHONE = "tap a night to start";
    public static final String HINT_IDLE_PHONE =
            "tap LAMP to light it. a drag is the sound of it moving; a creak "
                    + "is the building.";
    public static final String HINT_OVER_PHONE = "the night is over.";

    // ---- The band --------------------------------------------------------

    public static final String BAND_NIGHT = "NIGHT %d";
    public static final String BAND_CHAIR = "IN THE CHAIR";
    public static final String METER_LABEL = "AGITATION";
    public static final String SHOCK_READY = "SHOCK  x1";
    public static final String SHOCK_SPENT = "SHOCK  SPENT";

    /** The clock, indexed by the hour. Six is the end of the night. */
    public static final String[] CLOCKS =
            {"12 AM", "1 AM", "2 AM", "3 AM", "4 AM", "5 AM", "6 AM"};

    // ---- The strip -------------------------------------------------------

    public static final String STRIP_LABEL = "THE LAMP IS SHOWING";
    /** The lamp is on and has not warmed yet. A flash is not a look. */
    public static final String WARMING = "warming\u2026";
    public static final String DARK = "nothing. it is dark.";

    public static final String LAMP_ON = "LAMP  ON";
    public static final String LAMP_OFF = "LAMP  OFF";

    public static final String HINT_OVER = "ESC to the menu";
    public static final String HINT_UP =
            "it is up. the shock reaches it now -- and not before.";
    public static final String HINT_LIT =
            "the lamp is what it is waiting for. every second of it is charged.";
    public static final String HINT_COSTLY =
            "the lamp has cost a lot. it does not have to be lit to be "
                    + "listened to.";
    public static final String HINT_IDLE =
            "L lamp     SPACE shock     ESC pause     a drag is the sound of "
                    + "it moving; a creak is the building.";

    /** The two counters, at the right, where a player can ignore them. */
    public static final String COUNTS =
            "looks %d     drags heard %d     silent %d";

    // ---- The end ---------------------------------------------------------

    public static final String SCARE_OVER = "GAME OVER";
    public static final String SCARE_BACK = "ESC to the menu";
    /** The way out of a phone build. There is no ESC on a phone. */
    public static final String BACK_TO_NIGHTS = "Back to the nights";

    public static final String WIN_DESTROYED = "SALVAGE DESTROYED";
    public static final String WIN_SURVIVED = "6 AM";
    public static final String WIN_DESTROYED_LINE = "It got up, and you were ready.";
    public static final String WIN_SURVIVED_LINE = "Night %d survived. It never moved.";
    public static final String WIN_STATS =
            "%d looks, %d drags heard, %d steps taken without a sound.";
    public static final String WIN_COST =
            "The lamp cost %d%% of what it takes to wake it.";

    private Voice() {}
}
