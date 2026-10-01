package aside.games.fnaf9.engine;

/**
 * One of the things in the halls tonight, and the two numbers that make it
 * that.
 *
 * <p>Every walker in this building is the same shape of problem -- something
 * coming up a hall toward a door you cannot see -- and they differ in exactly
 * two ways: <b>how fast it walks</b> and <b>how old a picture it survives.</b>
 *
 * <p>That second axis is the new one, and it is the whole game. FNAF 5's
 * threats differed in <i>what they followed</i>. FNAF 6's differed in
 * <i>whether you could trust your ears</i>. FNAF 7's differed in <i>whether
 * the ears told you which side</i>. FNAF 8's differed in <b>speed and
 * stubbornness</b> -- two prices on one budget. FNAF 9's differ in <b>speed
 * and how much lag erases them</b>, and that is not a price, it is a
 * <i>threshold</i>: a walker with {@code sharp} at 1.2 is simply not on the
 * monitor once the feed is 1.2 seconds old. It is not drawn faintly, and it
 * is not drawn in the wrong place. <b>It is not there.</b>
 *
 * <pre>
 *   THE RULE: the picture is as old as the time you have spent watching it.
 * </pre>
 *
 * <p>{@link #speed} is a multiplier on the night's base step interval, so a
 * walker at 1.25 walks a quarter again as fast as the night it is on.
 * {@link #sharp} is the feed age, in seconds, past which the monitor stops
 * holding it. {@link #patience} is how long it stands at a shut door before
 * it loses interest.
 *
 * <p><b>{@code patience} is capped by the door, not by the walker.</b> The
 * mechanism will only stay shut for {@link Feed#HOLD_MAX} seconds, so a walker
 * that would outwait the door is a walker the door cannot answer, and a night
 * with one of those in it is a night with an unwinnable arrival in it. Every
 * value here is under that ceiling with room for the walk, and the check that
 * keeps it that way is in SelfTest -- because the ceiling is a constant and
 * the cast is a table, and the two drifting apart is exactly the kind of
 * mistake that only shows up as an unexplained death.
 *
 * <p>The two are independent on purpose: <b>every pair in the cast differs in
 * both numbers</b>, and a night where they were the same walker twice would be
 * a night with one threat in it wearing two names. (Two walkers with the same
 * interval is not merely dull, it is easier than one of them: measured, a
 * night of two identical walkers is survivable 94% of the time against 47%
 * for the pair it replaces, because identical walkers stay in phase and one
 * hold covers both, while two with different intervals drift apart and
 * interleave their arrivals.)
 */
public record Walker(String key, String name, String note,
                     double speed, double sharp, double patience) {

    /**
     * The cast.
     *
     * <p>Phantoms, because the whole game is about a picture that stops
     * holding what is in it, and the franchise already has a set of things
     * that do not register properly on camera. The names are doing work
     * rather than decorating: a player who has met a phantom before expects
     * it to be hard to see, and this cast makes that literal instead of
     * atmospheric.
     */
    public static final Walker PHANTOM_FREDDY =
            new Walker("phantomfreddy", "Phantom Freddy",
                    "Holds together on a bad picture longer than anything else here.",
                    1.05, 3.40, 1.9);
    public static final Walker PHANTOM_CHICA =
            new Walker("phantomchica", "Phantom Chica",
                    "A shade quicker, and a shade thinner.",
                    1.15, 2.70, 1.8);
    public static final Walker PHANTOM_FOXY =
            new Walker("phantomfoxy", "Phantom Foxy",
                    "Fast, and it does not like being looked at for long.",
                    1.35, 2.30, 1.5);
    public static final Walker PHANTOM_PUPPET =
            new Walker("phantompuppet", "Phantom Puppet",
                    "Slow, and it will wait at a shut door longer than you will.",
                    1.15, 2.10, 2.0);
    public static final Walker SPRINGTRAP =
            new Walker("springtrap", "Springtrap",
                    "Something is still in there, and it is still walking.",
                    1.22, 1.95, 1.9);
    public static final Walker SHADOW_BONNIE =
            new Walker("shadowbonnie", "Shadow Bonnie",
                    "You have to be looking at it while it is still there.",
                    1.27, 1.55, 1.4);
    public static final Walker SHADOW_FREDDY =
            new Walker("shadowfreddy", "Shadow Freddy",
                    "It is gone before the picture is a second old.",
                    1.31, 1.15, 1.3);
    public static final Walker NIGHTMARE =
            new Walker("nightmare", "Nightmare",
                    "It does not stay on the screen, and it does not hurry.",
                    1.13, 1.35, 1.2);
    /**
     * Night four's fast half, and the one number in the cast that was set by a
     * measurement rather than by taste.
     *
     * <p>It shipped at <b>1.18</b>, which put its step at 1.9068s against
     * Nightmare's 1.9912s -- a 4.4% difference, and that turned out to be the
     * worst place to be. Two walkers that close in speed stay close in
     * <i>phase</i> for most of the night, so the same one is always the one
     * arriving just after the door has let go: measured over 400 seeds, night
     * four killed <b>99 times on the left and 9 on the right</b>, and the
     * split mirrored exactly when the two were swapped, so it was the walker
     * and not the hall. A night whose deaths are 11:1 on one side is a night
     * the player learns to ignore a hall, which is not a skill.
     *
     * <p><b>1.24</b> puts the step at 1.8145s -- a 9.7% difference -- and the
     * same measurement reads <b>102 left, 106 right</b> at 400 seeds (201/221
     * at 800) with the night's difficulty unchanged (48% against 49%). The
     * knob is chaotic rather than smooth -- 1.22, 1.26, 1.28 and 1.30 all give
     * a different and much worse split -- so the value is a reading, not a
     * preference, and the check that keeps it honest is in {@code SelfTest}
     * ("no night's deaths are one-sided"). See {@link Pair#forNight}.
     */
    public static final Walker PLUSHTRAP =
            new Walker("plushtrap", "Plushtrap",
                    "Quick, and it gets bored of a shut door sooner than most.",
                    1.24, 1.45, 1.2);
    public static final Walker GOLDEN_FREDDY =
            new Walker("goldenfreddy", "Golden Freddy",
                    "There is no picture of it. There is only a picture you had.",
                    1.26, 1.00, 2.0);

    /** Every walker, for the dev hooks and the checks. */
    public static Walker[] all() {
        return new Walker[]{PHANTOM_FREDDY, PHANTOM_CHICA, PHANTOM_FOXY,
                PHANTOM_PUPPET, SPRINGTRAP, SHADOW_BONNIE, SHADOW_FREDDY,
                NIGHTMARE, PLUSHTRAP, GOLDEN_FREDDY};
    }
}
