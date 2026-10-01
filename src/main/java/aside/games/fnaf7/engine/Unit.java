package aside.games.fnaf7.engine;

/**
 * One thing that has been watching you, and the one number that makes it
 * that one.
 *
 * <p>Every unit in the office is the same shape of problem -- something
 * that comes to the side you are not looking at -- and they differ in
 * exactly one way: <b>how often you can hear it arrive.</b>
 *
 * <p>That is the whole cast design, and it is deliberate. FNAF 5's threats
 * differed in <i>what they followed</i>, which made the night a question
 * about which counter to use. FNAF 6's units differed in <i>whether you
 * could trust your ears</i>, and the drag told you how far it had got.
 * FNAF 7's units differ in whether the ears tell you <b>which side</b>,
 * which is a different piece of information: it is the one thing the light
 * is for, so a unit you can hear is a unit you can answer without spending
 * a look -- and spending looks is the only thing that keeps the record
 * sharp enough to be worth reading.
 *
 * <pre>
 *   THE RULE: it does not have a pattern. It has yours.
 * </pre>
 *
 * <p>{@link #tell} is the chance that stepping into a hall makes a sound
 * you can place. It is a chance rather than a switch because a unit that
 * is <i>always</i> audible is a unit the light is never needed for, and a
 * unit that is never audible is a unit whose night is a night with no
 * audio channel at all. The middle is the game: <b>a silent arrival is an
 * arrival you did not place</b>, so the only way to know is to look, and
 * looking is what the record is made of.
 */
public record Unit(String key, String name, String note, double tell) {

    /**
     * The five, in the order the office receives them, and the week is the
     * order of how quiet they are.
     *
     * <p>Circus Baby is the loudest thing in the building and does not
     * care: you will hear her cross the floor and you will have time to
     * decide. Ennard is a composite and moves like several things at once,
     * which is loud in a way that is hard to place. Glitchtrap does not
     * have feet, and the sound it makes is the sound of the room being
     * wrong. Vanny is quiet and deliberate and gets a step in silently
     * more often than not.
     *
     * <p>Night five is the Mimic, and it is the one that has been in the
     * office since night one. It is not imitating anyone tonight: it has
     * been copying you, and the last night of the week is the night it
     * stops needing to be told which side you are on.
     */
    public static final Unit CIRCUS_BABY =
            new Unit("baby", "Circus Baby",
                    "You will hear her cross the floor.", 0.75);
    public static final Unit ENNARD =
            new Unit("ennard", "Ennard",
                    "Several things moving at once. Loud, and hard to place.", 0.55);
    public static final Unit GLITCHTRAP =
            new Unit("glitchtrap", "Glitchtrap",
                    "It does not have feet. The room is what sounds wrong.", 0.40);
    public static final Unit VANNY =
            new Unit("vanny", "Vanny",
                    "Quiet, and deliberate about it.", 0.28);
    public static final Unit MIMIC =
            new Unit("mimic", "The Mimic",
                    "It is not imitating anyone tonight.", 0.15);

    /** The unit the office receives on a given night. */
    public static Unit forNight(int night) {
        return switch (night) {
            case 1 -> CIRCUS_BABY;
            case 2 -> ENNARD;
            case 3 -> GLITCHTRAP;
            case 4 -> VANNY;
            default -> MIMIC;
        };
    }

    /** Every unit, for the dev hooks and the checks. */
    public static Unit[] all() {
        return new Unit[]{CIRCUS_BABY, ENNARD, GLITCHTRAP, VANNY, MIMIC};
    }
}
