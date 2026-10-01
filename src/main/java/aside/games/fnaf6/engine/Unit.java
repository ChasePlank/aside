package aside.games.fnaf6.engine;

/**
 * One salvaged animatronic, and the one number that makes it that one.
 *
 * <p>Every unit in the bay is the same shape of problem -- something in a
 * chair, in the dark, that may or may not get up -- and they differ in
 * exactly one way: <b>how often they move without making a sound.</b>
 *
 * <p>That is the whole cast design, and it is deliberate. FNAF 5's three
 * threats differed in <i>what they followed</i>, which made the night a
 * question about which counter to use. FNAF 6's units differ in
 * <i>whether you can trust your ears</i>, which makes the night a question
 * about whether you can afford to look. A loud unit is a unit that tells
 * you where it is; a quiet one is a unit that makes you spend the lamp
 * blind, and the lamp is the thing it is waiting for.
 *
 * <pre>
 *   THE RULE: the lamp is the only way to see it, and the lamp is what it
 *             is waiting for.
 * </pre>
 *
 * <p>{@link #silent} is the chance that one of its advances makes no sound
 * at all. It is a chance rather than a switch because a unit that is
 * <i>always</i> silent is a unit with no audio channel at all, and a unit
 * that is never silent is a unit the audio channel solves completely. The
 * middle is the game: <b>a silent advance is an advance you did not
 * count</b>, so your count drifts, and the only way to correct it is the
 * one action that brings the thing closer.
 */
public record Unit(String key, String name, String note, double silent) {

    /**
     * The four units, in the order the bay receives them, and the week is
     * the order of how quiet they are.
     *
     * <p>Scraptrap drags the chair leg on concrete almost every time it
     * moves, so a player who listens can follow it exactly. Scrap Baby is
     * louder and faster. Molten Freddy is a tangle of cable and
     * endoskeleton and gets a step in silently about two times in five.
     * Lefty barely moves at all and barely sounds like it when it does --
     * it is the quietest thing in the franchise, and it is the last thing
     * in the bay.
     *
     * <p>Night five is the first one, back. It has been in the building
     * since night one and it has learned the room: it is the quietest
     * thing you will ever see in that chair, and it is the fastest.
     */
    public static final Unit SCRAPTRAP =
            new Unit("scraptrap", "Scraptrap",
                    "Drags the chair leg. You will hear it coming.", 0.20);
    public static final Unit SCRAP_BABY =
            new Unit("scrapbaby", "Scrap Baby",
                    "Loud, and fast with it.", 0.35);
    public static final Unit MOLTEN_FREDDY =
            new Unit("moltenfreddy", "Molten Freddy",
                    "A tangle of cable. It gets a step in quietly.", 0.45);
    public static final Unit LEFTY =
            new Unit("lefty", "Lefty",
                    "Barely moves, and barely sounds like it when it does.", 0.55);
    public static final Unit SCRAPTRAP_BACK =
            new Unit("scraptrap", "Scraptrap",
                    "The first one, back. It has been listening.", 0.65);

    /** The unit the bay receives on a given night. */
    public static Unit forNight(int night) {
        return switch (night) {
            case 1 -> SCRAPTRAP;
            case 2 -> SCRAP_BABY;
            case 3 -> MOLTEN_FREDDY;
            case 4 -> LEFTY;
            default -> SCRAPTRAP_BACK;
        };
    }
}
