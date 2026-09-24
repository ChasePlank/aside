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
            printArtList(s);

            System.out.println("\n--- traversal ---");
            Bot.Report r = new Bot().run(s);
            System.out.print(r.summary());

            boolean clean = r.missingTargets.isEmpty()
                    && r.unreachableScenes().isEmpty()
                    && r.deadEnds.isEmpty()
                    && r.unreachableBeats.isEmpty()
                    && r.neverOfferedChoices().isEmpty()
                    && r.varsReadOnly.isEmpty()
                    && !r.budgetHit;
            System.out.println("\n" + (clean ? "CLEAN" : "ISSUES FOUND"));
            if (!clean) failures++;
            System.out.println();
        }
        if (failures > 0) System.exit(1);
    }

    static void printArtList(Script s) {
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
    }
}
