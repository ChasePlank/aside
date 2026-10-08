package aside.games.fruitjump;

import aside.games.fruitjump.engine.LevelMap;

import java.util.ArrayList;
import java.util.List;

/**
 * The first eleven levels are hand-built tutorials, not generated ones.
 *
 * Each teaches exactly one thing and shows it rather than saying it, in the
 * order the player needs it: move, fight, blast, shoot, deal with the bat,
 * know your items, know your enemies, know the ground, know the water,
 * know that the ground can move under you, and know what a boss is.
 * Level 11 ends the run back at the menu.
 *
 * THIS HEADER SAID "eight" AND "Level 8 ends the run" LONG AFTER LEVEL 9 EXISTED. Water was added as level 9 and
 * the count and the list were not revisited, so the claim was false in the file that DEFINES `LAST` - and the
 * check written to catch exactly this class of staleness did not see it, because it greps for the phrase
 * "ends at <N>" and this file says neither "ends at" nor a digit. Two spellings of the same claim, one covered.
 * A comment naming a value is a claim; a check that matches one phrasing of it is a check with a blind spot.
 * That check was widened on 2026-10-08 (aside/tools/check-tutorial-counts.py) and this header is one of the
 * claims it reads, so level 10 arriving here was the first time the count propagated BECAUSE something
 * checked it rather than because someone remembered.
 *
 * Built PROGRAMMATICALLY from a flat floor plus placed entities rather than as
 * hand-written ASCII grids. Flat rooms are exactly the shape you should
 * generate - I have been consistently wrong about hand-placed grids, and these
 * have to be right because they are the first thing anyone plays.
 */
public class Tutorial {
    public static final int LAST = 11;
    static final int W = 60, H = 20, FLOOR = 17;

    public static boolean isTutorial(int level) {
        return level >= 1 && level <= LAST;
    }

    /** The last tutorial level sends the player back to the menu instead of level 12. */
    public static boolean endsTheTutorial(int level) {
        return level == LAST;
    }

    /** Floating words the player walks into. Drawn at world coordinates. */
    public record Sign(double x, double y, String text) {}

    /** Place a character at a cell, ignoring anything out of bounds. */
    private static void put(char[][] g, int col, int row, char c) {
        if (row < 0 || row >= H || col < 0 || col >= W) return;
        g[row][col] = c;
    }

    private static void wall(char[][] g, int col, int top, int bottom) {
        for (int r = top; r <= bottom; r++) put(g, col, r, '#');
    }

    public static LevelMap map(int level) {
        char[][] g = new char[H][W];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');

        // Flat floor, no hazards. Everything on top of it is placed by hand.
        for (int c = 1; c < W - 1; c++) {
            for (int r = FLOOR; r < H; r++) g[r][c] = '#';
        }
        put(g, 2, FLOOR - 1, 'P');
        put(g, W - 3, FLOOR - 1, 'E');

        switch (level) {
            case 2 -> {
                // One spider to stomp.
                put(g, 22, FLOOR - 1, 'o');
            }
            case 3 -> {
                // A breakable wall, floor to ceiling, with a snake BEHIND it.
                // The snake dies in the blast, which is the point: it shows a
                // bomb kills enemies as well as walls.
                wall(g, 24, 6, FLOOR - 1);
                put(g, 24, FLOOR - 1, 'C');
                put(g, 24, FLOOR - 2, 'C');
                put(g, 24, FLOOR - 3, 'C');
                put(g, 27, FLOOR - 1, 's');
            }
            case 4 -> {
                // Target practice: two spiders at range, nothing in between.
                put(g, 30, FLOOR - 1, 'o');
                put(g, 40, FLOOR - 1, 'o');

                // AND A LEDGE THE HOOKSHOT IS THE ONLY WAY UP TO. The sign above teaches the control; this is the
                // level teaching the USE, which is what the sign could not do on its own - every other mechanic in
                // the tutorial is taught beside the thing it is for.
                //
                // SIX ROWS UP, and above the SIGN ROW rather than on it. The signs are drawn at y = (FLOOR - 4) *
                // 32, so a ledge at FLOOR - 4 would have the words inside the stone - and the sign-collision check
                // compares signs to signs, not signs to terrain, so it would not have said so.
                //
                // Six rows is 192px. The jump apex is about 73px, so it is well out of reach; the hookshot's range
                // is 400px, so it is well inside that.
                for (int c = 8; c <= 14; c++) put(g, c, FLOOR - 6, '#');
                put(g, 11, FLOOR - 7, 'h');     // a snack, so the reward is the reason to go up
            }
            case 5 -> {
                // Bats, and room to learn that one knockdown clears the swarm.
                put(g, 28, FLOOR - 5, 'b');
                put(g, 36, FLOOR - 6, 'b');
            }
            case 6 -> {
                // Every item the game currently has, spaced out to be read.
                put(g, 14, FLOOR - 1, 'h');   // snack
                put(g, 24, FLOOR - 1, 'j');   // heart in a jar
                put(g, 32, FLOOR - 1, 'k');   // key
                put(g, 42, FLOOR - 1, 'D');   // and the door the key opens
            }
            case 7 -> {
                // Every enemy, boxed and hovering, out of reach. A display
                // case, not a fight.
                box(g, 12, 8, 'o');
                box(g, 24, 7, 's');
                box(g, 36, 9, 'b');
            }
            case 8 -> {
                // The ground hazards, two tiles wide so they are legible, and set INTO the floor rather than on
                // top of it. They were at FLOOR - 1 with solid '#' directly below, so they rendered as spikes
                // sitting on the ground - while the sign two lines below them promises "a pit costs you a heart,
                // not the run - climb out". Reported from play as "the spike level had them above ground, not a
                // pit". Clearing the row above and putting the spike in the floor is what makes it a pit.
                for (int c = 18; c <= 19; c++) { g[FLOOR - 1][c] = ' '; g[FLOOR][c] = '^'; }
                for (int c = 30; c <= 31; c++) { g[FLOOR - 1][c] = ' '; g[FLOOR][c] = '^'; }
            }
            case 9 -> {
                // WATER. The tutorial taught the spider, the bomb, the arrow, the bat, the pickups and the
                // spikes, and then water became common and piranhas arrived - so a player met both for the
                // first time in a generated level with nothing to tell them what they were.
                //
                // The water is ONE ROW at FLOOR - 1: ankle-deep, on the walk's own floor, which is exactly how
                // a flooded level is built (see LevelGen). The floor is untouched, so walking through it is
                // walking. The piranhas sit at FLOOR - 2, at the surface, where you can see them coming.
                for (int c = 16; c <= 34; c++) g[FLOOR - 1][c] = '~';
                for (int c = 22; c <= 24; c++) g[FLOOR - 2][c] = 'f';   // a group, because they come in groups
                put(g, 40, FLOOR - 1, '~');                             // and one on its own, to show the difference

                // AND A POOL, because the water above is ankle-deep ON PURPOSE - it is how a flooded level is
                // built - and a player who only ever sees this one never meets the water they can SWIM in. The
                // game has two kinds: a flooded walk, one row, which you wade through; and a pool, two rows with
                // a floor under it, which you swim in and can dive in. The README documents "Space / W - jump,
                // and swim upward in water" and "Down / S - dive (water only)", and until now the tutorial taught
                // neither, because there was nothing deep enough to teach them in.
                for (int c = 44; c <= 50; c++) { g[FLOOR - 2][c] = '~'; g[FLOOR - 1][c] = '~'; }
            }
            case 10 -> {
                // MOVING PLATFORM. Everything before this taught what the player CONTROLS and every hazard the
                // world has. This one teaches something the world DOES: every other floor in the game holds
                // still, so the difficulty is not that a player would fail to understand it - it is that there
                // is nothing anywhere to notice.
                //
                // A LEDGE SIX ROWS UP with a snack on it, at the same height as level 4's - 192px, well past the
                // 70.3px the jump actually reaches (measured, not assumed), so the lift is the way up rather than
                // a decoration beside a step that could simply be jumped.
                //
                // THE LIFT HAS A BAY, and that is what makes this level pass its own gate. A platform resting on
                // the floor anywhere along the walk is a solid obstacle in the corridor, and `SelfTest` requires
                // a JUMP-ONLY bot to finish every tutorial level - so the level's traversability would depend on
                // where the platform happened to be in its cycle when the bot arrived. Two cells cut out of the
                // walk gives the lift somewhere to go: at the bottom of its travel it sits flush with the floor
                // INSIDE the bay rather than on the path, and the bot crosses the two-cell gap by jumping it,
                // which is well inside the four-cell gaps the generator itself produces.
                //
                // The bay is also the answer to falling in: it is two deep with a floor, and the lift is in it,
                // so a player who drops in stands on the lift and rides back up. Nothing here can cost the run.
                for (int c = 36; c <= 37; c++) { g[FLOOR][c] = ' '; g[FLOOR + 1][c] = ' '; }
                for (int c = 38; c <= 44; c++) put(g, c, FLOOR - 6, '#');
                put(g, 41, FLOOR - 7, 'h');   // the snack is the reason to go up

                // THE PLATFORM ITSELF IS NOT IN THIS GRID. Its path is numbers - how far it travels, how long a
                // cycle takes - and a grid holds one character per cell with no room for either. It is declared
                // below, where the numbers can say what they are for.
            }
            case 11 -> {
                // A BOSS. Everything before this taught what the player controls and what the world does;
                // this is the first thing in the game that fights back on purpose, and the first that cannot be
                // solved by being careful.
                //
                // A FLAT WALK AND NOTHING ELSE, deliberately. Every mechanic it needs is already taught - the
                // arrows, the bomb, the hookshot, the stomp - so the level adds no new furniture, and the fight is
                // read off the boss itself: the rim goes red when it is dangerous and blue when it is not, and the
                // mark on its back is lit only while damage counts. The one thing worth teaching here is that the
                // MARK is the opening, which the sign says and the fight repeats.
                //
                // THE BOSS IS NOT IN THIS GRID. Where it stands, how big it is and what seed its attack order
                // comes from are all decisions a character-per-cell grid has no room for, so it is declared below
                // beside the lift. See LevelMap.BossSpec.
            }
            default -> { }
        }

        for (int r = 0; r < H; r++) {
            g[r][0] = '#';
            g[r][W - 1] = '#';
        }

        StringBuilder sb = new StringBuilder();
        for (char[] row : g) sb.append(new String(row)).append('\n');
        LevelMap built = LevelMap.parse(sb.toString());

        // LEVEL 10'S LIFT, and the only moving platform in the tutorial.
        //
        // VERTICAL, and that is a teaching decision before it is a technical one: riding is the mechanic, and a
        // lift shows it with nothing to time. A horizontal ferry would teach it too and would make the level's
        // one hazard a wall-clock mistake, which is the wrong lesson on the level that introduces the idea.
        //
        // THE NUMBERS ARE THE GEOMETRY, not taste. It rises exactly 192px, the same as level 4's ledge:
        //   bottom - its surface at 544, the walk's own surface, so it sits flush with the floor inside its bay
        //            and a player simply walks onto it, with no step up to judge
        //   top    - its surface at 352, the ledge's own surface, so the step off is level
        // Centre travel is therefore 552 down to 360: origin 456, amplitude 96.
        //
        // It is centred in the two-cell bay at x 1152..1216, and the ledge starts at 1216, so at the top of the
        // travel the lift and the ledge are flush edge to edge. The lift stops BESIDE the ledge rather than
        // under it for a reason worth keeping: a lift rising into the ledge's own tiles would carry the player
        // up into solid stone, which is what the first draft of this level did.
        //
        // Period 6s over the 384px round trip: slow enough to read and to step onto, which is the only thing the
        // timing has to be. There is no window to miss - the bay holds you if you mistime it.
        // LEVEL 11'S BOSS, standing on the walk at the middle of the level.
        //
        // THE NUMBERS ARE THE GEOMETRY. FLOOR is row 17, so the walk's surface is at 544; a 64-high body centred at
        // 512 has its feet exactly on it. It is wide enough (64) to read as a wall you cannot walk through, which
        // is the point - the fight happens where it stands.
        //
        // SEEDED FROM THE LEVEL NUMBER, so its attack order is the same on every attempt at this level. Unseeded,
        // its `new Random()` would make every attempt at the fight different in a way nothing could test, which is
        // the mistake the FNAF screens record in their own comments ("made every run unreplayable").
        if (level == 11) {
            built.addBoss(new LevelMap.BossSpec(31 * 32, 512, 64, 64, level));
        }

        if (level == 10) {
            built.addMover(LevelMap.MoverSpec.vertical(
                    37 * 32,     // x 1184: centred in the bay, right edge flush with the ledge at 1216
                    456,         // path origin; 96px either way gives 360 (top) and 552 (bottom)
                    64, 16,      // two cells wide, half a cell thick - exactly the bay's width
                    96, 6.0));
        }
        return built;
    }

    /** A sealed 3x3 display box holding one enemy, for level 7. */
    private static void box(char[][] g, int col, int row, char enemy) {
        for (int c = col - 1; c <= col + 1; c++) {
            put(g, c, row - 1, '#');
            put(g, c, row + 1, '#');
        }
        put(g, col - 1, row, '#');
        put(g, col + 1, row, '#');
        put(g, col, row, enemy);
    }

    /** The floating words for a level. */
    public static List<Sign> signs(int level) {
        List<Sign> s = new ArrayList<>();
        double y = (FLOOR - 4) * 32.0;
        switch (level) {
            case 1 -> {
                s.add(new Sign(6 * 32, y, "MOVE   A / D   or   LEFT / RIGHT"));
                s.add(new Sign(30 * 32, y, "JUMP   SPACE"));
            }
            case 2 -> {
                s.add(new Sign(10 * 32, y, "THAT IS A SPIDER"));
                s.add(new Sign(20 * 32, y + 26, "JUMP ON ITS HEAD TO SQUASH IT"));
            }
            case 3 -> {
                // Beside the wall it names, not back at the spawn - the label
                // has to be on the thing it is labelling.
                s.add(new Sign(17 * 32, y, "CRACKED WALL"));
                s.add(new Sign(17 * 32, y + 26, "BOMB   G   -   it drops at your feet"));
                s.add(new Sign(29 * 32, y, "STAND BACK. the blast hurts you too"));
            }
            case 4 -> {
                // WAS JUST "ARROWS   F", the only sign in the tutorial that named a key without saying what it
                // does, when every other one follows "BOMB  G  -  it drops at your feet".
                s.add(new Sign(20 * 32, y, "ARROWS   F   -   hits what you face"));
                s.add(new Sign(32 * 32, y, "the spiders are out of reach. shoot them"));
                // AND THE HOOKSHOT, which the README leads with - "with hookshot, bombs, bow" - and which the
                // tutorial never mentioned in any of its levels. It fires at a wall and pulls you to it, so
                // it belongs on the level that is already about reaching what you cannot walk to.
                // TWO SIGNS, AND THE SECOND IS BELOW THE FIRST'S WRAP. The first is 37 characters and wraps to a
                // 34-character line, so its second line lands where a sign 26px below would start - which is the
                // same collision the JAR and KEY signs had on tutorial 6. TutorialTest caught it on the first run.
                // BACK AT 44, under the ledge. The signs are about thirteen tiles wide once wrapped, so there is no
                // room for a third one between the arrows sign at 12 and the spiders sign at 30 - 24 overlapped
                // both, which the collision check caught twice.
                s.add(new Sign(8 * 32, y, "HOOKSHOT   X   -   pulls you to a wall"));
                s.add(new Sign(8 * 32, y + 52, "Shift + X fires it upward"));
            }
            case 5 -> {
                s.add(new Sign(10 * 32, y, "BATS KNOCK YOU DOWN"));
                s.add(new Sign(10 * 32, y + 26, "they cannot hurt you - and they do not stay"));
            }
            case 6 -> {
                s.add(new Sign(12 * 32, y, "SNACK  -  one life back"));
                s.add(new Sign(22 * 32, y, "JAR  -  a life FOREVER, and a full refill"));
                // MOVED RIGHT, from 30 * 32. The JAR sign to its left is 41 characters and wraps to a 34-character
                // line, so it reaches past x = 1000. The KEY sign started at 960 and sat INSIDE it, and the two
                // overlaid so that neither could be read - visible on tutorial 6 since October.
                s.add(new Sign(38 * 32, y, "KEY  -  opens the door"));
            }
            case 7 -> {
                s.add(new Sign(20 * 32, y - 60, "WHAT IS OUT THERE"));
                // One name per creature, at the creature's own column. This was a single string with eight
                // literal spaces between the words, starting at column 10, so the names could not track boxes
                // at 12, 24 and 36 - reported from play as "the enemies level didn't space the names to match
                // the creature". Literal spaces cannot align to anything; positions can.
                s.add(new Sign(11 * 32, y - 34, "spider"));
                s.add(new Sign(23 * 32, y - 34, "snake"));
                s.add(new Sign(35 * 32, y - 34, "bat"));
            }
            case 8 -> {
                s.add(new Sign(10 * 32, y, "SPIKES"));
                s.add(new Sign(10 * 32, y + 26, "a pit costs you a heart, not the run - climb out"));
            }
            case 9 -> {
                s.add(new Sign(8 * 32, y, "WATER"));
                s.add(new Sign(8 * 32, y + 26, "you walk through it. it does not slow you down"));
                s.add(new Sign(20 * 32, y - 60, "PIRANHA"));
                s.add(new Sign(20 * 32, y - 34, "unlike a bat, this one takes a HEART"));
                // THE POOL'S OWN SIGN, beside the pool. The water above is wading; this is swimming, and the two
                // controls the README documents for it - swim up and dive - are only true here.
                s.add(new Sign(44 * 32, y - 60, "DEEP WATER"));
                s.add(new Sign(44 * 32, y - 34, "SPACE swims up.   DOWN / S dives"));
                s.add(new Sign(8 * 32, y + 52, "one more thing after this one"));
                // MOVED DOWN to y + 26, from y - 8. The sign above it is 36 characters, so it WRAPS ONTO A SECOND
                // LINE - and that line landed exactly where this one started. A sign that wraps occupies two
                // rows, and the next sign down has to clear both.
                s.add(new Sign(20 * 32, y + 26, "they come in groups. get out and they lose you"));
                // "that is everything. good luck" MOVED OFF THIS LEVEL AGAIN when level 11 arrived, and for the
                // second time. It is the tutorial's last word, and leaving it on a level that is no longer last
                // would be the same small lie twice - so it lives on whatever level is last, which last time was
                // 10 and is now 11.
            }
            case 10 -> {
                s.add(new Sign(24 * 32, y, "MOVING PLATFORM"));
                s.add(new Sign(24 * 32, y + 26, "stand on it. it lifts you"));
                s.add(new Sign(24 * 32, y + 52, "it comes back. nothing here can cost the run"));
                // ABOVE THE LEDGE, at y - 96, not on the sign row. The ledge's own surface is at FLOOR - 6, which
                // is ABOVE where signs are drawn by default (FLOOR - 4), so words on the usual row would have
                // landed inside the stone - the fault level 4 records finding by rendering it.
                // the parting line moved to level 11, which is now the last one
            }
            case 11 -> {
                s.add(new Sign(22 * 32, y, "A BOSS"));
                s.add(new Sign(22 * 32, y + 26, "it is only hurt while the mark is lit"));
                s.add(new Sign(22 * 32, y + 52, "back off when the rim goes red"));
                s.add(new Sign(44 * 32, y - 34, "that is everything. good luck"));
            }
            default -> { }
        }
        return s;
    }
}
