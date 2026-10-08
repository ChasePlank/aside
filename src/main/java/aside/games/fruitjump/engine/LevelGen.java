package aside.games.fruitjump.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * LevelGen - procedural platformer levels in the LevelMap ASCII format.
 *
 * Approach: a ground random walk (the guaranteed path) plus SOLID
 * TERRAIN beneath it. The walk carves a floor with bounded jumps and
 * bounded climbs:
 *   - horizontal gaps <= MAX_GAP_CELLS (jumpable)
 *   - vertical steps up <= MAX_STEP_CELLS (jumpable)
 *   - drops can be any height (falling is free)
 * and the level then ASCENDS to the exit on a staircase of 2-cell
 * climbs.
 *
 * Every generated level is completable BY CONSTRUCTION; the validator
 * bot exists to prove it empirically.
 */
public class LevelGen {
    // Player physics (measured from the engine at 1x velocity):
    // jump v0 = 420, gravity 1200 -> apex ~73px ~ 2.3 cells
    // run speed 200px/s, air time ~0.7s -> jump distance ~140px ~ 4.4 cells
    static final int BASE_MAX_GAP_CELLS = 3;    // conservative: 3-cell gaps

    /**
     * Chance an eligible gap is flooded. 0 disables flooded gaps.
     *
     * <p>Was 0.35 and only applied to gaps of 2+, which measured at **0.15 pools per level** - one pool every
     * seven levels, and less than one water cell per level. Kinger's report ("water almost never spawns") was
     * right and the constant was not the only reason: water can only go where a gap is, and a level has about
     * one gap, so a third of one gap is a third of one pool. Both levers are pulled now - the rate, and letting
     * a ONE-cell gap flood too, which is a 32px pool the 24px body fits in.
     */
    static final double FLOODED_GAPS = 0.85;

    /**
     * Chance a level is a FLOODED one - the walk itself under water.
     *
     * <p>About one level in seven. Separate from {@link #FLOODED_GAPS}, which floods the gaps in an ordinary
     * level: this floods the crossing, which is a different level rather than a wetter version of the same one,
     * and it is where the piranhas are meant to matter.
     */
    static final double FLOODED_LEVELS = 0.25;

    /**
     * How likely a heal is, by level.
     *
     * <p>Was a flat 0.5 for the floor pocket and unconditional for the vault's heart, so a level 25 heal was as
     * easy to find as a level 2 one - and a heal late is worth far more, because that is where the difficulty
     * is. Kinger: "heals chance needs to decrease with levels otherwise its too easy to heal in later levels."
     *
     * <p>Floored at 0.12 rather than going to zero: a level with no heal at all is a different design, and the
     * later levels are where a run is supposed to be survivable but not comfortable.
     */
    static double healChance(int levelNum) {
        return Math.max(0.12, 0.55 - levelNum * 0.015);
    }
    static final int BASE_MAX_STEP_CELLS = 2;   // conservative: 2-cell climbs

    /** Ascent to the exit: climbs of 2 cells, each followed by a landing
     *  runway. A jump arc lands 2+ cells past a step, so arriving
     *  airborne at the next face wedges the player against it. */
    static final int STAIR_STEPS = 3;
    static final int STAIR_RUNWAY = 3;
    static final int EXIT_PLATFORM = 6;
    /** Columns reserved at the right edge for the staircase + exit. */
    static final int EXIT_ZONE = STAIR_STEPS * (1 + STAIR_RUNWAY) + EXIT_PLATFORM;

    /**
     * How often a run gets a boss: every tenth level.
     *
     * <p>THE PACING DECISION, AND IT IS ONE CONSTANT. The boss exists in the tutorial, where it teaches the fight;
     * without this it exists ONLY there, and a player who starts a New Game and plays a hundred levels would never
     * meet one - a feature in a lesson and nowhere else. A boss every tenth level is the ordinary shape of that
     * rhythm, and it is reversible by changing this number, which is why the number is here and not spread through
     * the placement code.
     *
     * <p>It is placed ON the walk rather than in a side arena, and it is killable rather than avoidable - see
     * {@link #bossColumn}, which will only use a stretch the boss can actually stand in.
     */
    public static final int BOSS_EVERY = 10;

    /**
     * How long a run is: reaching the exit on this level is the way home.
     *
     * <p><b>THE GAME HAD NO ENDING AT ALL.</b> Its own sentence is "a climber, a sunset, and a way home" - and
     * until this existed the generated run was endless and death was the only terminal state. The only trace of an
     * ending anywhere in the code was a `Music.VICTORY` entry that nothing played and no file backed. A premise
     * with a destination and a game without one is a game that contradicts its own sentence.
     *
     * <p>Forty because that is where the evening finishes: {@code GameplayScreen.LEVELS_TO_DUSK} is this number,
     * so the sun is fully down at exactly the level that ends the run. A check asserts the two agree, because
     * "the game ends while the sun is still up" is a taste fault that nothing else would notice.
     *
     * <p>IT IS A DOOR, NOT A WALL. The ending screen offers to keep climbing, so the endless run is still there for
     * anyone who wants it - this gives the climb a shape without taking the treadmill away.
     */
    public static final int FINAL_LEVEL = 40;

    /**
     * How often a run gets a moving platform: every third level.
     *
     * <p><b>THE RUN NEVER HAD ONE, AND THE TUTORIAL IS WHERE THE MECHANIC LIVED.</b> Level 10 has a lift, and its
     * own comment explains the bay built around it. `LevelMap` builds whatever a map declares, `World` carries a
     * rider, the screen draws them - and `LevelGen` has never called `addMover`, so across forty generated levels
     * a player would not meet the thing the tutorial taught. That is the boss's shape exactly: the machinery
     * complete, the recognition present, and the appearance confined to a lesson.
     *
     * <p>Three rather than ten, because a moving platform is furniture rather than a set-piece. A ferry is not a
     * gate: it is placed over a gap the player can already jump, so riding it is a way across rather than the only
     * way across, and a level where it is missed is a level that still finishes.
     */
    public static final int MOVER_EVERY = 3;

    /** How often a run gets a one-way plank: every fourth level. See the placement for why it exists at all. */
    public static final int PLANK_EVERY = 4;

    final int width, height;
    final Random rng;
    final int levelNum;
    final int maxGapCells;
    final int maxStepCells;
    /** Floor row of the carved path per column (-1 = no path floor).
     *  Recorded during generation so tests measure the ACTUAL path,
     *  not a heuristic re-read of the grid. */
    public final int[] pathFloor;
    /** The most recently generated map (for validateGenerated). */
    public LevelMap lastMap;

    // ---- Bombable side vault (recorded while walking, built after) ----
    /** Face column of the vault's 2-tall wall, or -1 if none. */
    public int vaultCol = -1;
    /** Walk row on the player's side of that wall. */
    public int vaultLowerRow = -1;
    /** Walk row on the far side (2 cells above vaultLowerRow). */
    public int vaultUpperRow = -1;

    public LevelGen(int width, int height, long seed) {
        this(width, height, seed, 1);
    }

    public LevelGen(int width, int height, long seed, int levelNum) {
        this.width = width;
        this.height = height;
        this.rng = new Random(seed);
        this.levelNum = levelNum;
        this.pathFloor = new int[width];
        java.util.Arrays.fill(this.pathFloor, -1);

        // Difficulty scaling: gaps grow by 1 every 5 levels.
        //
        // CAPPED AT 4. It reached 5 from level 11 on, and a 5-cell gap is 160px
        // against a ~137px jump - an impossible gap, on every level from 11 up,
        // which is not difficulty, it is a wall. The release found this and fixed
        // it there; the fix never came back, so aside kept generating walls while
        // the release's own GroundFillTest asserted the property that forbids them
        // ("no walk gap exceeds the jump"). Measured on the way in: aside produced
        // 13 five-cell gaps per 100 levels at level 22; the release produced none.
        // Difficulty comes from the platform climbs instead, which are hard and
        // always passable.
        this.maxGapCells = Math.min(4, BASE_MAX_GAP_CELLS + (levelNum - 1) / 5);
        // Steps stay at 2 (harder to tune without breaking path)
        this.maxStepCells = BASE_MAX_STEP_CELLS;
    }

    /** Generate a level as ASCII rows. */
    /**
     * A gap a ferry can shuttle across, as {middle column, walk row}, or null if this level has no such gap.
     *
     * <p><b>IT REFUSES RATHER THAN GUESSES</b>, the same rule the boss placement follows and for the same reason: a
     * generated level is a random walk, and a platform dropped at "about the middle" would sometimes be inside a
     * hill. What it wants is a run of columns with NO floor, two to four wide, with flat floors either side at the
     * same height - the ordinary gap the jump already clears. The ferry rides at that height, so its box passes
     * through the air above the neighbouring floors and intersects nothing.
     */
    private int[] ferryGap(char[][] g) {
        // THE WHOLE WALK, NOT ITS MIDDLE HALF. The first version searched the middle half and found NOTHING on any
        // of the thirteen levels that asked for a ferry - measured - because a gap in the carved walk is rare and
        // turns up wherever the walk happened to jump: level 3's sits at columns 9 and 10, well outside a search
        // that started at fifteen. The guard columns are the spawn area at the left and the exit stair at the right.
        for (int col = 6; col <= width - 8; col++) {
            if (pathFloor[col] >= 0) continue;
            int start = col, end = col;
            while (end + 1 < width && pathFloor[end + 1] < 0) end++;
            int len = end - start + 1;
            col = end;
            if (len < 2 || len > 4) continue;
            if (start == 0 || end + 1 >= width) continue;
            int left = pathFloor[start - 1], right = pathFloor[end + 1];
            if (left < 3 || right < 3 || left != right) continue;   // flat either side, and both are real floors
            return new int[] { (start + end) / 2, left };
        }
        return null;
    }

    /**
     * A column the boss can stand in, or -1 if this level has no such stretch.
     *
     * <p><b>IT REFUSES RATHER THAN GUESSES.</b> A generated level is a random walk, so a boss dropped at "about the
     * middle" would sometimes be inside a wall or over a pit - the kind of fault that reads as "the boss is stuck
     * in the floor" in play and appears in no test. So this looks for a stretch that is FLAT over four columns and
     * CLEAR over the two cells the boss's 64x64 body occupies, and returns -1 rather than a bad spot. The middle
     * half is searched first so the fight is not on top of the spawn or the exit.
     *
     * <p>The boss is two cells wide and two tall: its feet sit on the walk's surface at {@code pathFloor[col] * 32}
     * and its centre is half its height above that, which is why the rows checked are R-1 and R-2.
     */
    private int bossColumn(char[][] g) {
        int from = width / 4, to = (width * 3) / 4 - 3;
        List<Integer> candidates = new ArrayList<>();
        for (int col = from; col <= to; col++) {
            if (pathFloor[col] < 3 || pathFloor[col + 1] < 3) continue;
            int r = pathFloor[col];
            if (pathFloor[col + 1] != r) continue;
            // flat either side too, so the fight does not open against a step
            if (col > 0 && pathFloor[col - 1] != r) continue;
            if (col + 2 < width && pathFloor[col + 2] != r) continue;
            boolean clear = true;
            for (int c = col; c <= col + 1 && clear; c++)
                for (int rr = r - 1; rr >= r - 2; rr--) {
                    // ' ' is the only empty cell: everything else is terrain, a hazard, water or content.
                    if (g[rr][c] != ' ') { clear = false; break; }
                }
            if (clear) candidates.add(col);
        }
        if (candidates.isEmpty()) return -1;
        // AND ANY OF THEM, BY THE LEVEL'S OWN SEED. Taking the first was the first version, and every tenth level
        // in the range put the boss at the same x - the first qualifying stretch is often the same one, so the
        // fight always opened in the same place in the level. Seeded, so it is still the same encounter on every
        // attempt at that level, which is what the level's seed is for.
        return candidates.get(rng.nextInt(candidates.size()));
    }

    public LevelMap generate() {
        // Every tenth level is a safe room.
        if (levelNum % 10 == 0) return generateSafeRoom();

        // Grid of chars, all spaces initially
        char[][] g = new char[height][width];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');

        // --- The guaranteed path: a ground walk left to right ---
        int floorRow = height - 3;  // ground level
        int col = 2;

        // Spawn platform
        for (int c = col - 2; c <= col + 2; c++) {
            if (c >= 0) { g[floorRow][c] = '#'; pathFloor[c] = floorRow; }
        }
        g[floorRow - 1][col] = 'P';

        col += 3;
        int lastFloorRow = floorRow;
        boolean needRunway = false;  // a climb just happened: no gap next
        final int walkEnd = width - EXIT_ZONE;

        while (col < walkEnd) {
            // Choose the next segment: flat, gap, step-up, step-down.
            // After a climb, force a LANDING RUNWAY (3 flat cells)
            // before any gap -- a jump arc lands 2+ cells past a step,
            // and arriving airborne at a gap lip means no jump fires
            // and the bot falls in (real level-design constraint:
            // players need landing room too).
            double roll = rng.nextDouble();

            // Guarantee the vault happens. Chance-gating it meant only
            // half the levels got one, which reads as an inconsistent
            // feature rather than a find. Once we're past the middle of
            // the level with no candidate, force the next segment to be
            // the 2-cell climb the vault needs.
            if (vaultCol < 0 && col >= width / 2 && col < (2 * width) / 3) {
                // Must land inside the step-up band (0.45 .. 0.60).
                // 0.60 is exclusive there and falls through to the
                // step-DOWN branch, which never records a vault face.
                roll = 0.50;
            }

            if (roll < 0.30) {
                // Flat run (2-4 cells)
                int len = 2 + rng.nextInt(3);
                for (int i = 0; i < len && col < walkEnd; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
            } else if (roll < 0.45) {
                // Gap (1 to MAX_GAP_CELLS), then continue at same height.
                // No gap right after a climb -- landing runway required.
                if (needRunway) {
                    for (int i = 0; i < 3 && col < walkEnd; i++, col++) {
                        g[lastFloorRow][col] = '#';
                        pathFloor[col] = lastFloorRow;
                    }
                } else {
                    int gap = 1 + rng.nextInt(maxGapCells);
                    int gapStart = col;
                    col += gap;

                    // Some gaps are FLOODED. A gap is the one place the walk already leaves empty, so water costs
                    // the guaranteed path nothing: the bot jumps gaps exactly as before, and a player who misses
                    // the jump lands in water instead of falling out of the level.
                    //
                    // TWO rows of water, not three: at the walk's default floor row, three rows plus a pool floor
                    // does not fit inside the map at all - the condition is never true and it silently floods
                    // nothing. A pool that looks reasonable and can never exist is worth checking by counting, not
                    // by reading.
                    //
                    // Escapable by construction: the surface sits at the path level and the pool is 64px deep, so
                    // the breach hop clears the lip. Deeper would be a trap. One-cell gaps are left alone.
                    if (gap >= 1 && rng.nextDouble() < FLOODED_GAPS
                            && lastFloorRow + 2 < height) {
                        for (int cc = gapStart; cc < col; cc++) {
                            g[lastFloorRow][cc] = '~';       // surface, level with
                            g[lastFloorRow + 1][cc] = '~';   // the walk
                            g[lastFloorRow + 2][cc] = '#';   // pool floor, so it is
                        }                                    // not bottomless

                        // --- Piranhas, in the pool that was just made ---
                        //
                        // Kinger: "spawn in groups". A group is placed together in one pool, because the threat
                        // is meant to be the pool rather than the fish - one piranha in a pool you cross in a
                        // second is nothing, and four make the crossing a decision.
                        //
                        // Only in pools that are wide enough to hold them (3+ cells), and only from level 3, so
                        // the first two levels stay about learning to move.
                        if (gap >= 3 && levelNum >= 3) {
                            int howMany = 3 + rng.nextInt(3);          // 3..5
                            for (int k = 0; k < howMany; k++) {
                                int cc = gapStart + rng.nextInt(gap);
                                // ONE ROW ABOVE THE SURFACE, not in it.
                                //
                                // Writing a fish INTO the pool replaces a water cell with a fish, and the pool
                                // then measures one row deep instead of two - which the shape assertion caught
                                // on the first run ("level 15 column 15 is 1 rows, not 2"). A char grid cannot
                                // hold water and a fish in the same cell, so the fish sits at the waterline and
                                // the water stays whole. That is also what a piranha looks like from the bank.
                                g[lastFloorRow - 1][cc] = 'f';
                            }
                        }
                    }
                    int len = 2 + rng.nextInt(2);
                    for (int i = 0; i < len && col < walkEnd; i++, col++) {
                        g[lastFloorRow][col] = '#';
                        pathFloor[col] = lastFloorRow;
                    }
                    // A gap jump arc lands ~140px (4+ cells) past the
                    // lip -- a climb inside the landing zone wedges the
                    // player against the step face mid-descent. Runway
                    // required after gaps too.
                    needRunway = true;
                    continue;
                }
            } else if (roll < 0.60) {
                // Step up (1 to MAX_STEP_CELLS) -- jumpable climb.
                //
                // Forced to exactly 2 for the bombable vault: a step-up
                // of 2 is the ONLY place this layout produces a 2-tall
                // VERTICAL face, and it faces the approaching player.
                int step = 1 + rng.nextInt(maxStepCells);
                boolean vaultHere = vaultCol < 0
                        && col > width / 3 && col < (2 * width) / 3;
                if (vaultHere) step = 2;

                int lowerRow = lastFloorRow;
                int faceCol = col;
                int newRow = Math.max(4, lastFloorRow - step);
                step = lowerRow - newRow;          // respect the ceiling clamp
                if (step < 2) vaultHere = false;   // no room for a full face

                for (int r = newRow; r < height; r++) {
                    g[r][col] = '#';
                }
                pathFloor[col] = newRow;
                lastFloorRow = newRow;
                col++;

                int len = 2 + rng.nextInt(2);
                if (vaultHere) len = Math.max(len, 3);  // chamber needs 2 clear columns
                for (int i = 0; i < len && col < walkEnd; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
                if (vaultHere) {
                    vaultCol = faceCol;
                    vaultLowerRow = lowerRow;
                    vaultUpperRow = newRow;
                }
                needRunway = true;
                continue;
            } else if (roll < 0.75) {
                // Step down (1-3 cells) -- free fall
                int step = 1 + rng.nextInt(3);
                int newRow = Math.min(height - 3, lastFloorRow + step);
                for (int r = newRow; r < height; r++) {
                    g[r][col] = '#';
                }
                pathFloor[col] = newRow;
                lastFloorRow = newRow;
                col++;
                int len = 2 + rng.nextInt(2);
                for (int i = 0; i < len && col < walkEnd; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
            } else {
                // Wide flat run -- a breather stretch. (This slot used to
                // drop a decorative spike into the void below the floor,
                // which could never be seen: the floor had no mass under
                // it, so anything "below the ground" floated in empty
                // space. Spikes now live in the gap pits, on real ground.)
                int len = 4 + rng.nextInt(4);
                for (int i = 0; i < len && col < walkEnd; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
            }
        }

        // --- Ascent to the exit ---
        // The exit sits on a raised platform at the top of a staircase.
        // Every climb is 2 cells (inside the 73px apex) and every climb
        // is followed by a landing runway. This is the difficulty beat
        // the playtest asked for: you have to climb to leave.
        for (int s = 0; s < STAIR_STEPS; s++) {
            int newRow = Math.max(4, lastFloorRow - 2);
            if (newRow >= lastFloorRow) break;   // ran out of headroom
            for (int r = newRow; r < height; r++) {
                g[r][col] = '#';
            }
            pathFloor[col] = newRow;
            lastFloorRow = newRow;
            col++;
            for (int i = 0; i < STAIR_RUNWAY && col < width - 2; i++, col++) {
                g[lastFloorRow][col] = '#';
                pathFloor[col] = lastFloorRow;
            }
        }
        // Exit platform bridges whatever is left to the border wall at
        // the staircase's final height.
        for (int c = col; c < width; c++) {
            g[lastFloorRow][c] = '#';
            pathFloor[c] = lastFloorRow;
        }
        g[lastFloorRow - 1][width - 3] = 'E';

        // --- Terrain mass ---
        // Everything below the walk becomes rock. Without this the
        // floor was a one-cell strip floating over a void: ground with
        // nothing behind it, so a heart "under the ground" was really a
        // heart hanging in empty space, and there was no wall anywhere
        // to put a bombable block in.
        for (int c = 0; c < width; c++) {
            if (pathFloor[c] < 0) continue;
            for (int r = pathFloor[c] + 1; r < height; r++) g[r][c] = '#';
        }

        // --- Gap pits ---
        // A gap is still a gap (the path data never claims a floor
        // there, so the bot must still jump it), but it is now two cells
        // deep with a spike floor instead of bottomless. Falling in
        // costs a hit and a 2-cell climb out rather than the run.
        //
        // A FLOODED gap is not a spike pit. The flood pass above writes two
        // rows of water with a solid floor under them, and this pass then
        // wrote '^' straight over the water's second row - so every flooded
        // gap in every level was one row of water sitting on a row of spikes,
        // and the pool floor it was built with was gone. What showed it was
        // the count, not the code: 17 water cells across 40 levels, where two
        // rows over four pools of two to five columns is more than twice
        // that. Both passes were written as if they owned the gap column.
        // They are alternatives, so the flooded one keeps it.
        for (int c = 1; c < width - 1; c++) {
            if (pathFloor[c] != -1) continue;
            int row = -1;
            for (int k = c - 1; k >= 0; k--) {
                if (pathFloor[k] >= 0) { row = pathFloor[k]; break; }
            }
            if (row < 0 || row + 2 >= height) continue;
            if (g[row][c] == '~') continue;   // flooded: the water is the cost, not the spikes
            g[row + 1][c] = '^';
            g[row + 2][c] = '#';
        }

        buildVault(g);

        // --- Locked door across the path (with a guaranteed key) ---
        // Pick a flat path column in the middle third. The door spans
        // the 2 cells above the floor. A key is placed on the path
        // ~8-12 columns BEFORE the door, 2 cells above the floor
        // (reachable by jump, not sitting in the walking line).
        // Chance-gated so not every level has one.
        if (rng.nextDouble() < 0.6) {
            int doorCol = -1;
            int from = width / 3, to = 2 * width / 3;
            for (int c = to; c >= from; c--) {
                int fr = pathFloor[c];
                if (fr >= 2 && fr <= height - 4
                        && pathFloor[c + 1] == fr && pathFloor[c + 2] == fr
                        && g[fr][c] == '#' && g[fr - 1][c] == ' '
                        && g[fr - 2][c] == ' '
                        && !nearVault(c)) {
                    doorCol = c;
                    break;
                }
            }
            if (doorCol > 4) {
                int fr = pathFloor[doorCol];
                // 'D' parses as a 2-tall door from the SINGLE lower cell
                // (fr-1): door occupies rows fr-2..fr-1. Only mark the
                // lower cell -- two 'D' chars would make overlapping doors.
                g[fr - 1][doorCol] = 'D';
                // Key: on a FLAT stretch before the door, 1 cell above
                // the floor (head height while walking). Must be a run
                // with no gap/climb within 3 columns either side -- a key
                // inside a jump arc is passed over airborne and never
                // collected (found by trace: key sat in a climb's arc).
                int keyCol = -1;
                for (int tries = 0; tries < 15 && keyCol < 0; tries++) {
                    int c = Math.max(3, doorCol - 6 - rng.nextInt(Math.max(1, doorCol - 10)));
                    if (pathFloor[c] < 0 || nearVault(c)) continue;
                    boolean flat = true;
                    for (int cc = c - 3; cc <= c + 3; cc++) {
                        if (cc < 0 || cc >= width || pathFloor[cc] != pathFloor[c]) {
                            flat = false; break;
                        }
                    }
                    if (!flat) continue;
                    int kfr = pathFloor[c];
                    if (g[kfr - 1][c] == ' ' && g[kfr][c] == '#') keyCol = c;
                }
                if (keyCol >= 0) {
                    g[pathFloor[keyCol] - 1][keyCol] = 'k';
                }
            }
        }

        // --- Enemies on wide flat stretches of the main path ---
        // Frequency scales with level: 40% + 10%/level, capped 85%.
        // (Playtest: level 1 had ~1 enemy -- nothing to fight.)
        double enemyChance = Math.min(0.85, 0.40 + 0.10 * (levelNum - 1));
        for (int r = 2; r < height - 1; r++) {
            int run = 0;
            for (int c = 0; c < width; c++) {
                if (g[r][c] == '#' && (r == 0 || (g[r-1][c] == ' ' || g[r-1][c] == 'P' || g[r-1][c] == 'E'))
                    && (r < 1 || g[r-1][c] != '^')) {
                    run++;
                } else {
                    // Skip enemy placement near the spawn -- a run that
                    // starts at column 0 puts its midpoint enemy right
                    // on the player spawn (playtest: "enemy spawns right
                    // on you, instantly taking a life"). 6 cells ~ the
                    // spawn platform plus a safe walking buffer.
                    int mid = c - run / 2;
                    if (run >= 5 && mid > 6 && rng.nextDouble() < enemyChance) {
                        g[r-1][mid] = rng.nextDouble() < 0.5 ? 'o' : 's';
                    }
                    run = 0;
                }
            }
        }

        // Border walls
        // --- Bats: airborne threats above the path ---
        // Placed in OPEN AIR over the walk. Deliberately NOT kept away from
        // gaps: Kinger's call (Sept 29) is that a bat able to engage over a
        // gap is a real threat, not an overlooked inconvenience, and it is
        // fair because a gap is a climbable spike pit - a mid-air knockover
        // costs a hit and a climb, not the run.
        {
            int want = 1 + Math.min(2, levelNum / 3);   // 1..3, grows slowly
            int placed = 0;
            for (int tries = 0; tries < 40 && placed < want; tries++) {
                int c = 8 + rng.nextInt(Math.max(1, width - 16));
                int fr = pathFloor[c];
                if (fr < 0) continue;
                int row = fr - (3 + rng.nextInt(2));    // 3-4 cells above the walk
                if (row < 1 || g[row][c] != ' ') continue;
                g[row][c] = 'b';
                placed++;
            }
        }

        // --- Cracked floor + hidden pocket (bombable, off the bot's path) ---
        //
        // Ported from the release, where it was written on Sept 27 and never came back. Two adjacent
        // floor tiles become CRACKED with a pocket beneath them holding a snack: intact you walk over
        // it, bombed you drop in, take it and jump back out.
        //
        // This is a DIFFERENT chamber from buildVault's, not a replacement for it. The vault is a door
        // in a cliff FACE; this is a trapdoor in the FLOOR. Both are bombable rooms with something
        // inside, and the vault's own comment explains why it did not use a floor hole: "the player
        // body is 48px wide and a floor hole is one cell (32px) wide, so the body is always carried
        // across the lips and can never fall in." That reasoning is right about a ONE-cell hole and
        // does not apply here - this is two cells, 64px, and the body fits through it.
        //
        // TWO cells wide, not one. One cell is spanned by the lips and can never be entered.
        //
        // WHY THE SEAT IS ONE CELL: the bombed hole is the second cell of air. A pocket H cells tall
        // has its floor one row below that, so climbing straight out is H+1 cells; the body is 44px
        // and a cell is 32px, so H must be at least 2 to fit inside at all - which makes the climb
        // 3 cells = 96px against a 73px jump. There is no H that works: the door has to be the hole.
        // Hence one cell of seat plus the open hole above it (64px of air, the body fits with its head
        // poking up) and a floor two rows down, which is a 2-cell climb = 64px, inside the jump with
        // the same margin the walk's own step limit assumes.
        //
        // Placed after the enemy and bat passes because both look for wide flat stretches of path,
        // and a bombable floor is not a stretch to stand on. It writes its own floor: the terrain
        // mass above filled below the walk, but a pocket carved into that fill would otherwise have
        // the snack as its lowest block, and anything below the lowest block is treated as ground.
        if (rng.nextDouble() < healChance(levelNum)) {
            int from = 6, to = width - 10;
            for (int tries = 0; tries < 10; tries++) {
                int c = from + rng.nextInt(Math.max(1, to - from));
                int fr = pathFloor[c];
                if (fr < 0 || fr > height - 4) continue;
                if (g[fr][c] != '#' || g[fr][c + 1] != '#') continue;
                // Nothing standing on either tile: a door, a key or a sign would be buried by the
                // carve, and the vault's own columns are the same cliff face.
                if (g[fr - 1][c] != ' ' || g[fr - 1][c + 1] != ' ') continue;
                if (nearVault(c) || nearVault(c + 1)) continue;
                boolean flat = true;
                for (int cc = c - 1; cc <= c + 2; cc++) {
                    if (cc < 0 || cc >= width || pathFloor[cc] != fr) { flat = false; break; }
                }
                if (!flat) continue;
                boolean clear = true;
                for (int cc = c; cc <= c + 1 && clear; cc++) {
                    for (int rr = fr + 1; rr <= fr + 2; rr++) {
                        if (g[rr][cc] != ' ' && g[rr][cc] != '#') { clear = false; break; }
                    }
                }
                if (!clear) continue;
                g[fr][c] = 'C';          // cracked floor: the pocket's ceiling
                g[fr][c + 1] = 'C';      // AND its doorway
                g[fr + 1][c] = ' ';      // one cell of seat (the hole is the other)
                g[fr + 1][c + 1] = 'h';  // the snack in the seat
                g[fr + 2][c] = '#';      // pocket floor, written explicitly
                g[fr + 2][c + 1] = '#';
                break;
            }
        }

        // --- Flooded levels ---
        //
        // Kinger's fourth ask: "the chance of some levels being mostly water with a new enemy, piranha". A
        // flooded level is THE WALK ITSELF under water - not a pool in a gap, the whole crossing - which is what
        // gives the piranhas somewhere to be and makes the level a different shape rather than a different
        // decoration.
        //
        // TWO rows, the same as a pool, for two reasons. The player swims rather than wades and the breach hop
        // clears the lip, so it stays escapable; and the water shape assertion holds - a one-row flood would
        // trip it on the first run, which is exactly how the fish-placement bug was found.
        //
        // PLACED LAST, after the pocket, because every other pass looks for flat dry stretches to stand on: a
        // door, a bat, an enemy or a pocket put down first would otherwise be standing in water. Flooding last
        // means those passes still see the level they were written for.
        //
        // The first six and last four cells stay dry, so the player never starts or finishes a level in water.
        if (rng.nextDouble() < FLOODED_LEVELS) {
            // A CAUSEWAY, NOT A LAKE. Two rows of water submerges the body about 90%, so the air bar runs the
            // whole time - and a flooded walk of forty cells drowns the player before they reach the far side.
            // The validator bot proved it: four of a hundred levels stopped being completable, and disabling the
            // piranhas did not change the count, so it was the water itself.
            //
            // So the flood comes in runs, with dry ledges between them: long enough to be a crossing, short
            // enough to make. That is also what a flooded causeway looks like, and it is where the piranhas
            // belong - each run is one decision.
            // The runs are decided BEFORE the walk is written, so the pattern is a fact rather than a side effect
            // of two counters that were supposed to take turns. The first version used counters and produced a
            // nineteen-cell lake on seed 276 - the water ran from column 6 to column 24 without a break - and the
            // code read correctly, which is why the dump was worth taking.
            java.util.Set<Integer> wet = new java.util.HashSet<>();
            for (int c = 6; c < walkEnd - 4; ) {
                int run = 4 + rng.nextInt(5);                  // 4..8 wet
                for (int k = 0; k < run && c < walkEnd - 4; k++, c++) wet.add(c);
                int dry = 2 + rng.nextInt(3);                  // 2..4 dry
                c += dry;
            }
            for (int c = 6; c < walkEnd - 4; c++) {
                if (!wet.contains(c)) continue;
                int fr = pathFloor[c];
                if (fr < 0 || fr + 2 >= height) continue;
                if (g[fr][c] != '#') continue;      // a door, a step face, anything already spoken for
                // FLAT ONLY. At a step, column c's two rows and column c+1's two rows overlap into a THREE-row
                // run, because the step moves the whole band down by one - and the shape assertion caught that
                // too ("no floor under the pool, ' ' instead"). One row per column at a step is the alternative,
                // and that fails the other half of the same assertion. So the water crosses the flat stretches
                // and the steps stay dry: a flooded level with dry ledges, which is also what it should look
                // like, since a step is where you get out.
                if (c > 6 && pathFloor[c - 1] != fr) continue;
                if (c < walkEnd - 5 && pathFloor[c + 1] != fr) continue;
                // THE WATER SITS ON THE WALK, ONE ROW ABOVE IT. The floor row keeps its '#', so the level's
                // walkable surface is EXACTLY where it was and the validator bot crosses without knowing water
                // exists - which is the only arrangement that has held up. Everything else was tried:
                //
                //   two rows at the walk   the bot dropped into the pool and wedged against the bank
                //   one row at the walk    the same, because the surface row is the row the bank is solid on
                //   water counted as floor the same, one row up
                //
                // In all three the bot's feet ended up level with a solid cell. Here the water is ankle-deep and
                // the ground under it is untouched, so the path the generator carved is still the path.
                g[fr - 1][c] = '~';
                // Piranhas, densely, because on this level the water is the level rather than a hazard in it.
                if (rng.nextDouble() < 0.30) {
                    int howMany = 3 + rng.nextInt(3);       // 3..5, in a group
                    for (int k = 0; k < howMany; k++) {
                        int cc = Math.min(width - 2, c + rng.nextInt(6));
                        int cf = pathFloor[cc];
                        if (cf > 0 && g[cf - 1][cc] == ' ') g[cf - 1][cc] = 'f';
                    }
                }
            }
        }

        // Border walls
        for (int r = 0; r < height; r++) {
            g[r][0] = (g[r][0] == ' ') ? '#' : g[r][0];
            if (g[r][width - 1] == ' ') g[r][width - 1] = '#';
        }

        // Assemble rows (top to bottom)
        // A ONE-WAY PLANK OVER A GAP, every fourth level that is not already getting a ferry. A one-way platform is
        // the thing you jump up THROUGH and then stand on, and until now the generated run never contained one: a
        // coverage probe over levels 1 to 40 found water on 18, cracked floor on 36, bats on 36, snakes on 26,
        // pickups on 29 - and ONE-WAYS ON NONE. Fourth time this shape has turned up in three days (the boss, the
        // ending, the mover): the mechanic exists, the tutorial teaches it, and the run never shows it.
        //
        // Written straight into the grid rather than declared as a spec, because a one-way IS a cell - LevelMap
        // parses '=' into world.oneways. One row above the walk, over a gap: the plank is a bridge you can jump
        // onto, the water below is what it is, and the gap was already jumpable so the plank is a route rather
        // than the route.
        if (levelNum > 0 && levelNum % PLANK_EVERY == 0 && levelNum % MOVER_EVERY != 0) {
            int[] gap = ferryGap(g);
            if (gap != null) {
                int row = gap[1] - 1;
                for (int c = gap[0] - 1; c <= gap[0] + 1; c++) {
                    if (c > 0 && c < width - 1 && g[row][c] == ' ') g[row][c] = '=';
                }
            }
        }

        List<String> rows = new ArrayList<>();
        for (char[] row : g) rows.add(new String(row));
        this.lastMap = new LevelMap(rows);
        // The walk's floor per column, or -1 where there is no walk (a gap). Kept because a check cannot tell a
        // FLOODED GAP from a FLOODED WALK by looking at the grid: both are water at the walk's level with a solid
        // floor under them. The difference is whether the walk claims a floor there, and only the generator knows.
        // AND THIS IS THE WALK PATH, which is where the ferry belongs. My first attempt put this block beside
        // the BOSS, and the boss's block is in the SAFE-ROOM path - the one only tenth levels take. A
        // diagnostic print fired on level 30 and on no other multiple of three, which is how that was found:
        // thirteen levels asked for a ferry, none got one, and the reason was which of the two `return`s the
        // code sat above.
        // A FERRY OVER A GAP, every third level. Placed after the map is built because it needs the finished walk to
        // find a gap, and skipped silently when there is none - a level without a ferry is a level without a
        // ferry, and a generator that refuses is worse than one that occasionally leaves something out.
        if (levelNum > 0 && levelNum % MOVER_EVERY == 0) {
            int[] gap = ferryGap(g);
            if (gap != null) {
                double surface = gap[1] * 32.0;
                double centreX = gap[0] * 32.0 + 32.0;      // the middle of the cell
                double w = 96, h = 16;                      // three cells wide, one thin platform
                // IT RIDES ONE ROW ABOVE THE WALK, which is not decoration: THE GAPS ARE WHERE THE WATER GOES
                // ("pools form in the gaps"), so a dry gap essentially does not exist - the first version refused
                // flooded ones and consequently found NOTHING on any of the thirteen levels that asked. One row up
                // clears the water's surface, which sits on the walk row, and 32px is well inside the 73px jump, so
                // the ferry is boardable from either lip.
                // Amplitude walks it across the gap either side of its middle, so it reaches both. Period three
                // seconds: slow enough to step onto, fast enough that waiting is not the game.
                this.lastMap.addMover(LevelMap.MoverSpec.horizontal(
                        centreX, surface - h / 2 - 32, w, h, 48, 3.0));
            }
        }

        this.lastPathFloor = pathFloor.clone();
        return this.lastMap;
    }

    /** True if column c is inside the vault's carve span. */
    private boolean nearVault(int c) {
        if (vaultCol < 0) return false;
        return c >= vaultCol - 1 && c <= vaultCol + 3;
    }

    /**
     * Bombable side vault: a two-tall doorway in a cliff face, with a
     * chamber carved into the rock behind it and a heart inside.
     *
     * Why the wall and not the floor: the player body is 48px wide and
     * a floor hole is one cell (32px) wide, so the body is always
     * carried across the lips and can never fall in -- a "cracked floor
     * with a pocket underneath" cannot work, which is also why the
     * heart ended up buried in the ground. A VERTICAL doorway has no
     * such problem: passage depends on headroom, not width, and two
     * cells (64px) clears the 44px body.
     *
     * Layout, with L = the walk row the player arrives on and
     * U = L-2 = the raised row beyond the climb:
     *   face  (c,       U..L-1)   -> cracked wall, 2 cells tall
     *   vault (c+1..c+2, L-1..L)  -> chamber, tucked under the ledge
     * The chamber floor sits one cell below the arriving floor, so the
     * player steps down into it; its ceiling is the raised walkway,
     * which is what makes this a hidden cellar and not an open hole.
     */
    /** The walk's floor per column for the last level generated, or -1 where there is no walk. */
    public int[] lastPathFloor;

    private void buildVault(char[][] g) {
        if (vaultCol < 0) return;
        int c = vaultCol, L = vaultLowerRow, U = vaultUpperRow;
        if (c < 4 || c + 3 >= width || U < 1 || L - 2 < 1) return;

        // The chamber needs two clear columns on the raised ledge.
        if (pathFloor[c + 1] != U || pathFloor[c + 2] != U) return;
        // ...and an intact ledge floor above it, or the vault would
        // open a hole in the path the player walks on.
        if (g[U][c + 1] != '#' || g[U][c + 2] != '#') return;
        // Rock to carve: the terrain mass must have filled it.
        if (g[L - 1][c + 1] != '#' || g[L - 1][c + 2] != '#'
                || g[L][c + 1] != '#' || g[L][c + 2] != '#') return;
        // The face itself must still be plain rock.
        if (g[U][c] != '#' || g[L - 1][c] != '#') return;
        // The plug has to reach down through the walk-level cell too.
        // A player standing on the lower floor is 44px tall, so their head
        // sits 12px INTO row U - the ledge's own floor row. That means the
        // chamber (which is under the ledge) cannot be walked into
        // horizontally: the ledge is at head height the moment you cross the
        // threshold, and the player is simply too tall. So the doorway is a
        // step DOWN instead - the plug includes row L, and bombing it drops
        // the player one cell into the chamber, where the ceiling is the
        // ledge and their head finally clears it.
        // (Playtest: "the cave it opens is too small to enter so i cant get
        // the heart". The chamber was the right size; the ENTRY was not.)
        if (g[L][c] != '#') return;

        // Cracked wall: the two cells of cliff face at the player's own
        // body height. Bombing them opens the doorway.
        g[U][c] = 'C';
        g[L - 1][c] = 'C';
        g[L][c] = 'C';

        // Chamber behind it.
        g[L - 1][c + 1] = ' ';
        g[L][c + 1] = ' ';
        g[L - 1][c + 2] = ' ';
        g[L][c + 2] = ' ';

        // Heart on the chamber floor, at the far end so the player has
        // to step all the way in to reach it - and only sometimes, on the same
        // curve as the floor pocket. The chamber itself is still built either
        // way: a bombable room that is sometimes empty is a room, and one that
        // is only sometimes THERE is a coin flip the player cannot read.
        if (rng.nextDouble() < healChance(levelNum)) g[L][c + 2] = 'h';
    }

    /**
     * Every tenth level: a safe room.
     *
     * Flat ground, no hazards, no enemies, no vault, and a heart in a jar
     * that raises the lives cap by one and fills you to it. This is the pacing
     * beat for the difficulty climb - the reward for ten levels is breathing
     * room and a bigger ceiling, not another fight. The path is one flat row,
     * so it records no jump waypoints at all and the validator walks straight
     * through it.
     */
    private LevelMap generateSafeRoom() {
        char[][] g = new char[height][width];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');

        int floorRow = height - 6;          // a little lower than usual: open sky
        for (int c = 1; c < width - 1; c++) {
            for (int r = floorRow; r < height; r++) g[r][c] = '#';
            pathFloor[c] = floorRow;
        }
        pathFloor[0] = floorRow;
        pathFloor[width - 1] = floorRow;

        g[floorRow - 1][2] = 'P';                      // spawn
        g[floorRow - 1][width / 2] = 'j';              // the reward
        g[floorRow - 1][width - 3] = 'E';              // the way on

        for (int r = 0; r < height; r++) {
            g[r][0] = '#';
            g[r][width - 1] = '#';
        }

        List<String> rows = new ArrayList<>();
        for (char[] row : g) rows.add(new String(row));
        this.lastMap = new LevelMap(rows);
        // AND THE WALK'S FLOORS, which this path filled into `pathFloor` and then never published.
        //
        // Every tenth level comes through here, so `lastPathFloor` was NULL for a safe room on a fresh
        // generator and - worse - the PREVIOUS level's floors on a reused one, because the field is only
        // written by the other path. A stale answer rather than a missing one, which is the harder kind to
        // notice.
        //
        // The readers guard with `c < gen.lastPathFloor.length`, and that guard NPEs on null rather than
        // skipping - so the gate survived only because none of the checks happen to walk a safe-room level.
        // Found by writing a difficulty-curve probe that walked levels 1 to 40 and died on level 10.
        this.lastPathFloor = pathFloor.clone();

        // EVERY TENTH LEVEL GETS A BOSS, placed after the map is built because it needs the finished grid to find a
        // stretch that is flat and clear. It is SKIPPED SILENTLY when there is none: a level without a boss is a
        // level without a boss, and the alternative - a generator that refuses - is worse.
        if (levelNum > 0 && levelNum % BOSS_EVERY == 0) {
            int col = bossColumn(g);
            if (col >= 0) {
                int surface = pathFloor[col] * 32;
                this.lastMap.addBoss(new LevelMap.BossSpec(col * 32 + 32, surface - 32, 64, 64, levelNum));
            }
        }
        return this.lastMap;
    }
}
