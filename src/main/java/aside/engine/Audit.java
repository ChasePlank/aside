package aside.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Command-line story audit:
 *
 *   java aside.engine.Audit stories/overtime.aside
 *
 * Prints the traversal report, plus every stage/pose name the script
 * asks for — which doubles as the art shopping list. A pose that no
 * image exists for is a gap the writer created and the artist hasn't
 * seen yet.
 */
public class Audit {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("usage: Audit <story.aside> [more.aside ...]");
            return;
        }
        int failures = 0;
        int inconclusiveRuns = 0;
        int heapHits = 0;
        for (String arg : args) {
            Path p = Path.of(arg);
            if (!Files.exists(p)) {
                System.out.println("no such story: " + arg);
                failures++;
                continue;
            }
            Script s = Script.load(p);
            System.out.println("=========================================================");
            System.out.println("  " + s.title + "  (" + p + ")");
            System.out.println("=========================================================");

            int beats = 0, options = 0;
            for (Scene sc : s.scenes.values()) {
                beats += sc.beats.size();
                for (Beat b : sc.beats) {
                    if (b.kind == Beat.Kind.CHOICE) options += b.choices.size();
                }
            }
            System.out.println("scenes " + s.scenes.size() + "   beats " + beats
                    + "   choice options " + options);

            System.out.println("\n--- art the script asks for ---");
            printArtList(s, p);

            System.out.println("\n--- traversal ---");
            Bot.Report r = new Bot().run(s);
            System.out.print(r.summary());

            boolean clean = r.missingTargets.isEmpty()
                    && r.unreachableScenes().isEmpty()
                    && r.deadEnds.isEmpty()
                    && r.unreachableBeats.isEmpty()
                    && r.neverOfferedChoices().isEmpty()
                    && r.varsReadOnly.isEmpty()
;
            // A budget hit is not a finding. The traversal not finishing means the results are PARTIAL, which is
            // different from them being wrong - and calling an honest "I could not check everything" ISSUES FOUND
            // teaches the reader to distrust the verdict. Three states, not two.
            boolean inconclusive = r.budgetHit || r.heapHit;
            System.out.println("\n" + (inconclusive
                    ? (r.heapHit
                        ? "INCONCLUSIVE - ran out of heap, results partial"
                        : "INCONCLUSIVE - budget hit, results partial")
                    : clean ? "CLEAN" : "ISSUES FOUND"));
            // An inconclusive run is not a failure. The verdict above already says the results are partial;
            // counting it here as well would make the exit status contradict the verdict, which is the same
            // mistake in a second place - a budget hit reported as a problem.
            if (!clean && !inconclusive) failures++;
            // `inconclusive` alone, not `inconclusive && clean`: an inconclusive run HAS unreachable scenes by
            // definition, so requiring clean meant the counter could never fire - a condition that excludes the
            // case it exists for.
            if (inconclusive) inconclusiveRuns++;
            if (r.heapHit) heapHits++;
            System.out.println();
        }
        if (inconclusiveRuns > 0) {
            System.out.println();
            System.out.println(inconclusiveRuns + " story(ies) inconclusive - raise the "
                    + (heapHits > 0 ? "HEAP (-Xmx) rather than the budget" : "budget")
                    + " to finish them.");
            System.out.println("Not counted as failures, because a partial check is not a finding.");
        }
        if (failures > 0) System.exit(1);
    }

    static void printArtList(Script s, java.nio.file.Path storyPath) {
        // The story id is only known here, which is why the existence checks below live in this method: the art
        // lists are aggregated across stories but a story's own art lives under art/stories/<id>/, and without
        // the id neither check can see it. Per-story art arrived after both checks were first written.
        String storyId = storyPath == null ? "" : storyPath.getFileName().toString().replace(".aside", "");
        Set<String> backgrounds = new LinkedHashSet<>();
        Set<String> music = new LinkedHashSet<>();
        Set<String> sfx = new LinkedHashSet<>();
        Set<String> poses = new LinkedHashSet<>();

        for (Scene sc : s.scenes.values()) {
            for (Beat b : sc.beats) {
                if (b.kind == Beat.Kind.STAGE) {
                    switch (b.directive) {
                        case "bg" -> backgrounds.add(b.arg);
                        // "music none" is silence, not an asset to source
                        case "music" -> { if (!"none".equals(b.arg)) music.add(b.arg); }
                        case "sfx" -> sfx.add(b.arg);
                        default -> {}
                    }
                }
                if (b.speaker != null && b.pose != null) {
                    poses.add(b.speaker + " (" + b.pose + ")");
                }
                if (b.kind == Beat.Kind.STAGE && "show".equals(b.directive) && b.arg2 != null) {
                    poses.add(b.arg + " (" + b.arg2 + ")");
                }
            }
        }
        System.out.println("  backgrounds: " + (backgrounds.isEmpty() ? "-" : ""));
        for (String b : backgrounds) System.out.println("      " + b);
        System.out.println("  music:       " + (music.isEmpty() ? "-" : ""));
        for (String m : music) System.out.println("      " + m);
        System.out.println("  sfx:         " + (sfx.isEmpty() ? "-" : ""));
        for (String x : sfx) System.out.println("      " + x);
        System.out.println("  poses:       " + poses.size());
        for (String p : poses) System.out.println("      " + p);

        // Which of those poses have no image yet. The list above is a shopping list and legitimate to be unmet,
        // so this is reported rather than counted as an issue - but it is reported, because a missing sprite
        // fails silently in the presenter where a missing cue was checked for and this was not. Same pattern as
        // the audio cues, ported late.
        if (new java.io.File("art/sprites").isDirectory()) {
            java.util.List<String> unmade = new java.util.ArrayList<>();
            for (String pose : poses) {
                String[] parts = pose.split(" \\(");
                String file = parts[0] + "-" + parts[1].replace(")", "") + ".png";
                if (!existsIn("art/sprites", "art/stories/" + storyId + "/sprites", file)) unmade.add(pose + " -> " + file);
            }
            System.out.println("  poses with no image yet: " + unmade.size());
            for (String u : unmade) System.out.println("      " + u);
        }

        // Backgrounds, the third check of the same shape and the one that was owed. A missing background draws
        // nothing where the scene should be, which fails as silently in the presenter as a missing sprite does.
        java.util.List<String> noBg = new java.util.ArrayList<>();
        for (String b : backgrounds) {
            if (!existsIn("art/backgrounds", "art/stories/" + storyId + "/backgrounds", b + ".png", b + ".jpg")) noBg.add(b);
        }
        System.out.println("  backgrounds with no image yet: " + noBg.size());
        for (String u : noBg) System.out.println("      " + u);
    }

    /** True if any of the given file names exists in any of the given directories. */
    static boolean existsIn(String dirA, String dirB, String... names) {
        for (String dir : new String[]{dirA, dirB}) {
            for (String n : names) {
                if (new java.io.File(dir, n).isFile()) return true;
            }
        }
        return false;
    }
}
