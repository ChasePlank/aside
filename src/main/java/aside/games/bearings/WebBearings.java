package aside.games.bearings;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generate the single-file phone build of bearings.
 *
 * The eighth of mine to get one, and the last: Bearings was the only game
 * of mine still on the desktop.
 *
 * WHAT IS NOT DUPLICATED. The template
 * (src/main/resources/bearings/web.html) holds the layout and the CSS. Every
 * fixed line the game says -- the opening, the rules, the weather lines, the
 * day's colour, the prompts, the book's labels, the sighting, the verdict,
 * the closing -- is generated from {@link Bearings}, the same class the
 * engine screen reads. The prose was moved out of {@link BearingsScreen}
 * into the model to make that true; the screen now only draws it.
 *
 * WHAT IS PORTED RATHER THAN RESOLVED. Handoff and Inventory could resolve
 * their whole simulation into a table because what they resolve is a rule
 * with a small domain. This one cannot: the player makes sixteen choices and
 * every one of them changes what the next day is, so the phone has to run
 * the voyage. The model is ported to JavaScript in the template, and it is
 * portable for one specific reason -- the draw is counter-based, not a
 * stream. Every number is a pure function of (seed, day, salt), so there is
 * no RNG position to carry and a seed replays a voyage exactly, here and in
 * Java. The arithmetic is BigInt because Java's {@code long} is 64-bit and a
 * JS number is not; a double would lose the low bits and the two builds
 * would diverge on the first roll.
 *
 * That is also what makes it checkable. {@code SelfTest} drives both models
 * through the same seeds and the same choices and compares the book, the
 * true run and the ending after every day -- see the trace in
 * {@code diag/trace/trace-bearings.js}. Two builds can both be
 * self-consistent and still disagree about what a voyage means.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.bearings.WebBearings [out.html]
 */
public final class WebBearings {

    static final String MARKER = "/*__CONTENT__*/";
    static final Path TEMPLATE = Path.of("src", "main", "resources", "bearings", "web.html");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "web/bearings.html");
        String html = html();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, html);
        System.out.println("wrote " + out + "  (" + html.length() / 1024 + " KB, "
                + Bearings.DAYS + " days)");
    }

    /** The generated file, as a string, so a test can compare it to the checked-in one. */
    public static String html() throws Exception {
        if (!Files.exists(TEMPLATE)) {
            throw new IllegalStateException("no template at " + TEMPLATE.toAbsolutePath()
                    + " -- run this from the repository root");
        }
        String t = Files.readString(TEMPLATE);
        int at = t.indexOf(MARKER);
        if (at < 0) throw new IllegalStateException("the template has no " + MARKER + " in it");
        return (t.substring(0, at) + content() + t.substring(at + MARKER.length()))
                .replace(aside.game.WebAudio.MARKER, aside.game.WebAudio.js());
    }

    // ------------------------------------------------------------- content

    static String content() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("\"wordmark\":").append(str(Bearings.WORDMARK)).append(",\n");
        b.append("\"where\":").append(str(Bearings.WHERE)).append(",\n");
        b.append("\"needed\":").append(num(Bearings.NEEDED)).append(",\n");
        b.append("\"days\":").append(Bearings.DAYS).append(",\n");
        b.append("\"tolerance\":").append(num(Bearings.TOLERANCE)).append(",\n");
        b.append("\"sightCost\":").append(Bearings.SIGHT_COST).append(",\n");
        // The rule's other knobs, and the drift table. These were literals in
        // the template, which meant the balance lived in two places and only
        // one of them was the game. Emitted here so a change to any of them
        // without a regeneration fails a check instead of shipping two
        // slightly different voyages.
        b.append("\"rule\":{\"clearBelow\":").append(Bearings.CLEAR_BELOW)
         .append(",\"fairBelow\":").append(Bearings.FAIR_BELOW)
         .append(",\"openFromDay\":").append(Bearings.SKY_OPENS_FROM_DAY)
         .append(",\"openAfter\":").append(Bearings.SKY_OPENS_AFTER)
         .append(",\"sharedCauseBelow\":").append(Bearings.SHARED_CAUSE_BELOW)
         .append(",\"aMovesBelow\":").append(Bearings.A_MOVES_BELOW)
         .append(",\"bMovesBelow\":").append(Bearings.B_MOVES_BELOW)
         .append(",\"lostCap\":").append(Bearings.LOST_PENALTY_CAP)
         .append(",\"lostDivisor\":").append(Bearings.LOST_PENALTY_DIVISOR)
         .append("},\n");
        b.append("\"driftValue\":").append(doubles(Bearings.DRIFT_VALUE)).append(",\n");
        b.append("\"driftWeight\":").append(doubles(Bearings.DRIFT_WEIGHT)).append(",\n");

        b.append("\"voice\":{\n");
        b.append("  \"opening\":").append(strList(Bearings.OPENING)).append(",\n");
        b.append("  \"rulesHeading\":").append(str(Bearings.RULES_HEADING)).append(",\n");
        b.append("  \"rules\":[");
        for (int i = 0; i < Bearings.RULES.length; i++) {
            String[] r = Bearings.RULES[i];
            if (i > 0) b.append(',');
            b.append("{\"key\":").append(str(r[0])).append(",\"text\":").append(str(r[1])).append('}');
        }
        b.append("],\n");

        b.append("  \"startLine\":").append(str(Bearings.START_LINE)).append(",\n");
        b.append("  \"goOn\":").append(str(Bearings.GO_ON)).append(",\n");
        b.append("  \"again\":").append(str(Bearings.AGAIN)).append(",\n");
        b.append("  \"btnBegin\":").append(str(Bearings.BTN_BEGIN)).append(",\n");
        b.append("  \"btnGoOn\":").append(str(Bearings.BTN_GO_ON)).append(", \n");
        b.append("  \"btnAgain\":").append(str(Bearings.BTN_AGAIN)).append(",\n");
        b.append("  \"dayOf\":").append(str(Bearings.DAY_OF)).append(",\n");
        b.append("  \"voyageOver\":").append(str(Bearings.VOYAGE_OVER)).append(",\n");
        b.append("  \"dayHint\":").append(str(Bearings.DAY_HINT)).append(",\n");

        b.append("  \"weather\":{\"FAIR\":").append(str(Bearings.WEATHER_FAIR))
         .append(",\"CLEAR\":").append(str(Bearings.WEATHER_CLEAR))
         .append(",\"FOUL\":").append(str(Bearings.WEATHER_FOUL)).append("},\n");
        b.append("  \"weatherName\":{\"FAIR\":").append(str(Bearings.weatherName(Bearings.Weather.FAIR)))
         .append(",\"CLEAR\":").append(str(Bearings.weatherName(Bearings.Weather.CLEAR)))
         .append(",\"FOUL\":").append(str(Bearings.weatherName(Bearings.Weather.FOUL))).append("},\n");
        b.append("  \"dayProse\":{\"FAIR\":").append(strList(Bearings.DAY_FAIR))
         .append(",\"CLEAR\":").append(strList(Bearings.DAY_CLEAR))
         .append(",\"FOUL\":").append(strList(Bearings.DAY_FOUL)).append("},\n");

        b.append("  \"promptNever\":").append(str(Bearings.PROMPT_NEVER)).append(",\n");
        b.append("  \"promptLong\":").append(str(Bearings.PROMPT_LONG)).append(",\n");
        b.append("  \"promptDefault\":").append(str(Bearings.PROMPT_DEFAULT)).append(",\n");

        b.append("  \"whatEachClock\":").append(str(Bearings.WHAT_EACH_CLOCK)).append(",\n");
        b.append("  \"clockA\":").append(str(Bearings.CLOCK_A)).append(",\n");
        b.append("  \"clockB\":").append(str(Bearings.CLOCK_B)).append(",\n");
        b.append("  \"takeSight\":").append(str(Bearings.TAKE_SIGHT)).append(",\n");
        b.append("  \"skyOpen\":").append(str(Bearings.SKY_OPEN)).append(",\n");
        b.append("  \"skyClosed\":").append(str(Bearings.SKY_CLOSED)).append(",\n");
        b.append("  \"miles\":").append(str(Bearings.MILES)).append(",\n");

        b.append("  \"bookHead\":").append(str(Bearings.BOOK_HEAD)).append(",\n");
        b.append("  \"clocksHead\":").append(str(Bearings.CLOCKS_HEAD)).append(",\n");
        b.append("  \"ofMiles\":").append(str(Bearings.OF_MILES)).append(",\n");
        b.append("  \"rowLastLooked\":").append(str(Bearings.ROW_LAST_LOOKED)).append(",\n");
        b.append("  \"rowAndFound\":").append(str(Bearings.ROW_AND_FOUND)).append(",\n");
        b.append("  \"rowLooksUsed\":").append(str(Bearings.ROW_LOOKS_USED)).append(",\n");
        b.append("  \"rowDaysLeft\":").append(str(Bearings.ROW_DAYS_LEFT)).append(",\n");
        b.append("  \"never\":").append(str(Bearings.NEVER)).append(",\n");
        b.append("  \"ahead\":").append(str(Bearings.AHEAD)).append(",\n");
        b.append("  \"behind\":").append(str(Bearings.BEHIND)).append(",\n");
        b.append("  \"foundExact\":").append(str(Bearings.FOUND_EXACT)).append(",\n");
        b.append("  \"rateNote\":").append(str(Bearings.RATE_NOTE)).append(",\n");
        b.append("  \"ratedAt\":").append(str(Bearings.RATED_AT)).append(",\n");
        b.append("  \"inPort\":").append(str(Bearings.IN_PORT)).append(",\n");

        b.append("  \"sightHead\":").append(str(Bearings.SIGHT_HEAD)).append(",\n");
        b.append("  \"sightWas\":").append(str(Bearings.SIGHT_WAS)).append(",\n");
        b.append("  \"sightExact\":").append(str(Bearings.SIGHT_EXACT)).append(",\n");
        b.append("  \"sightAhead\":").append(str(Bearings.SIGHT_AHEAD)).append(",\n");
        b.append("  \"sightBehind\":").append(str(Bearings.SIGHT_BEHIND)).append(",\n");
        b.append("  \"sightRerated\":").append(str(Bearings.SIGHT_RERATED)).append(",\n");
        b.append("  \"sightProse\":").append(str(Bearings.SIGHT_PROSE)).append(",\n");

        b.append("  \"voyageHead\":").append(str(Bearings.VOYAGE_HEAD)).append(",\n");
        b.append("  \"voyageCols\":").append(str(Bearings.VOYAGE_COLS)).append(",\n");
        b.append("  \"looked\":").append(str(Bearings.LOOKED)).append(",\n");

        b.append("  \"headFound\":").append(str(Bearings.HEAD_FOUND)).append(",\n");
        b.append("  \"headShort\":").append(str(Bearings.HEAD_SHORT)).append(",\n");
        b.append("  \"headPast\":").append(str(Bearings.HEAD_PAST)).append(",\n");
        b.append("  \"headSeason\":").append(str(Bearings.HEAD_SEASON)).append(",\n");
        b.append("  \"verdictFound\":").append(str(Bearings.VERDICT_FOUND)).append(",\n");
        b.append("  \"verdictShort\":").append(str(Bearings.VERDICT_SHORT)).append(",\n");
        b.append("  \"verdictPast\":").append(str(Bearings.VERDICT_PAST)).append(",\n");
        b.append("  \"verdictSeason\":").append(str(Bearings.VERDICT_SEASON)).append(",\n");

        b.append("  \"closingNone\":").append(str(Bearings.CLOSING_NONE)).append(",\n");
        b.append("  \"closingOne\":").append(str(Bearings.CLOSING_ONE)).append(",\n");
        b.append("  \"closingTwo\":").append(str(Bearings.CLOSING_TWO)).append(",\n");
        b.append("  \"closingMany\":").append(str(Bearings.CLOSING_MANY)).append(",\n");
        b.append("  \"closingShared\":").append(str(Bearings.CLOSING_SHARED)).append(",\n");

        // The lines that depend on a number, emitted once per number rather
        // than written into the build by hand.
        b.append("  \"ageToday\":").append(str(Bearings.ageLabel(0))).append(",\n");
        b.append("  \"ageYesterday\":").append(str(Bearings.ageLabel(1))).append(",\n");
        b.append("  \"ageDays\":").append(str(Bearings.ageLabel(2).replace("2", "%s"))).append("\n");
        b.append("}\n}\n");
        return b.toString();
    }

    // ------------------------------------------------------------- helpers

    static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** A Java double array as a JSON array. */
    static String doubles(double[] xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) b.append(',');
            b.append(xs[i]);
        }
        return b.append(']').toString();
    }

    static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    static String strList(String[] xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) b.append(',');
            b.append(str(xs[i]));
        }
        return b.append(']').toString();
    }

    private WebBearings() { }
}
