package aside.games.fnaf8.engine;

/**
 * One half of a pair, and the two numbers that make it that half.
 *
 * <p>Every unit in this office is the same shape of problem -- something
 * walking up a hall toward a doorway it is not allowed to share -- and they
 * differ in exactly two ways: <b>how fast it walks</b> and <b>how much lamp
 * it takes to turn it around.</b>
 *
 * <p>That is the whole cast design, and it is deliberately a different pair
 * of axes from FNAF 7's. FNAF 5's threats differed in <i>what they
 * followed</i>, which made the night a question about which counter to use.
 * FNAF 6's units differed in <i>whether you could trust your ears</i>. FNAF
 * 7's differed in <i>whether the ears told you which side</i>, which is the
 * one thing its lamp was for. FNAF 8's differ in <b>speed and stubbornness</b>,
 * and those are the two numbers the lamp is spent against -- so a pair is not
 * two threats, it is <i>one budget with two prices on it.</i>
 *
 * <pre>
 *   THE RULE: they are not coming for you. They are coming for each other.
 * </pre>
 *
 * <p>{@link #pace} is a multiplier on the night's base step interval, so a
 * unit at 1.25 walks a quarter again as fast as the night it is on. {@link
 * #stubborn} multiplies the time the lamp needs to push it one step back, so
 * a unit at 1.6 is one the beam has to be held on for half again as long.
 * The two are independent on purpose: <b>the fast one is not the stubborn
 * one</b>, and a night where they were the same unit twice would be a night
 * with one threat in it wearing two names.
 */
public record Unit(String key, String name, String note,
                   double speed, double stubborn) {

    /**
     * The week, as five pairs.
     *
     * <p>Chase asked for the franchise by name with "each gets harder than
     * the last", and the ramp here is not only in the tables -- it is in
     * <b>who is walking</b>. Night one is two bandmates who move at the same
     * speed and take the same lamp. By night five one of them is fast and the
     * other is stubborn, and the pair that used to be one problem is two
     * problems that want the same pair of hands.
     */
    public static final Unit BONNIE =
            new Unit("bonnie", "Bonnie", "Walks like he is not in a hurry.", 0.95, 0.85);
    public static final Unit CHICA =
            new Unit("chica", "Chica", "A shade quicker, and just as light.", 1.05, 0.80);
    public static final Unit FREDDY =
            new Unit("freddy", "Freddy", "Slow, and the lamp barely moves him.", 0.95, 1.35);
    public static final Unit FOXY =
            new Unit("foxy", "Foxy", "Fast, and easy to turn around.", 1.30, 0.85);
    public static final Unit TOY_BONNIE =
            new Unit("toybonnie", "Toy Bonnie", "Newer, and quicker for it.", 1.05, 1.00);
    public static final Unit TOY_CHICA =
            new Unit("toychica", "Toy Chica", "Quicker still, and just as light.", 1.12, 0.95);
    public static final Unit GOLDEN_FREDDY =
            new Unit("goldenfreddy", "Golden Freddy",
                    "It does not walk so much as arrive.", 1.05, 1.55);
    public static final Unit PUPPET =
            new Unit("puppet", "The Puppet",
                    "It has been doing this longer than the building has.", 1.25, 1.30);
    public static final Unit SPRINGTRAP =
            new Unit("springtrap", "Springtrap",
                    "Something is still in there, and it is still fast.", 1.30, 1.30);
    public static final Unit NIGHTMARE =
            new Unit("nightmare", "Nightmare",
                    "The lamp does not so much push it as annoy it.", 1.25, 1.75);

    /** Every unit, for the dev hooks and the checks. */
    public static Unit[] all() {
        return new Unit[]{BONNIE, CHICA, FREDDY, FOXY, TOY_BONNIE, TOY_CHICA,
                GOLDEN_FREDDY, PUPPET, SPRINGTRAP, NIGHTMARE};
    }
}
