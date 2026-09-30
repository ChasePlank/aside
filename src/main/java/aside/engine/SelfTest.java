package aside.engine;

import aside.game.PhoneShelf;
import aside.ui.LibraryLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        System.out.println("\n--- partial traversal is still an honest report ---");
        // The budget counts states, but the memory cost per state is what
        // actually runs out, so the traversal is allowed to fail. What it
        // must never do is present a partial walk as a complete one.
        Bot small = new Bot();
        // night-shift is small: a full walk of it expands 25 states, so the
        // budget has to be well under that to actually stop it.
        small.budget = 5;
        Bot.Report part = small.run(s);
        check("a tiny budget stops the traversal", part.budgetHit);
        check("a stopped traversal says so in the summary",
                part.summary().contains("BUDGET HIT"));
        check("a stopped traversal marks its findings unreliable",
                part.summary().contains("UNRELIABLE"));
        check("a stopped traversal prefixes its findings with ??",
                part.summary().contains("?? "));
        check("a stopped traversal explored less than a full one",
                part.statesExpanded < r.statesExpanded);
        // The structural findings do not depend on the traversal at all, so
        // they have to be identical whether or not it finished. A partial
        // report that also lost its certain answers would be worse than
        // useless.
        check("structural findings survive a stopped traversal",
                part.missingTargets.equals(r.missingTargets)
                        && part.unreachableBeats.equals(r.unreachableBeats)
                        && part.varsReadOnly.equals(r.varsReadOnly)
                        && part.varsNeverRead.equals(r.varsNeverRead));
        check("a stopped traversal reports fewer reached scenes",
                part.scenesReached.size() <= r.scenesReached.size());

        System.out.println("\n--- the dedupe key ---");
        // The set holds a hash per state, not the signature string: the
        // string was what ran out of memory. A collision costs one branch
        // re-explored, never a wrong answer, so the only thing worth
        // asserting is that the key is stable and separating.
        Bot hasher = new Bot();
        Vn a = new Vn(s);
        Vn b = new Vn(s);
        check("the same state hashes the same", hasher.signatureHash(a) == hasher.signatureHash(b));
        b.vars.put("aff_monty", 3.0);
        check("a different variable value hashes differently",
                hasher.signatureHash(a) != hasher.signatureHash(b));
        Vn c = new Vn(s);
        c.index = a.index + 1;
        check("a different position hashes differently",
                hasher.signatureHash(a) != hasher.signatureHash(c));
        check("the signature itself is unchanged by hashing",
                hasher.signature(a).equals(hasher.signature(new Vn(s))));

        System.out.println("\n--- the library list ---");
        // The library has failed twice at a row count it had not seen yet: a
        // fixed 88px pitch that put the newest game at y=830 on a 720 canvas,
        // and a selection bar that reached into the row above at eleven rows.
        // Both were arithmetic, both were invisible in the code, and both were
        // found by rendering a frame. This is the arithmetic, checked here so
        // the next row count is not found the same way.
        int probe = 24;
        check("the last row is on the canvas",
                LibraryLayout.rowY(probe - 1, probe) <= LibraryLayout.LIST_BOTTOM);
        check("the first row's bar clears the header",
                LibraryLayout.barTop(0, probe) >= LibraryLayout.HEADER_BOTTOM);
        check("the last row's bar is on the canvas",
                LibraryLayout.barBottom(probe - 1, probe) <= LibraryLayout.CANVAS_H);
        check("the bar never reaches the blurb above it, up to the limit",
                LibraryLayout.clearance(LibraryLayout.maxRows()) > 0);
        int limit = LibraryLayout.maxRows();
        check("the library holds at least fifteen rows", limit >= 15);
        check("the limit is where the geometry actually stops",
                LibraryLayout.clearance(limit + 1) <= 0);
        System.out.println("       rows the bar geometry holds: " + limit
                + "  (clearance at " + limit + " is "
                + Math.round(LibraryLayout.clearance(limit) * 10) / 10.0 + "px)");

        System.out.println("\n--- the phone shelf ---");
        // Every web build is a generated file, and a generated file that has
        // gone stale is worse than no file: it is a second copy of the game
        // quietly disagreeing with the first. The shelf is the worst case of
        // that, because it is a copy of every game at once -- and it fails in
        // the other direction too, by not mentioning a build that exists.
        try {
            Path shelfFile = Path.of("web", PhoneShelf.SHELF_FILE);
            if (!Files.exists(shelfFile)) {
                System.out.println("       (no web/" + PhoneShelf.SHELF_FILE
                        + " from here -- run from the repository root)");
            } else {
                String generated = PhoneShelf.html();
                check("web/" + PhoneShelf.SHELF_FILE
                                + " is current -- regenerate it with aside.game.PhoneShelf",
                        generated.equals(Files.readString(shelfFile)));

                List<PhoneShelf.Entry> entries = PhoneShelf.entries();
                check("the shelf has something on it", !entries.isEmpty());
                Set<String> listed = new HashSet<>();
                for (PhoneShelf.Entry e : entries) {
                    String name = Path.of(e.file()).getFileName().toString();
                    listed.add(name);
                    check("the shelf lists something that is there: " + e.file(),
                            Files.exists(Path.of(e.file())));
                    check("the shelf carries the title " + e.title(), generated.contains(e.title()));
                    check("the shelf carries the line for " + e.title(), generated.contains(e.blurb()));
                }
                // The failure that would otherwise be silent: a game gets a
                // phone build and the shelf quietly does not know about it.
                List<String> onDisk = PhoneShelf.webBuilds();
                for (String f : onDisk) check("web/" + f + " is on the shelf", listed.contains(f));
                check("and the shelf lists nothing that is not on disk",
                        listed.size() == onDisk.size());
                System.out.println("       shelf: " + entries.size() + " builds, "
                        + (generated.length() / 1024) + " KB");
            }
        } catch (Exception e) {
            check("the shelf can be generated from here (" + e.getMessage() + ")", false);
        }

        System.out.println("\n--- the story exports ---");
        // The two .aside stories have had a web export since WebExport existed,
        // and nothing has ever checked that the exports are current. Same rule
        // as the games: regenerate and compare. A story edited in stories/ and
        // not re-exported is a phone build of a story that no longer exists.
        storyExport("night-shift");
        storyExport("overtime");

        System.out.println("\n=== " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }

    /** Regenerate one story's web export and compare it to the checked-in one. */
    static void storyExport(String name) throws Exception {
        Path story = Path.of("stories", name + ".aside");
        Path out = Path.of("web", name + ".html");
        if (!Files.exists(story) || !Files.exists(out)) {
            System.out.println("       (no " + story + " or " + out + " from here)");
            return;
        }
        String generated = WebExport.convert(Files.readString(story));
        check("web/" + name + ".html is current -- regenerate it with aside.engine.WebExport",
                generated.equals(Files.readString(out)));
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
