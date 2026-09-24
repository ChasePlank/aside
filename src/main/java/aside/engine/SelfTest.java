package aside.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Headless checks. The engine is deliberately free of JavaFX so it can
 * be driven from here — a story engine you can only test by clicking
 * through a window is a story engine you won't test.
 */
public class SelfTest {
    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + name); }
        else    { fail++; System.out.println("  FAIL " + name); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Aside engine self-test ===\n");

        Path story = Path.of("stories", "night-shift.aside");
        if (!Files.exists(story)) {
            story = Path.of("..", "stories", "night-shift.aside");
        }
        Script s = Script.load(story);

        System.out.println("script: \"" + s.title + "\" by " + s.author);
        System.out.println("scenes: " + s.scenes.size());
        int beats = 0, choices = 0;
        for (Scene sc : s.scenes.values()) {
            beats += sc.beats.size();
            for (Beat b : sc.beats) if (b.kind == Beat.Kind.CHOICE) choices += b.choices.size();
        }
        System.out.println("beats:  " + beats + "   choice options: " + choices + "\n");

        System.out.println("--- parser ---");
        check("start scene exists", s.scene(s.startScene) != null);
        check("dialogue parsed with speaker",
                s.scene("start").beats.stream().anyMatch(b -> "monty".equals(b.speaker)));
        check("narration parsed with null speaker",
                s.scene("start").beats.stream().anyMatch(b ->
                        b.kind == Beat.Kind.TEXT && b.speaker == null));
        check("prose with a colon stays narration",
                s.scene("cove").beats.stream().anyMatch(b ->
                        b.kind == Beat.Kind.TEXT && b.speaker == null
                                && b.text.contains("being very still")));
        check("staging parsed (bg)",
                s.scene("start").beats.stream().anyMatch(b ->
                        b.kind == Beat.Kind.STAGE && "bg".equals(b.directive)));
        check("`at` position captured",
                s.scene("start").beats.stream().anyMatch(b ->
                        "left".equals(b.arg3)));
        check("choice condition captured",
                lastChoiceCondition(s) != null);
        check("leading `~` becomes its own effect beat",
                s.scene("defiant").beats.get(0).kind == Beat.Kind.EFFECT
                        && s.scene("defiant").beats.get(0).effects.size() == 1);
        check("trailing `~` attaches to the beat above it",
                s.scene("ending_warm").beats.stream().anyMatch(b ->
                        b.kind == Beat.Kind.TEXT && !b.effects.isEmpty()));
        check("`~` under a choice attaches to that choice",
                hasChoiceEffect(s, "start"));

        System.out.println("\n--- expressions ---");
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("aff", 3.0); v.put("met", Boolean.TRUE);
        check("numeric >=", Expr.test("aff >= 3", v));
        check("numeric < false case", !Expr.test("aff < 3", v));
        check("bare truthy flag", Expr.test("met", v));
        check("negation", !Expr.test("!met", v));
        check("and", Expr.test("met and aff >= 2", v));
        check("or", Expr.test("!met or aff >= 2", v));
        check("unknown var is falsy", !Expr.test("nope", v));

        Expr.apply("aff +1", v);
        check("effect +1", Expr.asNumber(v.get("aff")) == 4.0);
        Expr.apply("aff -2", v);
        check("effect -2", Expr.asNumber(v.get("aff")) == 2.0);
        Expr.apply("aff = 9", v);
        check("effect assign", Expr.asNumber(v.get("aff")) == 9.0);
        Expr.apply("flag = true", v);
        check("effect boolean", Boolean.TRUE.equals(v.get("flag")));

        System.out.println("\n--- playthrough: the warm path ---");
        Vn vn = new Vn(s);
        check("opens on a line", vn.mode == Vn.Mode.SHOWING);
        // walk: quiet -> cove -> "I can hear you" -> offer -> ending_warm
        runUntilBlocked(vn);              // through the intro lines
        check("reaches a choice", vn.mode == Vn.Mode.CHOOSING);
        List<Choice> c0 = vn.availableChoices();
        check("three options at the first choice", c0.size() == 3);
        vn.choose(1);                     // "You say nothing."
        check("quiet branch raised affinity", Expr.asNumber(vn.vars.get("aff_monty")) == 1.0);
        runUntilBlocked(vn);
        if (vn.mode == Vn.Mode.CHOOSING) {
            List<Choice> c1 = vn.availableChoices();
            check("hallway offers three options", c1.size() == 3);
            vn.choose(1);                 // "I'm doing my rounds."
            runUntilBlocked(vn);
        }
        if (vn.mode == Vn.Mode.CHOOSING) {
            List<Choice> c2 = vn.availableChoices();
            check("cove offers three options", c2.size() == 3);
            vn.choose(0);                 // "I can hear you."
            check("saw_freddy flag set", Boolean.TRUE.equals(vn.vars.get("saw_freddy")));
            runUntilBlocked(vn);
        }
        check("warm path ends", vn.mode == Vn.Mode.ENDED);
        check("nights_survived incremented",
                Expr.asNumber(vn.vars.get("nights_survived")) == 1.0);
        check("history recorded lines", vn.history.size() > 5);

        System.out.println("\n--- save / load round trip ---");
        String blob = vn.serialize();
        Vn loaded = Vn.deserialize(s, blob);
        check("scene id survives", vn.sceneId.equals(loaded.sceneId));
        check("index survives", vn.index == loaded.index);
        check("vars survive", String.valueOf(vn.vars).equals(String.valueOf(loaded.vars)));
        check("visited survives", vn.visited.equals(loaded.visited));
        check("history survives", vn.history.size() == loaded.history.size());
        check("music survives", String.valueOf(vn.music).equals(String.valueOf(loaded.music)));
        check("re-serialize is stable", blob.equals(loaded.serialize()));

        System.out.println("\n--- blind traversal ---");
        Bot.Report r = new Bot().run(s);
        System.out.println(r.summary());

        check("traversal reached an ending", !r.endings.isEmpty());
        check("traversal found multiple endings", r.endings.size() >= 2);
        check("traversal caught the unreachable scene",
                r.unreachableScenes().contains("epilogue"));
        check("traversal caught the read-only variable typo",
                r.varsReadOnly.contains("aff_montal"));
        check("traversal caught beats authored after a jump",
                r.unreachableBeats.stream().anyMatch(d -> d.startsWith("apologist_alt")));
        check("traversal reported no missing targets", r.missingTargets.isEmpty());
        check("traversal reached every scene that is actually reachable",
                r.unreachableScenes().size() == 2);
        check("choices were offered during traversal",
                r.choiceSitesOffered.size() >= 5);

        System.out.println("\n=== " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }

    static void runUntilBlocked(Vn vn) {
        int guard = 0;
        while (vn.mode == Vn.Mode.SHOWING && guard++ < 10000) vn.advance();
    }

    static String lastChoiceCondition(Script s) {
        for (Scene sc : s.scenes.values()) {
            for (Beat b : sc.beats) {
                if (b.kind == Beat.Kind.CHOICE) {
                    for (Choice c : b.choices) if (c.condition != null) return c.condition;
                }
            }
        }
        return null;
    }

    static boolean hasChoiceEffect(Script s, String sceneId) {
        Scene sc = s.scene(sceneId);
        if (sc == null) return false;
        for (Beat b : sc.beats) {
            if (b.kind == Beat.Kind.CHOICE) {
                for (Choice c : b.choices) if (!c.effects.isEmpty()) return true;
            }
        }
        return false;
    }
}
