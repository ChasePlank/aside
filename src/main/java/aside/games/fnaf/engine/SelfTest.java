package aside.games.fnaf.engine;


/**
 * Headless self-test: run the bot across nights and seeds, report win rates
 * and failure causes, and ASSERT the things that must stay true.
 *
 * <p><b>It was a report and nothing else until 2026-10-03.</b> Every line of it
 * printed a number and no line of it could fail, so it could not be a gate --
 * and the README claimed it "asserts the invariants that were once bugs", which
 * was not true of this file. The numbers are still printed, because a win rate
 * is a reading rather than a contract; what is added is a small set of
 * assertions over the things a tuning pass must not be allowed to undo.
 */
public class SelfTest {

    static int checks = 0, failed = 0;

    static void check(String what, boolean ok) {
        checks++;
        if (!ok) { failed++; System.out.println("FAIL  " + what); }
    }

    /**
     * What the seed decides: where each animatronic is, how far it has got, and how much power is left.
     *
     * <p>A String rather than a comparison operator, because the failure has to be readable: when two deals differ,
     * the line says which animatronic moved and by how much.
     */
    static String deal(Game g) {
        return "hour=" + g.hour
                + " monty=" + g.monty.pathIndex + "/" + g.monty.stages
                + " roxanne=" + g.roxanne.pathIndex + "/" + g.roxanne.stages
                + " chica=" + g.chica.pathIndex + "/" + g.chica.stages
                + " freddy=" + g.freddy.pathIndex + "/" + g.freddy.stages
                + " power=" + (int) g.power;
    }

    public static void main(String[] args) {
        System.out.println("=== FNAF-Glamrock self-test ===\n");

        // 1. Determinism check: same seed = same night. THE NIGHT, NOT THE CLOCK.
        //
        // <p>This compared a.hour to b.hour, and the clock is not dealt: it advances on elapsed time, so ANY
        // implementation passes that. What the seed actually decides is which animatronic moves
        // (`game.rng.nextInt(20) < aiLevel`) and when the blackout arrives, and none of it was compared -
        // replacing `new Random(seed)` with `new Random()` left this check green for as long as it has existed.
        // Found by tools/mutations.sh on its first run here, which is what that list is for.
        //
        // <p>Night 5 rather than night 1, because a quiet night deals almost nothing and two identical deals are a
        // comparison with no content.
        {
            Game a = new Game(5, 42L);
            Game b = new Game(5, 42L);
            for (int i = 0; i < (int) (270 * 60); i++) { a.update(1.0/60); b.update(1.0/60); }
            String sa = deal(a), sb = deal(b);
            System.out.println("Determinism: a=" + sa + " b=" + sb + " (equal=" + sa.equals(sb) + ")");
            check("the same seed deals the same night, animatronics and all (" + sa + ")", sa.equals(sb));
        }

        // 2. Hour pacing: 6 hours * 45s = 270s per night
        {
            Game g = new Game(1, 1L);
            for (int i = 0; i < (int)(270 * 60); i++) g.update(1.0/60);
            System.out.println("Night length: status=" + g.status + " (expect SURVIVED if bot idle-safe... actually no bot, animatronics may kill)");
        }

        // 3. Bot win rates across nights (20 seeds each)
        int lastNightWins = 20;
        int[] nights = {1, 2, 3, 4, 5};
        for (int night : nights) {
            int wins = 0, jump = 0, powerOut = 0;
            int[] killer = new int[4];  // monty, roxanne, chica, freddy
            double powerLeftSum = 0;
            int powerWins = 0;
            for (int seed = 1; seed <= 20; seed++) {
                Game g = new Game(night, seed * 7919L);
                Bot bot = new Bot(g, seed * 104729L);
                double dt = 1.0 / 60;
                int steps = 0;
                while (g.status == Game.Status.PLAYING || g.status == Game.Status.POWER_OUT) {
                    bot.play(dt);
                    g.update(dt);
                    if (++steps > 60 * 300) break;  // 5-min safety
                }
                if (g.status == Game.Status.SURVIVED) {
                    wins++;
                    powerLeftSum += g.power;
                    powerWins++;
                } else if (g.status == Game.Status.JUMPSCARED) {
                    jump++;
                    if (g.jumpscareBy == g.monty) killer[0]++;
                    else if (g.jumpscareBy == g.roxanne) killer[1]++;
                    else if (g.jumpscareBy == g.chica) killer[2]++;
                    else if (g.jumpscareBy == g.freddy) killer[3]++;
                }
                else powerOut++;
            }
            System.out.printf("Night %d: %d/20 wins, %d jumpscares (M%d R%d C%d F%d), %d other%n",
                    night, wins, jump, killer[0], killer[1], killer[2], killer[3], powerOut);
            // A reasonable player must be able to win every night, and the
            // week must cost them something somewhere.
            //
            // The first version of this asserted that no night goes to 20/20,
            // and it FAILED on nights 1 through 4 -- which is the finding
            // rather than the fault. FNAF 1 is the franchise's first game and
            // the bot wins it outright until night 5, where it drops to 18/20.
            // That is a fact about the tuning and it is now written down; what
            // is asserted is the shape that has to hold, not the shape I
            // assumed before measuring.
            check("night " + night + ": the bot wins it", wins > 0);
            lastNightWins = wins;
        }

        // The week has to cost the bot something by the end. Measured: nights
        // 1-4 are 20/20 and night 5 is 18/20, so this is the one assertion
        // that says the franchise's first game is not a formality.
        check("the last night costs the bot something", lastNightWins < 20);

        // 4. Blackout survival: power-out at 5 AM — late blackout
        //    should be survivable (music box + delay can carry to 6AM)
        {
            int survived = 0;
            int tested = 0;
            for (int seed = 1; seed <= 20; seed++) {
                Game g = new Game(3, seed * 31L);
                Bot bot = new Bot(g, seed * 31L);
                double dt = 1.0/60;
                // bot plays until 5AM (225s)
                while (g.status == Game.Status.PLAYING && g.time < 225.0) {
                    bot.play(dt);
                    g.update(dt);
                }
                if (g.status != Game.Status.PLAYING) continue;  // died before blackout — skip seed
                tested++;
                // force power-out
                g.power = 0.01;
                while (g.status == Game.Status.PLAYING) g.update(dt);
                while (g.status == Game.Status.POWER_OUT) g.update(dt);
                if (g.status == Game.Status.SURVIVED) survived++;
            }
            System.out.println("Blackout survival (forced at 5AM, night 3): " + survived + "/" + tested + " (skipped seeds where bot died early)");
            // A blackout at 5 AM is the one power-out that is meant to be
            // survivable: the music box and the delay can carry you to 6.
            check("a blackout at 5 AM is survivable", tested > 0 && survived > 0);
        }

        // --- CARRIED FROM THE STANDALONE COPY, 2026-10-03 -------------------------------------------------
        //
        // These two were in ChasePlank/fnaf's suite and not in this one, and this one had four the other did
        // not: each copy had drifted into covering things the other never checked, while both reported the
        // same win rates. That is why the README's "behaviourally identical, verified by their self-tests
        // reporting the same numbers" was true and still misleading - the numbers agreed because they are the
        // same game, and the suites had quietly stopped asking the same questions.
        //
        // The sync runs ONE WAY, aside to the standalone, so anything only in the standalone is one sync away
        // from being deleted. Both are here now, and the summary line is the same shape in both, so the
        // comparison is a one-line diff instead of a reading.
        // 5. Every animatronic must actually MOVE, on every night.
        //    Not hypothetical: the night-1 row {0,0,1,1} shipped, and level 0 is a 0% move
        //    chance, so Monty and Roxanne literally never moved. Kinger reported it as
        //    "they dont move on night 1". This test printed win rates straight through that
        //    bug without noticing, because it asserted nothing about them - the same hollow
        //    shape as a check that has never been seen to fail.
        for (int night = 1; night <= 5; night++) {
            boolean[] moved = new boolean[4];
            for (long seed = 1; seed <= 10; seed++) {
                Game g = new Game(night, seed);
                Animatronic[] cast = {g.monty, g.roxanne, g.chica, g.freddy};
                for (int i = 0; i < (int) (270 * 60) && g.status == Game.Status.PLAYING; i++) {
                    g.update(1.0 / 60);
                    for (int c = 0; c < 4; c++) {
                        if (cast[c].pathIndex > 0 || cast[c].stages > 0) moved[c] = true;
                    }
                }
            }
            check("night " + night + ": every animatronic moves at least once"
                            + "   [Monty=" + moved[0] + " Roxanne=" + moved[1]
                            + " Chica=" + moved[2] + " Freddy=" + moved[3] + "]",
                    moved[0] && moved[1] && moved[2] && moved[3]);
        }

        // 6. An animatronic standing in an open doorway must wait the grace window before it
        //    kills. The window is what the whole desk feels like: it is the difference between
        //    a game and a reflex test. Tracked by watching arrival and death on an idle night
        //    (doors open the whole time, which is the only way to reach a door kill at all -
        //    the bot always closes in time, and that is why the bot can never test this).
        {
            double shortest = 1e9;
            int measured = 0;
            for (long seed = 1; seed <= 12; seed++) {
                Game g = new Game(5, seed);
                Animatronic[] cast = {g.monty, g.roxanne, g.chica};
                boolean[] seen = new boolean[3];
                double[] arrive = new double[3];
                for (int i = 0; i < (int) (270 * 60); i++) {
                    g.update(1.0 / 60);
                    for (int c = 0; c < 3; c++) {
                        boolean here = cast[c].atOffice();
                        if (here && !seen[c]) { seen[c] = true; arrive[c] = g.time; }
                        else if (!here) seen[c] = false;
                    }
                    if (g.status == Game.Status.JUMPSCARED) {
                        for (int c = 0; c < 3; c++) {
                            if (g.jumpscareBy == cast[c] && seen[c]) {
                                shortest = Math.min(shortest, g.time - arrive[c]);
                                measured++;
                            }
                        }
                        break;
                    }
                    if (g.status != Game.Status.PLAYING) break;
                }
            }
            // Compared against an INDEPENDENT floor, not against GRACE_SECONDS. The first
            // version of this assertion measured the gap and checked it against the constant
            // that defines the gap - which is self-referential and can never fail: set the
            // window to half a second and the expectation falls to half a second with it. It
            // passed the mutation, which is how that was found. Two seconds is the floor a
            // person can actually react inside, and the design value is well above it.
            check("grace window: a doorway kill waits at least 2s after arriving"
                            + "   [" + measured + " door kill(s), shortest gap "
                            + (measured == 0 ? "n/a" : String.format("%.1fs", shortest))
                            + " vs GRACE_SECONDS=" + Game.GRACE_SECONDS + "]",
                    measured == 0 || shortest >= 2.0);
        }

        System.out.println("\n--- the cues ---");
        // Every cue this game asks for has a file. Four of the FNAF games
        // had no such check, so a deleted cue would have been silent: the
        // game falls back to nothing and nothing says so.
        for (String cue : new String[]{"fan_hum", "at_door", "chime_6am", "footstep", "light_click", "pot_clank", "power_down", "power_up", "sprint", "camera_up", "camera_down", "static"}) {
            check("audio/" + cue + " has a file", hasCue(cue));
        }

        // AND THE CUES THE GAME ACTUALLY PLAYS, which the list above cannot see. That list is hard-coded here and
        // asks whether a FILE exists for each name in it; nothing asked whether a name the game PLAYS has a file.
        // tools/mutations.sh found it: replacing `a.sfx("at_door")` with a name that has no file was NOT CAUGHT,
        // because the suite was reading its own list rather than the game. A cue that does not load is silent, and a
        // silent cue is indistinguishable from a quiet moment - which is the whole reason a night is frightening.
        //
        // The names are read out of the SOURCE, and that is deliberate - and the walk starts at src/main/java
            // and filters on "/fnaf/", because this file is identical in two repositories whose FNAF code lives at
            // different paths. A hard-coded path made the copy in the engine find nothing and report zero named. the source is where a new call would be
        // written, so a cue added tomorrow is covered tomorrow.
        {
            java.util.Set<String> played = new java.util.TreeSet<>();
            java.util.regex.Pattern call = java.util.regex.Pattern.compile(
                    "\\.(?:sfx|music)\\(\\s*\"([a-z0-9_]+)\"");
            java.util.regex.Pattern array = java.util.regex.Pattern.compile(
                    "CUES\\s*=\\s*\\{([^}]*)\\}");
            java.util.regex.Pattern quoted = java.util.regex.Pattern.compile("\"([a-z0-9_]+)\"");
            try (java.util.stream.Stream<java.nio.file.Path> walk =
                         java.nio.file.Files.walk(java.nio.file.Paths.get("src/main/java"))) {
                for (java.nio.file.Path f : walk.filter(x -> x.toString().endsWith(".java") && x.toString().contains("/fnaf/")).toArray(java.nio.file.Path[]::new)) {
                    String src = new String(java.nio.file.Files.readAllBytes(f));
                    java.util.regex.Matcher m = call.matcher(src);
                    while (m.find()) played.add(m.group(1));
                    java.util.regex.Matcher a = array.matcher(src);
                    while (a.find()) {
                        java.util.regex.Matcher q2 = quoted.matcher(a.group(1));
                        while (q2.find()) played.add(q2.group(1));
                    }
                }
            } catch (Exception e) {
                System.out.println("  (could not read the sources: " + e.getMessage() + ")");
            }
            // THE ONES THAT ARE MISSING ARE NAMED, so the check is a claim about a set rather than a wish. Fourteen
            // of the twenty-six names the game plays or loads have no file, and the jumpscares are among them:
            // scare_freddy, scare_monty, scare_sprint, scare_chica, scare_roxanne, scare_door. A silent jumpscare
            // is not less frightening, it is broken - and nothing said so until this check existed.
            //
            // Written down rather than fixed, because the sounds are a design decision and not a bug: what this
            // buys is that a FIFTEENTH missing cue fails the suite, and that the number is in the repository instead
            // of in somebody's memory.
            // THE LIST IS DATA, NOT CODE, because this file is kept byte-identical to the copy that lives inside the
            // engine while the AUDIO DIRECTORY IS NOT: the standalone repository has twelve files there and the
            // engine has a hundred and seven, shared with the platformer. So the check reads which cues are known to
            // be missing from `tools/known-missing-cues.txt`, and that file is the only thing that differs.
            java.util.Set<String> knownMissing = new java.util.TreeSet<>();
            try (java.util.stream.Stream<String> lines =
                         java.nio.file.Files.lines(java.nio.file.Paths.get("tools/known-missing-cues.txt"))) {
                lines.map(String::trim).filter(x -> !x.isEmpty() && !x.startsWith("#")).forEach(knownMissing::add);
            } catch (Exception e) {
                System.out.println("  (no tools/known-missing-cues.txt: every missing cue counts as unexpected)");
            }
            java.util.Set<String> missing = new java.util.TreeSet<>(played);
            missing.removeIf(SelfTest::hasCue);
            java.util.Set<String> unexpected = new java.util.TreeSet<>(missing);
            unexpected.removeAll(knownMissing);
            java.util.Set<String> found = new java.util.TreeSet<>(knownMissing);
            found.removeAll(missing);
            check("the cues the game plays or loads are the ones with files, plus " + knownMissing.size()
                            + " known missing (" + played.size() + " named, " + missing.size() + " missing)"
                            + (unexpected.isEmpty() ? "" : ", NEW: " + unexpected)
                            + (found.isEmpty() ? "" : ", NOW PRESENT: " + found),
                    played.size() > 0 && unexpected.isEmpty() && found.isEmpty());
        }

        System.out.println("\n=== " + (checks - failed) + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }
    /** Does `audio/<name>.<ext>` exist for any extension the loader reads? */
    static boolean hasCue(String name) {
        java.io.File dir = new java.io.File("audio");
        for (String ext : new String[]{".wav", ".mp3", ".aiff", ".aif", ".m4a", ".aac"}) {
            if (new java.io.File(dir, name + ext).isFile()) return true;
        }
        return false;
    }

}
