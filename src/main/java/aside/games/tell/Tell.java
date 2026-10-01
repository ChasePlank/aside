package aside.games.tell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * tell, the model.
 *
 * The seventeenth verb game, and the first one in this library with an
 * adversary in it. Every other game here is a situation: a ledger, a record,
 * two clocks, a night of questions. This one is a *thing* -- something in the
 * house that cannot see you, is listening to you move, and is getting better
 * at you the longer you take.
 *
 * THE RULE THE WHOLE THING RESTS ON. It does not look for you. It predicts
 * you. It counts how many times you have gone north, east, south and west --
 * not from any particular room, from anywhere -- and it expects the directions
 * you use most. If you go where it expects, it learns you faster. If you go
 * somewhere it did not expect, it loses ground.
 *
 * WHY THAT IS A GAME AND NOT A PUZZLE WITH ONE ANSWER. The door is in a fixed
 * corner, so getting to it means using two directions over and over, and those
 * are the two it learns. The only way to surprise it is to use a direction you
 * have been using *less* -- which, in a house with one door, is usually the
 * way back. Your goal is the thing that makes you predictable, and the game is
 * the price of not being. At reach 3 there is exactly one direction left that
 * surprises it, and it is your least-used one, so the whole night is a
 * question about which way you can still afford to spend.
 *
 * WHAT IS FIXED AND WHAT IS NOT. The five nights, the sizes, the read
 * arithmetic and the reach are fixed. What the seed decides is which corners
 * you start and finish in, and which rooms are lit. The lit rooms are the
 * other half of the squeeze: they cost read on entry, so the cheap route and
 * the unpredictable route are usually two different routes.
 *
 * WHY IT IS FAIR. The house's model of you is on the wall -- four numbers, one
 * per direction, and the ones it expects are marked. Nothing about its
 * reasoning is hidden, which is the point: you are not being out-thought, you
 * are being *read*, and the game shows you the reading. See
 * aside.games.tell.SelfTest: it holds the expectation rule to the prose, and
 * it checks that a player who spreads their moves across all four directions
 * beats a player who leans on two, which is the only claim the game makes.
 */
public final class Tell {

    public static final String WORDMARK = "Tell";
    public static final String WHERE = "the house";
    public static final String WHERE_REPORT = "the far door";

    public static final int NIGHTS = 5;
    /** At 100 it does not have to look. */
    public static final int READ_MAX = 100;
    public static final int DIRS = 4;
    public static final int[] DX = { 0, 1, 0, -1 };
    public static final int[] DY = { -1, 0, 1, 0 };
    public static final String[] DIR_NAME = { "north", "east", "south", "west" };
    public static final String[] DIR_KEY = { "N", "E", "S", "W" };

    /**
     * The five nights.
     *
     * REACH is how many directions it covers, and it is the whole difficulty
     * curve: at reach 1 there are three ways to surprise it, at reach 3 there
     * is exactly one, and the one is whichever direction you have spent least.
     * BASE is what a turn costs you whether or not it read you, MISS is what
     * surprising it gives back, and HIT is what being read costs -- so a
     * player who is never read is on a clock of BASE - MISS a turn, and a
     * player who is read every turn is on a clock of BASE + HIT.
     */
    static final int[] SIZE  = { 5, 5, 6, 6, 7 };
    static final int[] REACH = { 1, 2, 2, 3, 3 };
    static final int[] BASE  = { 2, 2, 2, 2, 3 };
    static final int[] MISS  = { 4, 4, 4, 4, 4 };
    static final int[] HIT   = { 12, 12, 13, 13, 13 };
    static final int[] LIT   = { 8, 8, 9, 9, 10 };
    static final int[] LAMPS = { 3, 4, 5, 6, 7 };
    static final int[] LIMIT = { 16, 18, 22, 26, 34 };

    // ------------------------------------------------------------- the deal

    /**
     * Counter-based, not a stream: every number is a pure function of
     * (seed, salt), so there is no RNG position to carry and a phone build
     * could compute the same house without porting java.util.Random. Same
     * arithmetic as drift, corroboration and interval, on purpose -- one
     * mixing function in the library is one thing to check.
     */
    static long mix(long seed, int salt) {
        long z = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static int pick(long seed, int salt, int n) {
        if (n <= 1) return 0;
        return (int) Long.remainderUnsigned(mix(seed, salt), n);
    }

    // ------------------------------------------------------------ the state

    public final long seed;
    public int night;
    public int size;
    public int px, py;
    public int ex, ey;
    public int sx, sy;
    /** Which rooms are lit. Indexed y * size + x. */
    public boolean[] lamp;
    /** Departures from each room, by direction. Indexed y * size + x. */
    public int[][] hist;
    /** The turn each departure happened on, for the tie-break. */
    public int[][] last;
    public int read;
    public int turn;
    public boolean won;
    public boolean caught;

    Tell(long seed) {
        this.seed = seed;
        this.night = 0;
        this.size = SIZE[0];
        place();
        this.lamp = new boolean[size * size];
        this.hist = new int[size * size][DIRS];
        this.last = new int[size * size][DIRS];
        dealLamps();
    }

    /** Put the player and the door in opposite corners, dealt from the seed. */
    private void place() {
        int corner = pick(seed, 7, 4);
        int[] cx = { 0, size - 1, size - 1, 0 };
        int[] cy = { 0, 0, size - 1, size - 1 };
        sx = cx[corner];
        sy = cy[corner];
        ex = cx[(corner + 2) % 4];
        ey = cy[(corner + 2) % 4];
        px = sx;
        py = sy;
    }

    private void dealLamps() {
        int n = LAMPS[night];
        int placed = 0;
        for (int i = 0; i < size * size && placed < n; i++) {
            int idx = pick(seed, 1000 + night * 100 + i, size * size);
            if (idx == sy * size + sx || idx == ey * size + ex) continue;
            if (lamp[idx]) continue;
            lamp[idx] = true;
            placed++;
        }
    }

    public static Tell of(long seed) { return new Tell(seed); }
    public static Tell of() { return new Tell(System.nanoTime()); }

    /**
     * Start the next night in the same house.
     *
     * The counts are reset, and they have to be: the house is dealt fresh each
     * night, and a model carried over from a smaller house would be a model of
     * a person who is not in this one. What carries over is the seed, so the
     * house has the same shape and the same lamps every time you come back to
     * it.
     */
    public boolean nextNight() {
        if (night + 1 >= NIGHTS) return false;
        night++;
        size = SIZE[night];
        place();
        hist = new int[size * size][DIRS];
        last = new int[size * size][DIRS];
        lamp = new boolean[size * size];
        read = 0;
        turn = 0;
        won = false;
        caught = false;
        dealLamps();
        return true;
    }

    public int reach() { return REACH[night]; }
    public int base() { return BASE[night]; }
    public int hitCost() { return HIT[night]; }
    public int missGain() { return MISS[night]; }
    public int litCost() { return LIT[night]; }
    public int limit() { return LIMIT[night]; }

    // ------------------------------------------------------------- the rule

    public int idx(int x, int y) { return y * size + x; }
    public int here() { return idx(px, py); }

    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < size && y < size; }

    public boolean canMove(int dir) {
        return !done() && dir >= 0 && dir < DIRS && inBounds(px + DX[dir], py + DY[dir]);
    }

    public int dist(int x, int y) { return Math.abs(x - ex) + Math.abs(y - ey); }

    /** Distance to the door after taking dir, or a large number if illegal. */
    public int stepDist(int dir) {
        int nx = px + DX[dir], ny = py + DY[dir];
        if (!inBounds(nx, ny)) return 9999;
        return dist(nx, ny);
    }

    /** The legal directions from here. */
    public List<Integer> legal() {
        List<Integer> out = new ArrayList<>();
        for (int d = 0; d < DIRS; d++) if (canMove(d)) out.add(d);
        return out;
    }

    /**
     * The directions it expects, in the order it expects them.
     *
     * Most-used first, most recent as the tie-break, top {@link #reach()} of
     * them. Before you have moved at all it does not guess a habit -- it
     * guesses the door, because that is what a person in a house with a door
     * wants. That is the only place the rule looks at the house instead of at
     * you, and it is why the first step of a night is never free.
     */
    public List<Integer> expected() {
        int i = here();
        int[] c = hist[i];
        int total = 0;
        for (int d = 0; d < DIRS; d++) total += c[d];
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        if (total == 0) {
            // A room it has never heard you leave: it does not guess a habit,
            // it guesses the door -- and it guesses exactly *one* way to the
            // door, the best one. That single guess is the whole of the game.
            // If it covered both ways forward there would be no move that is
            // both safe and useful, and the night would be a tax rather than a
            // choice; if it covered none, walking straight at the door would
            // be free and there would be nothing to read.
            order.sort((a, b) -> Integer.compare(stepDist(a), stepDist(b)));
            return new ArrayList<>(order.subList(0, 1));
        }
        order.sort((a, b) -> {
            if (c[b] != c[a]) return Integer.compare(c[b], c[a]);
            return Integer.compare(last[i][b], last[i][a]);
        });
        return new ArrayList<>(order.subList(0, Math.min(reach(), DIRS)));
    }

    public boolean isExpected(int dir) { return expected().contains(dir); }

    /**
     * The directions that would surprise it.
     *
     * At reach 3 in an open room there is exactly one, and it is whichever
     * direction you have spent least -- so the game's whole question is
     * whether that direction is one you can afford to walk.
     */
    public List<Integer> surprises() {
        List<Integer> out = new ArrayList<>();
        List<Integer> exp = expected();
        for (int d = 0; d < DIRS; d++) if (!exp.contains(d)) out.add(d);
        return out;
    }

    /** The legal directions that would surprise it. */
    public List<Integer> legalSurprises() {
        List<Integer> out = new ArrayList<>();
        for (int d : surprises()) if (canMove(d)) out.add(d);
        return out;
    }

    public boolean move(int dir) {
        if (!canMove(dir)) return false;
        boolean predicted = isExpected(dir);
        int i = here();
        hist[i][dir]++;
        last[i][dir] = turn;
        px += DX[dir];
        py += DY[dir];
        turn++;
        if (predicted) read += hitCost();
        else read = Math.max(0, read - missGain());
        read += base();
        if (lamp[here()]) read += litCost();
        if (px == ex && py == ey) won = true;
        else if (read >= READ_MAX) caught = true;
        else if (turn >= limit()) caught = true;
        return true;
    }

    public boolean done() { return won || caught; }

    /** How well it knows you, as a percentage, for the bar and the report. */
    public int readPct() { return Math.min(READ_MAX, read); }

    /** How evenly you spent your moves. 1.0 is perfectly spread. */
    public double spread() {
        int total = 0, max = 0;
        for (int[] room : hist) {
            for (int d = 0; d < DIRS; d++) { total += room[d]; max = Math.max(max, room[d]); }
        }
        if (total == 0) return 1.0;
        return 1.0 - (double) (max - total / (double) DIRS) / total;
    }

    // -------------------------------------------------------------- the file

    /**
     * The house, as three lines of plain text: the seed, where you are, and
     * the four numbers it has on you.
     *
     * The counts are in the save on purpose. They are the whole of the
     * adversary, and a player who reads the file can see exactly how well it
     * knows them -- which is the same joke the other plain-text saves make,
     * except that here the file is the monster.
     */
    public void save(Path p) throws IOException {
        StringBuilder h = new StringBuilder();
        StringBuilder l = new StringBuilder();
        for (int i = 0; i < size * size; i++) {
            for (int d = 0; d < DIRS; d++) {
                if (h.length() > 0) h.append(' ');
                h.append(hist[i][d]);
                if (l.length() > 0) l.append(' ');
                l.append(last[i][d]);
            }
        }
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, seed + "\n"
                + night + " " + px + " " + py + " " + read + " " + turn + " "
                + (won ? 1 : 0) + " " + (caught ? 1 : 0) + "\n"
                + h + "\n" + l + "\n");
    }

    public static Tell load(Path p) throws IOException {
        if (!Files.exists(p)) return of();
        List<String> lines = Files.readAllLines(p);
        if (lines.isEmpty()) return of();
        long seed;
        try { seed = Long.parseLong(lines.get(0).trim()); } catch (Exception e) { return of(); }
        Tell t = of(seed);
        if (lines.size() > 1) {
            String[] f = lines.get(1).trim().split("\\s+");
            try {
                int night = Integer.parseInt(f[0]);
                for (int k = 0; k < night && k < NIGHTS - 1; k++) t.nextNight();
                t.px = Integer.parseInt(f[1]);
                t.py = Integer.parseInt(f[2]);
                t.read = Integer.parseInt(f[3]);
                t.turn = Integer.parseInt(f[4]);
                t.won = f[5].equals("1");
                t.caught = f[6].equals("1");
            } catch (Exception ignored) { }
        }
        if (lines.size() > 2) {
            String[] f = lines.get(2).trim().split("\\s+");
            int k = 0;
            for (int i = 0; i < t.size * t.size && k + DIRS <= f.length; i++)
                for (int d = 0; d < DIRS; d++) t.hist[i][d] = Integer.parseInt(f[k++]);
        }
        if (lines.size() > 3) {
            String[] f = lines.get(3).trim().split("\\s+");
            int k = 0;
            for (int i = 0; i < t.size * t.size && k + DIRS <= f.length; i++)
                for (int d = 0; d < DIRS; d++) t.last[i][d] = Integer.parseInt(f[k++]);
        }
        return t;
    }

    // -------------------------------------------------------------- the prose

    public static final List<String> OPENING = List.of(
            "There is something in the house with you. It cannot see you. It has "
                    + "been listening to you move, and it is counting.",
            "Every room you leave, you leave by a door, and it has counted which "
                    + "doors you use. In a room you have never left, it does not guess "
                    + "a habit -- it guesses the door, because that is what a person in "
                    + "a house with a door wants. It will tell you what it expects.",
            "Go where it expects and it learns you faster. Go somewhere it did not "
                    + "expect and it loses ground. The read is how well it knows you. "
                    + "At a hundred it does not have to look.",
            "The door out is in the far corner, and it knows that too. The lit rooms "
                    + "are not hiding you.");

    public static final String RULES_HEADING = "THE HOUSE";
    public static final List<String[]> RULES = List.of(
            new String[] { "MOVE", "arrow keys or WASD. One room at a time." },
            new String[] { "THE COUNT", "the numbers in each room are how many "
                    + "times you have left it each way. That is the whole of its "
                    + "model of you, and it is on the floor." },
            new String[] { "THE READ", "rises every turn. It rises faster when you "
                    + "go where it expects, and falls when you do not." },
            new String[] { "LIT ROOMS", "cost read to enter. The door is in the far "
                    + "corner, and the cheap way there is the obvious one." });

    public static final String EXPECT_HEAD = "IT EXPECTS";
    public static final String EXPECT_NONE = "nothing, yet";
    public static final String COUNT_HEAD = "WHAT IT HAS COUNTED";
    public static final String READ_HEAD = "HOW WELL IT KNOWS YOU";
    public static final String DOOR_HEAD = "THE DOOR";
    public static final String START_LINE = "ENTER to go in.";
    public static final String START_BUTTON = "Go in";
    public static final String AGAIN = "Another house";
    public static final String NEXT_NIGHT = "ENTER for the next night";
    public static final String HINT = "arrows or WASD to move    ESC the library";
    public static final String HINT_DONE = "ENTER to go on    ESC the library";
    public static final String NO_SUCH_DOOR = "There is no door that way.";

    public static final String REPORT_HEAD = "THE FAR DOOR";
    public static final String CAUGHT_HEAD = "IT KNEW YOU";

    /**
     * What it says when it catches you, by how far past the line it was.
     *
     * Three fixed sentences rather than one built on the spot, because the
     * phone build has to pick one of them too and a sentence assembled in two
     * places is a sentence that can end up assembled two ways.
     */
    public static final String CAUGHT_FAR =
            "It was already in the room. It did not have to look.";
    public static final String CAUGHT_NEAR =
            "It came in behind you and did not check the corners.";
    public static final String CAUGHT_CLOSE =
            "It knew where you would be, and it was there.";

    public static String caughtLine(int read) {
        if (read >= READ_MAX + 40) return CAUGHT_FAR;
        if (read >= READ_MAX + 15) return CAUGHT_NEAR;
        return CAUGHT_CLOSE;
    }

    /**
     * The closing sentence of a night. One per ending.
     *
     * Two of the four carry the turn count, so they are templates with one
     * number in them and nothing else. The phone substitutes the number; it
     * does not get to write the sentence.
     */
    public static final String CLOSING_LOST =
            "It reads you the way you read a room you have been in before. There is "
                    + "nothing in that to be ashamed of, and there is no way to be in a "
                    + "house for %d turns without leaving a shape.";
    public static final String CLOSING_LOW =
            "You got out and it never had you. That is not luck -- that is a person who "
                    + "walked away from their own habits on purpose, over and over, for "
                    + "%d turns.";
    public static final String CLOSING_MID =
            "You got out. It knew some of you by the end, and what it knew was true, "
                    + "and it was still not enough to be waiting at the door.";
    public static final String CLOSING_HIGH =
            "You got out with it nearly certain. Whatever it was going to learn about "
                    + "you, it learned, and you spent the whole night being read and "
                    + "left anyway.";

    public static String closing(boolean won, int read, int turn) {
        if (!won) return String.format(CLOSING_LOST, turn);
        if (read <= 20) return String.format(CLOSING_LOW, turn);
        if (read <= 55) return CLOSING_MID;
        return CLOSING_HIGH;
    }

    /** The read, said plainly, for the report. Four fixed lines. */
    public static final String READ_BARELY = "It barely had you.";
    public static final String READ_SHAPE = "It had the shape of you.";
    public static final String READ_WAYS = "It knew which ways were yours.";
    public static final String READ_KNEW = "It knew you.";

    public static String readLine(int read) {
        if (read <= 20) return READ_BARELY;
        if (read <= 45) return READ_SHAPE;
        if (read <= 75) return READ_WAYS;
        return READ_KNEW;
    }

    public static String nightName(int night) {
        return night >= 0 && night < NIGHT_NAMES.length
                ? NIGHT_NAMES[night] : String.valueOf(night + 1);
    }

    /** "3" -> "three". Small numbers only; the game never needs more. */
    public static String word(int n) {
        return n >= 0 && n < WORDS.length ? WORDS[n] : String.valueOf(n);
    }

    public static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "north, east and south" -- the expectation, as a sentence. */
    public static String join(List<Integer> dirs) {
        if (dirs.isEmpty()) return EXPECT_NONE;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < dirs.size(); i++) {
            if (i > 0) b.append(i == dirs.size() - 1 ? " and " : ", ");
            b.append(DIR_NAME[dirs.get(i)]);
        }
        return b.toString();
    }

    // ------------------------------------------------- the prose, as tables

    /**
     * The lines the screen draws that are not fixed sentences.
     *
     * These live here rather than in TellScreen because the phone build has to
     * say them too, and a second copy of a sentence in a template is a second
     * copy that can drift. The screen and the phone both read these; the phone
     * substitutes numbers into the same templates, which is the only
     * substitution it is allowed to do, because a number cannot be reworded.
     */
    public static final String[] NIGHT_NAMES = { "one", "two", "three", "four", "five" };
    /** "no" through "twelve" -- twelve is the far corner of the biggest house. */
    public static final String[] WORDS = { "no", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten", "eleven", "twelve" };

    public static final String NIGHT_LABEL = "night %s of five";
    public static final String AWAY_ONE = "one room away";
    public static final String AWAY_MANY = "%s rooms away";
    public static final String TURN_LABEL = "turn %d of %d";
    public static final String COVERS_ONE =
            "It covers the one door you use most here. Going anywhere else costs it "
                    + "%d; going where it expects costs you %d.";
    public static final String COVERS_MANY =
            "It covers the %s doors you use most here. Going anywhere else costs it "
                    + "%d; going where it expects costs you %d.";
    public static final String WON_HEAD = "You reached the far door on night %s.";
    public static final String TOOK_LINE = "It took you %d turns to be read that far.";
    public static final String READ_OF = "%d of 100";

    public static String nightLabel(int night) {
        return NIGHT_LABEL.replace("%s", nightName(night));
    }

    public static String awayLine(int away) {
        return away == 1 ? AWAY_ONE : AWAY_MANY.replace("%s", word(away));
    }

    public static String turnLabel(int turn, int limit) {
        return String.format(TURN_LABEL, turn, limit);
    }

    public static String coversLine(int reach, int miss, int hit) {
        return reach == 1 ? String.format(COVERS_ONE, miss, hit)
                : String.format(COVERS_MANY, word(reach), miss, hit);
    }

    public static String wonHead(int night) {
        return WON_HEAD.replace("%s", nightName(night));
    }

    public static String tookLine(int turn) {
        return String.format(TOOK_LINE, turn);
    }

    public static String readOf(int pct) {
        return String.format(READ_OF, pct);
    }
}
