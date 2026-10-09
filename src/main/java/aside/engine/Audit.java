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

            // The verdict separates what the report KNOWS from what it merely could not finish. Missing targets,
            // dead ends, the variable analysis and the choice check do not depend on the traversal at all;
            // unreachable scenes, unreachable beats and never-offered choices do, so on an unfinished walk they are
            // unproven rather than wrong.
            //
            // Before this, "clean" included a finished traversal, so a large story with nothing wrong with it
            // printed ISSUES FOUND and exited 1 - Overtime, 131 scenes, did exactly that at the default budget,
            // which reads as "your story is broken" when it means "the search did not finish".
            boolean reliable = !r.missingTargets.isEmpty() || !r.deadEnds.isEmpty()
                    || !r.varsReadOnly.isEmpty() || !r.varsNeverRead.isEmpty()
                    || !r.falseChoices.isEmpty();

            // A COMPLETENESS CLAIM THAT NEEDS NO SEARCH: if every DEFINED scene was reached, the unreachable-scene
            // list cannot be missing an entry, so that finding is proven even when the budget ran out. The other
            // two traversal findings are not covered by it - a beat can be unreachable inside a scene that was
            // reached, and a choice can be offered only on a path not yet walked.
            boolean unfinished = r.budgetHit || r.heapHit;   // either way: the walk did not finish
            boolean scenesComplete = r.scenesReached.size() >= r.scenesDefined.size();
            boolean scenesIssue = !r.unreachableScenes().isEmpty();
            boolean otherTraversal = !r.unreachableBeats.isEmpty()
                    || !r.neverOfferedChoices().isEmpty();

            boolean provenIssue = reliable
                    || (scenesIssue && (scenesComplete || !unfinished))
                    || (otherTraversal && !unfinished);

            if (provenIssue) {
                System.out.println("\nISSUES FOUND");
                failures++;
            } else if (otherTraversal && unfinished) {
                // AND THE MESSAGE CLAIMS ONLY WHAT THE CONDITION CHECKED. The first version of this said "every
                // scene was accounted for" while checking nothing of the sort, and it is false on a walk that
                // stopped at 89 of 131 scenes - caught by running that case at a small budget.
                System.out.println("\nINCONCLUSIVE: "
                        + (scenesComplete
                           ? "every scene was accounted for, but unreachable beats and never-offered choices are "
                             + "path questions, and the traversal did not finish."
                           : "the traversed part of the story shows unreachable beats or never-offered choices, "
                             + "and " + r.scenesReached.size() + " of " + r.scenesDefined.size()
                             + " scene(s) were reached - so this is a path question on an unfinished walk.")
                        + " Raise the budget:\n    java -Xmx2g -Daside.frontier.paths=true -Daside.budget="
                        + Math.max(4_000_000, r.statesExpanded * 4) + " aside.engine.Audit <story>");
                failures++;   // distinct from clean, and distinct from proven issues
            } else if (unfinished) {
                System.out.println("\nCLEAN SO FAR (traversal partial: " + r.scenesReached.size() + "/"
                        + r.scenesDefined.size() + (r.heapHit ? " scenes, heap hit)" : " scenes, budget hit)")
                        + (scenesComplete
                           ? "\n  the unreachable-scene list is nonetheless COMPLETE: every scene was reached, "
                             + "so nothing can be missing from it"
                           : ""));
            } else {
                System.out.println("\nCLEAN");
            }
            // An unfinished run is not a failure. The verdict above already says the results are partial, and
            // counting it here would make the exit status contradict the verdict.
            if (unfinished) inconclusiveRuns++;
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
