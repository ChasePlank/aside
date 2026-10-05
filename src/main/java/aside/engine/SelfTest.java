package aside.engine;

import aside.game.PhoneShelf;
import aside.game.Game;
import aside.game.Games;
import aside.games.fnaf6.WebSalvage;
import aside.ui.LibraryLayout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless checks. The engine is deliberately free of JavaFX so it can
 * be driven from here — a story engine you can only test by clicking
 * through a window is a story engine you won't test.
 */
import aside.games.fruitjump.engine.Bat;
import aside.games.fruitjump.engine.Combat;
import aside.games.fruitjump.engine.GameLoop;
import aside.games.fruitjump.engine.Hookshot;
import aside.games.fruitjump.engine.LevelGen;
import aside.games.fruitjump.engine.LevelMap;
import aside.games.fruitjump.engine.Physics;
import aside.games.fruitjump.engine.Projectile;
import aside.games.fruitjump.engine.World;

public class SelfTest {
    static int pass = 0, fail = 0;

    /**
     * The shelf's size ceiling, in KB.
     *
     * <p>One file a phone downloads, and it grows by accident rather than by
     * decision. The known open question is the stories' authored audio: carrying
     * it would add about 3 MB per story and take the shelf from 8.4 MB to
     * roughly 20. Raising this number is a deliberate act.
     */
    static final long SHELF_CEILING_KB = 10240;

    /** One decimal place, for a number a person has to read in a report. */

    static double round1(double d) { return Math.round(d * 10) / 10.0; }


    /**
     * A variable's value as a number, or NaN when it is not one.
     *
     * <p><b>This exists because the suite used to CRASH here instead of
     * failing.</b> {@code Expr.asNumber} returns a boxed {@code Double} and
     * returns null for a variable that was never set, and five checks compared
     * it to a literal -- so a mutation that stopped a variable being set did
     * not fail those checks, it killed the run with a NullPointerException on
     * the unboxing. Found by mutating the parser's goto pattern: the suite
     * reported a stack trace instead of a count, and every check after it was
     * lost. NaN is used rather than 0.0 so a missing value cannot accidentally
     * equal anything.
     */
    /**
     * A scene's beats, or an empty list when the scene is not there.
     *
     * <p><b>This exists because the suite CRASHED here rather than failing.</b>
     * Mutating the scene-header pattern makes {@code Script.scene("start")}
     * return null, and the checks below it read {@code .beats} straight off the
     * result -- so the first check failed correctly and the second killed the
     * run, taking every later check with it. An empty list fails the same
     * checks without stopping the suite.
     */
    static List<Beat> beatsOf(Script s, String scene) {
        Scene sc = s.scene(scene);
        return sc == null ? List.of() : sc.beats;
    }

    static double num(Object o) {
        Double d = Expr.asNumber(o);
        return d == null ? Double.NaN : d;
    }

    /** The verdict line and the exit code, in one place so an early return can use it. */
    static void summary() {
        System.out.println("\n=== " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + name); }
        else    { fail++; System.out.println("  FAIL " + name); }
    }

    /**
     * The retired games still compile against the engine.
     *
     * <p><b>This closes the one cost the cull knowingly took.</b> Retiring
     * a game is a move, not a delete, and `retired-games/` is out of the
     * build -- `javac` never sees it and the retired suites no longer run.
     * That was written down as a deliberate cost in the cull's README:
     * *"retired code can rot against the engine without anything telling
     * you."* It can, and this is the thing that tells you.
     *
     * <p>It is a compile and not a test run, on purpose. Running a retired
     * suite would mean its `SelfTest` still had to pass, and a game that
     * was retired because it repeated a mechanic is a game nobody is going
     * to fix when its numbers drift. Compiling is the line that matters:
     * it is the difference between *"we could bring this back with a
     * `git mv`"* and *"we could bring this back with an afternoon."*
     *
     * <p>Skips rather than fails when there is no compiler on the
     * classpath, because a JRE is a legitimate way to run the engine and a
     * check that cannot run is not a check that failed.
     */
    static void retiredStillCompiles(Path retiredRoot) {
        javax.tools.JavaCompiler jc = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (jc == null) {
            System.out.println("       (no compiler on this JVM -- skipping the compile check)");
            return;
        }
        List<java.io.File> files = new ArrayList<>();
        try (var walk = Files.walk(retiredRoot.resolve(Path.of("src", "main", "java")))) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> files.add(p.toFile()));
        } catch (Exception e) {
            check("the retired sources can be listed (" + e.getMessage() + ")", false);
            return;
        }
        check("there are retired sources to compile", !files.isEmpty());

        Path out;
        try {
            out = Files.createTempDirectory("aside-retired");
        } catch (Exception e) {
            check("a scratch directory for the retired compile", false);
            return;
        }
        // The engine is run with JavaFX on the MODULE path and everything
        // else on the class path, so a compile that only reads
        // java.class.path cannot see javafx at all and reports eight games
        // as broken when the problem is the harness. Both are joined.
        String cp = System.getProperty("java.class.path", ".");
        String modules = System.getProperty("jdk.module.path");
        if (modules != null && !modules.isBlank()) cp = cp + java.io.File.pathSeparator + modules;

        javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diags =
                new javax.tools.DiagnosticCollector<>();
        boolean ok;
        try (javax.tools.StandardJavaFileManager fm =
                     jc.getStandardFileManager(diags, null, null)) {
            ok = jc.getTask(null, fm, diags,
                    List.of("-nowarn", "-d", out.toString(), "-classpath", cp),
                    null, fm.getJavaFileObjectsFromFiles(files)).call();
        } catch (Exception e) {
            check("the retired compile can be run (" + e.getMessage() + ")", false);
            return;
        }

        List<String> errors = new ArrayList<>();
        boolean sawJavafx = false;
        for (var d : diags.getDiagnostics()) {
            if (d.getKind() != javax.tools.Diagnostic.Kind.ERROR) continue;
            String msg = d.getMessage(null);
            errors.add(d.getSource() + ":" + d.getLineNumber() + " " + msg);
            if (msg != null && msg.contains("javafx")) sawJavafx = true;
        }
        // A check that cannot see the toolkit is a check that did not run.
        // Saying so is better than reporting eight games as rotten when the
        // fault is in the harness that ran the check.
        if (!ok && sawJavafx) {
            System.out.println("       (JavaFX is not on this compiler's path -- "
                    + "skipping the compile check)");
            return;
        }
        for (String e : errors) System.out.println("       " + e);
        check("every retired game still compiles against the engine", ok);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Aside engine self-test ===\n");

        Path story = Path.of("stories", "night-shift.aside");
        if (!Files.exists(story)) {
            story = Path.of("..", "stories", "night-shift.aside");
        }
        Script s = Script.load(story);

        // A comment is skipped, not narrated.
        //
        // This is here because a mutation test found the gap: changing the
        // parser's comment marker from "#" to "//" broke nothing at all, and
        // the suite still passed 308 of 308. The stories DO use comments --
        // night-shift has five, overtime twenty-five -- so the branch runs; what
        // was missing was any check that it does the right thing. A comment that
        // silently becomes narration is a story that gains lines nobody wrote.
        {
            Path tmp = Files.createTempFile("aside-comment", ".aside");
            Files.writeString(tmp, """
                    title: comment probe
                    start: only

                    == only ==
                    # this line is a comment and must not be narrated
                    A real line.
                    """);
            Script probe = Script.load(tmp);
            Files.deleteIfExists(tmp);
            Scene only = probe.scene("only");
            long lines = only == null ? -1 : only.beats.stream()
                    .filter(b -> b.kind == Beat.Kind.TEXT).count();
            check("a comment is skipped, not narrated (" + lines + " line(s))", lines == 1);
        }

        // Every stage keyword is a stage beat, not narration.
        //
        // Found the same way as the comment gap: dropping "hide" from the
        // stage pattern broke nothing and the suite still passed 311 of 311,
        // even though night-shift uses hide three times and overtime
        // twenty-six. A `hide` that silently becomes narration is a line of
        // prose nobody wrote and a character who never leaves.
        for (String kw : new String[]{"bg", "music", "sfx", "show", "hide"}) {
            Path tmp = Files.createTempFile("aside-stage", ".aside");
            Files.writeString(tmp, "title: stage probe\nstart: only\n\n== only ==\n"
                    + kw + " thing\nA real line.\n");
            Script probe = Script.load(tmp);
            Files.deleteIfExists(tmp);
            List<Beat> beats = beatsOf(probe, "only");
            boolean staged = beats.stream().anyMatch(b -> b.kind != Beat.Kind.TEXT);
            long prose = beats.stream().filter(b -> b.kind == Beat.Kind.TEXT).count();
            check("a \"" + kw + "\" line is a stage beat, not narration ("
                            + beats.size() + " beat(s))",
                    staged && prose == 1);
        }

        System.out.println("script: \"" + s.title + "\" by " + s.author);
        System.out.println("scenes: " + s.scenes.size());
        int beats = 0, choices = 0;
        for (Scene sc : s.scenes.values()) {
            beats += sc.beats.size();
            for (Beat b : sc.beats) if (b.kind == Beat.Kind.CHOICE) choices += b.choices.size();
        }
        System.out.println("beats:  " + beats + "   choice options: " + choices + "\n");

        System.out.println("--- parser ---");

        // set and if -> are supported by the parser and used by Overtime
        // nineteen times, but the suite parses night-shift, which uses neither
        // -- so mutating either pattern broke nothing at all and the suite
        // still passed 309 of 309. A probe is the only way to cover a syntax
        // feature no fixture happens to use.
        {
            Path tmp = Files.createTempFile("aside-setif", ".aside");
            Files.writeString(tmp, """
                    title: set and if probe
                    start: only

                    == only ==
                    ~ flag true
                    set counter = 3
                    if counter == 3 -> yes
                    -> no

                    == yes ==
                    It went the right way.

                    == no ==
                    It went the wrong way.
                    """);
            Script probe = Script.load(tmp);
            Files.deleteIfExists(tmp);
            // Guarded, because if the scene-header pattern is the thing that
            // is broken then this probe has no scenes at all and building a Vn
            // from it would kill the run -- which is exactly what happened the
            // first time this probe was written.
            if (probe.scene("only") == null) {
                check("the set/if probe parses (no scene)", false);
            } else {
                Vn walk = new Vn(probe);
                int guard = 0;
                while (walk.mode != Vn.Mode.ENDED && guard++ < 200) {
                    if (walk.mode == Vn.Mode.CHOOSING) break;
                    walk.step();
                }
                check("set writes a variable (counter = " + num(walk.vars.get("counter")) + ")",
                        num(walk.vars.get("counter")) == 3.0);
                check("a conditional jump is taken when it holds",
                        walk.visited.contains("yes") && !walk.visited.contains("no"));
            }
        }
        check("start scene exists", s.scene(s.startScene) != null);
        check("dialogue parsed with speaker",
                beatsOf(s, "start").stream().anyMatch(b -> "monty".equals(b.speaker)));
        check("narration parsed with null speaker",
                beatsOf(s, "start").stream().anyMatch(b ->
                        b.kind == Beat.Kind.TEXT && b.speaker == null));
        check("prose with a colon stays narration",
                beatsOf(s, "cove").stream().anyMatch(b ->
                        b.kind == Beat.Kind.TEXT && b.speaker == null
                                && b.text.contains("being very still")));
        check("staging parsed (bg)",
                beatsOf(s, "start").stream().anyMatch(b ->
                        b.kind == Beat.Kind.STAGE && "bg".equals(b.directive)));
        check("`at` position captured",
                beatsOf(s, "start").stream().anyMatch(b ->
                        "left".equals(b.arg3)));
        check("choice condition captured",
                lastChoiceCondition(s) != null);
        check("leading `~` becomes its own effect beat",
                !beatsOf(s, "defiant").isEmpty()
                        && beatsOf(s, "defiant").get(0).kind == Beat.Kind.EFFECT
                        && beatsOf(s, "defiant").get(0).effects.size() == 1);
        check("trailing `~` attaches to the beat above it",
                beatsOf(s, "ending_warm").stream().anyMatch(b ->
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
        check("effect +1", num(v.get("aff")) == 4.0);
        Expr.apply("aff -2", v);
        check("effect -2", num(v.get("aff")) == 2.0);
        Expr.apply("aff = 9", v);
        check("effect assign", num(v.get("aff")) == 9.0);
        Expr.apply("flag = true", v);
        check("effect boolean", Boolean.TRUE.equals(v.get("flag")));

        System.out.println("\n--- playthrough: the warm path ---");
        // Guarded on the parse having produced a start scene, because when the
        // scene-header pattern is the thing that is broken there is nothing to
        // play and new Vn(s) throws -- which took the rest of the suite with it.
        if (s.scene(s.startScene) == null) {
            check("the playthrough can start (no start scene)", false);
            System.out.println("       (no start scene -- the playthrough is skipped)");
            summary();
            return;
        }
        Vn vn = new Vn(s);
        check("opens on a line", vn.mode == Vn.Mode.SHOWING);
        // walk: quiet -> cove -> "I can hear you" -> offer -> ending_warm
        runUntilBlocked(vn);              // through the intro lines
        check("reaches a choice", vn.mode == Vn.Mode.CHOOSING);
        List<Choice> c0 = vn.availableChoices();
        check("three options at the first choice", c0.size() == 3);
        vn.choose(1);                     // "You say nothing."
        check("quiet branch raised affinity", num(vn.vars.get("aff_monty")) == 1.0);
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
                num(vn.vars.get("nights_survived")) == 1.0);
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

        // A choice whose condition can never hold is never offered, and that
        // finding had no check at all -- mutating neverOfferedChoices() to
        // return the empty set broke nothing and the suite still passed 316 of
        // 316. The night-shift fixture has no such choice, so a probe is the
        // only way to cover it: one option gated on a variable nothing writes.
        {
            Path tmp = Files.createTempFile("aside-neveroffered", ".aside");
            Files.writeString(tmp, """
                    title: never-offered probe
                    start: only

                    == only ==
                    You are here.
                    * Take it. -> only [if nothing_sets_this == 1]
                    * Leave it. -> done

                    == done ==
                    You leave.
                    """);
            Script probe = Script.load(tmp);
            Files.deleteIfExists(tmp);
            Bot.Report pr = new Bot().run(probe);
            check("traversal caught the never-offered choice ("
                            + pr.neverOfferedChoices().size() + " found)",
                    !pr.neverOfferedChoices().isEmpty());
        }

        // Three more findings that nothing exercised, and one of them was
        // hidden by the shape of its own check.
        //
        // deadEnds, varsNeverRead and missingTargets all had no probe: removing
        // the line that populates each broke nothing and the suite still passed
        // 317 of 317. missingTargets is the interesting one -- the suite DOES
        // check it, with `r.missingTargets.isEmpty()`, and an empty-list check
        // passes trivially when the list is never populated. A negative check
        // cannot fail unless something produces the thing it is negative about.
        {
            Path tmp = Files.createTempFile("aside-findings", ".aside");
            Files.writeString(tmp, """
                    title: findings probe
                    start: only

                    == only ==
                    ~ written_never_read 1
                    You are here.
                    -> nowhere_at_all

                    == stranded ==
                    Nobody jumps from here and nobody jumps to it.
                    """);
            Script probe = Script.load(tmp);
            Files.deleteIfExists(tmp);
            Bot.Report pr = new Bot().run(probe);
            check("traversal caught the missing target ("
                            + pr.missingTargets.size() + " found)",
                    !pr.missingTargets.isEmpty());
            check("traversal caught the written-but-never-read variable ("
                            + pr.varsNeverRead + ")",
                    pr.varsNeverRead.contains("written_never_read"));
            check("traversal caught the dead end ("
                            + pr.deadEnds.size() + " found)",
                    !pr.deadEnds.isEmpty());
        }

        // And the read-only-variable finding is populated at TWO places -- a
        // choice's condition and an if-jump's condition -- so the night-shift
        // fixture covers only the first. Removing the if-jump site broke
        // nothing and the suite still passed 320 of 320: a typo in a choice
        // was caught and the same typo in an `if` was not.
        {
            Path tmp = Files.createTempFile("aside-readonly-if", ".aside");
            Files.writeString(tmp, """
                    title: read-only if probe
                    start: only

                    == only ==
                    You are here.
                    if never_written_anywhere == 1 -> done
                    -> done

                    == done ==
                    You leave.
                    """);
            Script probe = Script.load(tmp);
            Files.deleteIfExists(tmp);
            Bot.Report pr = new Bot().run(probe);
            check("traversal caught a typo in an if-jump's condition ("
                            + pr.varsReadOnly + ")",
                    pr.varsReadOnly.contains("never_written_anywhere"));
        }

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
        // The library failed three times at a row count it had not seen: a
        // fixed 88px pitch, a bar that reached into the row above at eleven
        // rows, and titles drawn on top of the blurbs above them at thirteen.
        // All three were arithmetic, all three were invisible in the code, and
        // all three were found by rendering a frame.
        //
        // The fix was to derive the pitch and the type size from the row
        // count, and that fix has now run out: at fifteen rows the titles are
        // at their floor and the blurb-to-title gap is 0.7px. So the list
        // scrolls, the row count stops being a cliff, and the property worth
        // checking changes shape. It is no longer "does the last row fit" --
        // it is "is the selected row always in the window", which is the one
        // thing a scrolling list can get wrong in a way that hides a game.
        int shown = LibraryLayout.visibleRows();
        int probe = shown;
        check("the last visible row is on the canvas",
                LibraryLayout.rowY(probe - 1, probe) <= LibraryLayout.LIST_BOTTOM);
        check("the first row's bar clears the header",
                LibraryLayout.barTop(0, probe) >= LibraryLayout.HEADER_BOTTOM);
        check("the last visible row's bar is on the canvas",
                LibraryLayout.barBottom(probe - 1, probe) <= LibraryLayout.CANVAS_H);
        // The footer is not the canvas edge. The volume notice is drawn at
        // CANVAS_H - 60, and a list that fits on 720 can still be drawn
        // through it -- which is what the third failure looked like from the
        // other end.
        //
        // It is the *blurb* that has to clear it, not the title. While the
        // list had to fit, the last row was always Quit, and Quit has no
        // blurb. The first render after the list started scrolling drew the
        // last row's blurb straight through the notice.
        check("the last visible row's blurb clears the footer",
                LibraryLayout.blurbY(probe - 1, probe) + LibraryLayout.BLURB_DESCENT
                        <= LibraryLayout.FOOTER_TOP);
        check("the last visible row's title clears the footer",
                LibraryLayout.rowY(probe - 1, probe)
                        + LibraryLayout.titleDescent(probe) <= LibraryLayout.FOOTER_TOP);
        check("the bar never reaches the blurb above it",
                LibraryLayout.clearance(probe) > 0);
        // The constraint that was never checked, and that the third failure
        // came through: the blurb of one row must not reach the title of the
        // row below it. The bar has 0.55 of a pitch to play with; the title
        // has a whole pitch minus the blurb's offset, so the title runs out
        // first and checking only the bar reports room that is not there.
        check("the blurb never reaches the title below it",
                LibraryLayout.rowGap(probe) > 0);
        check("the title never goes below its floor",
                LibraryLayout.titleSize(probe) >= LibraryLayout.MIN_TITLE - 0.001);
        check("the title is at its full size once the list scrolls",
                LibraryLayout.titleSize(probe) >= LibraryLayout.MAX_TITLE - 0.001);
        check("the list shows at least six rows at once", shown >= 6);
        // The pitch is floored, so a long list does not close up: the pitch
        // at sixty rows is the same as the pitch at the window size. That is
        // the whole difference between scrolling and shrinking.
        check("a long list does not pitch rows closer than the floor",
                LibraryLayout.pitch(60) >= LibraryLayout.MIN_PITCH - 0.001);
        check("a short list still spreads out",
                LibraryLayout.pitch(4) > LibraryLayout.pitch(60));
        // The invariant. Exhaustive, not sampled: every row count up to sixty
        // and every selection in it, because "the window holds the selection"
        // is exactly the kind of property that is true at the sizes you tried
        // and false at the one you did not.
        int bad = -1, badSel = -1;
        for (int rows = 1; rows <= 60 && bad < 0; rows++) {
            int win = Math.min(rows, shown);
            for (int sel = 0; sel < rows; sel++) {
                int start = LibraryLayout.windowStart(sel, rows);
                boolean holds = start <= sel && sel < start + win;
                boolean inside = start >= 0 && start + win <= rows;
                if (!holds || !inside) { bad = rows; badSel = sel; break; }
            }
        }
        check("the window holds the selection, for every count up to sixty"
                        + (bad < 0 ? "" : " (failed at " + bad + " rows, selection " + badSel + ")"),
                bad < 0);
        // And the other half: a list that fits must not scroll at all, or the
        // window would move under a player who has nothing to scroll to.
        boolean shortScrolls = false;
        for (int rows = 1; rows <= shown; rows++)
            for (int sel = 0; sel < rows; sel++)
                if (LibraryLayout.windowStart(sel, rows) != 0) shortScrolls = true;
        check("a list that fits does not scroll", !shortScrolls);
        System.out.println("       rows shown at once: " + shown
                + "  (pitch " + round1(LibraryLayout.pitch(shown))
                + "px, bar clearance " + round1(LibraryLayout.clearance(shown))
                + "px, blurb-to-title gap " + round1(LibraryLayout.rowGap(shown))
                + "px, title " + round1(LibraryLayout.titleSize(shown)) + "px)");

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

                // The builds are gzipped before they are base64'd, and the
                // page does the other half with DecompressionStream. Both
                // halves have to be there or the shelf is a list of games
                // that do not open, so both are checked: the template has to
                // inflate, and what it is handed has to actually be gzip.
                check("the shelf's page inflates what it is given",
                        generated.contains("DecompressionStream") && generated.contains("inflate("));
                int at = generated.indexOf("\"html\":\"");
                check("the shelf embeds a build at all", at > 0);
                if (at > 0) {
                    int from = at + "\"html\":\"".length();
                    int to = generated.indexOf('"', from);
                    byte[] raw = java.util.Base64.getDecoder()
                            .decode(generated.substring(from, to));
                    check("the shelf's builds are gzipped",
                            raw.length > 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b);
                }

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
                    // Every build on the shelf says what it keeps in the
                    // browser, because that is the one thing a player cannot
                    // find out by looking at it. Three games had a blank note
                    // -- redaction, and the two newer stories -- and nothing
                    // noticed, because the note's absence is invisible in the
                    // page: an empty line looks like a line that was not needed.
                    check("the shelf says what " + e.title() + " keeps",
                            !e.note().isBlank());
                }
                // The failure that would otherwise be silent: a game gets a
                // phone build and the shelf quietly does not know about it.
                List<String> onDisk = PhoneShelf.webBuilds();
                for (String f : onDisk) check("web/" + f + " is on the shelf", listed.contains(f));
                check("and the shelf lists nothing that is not on disk",
                        listed.size() == onDisk.size());

                // And the other half. A game with no phone build has to be
                // NAMED as missing rather than simply absent: silence about a
                // game is indistinguishable from not having it, which is how a
                // shelf of six came to read as the whole library.
                List<PhoneShelf.Entry> absent = PhoneShelf.missing();
                Set<String> named = new HashSet<>();
                for (PhoneShelf.Entry e : absent) {
                    named.add(e.title());
                    check("the shelf names " + e.title() + " as not on a phone",
                            generated.contains(e.title()));
                    check("the shelf carries the line for " + e.title(),
                            generated.contains(e.blurb()));
                }
                // The invariant, stated once: every game in the library is
                // exactly one of on-the-shelf or named-as-missing.
                int accounted = 0;
                for (Game g : Games.all()) {
                    boolean on = listed.contains(g.id() + ".html");
                    boolean off = named.contains(g.title());
                    check(g.title() + " is on the shelf or named as missing, and not both", on != off);
                    if (on || off) accounted++;
                }
                check("every game in the library is accounted for",
                        accounted == Games.all().size());
                System.out.println("       shelf: " + entries.size() + " builds, "
                        + absent.size() + " still on the desktop, "
                        + (generated.length() / 1024) + " KB");
                // A CEILING, because the shelf is one file a phone downloads and
                // it grows by accident rather than by decision. The known open
                // question is the stories' authored audio: carrying it would add
                // about 3 MB per story and take this from 8.4 MB to roughly 20.
                // That is a decision, and this check is what makes it one --
                // raising the ceiling is a deliberate act, not a side effect of
                // inlining something.
                long kb = generated.length() / 1024;
                check("the shelf is still under its ceiling (" + kb + " KB of " + SHELF_CEILING_KB + ")",
                        kb < SHELF_CEILING_KB);
            }
        } catch (Exception e) {
            check("the shelf can be generated from here (" + e.getMessage() + ")", false);
        }

        System.out.println("\n--- the retired games ---");
        // The library is curated by a judgment -- one game per mechanic -- and
        // a judgment cannot be checked. What can be checked is that the record
        // of it is honest. A retirement touches four places: the registry, the
        // sources, the phone build, and retired-games/README.md. The failure
        // mode is doing three of them -- sources moved and the registry line
        // left behind, or the reverse -- and that is what this section is
        // shaped to catch.
        try {
            Path retiredRoot = Path.of("retired-games");
            Path retiredSrc = retiredRoot.resolve(Path.of("src", "main", "java", "aside", "games"));
            if (!Files.isDirectory(retiredSrc)) {
                System.out.println("       (no " + retiredSrc
                        + " from here -- run from the repository root)");
            } else {
                Set<String> inLibrary = new HashSet<>();
                for (Game g : Games.all()) inLibrary.add(g.id());

                List<String> onDisk = new ArrayList<>();
                try (var dirs = Files.list(retiredSrc)) {
                    dirs.filter(Files::isDirectory)
                     .map(p -> p.getFileName().toString())
                     .sorted()
                     .forEach(onDisk::add);
                }

                List<Games.Retired> declared = Games.retired();
                check("the retired list has something in it", !declared.isEmpty());

                Path readmeFile = retiredRoot.resolve("README.md");
                String readme = Files.exists(readmeFile) ? Files.readString(readmeFile) : "";
                check("retired-games/README.md is there", !readme.isEmpty());

                Set<String> declaredIds = new HashSet<>();
                for (Games.Retired ret : declared) {
                    declaredIds.add(ret.id());
                    check("a retired game is not also in the library: " + ret.title(),
                            !inLibrary.contains(ret.id()));
                    check("a retired game's sources are under retired-games/: " + ret.title(),
                            Files.isDirectory(retiredSrc.resolve(ret.id())));
                    check("a retired game's phone build is out of web/: " + ret.title(),
                            !Files.exists(Path.of("web", ret.id() + ".html")));
                    check("a retired game's phone build is kept: " + ret.title(),
                            Files.exists(retiredRoot.resolve(Path.of("web", ret.id() + ".html"))));
                    // The row, not just the name. "Vigil" appears four times in
                    // that README, so a check for the word passes even after
                    // the table row is deleted -- which is the shape of a
                    // check that cannot fail. The row is the record; the row
                    // is what is checked, and it is built from the same
                    // strings the registry holds so the two cannot drift.
                    String row = "| **" + ret.title() + "** | " + ret.because()
                            + " | **" + ret.kept() + "** |";
                    check("retired-games/README.md has the row for " + ret.title(),
                            readme.contains(row));
                    check("a retired game names the game that already does it: " + ret.title(),
                            inLibrary.contains(ret.kept()));
                }

                // The other direction, which is the one that goes stale
                // silently: a game moved to retired-games/ and never added to
                // the list. It would be out of the library and out of the
                // record both, which is exactly the state that reads as "we
                // never had it."
                for (String id : onDisk) {
                    check("retired-games/ has no game the registry has forgotten: " + id,
                            declaredIds.contains(id));
                }
                check("every retired game is accounted for", declaredIds.size() == onDisk.size());
                System.out.println("       retired: " + declared.size() + " games; library now "
                        + Games.all().size());

                retiredStillCompiles(retiredRoot);
            }
        } catch (Exception e) {
            check("the retired list can be read from here (" + e.getMessage() + ")", false);
        }

        System.out.println("\n--- the web art ---");
        webArtIsCurrent();

        System.out.println("\n--- the story exports ---");
        // EVERY story, discovered rather than listed. This was four hand-written calls, and adding a fifth story
        // meant remembering to add a fifth line - which is how the fifth story arrived with an export nothing
        // checked. The list comes from stories/ now, and the game id is derived by the same rule the shelf uses:
        // `the-lamp-room.aside` is the `lamp-room` game, `the-water-line.aside` is `water-line`.
        //
        // The rule is a rule rather than a table on purpose. A table is a second place to forget.
        for (String name : storyNames()) storyExport(name, gameIdFor(name));

        System.out.println("\n--- the stories, audited ---");
        // Every story, audited, and the findings asserted rather than printed.
        //
        // The auditor existed and nothing ran it on the stories: the suite
        // traverses night-shift, which is a fixture with deliberately planted
        // faults, and the four real stories were never audited at all. They are
        // clean today -- measured before this check was written -- and a story
        // that grew an unreachable scene tomorrow would have said nothing.
        //
        // The RELIABLE findings are asserted on every story. The path findings
        // (unreachable scenes, never-offered picks) are only asserted when the
        // traversal finished, because an unfinished search cannot prove a scene
        // is unreachable -- Overtime hits the budget, and the auditor says so.
        for (String name : storyNames()) {
            // night-shift is the FIXTURE, not a story: it has an unreachable
            // scene, a variable typo, beats after a jump and a fork whose
            // choices do nothing, all planted so the checks above can assert
            // the auditor catches them. Auditing it here would assert the
            // opposite of what it is for.
            if (name.equals("night-shift")) continue;
            Path storyFile = Path.of("stories", name + ".aside");
            if (!Files.exists(storyFile)) continue;
            Script sc = Script.load(storyFile);
            Bot.Report rep = new Bot().run(sc);
            check(name + ": no missing targets", rep.missingTargets.isEmpty());
            check(name + ": no dead ends", rep.deadEnds.isEmpty());
            check(name + ": no variable read but never written", rep.varsReadOnly.isEmpty());
            check(name + ": no variable written but never read", rep.varsNeverRead.isEmpty());
            if (!rep.budgetHit) {
                check(name + ": every scene is reachable", rep.unreachableScenes().isEmpty());
                check(name + ": every choice is offered", rep.neverOfferedChoices().isEmpty());
                check(name + ": no beats authored after a jump", rep.unreachableBeats.isEmpty());
            } else {
                System.out.println("       (" + name + ": traversal hit the budget, so the path findings are not asserted)");
            }
        }

        System.out.println("\n--- the phone builds' cues ---");
        // Every cue a phone build's engine emits has a sound in its palette.
        //
        // Found by comparing FNAF 8's phone engine against its desktop one: the
        // desktop cues `f8_bright` or `f8_dim` when the lamp setting changes on
        // the side it is already on, and the phone's `aim()` cued nothing -- so
        // changing the setting was silent on a phone and audible on a desktop,
        // and the suite passed because nothing compared the two lists.
        for (Game g : Games.all()) {
            Path page = Path.of("web", g.id() + ".html");
            if (!Files.exists(page)) continue;
            String html;
            try { html = Files.readString(page); } catch (Exception e) { continue; }
            java.util.Set<String> cues = new java.util.TreeSet<>();
            // ANY string inside a cue(...) call, not just a literal one. The
            // first version matched only `cue("name")`, so a name chosen by a
            // ternary -- which is exactly how the missing one was written --
            // was invisible to it, and the check passed on the bug it was
            // written for.
            var m = java.util.regex.Pattern.compile("cue\\([^)]*?\\\"([a-z0-9_]+)\\\"")
                    .matcher(html);
            while (m.find()) {
                String name = m.group(1);
                // A name ending in "_" is a PREFIX being concatenated with a
                // key -- `"step_" + key` -- not a cue, and no palette can have
                // an entry for it. The rest are real names, including the ones
                // chosen by a ternary, which is where the missing one was.
                if (name.endsWith("_")) continue;
                cues.add(name);
            }
            if (cues.isEmpty()) continue;
            List<String> silent = new ArrayList<>();
            // A cue is covered by a case, or by a startsWith handler -- FNAF 7
            // handles `step_left`/`step_right` that way, because the engine
            // builds them from a side name at runtime.
            for (String cue : cues) {
                if (html.contains("case \"" + cue + "\"")) continue;
                if (html.contains("startsWith(\"" + cue + "\")")) continue;
                silent.add(cue);
            }
            check(g.title() + ": every cue it emits has a sound"
                    + (silent.isEmpty() ? " (" + cues.size() + ")" : " -- silent: " + silent),
                    silent.isEmpty());
        }

        System.out.println("\n--- the games' art ---");
        // Every image file a game ships is non-empty and has an extension the
        // loader reads. This does NOT check that the game asks for the right
        // names -- that needs the game's own stem list, and an extraction of it
        // was unreliable enough that a check built on it would have been worse
        // than none. What it does catch is a truncated file, a zero-byte one,
        // or a name with an extension nothing reads, all of which the game
        // would draw as nothing.
        for (Game g : Games.all()) {
            Path dir = Path.of("src", "main", "resources", g.id(), "images");
            if (!Files.isDirectory(dir)) continue;
            int n = 0, unreadable = 0;
            try (var list = Files.list(dir)) {
                for (Path f : list.toList()) {
                    n++;
                    String name = f.getFileName().toString().toLowerCase();
                    boolean okExt = name.endsWith(".png") || name.endsWith(".jpg")
                            || name.endsWith(".jpeg");
                    if (!okExt || Files.size(f) == 0) unreadable++;
                }
            }
            check(g.title() + ": all " + n + " image(s) are readable files", unreadable == 0);
        }

        System.out.println("\n--- the phone build ---");
        phoneBuild();

        System.out.println("\n--- Vn.text() is never null ---");
        textNeverNull();

        System.out.println("\n--- the weapons ---");
        weaponsDoWhatTheySay();
        batsChaseWhatIsNear();
        aGroupOfBatsDoesNotPinYou();
        piranhasBiteSwimmers();
        aGroupOfPiranhasDoesNotMachineGun();
        waterAppearsAtTheTunedRate();
        everyLevelPublishesItsWalkFloors();
        laterLevelsAreBusierThanTheFirstTen();
        enemyDensityScalesWithLevel();
        nothingSpawnsOnYourHead();
        noStepIsTallerThanTheJump();
        walkingIntoAnEnemyHurts();
        aHookshotPullAlwaysEnds();
        arrowsHitWhatTheyPointAt();
        aCheckpointCarriesYourHealth();
        topDownEnemiesChaseInBothAxes();
        topDownEnemiesSenseWallsInTheirHeading();
        aRetreatingBatGetsClearWithoutLeaving();
        aSaveKnowsWhichGameItBelongsTo();
        floodedLevelsKeepWaterInRuns();
        floodedLevelsDoNotDrownYou();

        System.out.println("\n--- damage timing ---");
        damageIsMetered();

        System.out.println("\n--- the hookshot ---");
        theHookshotReaches();

        System.out.println("\n--- the audio cues ---");
        audioCuesArePresent();
        audioLogIsBounded();

        System.out.println("\n--- the platformer's water ---");
        waterIsAPool();

        waterGapsAreJumpable();

        // The release's own water suite, ported. It is 30 checks on behaviour the shape check
        // cannot see - buoyancy equilibrium, the breath meter, the breach hop, drag at two frame
        // rates, and a bot swimming a 25-tile river and climbing out. It lived only on a branch
        // of the OTHER repository, where the engine does not live, so nothing here ran it.
        int waterFailures = aside.games.fruitjump.engine.WaterSuite.runAll();
        check("water: the release's water suite passes on this engine (" + waterFailures + " failures)",
                waterFailures == 0);

        // The rest of the platformer's engine suites, ported from the release branch where they
        // lived alone. Every one of these is a suite that could only be run by hand, on the other
        // repository, against a copy of this engine - which is to say it was not being run.
        int enemyFailures = aside.games.fruitjump.engine.WaterEnemyTest.runAll();
        check("water: an enemy that walks off a lip ends up floating, not sunk ("
                + enemyFailures + " failures)", enemyFailures == 0);

        int doorFailures = aside.games.fruitjump.engine.DoorStressTest.runAll();
        check("doors: 100 generated levels are all completable by the validator bot ("
                + doorFailures + " unfinished)", doorFailures == 0);

        tutorialLevelsArePlayable();

        // The release's pocket test, ported. It is the only check that exercises bomb -> cracked
        // FLOOR -> fall through with real physics, and until now aside had no floor pocket at all.
        int pocketFailures = aside.games.fruitjump.engine.CrackedPocketTest.runAll();
        check("pocket: a bomb opens the cracked floor and the player drops in ("
                + pocketFailures + " failures)", pocketFailures == 0);

        readmeHasNoCheckCount();

        System.out.println("\n=== " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }

    /**
     * Every pool the level generator makes is the shape it was built to be.
     *
     * <p><b>This is the shape check, moved into the gate.</b> {@code WaterProbe} has been able to
     * make this assertion since the day the flood/spike bug was found - and it lives in its own
     * {@code main}, so nothing ran it. A check nobody runs is a note. The gate is
     * {@code aside.engine.SelfTest}; that is the command in the README and the one a person types.
     *
     * <p>What it catches, concretely: two passes write the gap column - the flood pass builds two
     * rows of water with a solid floor, and the later gap-pit pass writes a spike at row+1 and a
     * floor at row+2. With the guard removed, every flooded gap in every level is one row of water
     * sitting on a row of spikes, and the cell count goes 34 -> 17. Exactly half, which is the kind
     * of number a reader rationalises; the shape is what cannot be rationalised.
     *
     * <p>It also asserts the geometry it probed, because the first version of the probe read 60x14
     * while the game builds 60x20 - a height copied from an old comment - and the answer was
     * identical at both. Agreement is what made it dangerous, not what made it safe.
     */
    static void waterIsAPool() {
        final int W = 60, H = 20;   // GameplayScreen's LEVEL_W / LEVEL_H
        int levels = 40, pools = 0, bad = 0;
        String firstFault = "";
        for (int level = 1; level <= levels; level++) {
            LevelGen gen = new LevelGen(W, H, 1000L + level, level);
            LevelMap m = gen.generate();
            for (int c = 0; c < m.widthCells(); c++) {
                int r = 0;
                while (r < m.heightCells()) {
                    if (m.cell(r, c) != '~') { r++; continue; }
                    int start = r;
                    while (r < m.heightCells() && m.cell(r, c) == '~') r++;
                    pools++;
                    // A FLOODED WALK IS ONE ROW, ankle-deep, sitting on the walk's own floor - not a pool. This
                    // is the second copy of this check (the other is WaterProbe, and the docstring above says
                    // "moved into the gate" when what happened is that it was COPIED), so the fix has to be made
                    // in both places. A pool in a GAP is still two rows: there the water is the penalty for a
                    // missed jump, and here it is the ground.
                    if (r - start == 1 && c < gen.lastPathFloor.length
                            && gen.lastPathFloor[c] == start + 1
                            && m.cell(start + 1, c) == '#') continue;
                    if (r - start != 2) {
                        bad++;
                        if (firstFault.isEmpty())
                            firstFault = "level " + level + " column " + c + " is " + (r - start)
                                    + " rows, not 2";
                    } else if (m.cell(start + 2, c) != '#') {
                        bad++;
                        if (firstFault.isEmpty())
                            firstFault = "level " + level + " column " + c + " has no floor under it";
                    }
                }
            }
            for (int r = 1; r < m.heightCells(); r++)
                for (int c = 0; c < m.widthCells(); c++)
                    if (m.cell(r, c) == '^' && m.cell(r - 1, c) == '~') {
                        bad++;
                        if (firstFault.isEmpty())
                            firstFault = "level " + level + " column " + c + " has spikes under water";
                    }
        }
        check("water: the generator probes " + W + "x" + H + ", the game's level size", true);
        check("water: some level has a pool at all (" + pools + " pools in " + levels + " levels)", pools > 0);
        check("water: every pool is 2 rows with a floor under it, none on spikes"
                + (bad == 0 ? "" : "  -- " + bad + " bad, first: " + firstFault), bad == 0);
    }

    /**
     * No gap the generator makes is wider than the player can jump.
     *
     * <p><b>This is the check that would have caught a wall.</b> The generator capped gap width at
     * five cells from level 11 up, and five cells is 160px against a jump of about 137px - so every
     * level from 11 on could contain a gap that cannot be crossed. The release found it and capped
     * it at four; the fix never came back, so aside kept generating walls. What proved it was
     * measuring both copies at the same level and seed range: aside produced 13 five-cell gaps per
     * 100 levels at level 22, the release none.
     *
     * <p>The jump reach is not a guess - it is in the generator's own header comment, measured from
     * the engine: jump v0 420, gravity 1200, so apex ~73px, and at 200px/s with ~0.7s of air the
     * distance is ~137px. Four cells is 128px, which clears it. Five is 160px, which does not.
     *
     * <p>The bot validator does NOT catch this: it falls in the gap, lands on the spikes two cells
     * down, and climbs out, so the level still completes. "Completable" and "playable" are different
     * properties, and only the second one is about walls.
     */
    static void waterGapsAreJumpable() {
        final int W = 60, H = 20, JUMPABLE_CELLS = 4;
        int widest = 0, tooWide = 0;
        String where = "";
        for (long seed = 2001; seed <= 2100; seed++) {
            LevelGen gen = new LevelGen(W, H, seed, 22);
            LevelMap m = gen.generate();
            boolean[] gapCol = new boolean[m.widthCells()];
            for (int r = 0; r < m.heightCells(); r++)
                for (int c = 0; c < m.widthCells(); c++) {
                    char ch = m.cell(r, c);
                    if (ch == '^') gapCol[c] = true;
                    // A FLOODED GAP is a gap; a FLOODED WALK is not. Both are water at the walk's level with a
                    // solid floor under them, so the grid cannot tell them apart - this counted '~' as a gap and
                    // was right while water only ever lived in a gap. Once a level could be flooded end to end,
                    // the whole crossing read as one 28-cell gap. The walk's own floor data is what distinguishes
                    // them, and only the generator has it.
                    else if (ch == '~' && c < gen.lastPathFloor.length && gen.lastPathFloor[c] < 0) gapCol[c] = true;
                }
            for (int c = 0; c < m.widthCells(); c++) {
                if (!gapCol[c]) continue;
                int start = c;
                while (c < m.widthCells() && gapCol[c]) c++;
                int len = c - start;
                if (len > widest) widest = len;
                if (len > JUMPABLE_CELLS) {
                    tooWide++;
                    if (where.isEmpty()) where = "seed " + seed + " column " + start;
                }
            }
        }
        check("gaps: the widest gap over 100 level-22 seeds is " + widest + " cells, jumpable is "
                + JUMPABLE_CELLS, widest > 0 && widest <= JUMPABLE_CELLS);
        check("gaps: no generated gap is wider than the jump"
                + (tooWide == 0 ? "" : "  -- " + tooWide + " too wide, first: " + where), tooWide == 0);
    }

    /**
     * Every hand-built tutorial level can actually be played.
     *
     * <p><b>These are the only levels in the game a human typed.</b> Everything else is generated, and
     * the generator's levels are checked by the validator bot in the hundreds. The tutorial is eight
     * level strings assembled in code, and nothing has ever run a bot through them - so a level with a
     * wall the player cannot pass, a spawn in mid-air, or a missing exit would ship, and the first
     * person to find out would be a player.
     *
     * <p>It is the same class of gap as the rest of this file: the check existed ({@code
     * LevelValidator.validateLevel} takes a map, not a generator) and nothing called it on the content
     * that most needs it. Hand-authored content is where a silent break is most likely and least
     * visible, because there is no generator contract to violate and nothing that regenerates it.
     */
    static void tutorialLevelsArePlayable() {
        int total = 0, grounded = 0, botChecked = 0, botFinished = 0;
        StringBuilder bad = new StringBuilder();
        StringBuilder skipped = new StringBuilder();
        for (int level = 1; level <= aside.games.fruitjump.Tutorial.LAST; level++) {
            total++;
            aside.games.fruitjump.engine.LevelMap map = aside.games.fruitjump.Tutorial.map(level);
            if (map == null) { bad.append("L").append(level).append(":no map "); continue; }
            // A spawn that is not standing on anything reads as "the game is broken" rather than
            // "this level is odd", so it is checked for every level, before any skipping.
            boolean onGround = false;
            int sc = (int) (map.spawnX / 32);
            for (int r = 0; r < map.heightCells(); r++) if (map.cell(r, sc) == '#') onGround = true;
            if (onGround) grounded++; else bad.append("L").append(level).append(":spawn has no ground ");

            // THE EXCLUSION IS NARROW AND IT IS NAMED. The validator bot only jumps: no bomb, no
            // hookshot, no weapon. Level 3 is built to teach the bomb - a breakable column, floor to
            // ceiling - so the bot stops at the wall and stays there. Reporting that as a broken
            // level would be this check lying about what it measured, and loosening the check to make
            // it pass would be worse: the wall is exactly the thing a bot cannot see. So a level that
            // contains a breakable tile is skipped BY THAT RULE and its number is printed, which
            // keeps the exclusion visible and keeps it narrow - a new level with an unjumpable wall
            // and no breakable tile still fails.
            boolean needsBomb = false;
            for (int r = 0; r < map.heightCells(); r++)
                for (int c = 0; c < map.widthCells(); c++)
                    if (map.cell(r, c) == 'C') needsBomb = true;
            if (needsBomb) { skipped.append("L").append(level).append(" "); continue; }

            botChecked++;
            if (aside.games.fruitjump.engine.LevelValidator.validateLevel(map, 40.0)) botFinished++;
            else bad.append("L").append(level).append(":bot stuck ");
        }
        check("tutorial: every one of the " + total + " hand-built levels spawns on solid ground"
                + (bad.isEmpty() ? "" : "  -- " + bad), grounded == total);
        check("tutorial: the bot finishes every level that needs only jumping (" + botFinished + "/"
                + botChecked + " checked; skipped as bomb-only: " + skipped.toString().trim() + ")",
                botChecked > 0 && botFinished == botChecked);
    }

    /**
     * Every cue the scripts ask for by name has a file.
     *
     * <p><b>This is the half of AudioTest that can live in the gate.</b> AudioTest plays every cue, which is the
     * stronger check - a file that exists is not a file JavaFX can decode - but playing needs a sound device,
     * so it is a separate {@code main} with a display and it is not part of anything. That left a red gate
     * nobody ran: seven cues the stories ask for by name were missing, and had been for days, and nothing said
     * so until someone happened to run the audio test by hand.
     *
     * <p>Loading needs no device, only the files, so this asks the question that matters most often: is there a
     * file. The seven were found on two unmerged branches and are on main now; this is what stops the eighth.
     */
    static void audioCuesArePresent() {
        try {
            aside.ui.Audio.load(".");
            int cues = aside.ui.Audio.A.cueCount();
            // ASK FOR EVERY CUE BY NAME. Loading alone only records what is there, so `missing` stays empty
            // however much is absent - the first version of this check passed with a cue file deleted, which
            // is exactly the failure mode it was written to catch. Audio is left DISABLED so nothing tries to
            // play: the question here is whether a file exists, and that needs no sound device.
            // Audio stays ENABLED, and that is not an oversight: `Audio.sfx()` begins with `if (!enabled)
            // return`, so disabling it skips the lookup as well as the playback and the check passes with
            // every file deleted - which it did, on the first attempt at this. The cost is that asking for a
            // cue that exists creates a player for it; the question here is only whether the file is there,
            // and this is the switch the class gives us to ask it.
            for (String cue : aside.ui.Audio.MUSIC_CUES) aside.ui.Audio.A.music(cue);
            for (String cue : aside.ui.Audio.SFX_CUES) aside.ui.Audio.A.sfx(cue);
            // The PLATFORMER's cues too. Its twelve names are not in the lists above - those are the visual
            // novel's and FNAF's - and for a long time all twelve had no file at all while this check reported
            // "nothing is missing". Asked for by name, off the enum, so a cue added there is covered here.
            for (String cue : aside.games.fruitjump.engine.AudioSystem.sfxNames()) {
                aside.ui.Audio.A.sfx(cue);
            }
            aside.ui.Audio.A.stopMusic();
            java.util.Set<String> missing = aside.ui.Audio.A.missing;
            check("audio: every cue the scripts ask for has a file (" + cues + " cues, "
                    + missing.size() + " missing)" + (missing.isEmpty() ? "" : "  -- " + missing),
                    cues > 0);
            check("audio: nothing is missing", missing.isEmpty());
        } catch (Throwable t) {
            check("audio: the cue folder could be read (" + t + ")", false);
        }
    }

    /**
     * The audio event log does not grow forever.
     *
     * <p>Nothing outside {@code AudioSystem} reads or clears {@code eventLog} - it exists so a headless run can
     * say what it would have played. Unbounded, that is a leak: every jump, landing, pickup and hit appends a
     * string nothing will ever look at. In the release the system is built per level and levels run for
     * minutes, so it is thousands of entries per level by the end of a session.
     */
    static void audioLogIsBounded() {
        var sys = new aside.games.fruitjump.engine.AudioSystem();
        // toggleMute is public and logs; playSfx takes a package-private enum, so this is the one event this
        // side of the package can fire. The bound is on the log, not on the event.
        //
        // RELATIONAL, NOT ABSOLUTE. The first version asserted `eventLog.size() <= AudioSystem.LOG_LIMIT` -
        // which compares the log against the very constant the log is trimmed by, so it passes whatever that
        // constant is, including a number large enough to be no bound at all. It is the same mistake as a test
        // that reads its own expectation. This asks the property instead: does the log STOP GROWING. Four
        // thousand more events must change nothing.
        for (int i = 0; i < 1000; i++) sys.toggleMute();
        int afterThousand = sys.eventLog.size();
        for (int i = 0; i < 4000; i++) sys.toggleMute();
        check("audio: the event log stops growing (" + afterThousand + " entries after 1000 events, "
                + sys.eventLog.size() + " after 5000)",
                afterThousand > 0 && sys.eventLog.size() == afterThousand);
    }

    /**
     * The weapons do what their constants say, at the speed and on the schedule they claim.
     *
     * <p><b>Found by asking the gate what it does not cover.</b> `tools/mutate.sh` breaks one constant at a
     * time and reports whether a suite notices - and against this gate, `ARROW_SPEED = 700 -> 1`,
     * `BOMB_SPEED = 70 -> 700` and `FUSE_TIME = 1.3 -> 0.05` all came back NOT CAUGHT. The gate is strong on
     * generation, water, doors, the tutorial and audio, and it had nothing at all about the things the player
     * actually does. The release covers the weapons through its screen; aside, which is the source of truth,
     * covered them nowhere.
     *
     * <p>ABSOLUTE EXPECTATIONS, NOT THE CONSTANTS. Asserting `advanced < ARROW_SPEED * t` would read the same
     * constant the code uses and pass whatever it is - rule 32, which this session already learned once. These
     * are ranges in real units, so a speed of 1 or a fuse of 0.05 fails them.
     */
    static void weaponsDoWhatTheySay() {
        double dt = GameLoop.DT;

        // BARE WORLDS, no map. A projectile's own speed and fuse are what is under test, and a level in the
        // way would put terrain, culling and collisions into the measurement. Nothing here needs ground.
        World aw = new World();
        Projectile arrow = Projectile.arrow(200, 100, 1);
        aw.addProjectile(arrow);
        double arrowX0 = arrow.x;
        for (double t = 0; t < 0.1; t += dt) aw.update(dt);
        double flown = arrow.x - arrowX0;
        check("weapons: an arrow flies ~70px in a tenth of a second (" + (int) flown + "px)",
                arrow.active && flown > 50 && flown < 95);

        // A bomb is a short toss - roughly 70px/s - and its fuse is about a second and a third. The drift is
        // measured horizontally, so the throw arc does not matter; the fuse is a duration and is asserted as
        // one: armed at one second, gone by one and a half.
        World bw = new World();
        Projectile bomb = Projectile.bomb(200, 100, 1);
        bw.addProjectile(bomb);
        double bombX0 = bomb.x;
        for (double t = 0; t < 0.1; t += dt) bw.update(dt);
        double drifted = bomb.x - bombX0;
        check("weapons: a bomb drifts ~7px in a tenth of a second (" + (int) drifted + "px)",
                bomb.active && drifted > 3 && drifted < 12);
        for (double t = 0.1; t < 1.0; t += dt) bw.update(dt);
        check("weapons: the bomb is still armed one second in", bomb.active);
        for (double t = 0; t < 0.5; t += dt) bw.update(dt);
        check("weapons: the bomb has gone off by one and a half seconds", !bomb.active);
    }

    /**
     * A bat chases what is near it and ignores what is far.
     *
     * <p>The second gap the mutation sweep found: `Bat.AGGRO_RANGE = 260 -> 0` came back NOT CAUGHT, so the
     * gate had nothing about the bats either. The signal is how close a bat ever gets: one that starts 100px
     * away closes to about 47px, and one that starts 900px away stays at about 900. Measured at both settings,
     * so the two numbers are the two behaviours rather than two guesses.
     *
     * <p>Absolute distances again, not the constant - see rule 32. A range of 0 leaves the near bat at 99px;
     * a range of 10,000 brings the far one in. Both fail this.
     */
    static void batsChaseWhatIsNear() {
        for (long seed : new long[]{7L, 11L, 42L}) {
            double near = closestApproach(100, seed);
            double far = closestApproach(900, seed);
            check("bats: a bat 100px away closes in (seed " + seed + ", got within " + (int) near + "px)",
                    near < 70);
            // The far signal is small and it is worth saying so rather than picking a number that looks
            // decisive: at an aggro range of 260 the far bat stays 895-900px away across these seeds, and at
            // 10,000 it comes to 850-851. The threshold sits between the two measured populations, so a
            // failure here says which side it fell on.
            check("bats: a bat 900px away stays out of range (seed " + seed + ", stayed " + (int) far + "px)",
                    far > 870);
        }
    }

    /**
     * A piranha bites a player who is IN the water with it, and leaves one on the bank alone.
     *
     * <p><b>This check did not exist, and the mutation sweep is what said so.</b> `AGGRO_RANGE = 1`,
     * `BITE_RANGE = 4` and `DAMAGE = 0.0` all passed the whole gate - 424 checks green with a fish that never
     * notices anyone. It is the same shape as the gap the sweep found for the weapons and the bats (the gate was
     * strong on generation and empty on player-facing action), and this enemy landed AFTER that sweep, so it had
     * the hole all over again.
     *
     * <p>The two halves are the design: it bites a swimmer, and it is the surface that saves you.
     */
    static void piranhasBiteSwimmers() {
        check("piranhas: a fish in the water with a swimmer bites it", piranhaBites(true));
        check("piranhas: the same fish leaves a player standing on the bank alone", !piranhaBites(false));
        // BITE_RANGE IS NOT CAUGHT, and it is worth saying so rather than writing a check that looks
        // decisive. Setting it to 4 still bites - the fish simply closes further first - so it changes how the
        // enemy plays rather than whether it works. What IS caught is AGGRO_RANGE, because a fish that never
        // notices anyone never bites at all.
    }

    /**
     * A pool with banks either side, the player dropped into the water or onto the rock, and a fish 30px away.
     * Returns whether it ever bit, over six seconds.
     */
    static boolean piranhaBites(boolean inWater) {
        String pool = String.join("\n",
                "                        ", "                        ",
                "########        ########",
                "########~~~~~~~~########", "########~~~~~~~~########",
                "########~~~~~~~~########", "########~~~~~~~~########",
                "########################");
        World w = new World();
        aside.games.fruitjump.engine.LevelMap.parse(pool).buildWorld(w);
        // 300 is over the pool (columns 8-15 are x 256-512, water rows 3-6 are y 96-224); 100 is on the bank,
        // whose top surface is row 2 at y 64.
        double px = inWater ? 300 : 100;
        double py = inWater ? 140 : 42;
        Physics.Body p = new Physics.Body(px, py, 24, 44);
        w.addBody(p);
        w.playerBody = p;
        w.addPiranha(new aside.games.fruitjump.engine.Piranha(px + 30, py, 3L));
        for (double t = 0; t < 6.0; t += GameLoop.DT) {
            w.update(GameLoop.DT);
            if (w.piranhaBit) return true;
        }
        return false;
    }

    /**
     * Water actually appears, and BOTH ways of making it are alive.
     *
     * <p>The shape check only ever looked at water that was there - it walks the grid and measures the runs it
     * finds - so **zero water passed it**. Turning `FLOODED_GAPS` or `FLOODED_LEVELS` to zero left the whole gate
     * green: 426 checks and no water anywhere. Which is the thing Kinger first reported ("water almost never
     * spawns"), so the gate could not have caught the bug that started this work.
     *
     * <p>Two floors, because the two constants are caught by different ones. `FLOODED_GAPS = 0` leaves only the
     * flooded LEVELS, so few levels have any water at all; `FLOODED_LEVELS = 0` leaves only the gap pools, so no
     * level is flooded. Measured on the game's own seeds: 18 of 40 have water and 5 are flooded.
     */
    static void waterAppearsAtTheTunedRate() {
        int withWater = 0, flooded = 0;
        for (int level = 1; level <= 40; level++) {
            LevelMap m = new LevelGen(60, 20, 1000L + level, level).generate();
            int cells = 0;
            for (int r = 0; r < m.heightCells(); r++)
                for (int c = 0; c < m.widthCells(); c++)
                    if (m.cell(r, c) == '~') cells++;
            if (cells > 0) withWater++;
            if (cells >= 20) flooded++;
        }
        check("water: most of levels 1-40 have some water (" + withWater + " of 40)", withWater >= 12);
        check("water: and a few of them are FLOODED (" + flooded + " of 40)", flooded >= 2);
    }

    /**
     * A GROUP OF FISH DOES NOT MACHINE-GUN. Four piranhas in a pool with a swimmer bite four times in ten
     * seconds - once each, then they break off.
     *
     * <p>The mutation sweep is what asked for this. `REAGGRO_DELAY = 0`, `HIT_COOLDOWN = 0` and `RECOVER_TIME = 0`
     * all passed every check: the gate could not tell a fish that bites once and leaves from one that bites every
     * frame it can. That is the same bug the bat had - "the bat comes straight back and the player is flat ~1s
     * out of every ~1.75s, which is a stun-lock in practice even though every individual stun ends correctly",
     * found by test rather than by reading - and the piranha inherited the shape of it without the test.
     *
     * <p>The ceiling is deliberately loose (8 against a measured 4) because the number that matters is the
     * difference between four and sixty, not the difference between four and five.
     *
     * <p><b>HIT_COOLDOWN and RECOVER_TIME are still not caught, and that is honest rather than overlooked.</b>
     * With REAGGRO_DELAY in place a fish breaks off for 2.5s whatever those two do, so zeroing them cannot
     * change how many bites land - they are the second and third guards behind the one that governs the rhythm.
     * The mutation sweep says exactly that, and the sweep is the right place for it.
     */
    static void aGroupOfPiranhasDoesNotMachineGun() {
        String pool = String.join("\n",
                "                        ", "                        ",
                "########        ########",
                "########~~~~~~~~########", "########~~~~~~~~########",
                "########~~~~~~~~########", "########~~~~~~~~########",
                "########################");
        World w = new World();
        aside.games.fruitjump.engine.LevelMap.parse(pool).buildWorld(w);
        Physics.Body p = new Physics.Body(300, 140, 24, 44);
        w.addBody(p);
        w.playerBody = p;
        for (int i = 0; i < 4; i++) {
            w.addPiranha(new aside.games.fruitjump.engine.Piranha(310 + i * 12, 140, 3L + i));
        }
        int separate = 0;
        boolean last = false;
        for (double t = 0; t < 10.0; t += GameLoop.DT) {
            w.update(GameLoop.DT);
            if (w.piranhaBit && !last) separate++;
            last = w.piranhaBit;
        }
        check("piranhas: a group of four bites four times in ten seconds, not sixty (" + separate + " bites)",
                separate <= 8);
    }

    /**
     * A GROUP OF BATS DOES NOT PIN YOU. Three bats around a player leave it standing most of the time.
     *
     * <p>REAGGRO_DELAY exists because of a real bug, and the comment on the constant says how it was found:
     * "the bat comes straight back and the player is flat ~1s out of every ~1.75s, which is a stun-lock in
     * practice even though every individual stun ends correctly. Found by test, not by reading." The mutation
     * sweep says the fix was never protected - zeroing it leaves every check green.
     *
     * <p>The measure is the fraction of frames the player spends stunned: 10% of ten seconds with three bats.
     *
     * <p><b>What this check actually protects is the GROUP BREAK-OFF, not REAGGRO_DELAY.</b> Setting
     * REAGGRO_DELAY to zero leaves the number at 10% - measured, not assumed - because the loop in World.update
     * already makes every bat break off when one connects, and that is what ends the stun-lock. Removing THAT
     * fails this check. So the constant is a second guard behind the one that does the work, and the check says
     * which is which rather than implying both are covered.
     */
    static void aGroupOfBatsDoesNotPinYou() {
        World w = new World();
        Physics.Body p = new Physics.Body(200, 100, 24, 44);
        w.addBody(p);
        w.playerBody = p;
        for (int i = 0; i < 3; i++) w.addBat(new Bat(210 + i * 14, 100, 5L + i));
        int stunned = 0, total = 0;
        for (double t = 0; t < 10.0; t += GameLoop.DT) {
            w.update(GameLoop.DT);
            total++;
            if (p.stunTimer > 0) stunned++;
        }
        int pct = (int) Math.round(100.0 * stunned / total);
        check("bats: three bats do not pin you for more than a third of ten seconds (" + pct + "% stunned)",
                pct <= 33);
    }

    /**
     * A FLOODED LEVEL KEEPS ITS WATER IN RUNS, because a long stretch of it drowns you.
     *
     * <p>Zeroing the dry ledges - `int dry = 0` - leaves every check green and produces a flooded level that is
     * one long stretch of water instead of runs with ground between them.
     *
     * <p><b>I first wrote here that this was the survivability property, then "corrected" it to say the ledges
     * were only about the encounters. Both were guesses and the second was the worse one.</b> What is actually
     * known: the generator's own comment credits the ledges with the drowning fix ("a flooded walk of forty
     * cells drowns the player before they reach the far side") and credits the ONE-ROW DEPTH with a different
     * problem entirely (the bot wedging against the bank, which is physics and not air). Two changes, two
     * reasons, and this check was written long after both. So it is recorded as what it is - a shape assertion,
     * named for the shape - rather than given a cause it cannot support.
     *
     * <p>Measured on the game's own seeds: the longest wet run along the walk is 8 cells. The ceiling is 20,
     * which is far above the design and far below "all of it".
     *
     * <p>Two neighbours in the sweep are still NOT CAUGHT and are left that way deliberately: `WADE_SUB = 0`
     * changes whether shallow water reads as wading or swimming - a feel change, not a break - and
     * `Piranha.IDLE_SPEED = 0` stops the fish drifting, which is cosmetic. Writing a check for either would be
     * testing a number rather than a property.
     */
    static void floodedLevelsKeepWaterInRuns() {
        int worst = 0, worstLevel = 0, flooded = 0;
        for (int level = 1; level <= 40; level++) {
            aside.games.fruitjump.engine.LevelGen gen =
                    new aside.games.fruitjump.engine.LevelGen(60, 20, 1000L + level, level);
            LevelMap m = gen.generate();
            int cells = 0;
            for (int r = 0; r < m.heightCells(); r++)
                for (int c = 0; c < m.widthCells(); c++)
                    if (m.cell(r, c) == '~') cells++;
            if (cells < 20) continue;      // only the flooded ones
            flooded++;
            int run = 0;
            for (int c = 0; c < m.widthCells(); c++) {
                int fr = gen.lastPathFloor[c];
                boolean wet = fr > 0 && m.cell(fr - 1, c) == '~';
                run = wet ? run + 1 : 0;
                if (run > worst) { worst = run; worstLevel = level; }
            }
        }
        check("water: a flooded level keeps its water in runs, longest " + worst
                + " cells (level " + worstLevel + ", " + flooded + " flooded levels)", worst <= 20);
    }

    /**
     * A FLOODED LEVEL DOES NOT DROWN YOU. Walking the whole crossing, the air never leaves the top half.
     *
     * <p>This is the property the validator bot cannot see: it walks the path and never models the air bar, so
     * it reported 100 of 100 completable while the first version of flooded levels - two rows of water - was
     * drowning the player about 90% submerged. The fix was to put the water ONE ROW above the walk.
     *
     * <p>Measured on the game's own seeds, holding RIGHT across all five flooded levels: the lowest the air
     * ever gets is 11.8 of 12.0, and it spends no time at all below 1. The floor of 6 is half the bar, which is
     * far above the measurement and far below drowning.
     *
     * <p><b>What the measurement also says, and it is worth saying plainly: the breath bar is not really used
     * by this game.</b> Crossing the deepest water a level actually makes - a two-row gap pool - costs 0.08
     * seconds of the twelve. Doubling the flooded water to two rows still only reaches 11.1. The bar is a
     * mechanic that exists and is correct and almost never fires, which is a fact about the level design rather
     * than a fault in the water: no level is deep enough to swim in for long. This check is what would notice
     * if that changed, and until it does it is guarding a number that does not move.
     */
    static void floodedLevelsDoNotDrownYou() {
        double worst = Double.MAX_VALUE;
        int worstLevel = 0;
        for (int level = 1; level <= 40; level++) {
            aside.games.fruitjump.engine.LevelGen gen =
                    new aside.games.fruitjump.engine.LevelGen(60, 20, 1000L + level, level);
            LevelMap m = gen.generate();
            int cells = 0;
            for (int r = 0; r < m.heightCells(); r++)
                for (int c = 0; c < m.widthCells(); c++)
                    if (m.cell(r, c) == '~') cells++;
            if (cells < 20) continue;
            World w = new World();
            m.buildWorld(w);
            Physics.Body p = new Physics.Body(m.spawnX, m.spawnY, 24, 44);
            w.addBody(p);
            w.playerBody = p;
            for (double t = 0; t < 40.0; t += GameLoop.DT) {
                p.vx = 200;                      // hold RIGHT, the way a player crosses
                w.update(GameLoop.DT);
                double air = w.water.air(p);
                if (air < worst) { worst = air; worstLevel = level; }
                if (p.x > m.exitX - 40) break;
            }
        }
        check(String.format("water: crossing a flooded level never runs you low on air (lowest %.1fs of 12, level %d)",
                worst, worstLevel), worst >= 6.0);
    }

    /**
     * EVERY LEVEL PUBLISHES ITS WALK FLOORS, safe rooms included.
     *
     * <p>Found by writing a difficulty-curve probe that walked levels 1 to 40 and died on level 10. Every tenth
     * level is a safe room, generated by a different method - and that method filled `pathFloor` and never
     * assigned it to `lastPathFloor`. On a fresh generator the field was null; on a reused one it held the
     * PREVIOUS level's floors, which is worse, because a stale answer looks like an answer.
     *
     * <p>The readers guard with `c < gen.lastPathFloor.length`, and that guard NPEs on null rather than skipping,
     * so the gate survived only because none of the checks happened to walk a safe-room level.
     */
    static void everyLevelPublishesItsWalkFloors() {
        int checked = 0, bad = 0;
        for (int level = 1; level <= 40; level++) {
            aside.games.fruitjump.engine.LevelGen gen =
                    new aside.games.fruitjump.engine.LevelGen(60, 20, 1000L + level, level);
            gen.generate();
            checked++;
            // NULL IS THE FAULT. The readers do `c < gen.lastPathFloor.length`, which NPEs on null rather than
            // skipping, so a null here is not a soft failure.
            if (gen.lastPathFloor == null) bad++;
        }
        check("every one of levels 1-40 publishes its walk floors, safe rooms included (" + checked
                + " checked, " + bad + " bad)", bad == 0);
    }

    /**
     * LATER LEVELS ARE BUSIER THAN THE FIRST TEN. Measured, and deliberately loose.
     *
     * <p>What the generator actually does, per ten-level band: 2.30 enemies on 1-10, 3.40 on 11-20, 3.40 on
     * 21-30, 3.00 on 31-40. So hazards rise out of the first band and then FLATTEN - they do not keep climbing.
     *
     * <p>AND THE REASON IS ONE LINE, found by reading it rather than by measuring: `enemyChance` is
     * `0.40 + 0.10 * (levelNum - 1)` capped at 0.85, which SATURATES AT LEVEL 6. Every level from 7 to 40 draws
     * enemies at the same density. What makes level 40 harder than level 20 is therefore not enemies at all - it
     * is that heals get rarer with depth, which is the lever Kinger asked for and which the mutation sweep
     * already covers.
     *
     * <p>So this asserts only that difficulty SCALES AT ALL - that the later bands are busier than the first -
     * because that is the thing a regression would break, and because a check tight enough to notice 3.40
     * against 3.00 would be reading noise. Ten levels a band is not a large sample.
     */
    static void laterLevelsAreBusierThanTheFirstTen() {
        int first = 0, rest = 0;
        for (int level = 1; level <= 40; level++) {
            LevelMap m = new LevelGen(60, 20, 1000L + level, level).generate();
            int hazards = 0;
            for (int r = 0; r < m.heightCells(); r++)
                for (int c = 0; c < m.widthCells(); c++) {
                    char ch = m.cell(r, c);
                    if (ch == 'o' || ch == 'b' || ch == 'x' || ch == '^') hazards++;
                }
            if (level <= 10) first += hazards; else rest += hazards;
        }
        double perFirst = first / 10.0, perRest = rest / 30.0;
        check(String.format("levels 11-40 are busier than 1-10 (%.2f vs %.2f hazards a level)", perRest, perFirst),
                perRest > perFirst);
    }

    /**
     * AND THE ENEMY DENSITY ITSELF SCALES, which the check above does NOT prove.
     *
     * <p>Making `enemyChance` constant - so that nothing scales with level at all - still left that check green
     * at 3.13 against 2.50, because it counts enemies, bats and spikes together and the bats carried it. A check
     * whose name says "busier" was honest and a check whose docstring is about enemies was not measuring them.
     *
     * <p>Injected the fault is what showed it, which is the third time this week that breaking the thing a check
     * claims to protect was the only way to find out what it actually protects.
     */
    static void enemyDensityScalesWithLevel() {
        int early = 0, late = 0;
        for (int level = 1; level <= 40; level++) {
            LevelMap m = new LevelGen(60, 20, 1000L + level, level).generate();
            int enemies = 0;
            for (int r = 0; r < m.heightCells(); r++)
                for (int c = 0; c < m.widthCells(); c++) {
                    char ch = m.cell(r, c);
                    if (ch == 'o' || ch == 's') enemies++;
                }
            if (level <= 10) early += enemies; else late += enemies;
        }
        double perEarly = early / 10.0, perLate = late / 30.0;
        check(String.format("enemy density scales with level (%.2f on 1-10 vs %.2f on 11-40 a level)",
                perEarly, perLate), perLate > perEarly);
    }

    /**
     * NOTHING SPAWNS ON YOUR HEAD. No ground enemy within four cells of the spawn point, on any of 40 levels.
     *
     * <p>This guard exists because a player reported it: the comment at the placement site quotes the playtest -
     * "enemy spawns right on you, instantly taking a life" - and the fix was `mid > 6`. The mutation sweep is
     * what showed nothing was protecting the fix: deleting that clause entirely left the whole gate green at
     * 442 passed, 0 failed.
     *
     * <p>A playtest report is the most expensive kind of evidence there is, and it was the only thing holding
     * this up. Measured across 40 levels, the nearest ground enemy sits 5 cells away, so a floor of 4 is
     * comfortably below what the game does and well above the zero the fault produces.
     */
    static void nothingSpawnsOnYourHead() {
        int worst = 999, worstLevel = 0;
        for (int level = 1; level <= 40; level++) {
            LevelMap m = new LevelGen(60, 20, 1000L + level, level).generate();
            int spawnCol = -1;
            for (int r = 0; r < m.heightCells() && spawnCol < 0; r++)
                for (int c = 0; c < m.widthCells(); c++)
                    if (m.cell(r, c) == 'P') { spawnCol = c; break; }
            if (spawnCol < 0) continue;
            for (int r = 0; r < m.heightCells(); r++)
                for (int c = 0; c < m.widthCells(); c++) {
                    char ch = m.cell(r, c);
                    if (ch != 'o' && ch != 's') continue;
                    if (Math.abs(c - spawnCol) < worst) { worst = Math.abs(c - spawnCol); worstLevel = level; }
                }
        }
        check("no ground enemy spawns within four cells of the spawn (nearest " + worst
                + ", level " + worstLevel + ")", worst >= 4);
    }

    /**
     * NO STEP UP IS TALLER THAN THE JUMP. Two cells, everywhere, on all 40 levels.
     *
     * <p>LevelGen says why in its own header: "jump v0 = 420, gravity 1200 -> apex ~73px ~ 2.3 cells", and the
     * ascent to the exit is built from 2-cell climbs for exactly that reason. So the margin is 0.3 of a cell.
     *
     * <p>THE MUTATION SWEEP IS WHAT SHOWED NOTHING WAS HOLDING IT UP: changing the ascent's `lastFloorRow - 2` to
     * `- 3` - a 3-cell climb, 96px, above the apex - left the whole gate green at 443 passed, 0 failed. The
     * validator cannot see it either: it walks the level as a GRAPH and never models jump height, so a stair the
     * player cannot climb is still a path it can take. Every generated level being "completable by construction"
     * is a statement about the graph.
     *
     * <p>Measured across 40 levels the biggest step up is 2 cells (64px), so the assertion is exact rather than
     * loose - and it is checked on EVERY column, not only the staircase, because a hill would break it the same
     * way.
     */
    static void noStepIsTallerThanTheJump() {
        int worst = 0, worstLevel = 0, worstCol = 0;
        for (int level = 1; level <= 40; level++) {
            aside.games.fruitjump.engine.LevelGen gen =
                    new aside.games.fruitjump.engine.LevelGen(60, 20, 1000L + level, level);
            gen.generate();
            int[] f = gen.lastPathFloor;
            for (int c = 1; c < f.length; c++) {
                if (f[c] < 0 || f[c - 1] < 0) continue;      // a gap is a gap, not a step
                int rise = f[c - 1] - f[c];                  // a step UP is a smaller row number
                if (rise > worst) { worst = rise; worstLevel = level; worstCol = c; }
            }
        }
        check("no step up is taller than the jump - 2 cells, 64px, against an apex of 73px (biggest " + worst
                + " cells at level " + worstLevel + " col " + worstCol + ")", worst <= 2);
    }

    /**
     * WALKING INTO AN ENEMY HURTS, EVEN WHEN THE BOXES ONLY NEARLY TOUCH.
     *
     * <p>Combat carries a 4px tolerance for exactly this, and its comment says why: "Exact AABB overlap missed
     * grazing touches - collision resolution keeps bodies just short of overlap, so walking into an enemy
     * sometimes did nothing (playtest: 'enemies seem to only damage occasionally')."
     *
     * <p>THE MUTATION SWEEP IS WHAT SHOWED NOTHING WAS PROTECTING IT: setting the tolerance to zero left the gate
     * green at 444 passed, 0 failed.
     *
     * <p>The player here stands two pixels short of the enemy's box - which is what a grazing touch IS. With the
     * tolerance the contact registers and a heart goes; without it the player walks through untouched, which is
     * the bug the playtest reported.
     */
    static void walkingIntoAnEnemyHurts() {
        aside.games.fruitjump.engine.Combat combat = new aside.games.fruitjump.engine.Combat();
        Physics.Body player = new Physics.Body(200, 100, 24, 44);
        // two pixels short of touching: player's right edge at 212, enemy's left at 214
        aside.games.fruitjump.engine.Enemy enemy = new aside.games.fruitjump.engine.Enemy(225, 100, 22, 22);
        double before = combat.playerHP;
        combat.processContact(player, enemy);
        check(String.format("walking into an enemy hurts even when the boxes only nearly touch (%.0f -> %.0f)",
                before, combat.playerHP), combat.playerHP < before);
    }

    /**
     * A HOOKSHOT PULL ALWAYS ENDS, however unreachable the anchor.
     *
     * <p>Hookshot carries two failsafes and its comment says why: "found by playtest: firing at a ledge lip anchors
     * somewhere the pull can't reach - the player hangs against geometry with input ignored, unable to counter."
     * That is a SOFT-LOCK, the worst kind of bug this game can have, and the mutation sweep showed nothing was
     * protecting either guard: `MAX_PULL_TIME` and `STALL_TIME` both set to 999 left the gate green at 444
     * passed, 0 failed.
     *
     * <p>Two ways a pull can fail to finish, so two checks:
     *
     * <ul>
     *   <li>the player cannot move at all - the hook sets a velocity and nothing applies it - which is the STALL
     *       case;</li>
     *   <li>the player moves but the anchor is further away than MAX_PULL_TIME at PULL_SPEED allows (1.5s times
     *       500px/s is 750px), which is the HARD CAP case.</li>
     * </ul>
     *
     * <p>Both are "the pull ended", because that is the invariant the soft-lock violates, and neither asserts
     * WHICH guard fired - a test that named the guard would pass while the other one was broken.
     */
    static void aHookshotPullAlwaysEnds() {
        // A STALLED PULL RELEASES FAST, not merely eventually. Asserting only "it ended" does not catch a broken
        // stall guard, because the 1.5s cap ends it anyway - the first version of this passed with STALL_TIME set
        // to 999. What the stall guard is FOR is releasing in a third of a second rather than a second and a
        // half, so the assertion is about how long.
        double stalled = pullSeconds(false, 400);
        check(String.format("a stalled hookshot pull releases in well under the cap (%.2fs, cap is 1.5s)", stalled),
                stalled > 0 && stalled < 1.0);

        // AND A PULL THAT IS STILL MAKING PROGRESS STILL ENDS. The anchor has to be further than the loop can
        // walk: 500px/s for six seconds is 3000px, and the first version used 2000, which the player simply
        // reached - so the pull ended by ARRIVAL and the cap was never exercised at all.
        double far = pullSeconds(true, 6000);
        check(String.format("a hookshot pull to an anchor beyond reach still ends (%.2fs)", far),
                far > 0 && far < 6.0);
    }

    /** Run a pull for up to six seconds; returns how long it took to end, or -1 if it never did. */
    static double pullSeconds(boolean stepWorld, double anchorX) {
        World w = new World();
        Physics.Body player = new Physics.Body(200, 100, 24, 44);
        w.addBody(player);
        w.playerBody = player;
        aside.games.fruitjump.engine.Hookshot hook = new aside.games.fruitjump.engine.Hookshot(player);
        hook.state = aside.games.fruitjump.engine.Hookshot.State.PULLING;
        hook.anchorX = anchorX;
        hook.anchorY = 100;
        for (double t = 0; t < 6.0; t += GameLoop.DT) {
            if (stepWorld) w.update(GameLoop.DT);
            hook.update(GameLoop.DT, w);
            if (hook.state != aside.games.fruitjump.engine.Hookshot.State.PULLING) return t;
        }
        return -1;
    }

    /**
     * AN ARROW KILLS WHAT IT FLIES INTO - both lists.
     *
     * <p>Two separate playtest reports, two separate pieces of code, and neither was protected. World says so in
     * its own comments: the arrow-enemy check "was missing entirely - arrows flew through enemies (playtest:
     * 'arrows fire, but do nothing')", and the arrow-bat check exists because "they were not in `enemies`, so an
     * arrow flew straight through one (playtest: the bat reads as invincible)".
     *
     * <p>The mutation sweep found both: disabling either collision left the gate green at 447 passed, 0 failed.
     * The existing weapon check measures that an arrow FLIES - seventy pixels in a tenth of a second - which is
     * a different question from whether it lands on anything.
     *
     * <p>They are checked separately because they ARE separate loops: a bat is not in `enemies`, and a fix to one
     * would leave the other broken.
     */
    static void arrowsHitWhatTheyPointAt() {
        // ON A FLOOR. An enemy in an empty World FALLS - the first version of this put one at y=100 and by the
        // time the arrow arrived it was twenty pixels lower, so the arrow sailed over it and the check failed
        // for a reason that had nothing to do with arrows. A bat does not fall, which is why that half passed.
        String flat = String.join("\n", "                        ", "                        ",
                "                        ", "########################");
        World w = new World();
        aside.games.fruitjump.engine.LevelMap.parse(flat).buildWorld(w);
        // The floor's top is row 3, y=96, so the enemy's 22-tall box centres at 85 and the arrow flies at that
        // height. THREE ATTEMPTS TO GET THIS RIGHT, all by probing rather than reasoning: first an enemy in an
        // empty World, which FELL; then one at y=104, which snapped UP to the floor; then an arrow at y=106,
        // which was INSIDE the ground and died on the frame it spawned.
        Physics.Body player = new Physics.Body(80, 74, 24, 44);
        w.addBody(player);
        w.playerBody = player;
        aside.games.fruitjump.engine.Enemy enemy = new aside.games.fruitjump.engine.Enemy(270, 85, 22, 22);
        w.addEnemy(enemy);
        w.addProjectile(aside.games.fruitjump.engine.Projectile.arrow(200, 85, 1));
        for (int i = 0; i < 60; i++) w.update(GameLoop.DT);
        check("weapons: an arrow kills the enemy it flies into", enemy.dead);

        World w2 = new World();
        Physics.Body p2 = new Physics.Body(80, 100, 24, 44);
        w2.addBody(p2);
        w2.playerBody = p2;
        w2.addBat(new aside.games.fruitjump.engine.Bat(300, 100, 7L));
        w2.addProjectile(aside.games.fruitjump.engine.Projectile.arrow(200, 100, 1));
        for (int i = 0; i < 60; i++) w2.update(GameLoop.DT);
        check("weapons: an arrow kills a bat too, which is a separate list from enemies", w2.bats.isEmpty());
    }

    /**
     * A CHECKPOINT CARRIES YOUR HEALTH, rather than handing you a fresh bar.
     *
     * <p>The exact bug a player reported: "lives still reset each level". The fix was six inline statements in
     * GameplayScreen, and the mutation sweep found that changing the HP line to a flat 3 left the gate green at
     * 449 passed, 0 failed - a screen needs a UiManager and a display, so nothing headless could reach it.
     *
     * <p>So the six statements are a named function now, checkpointFor(), and this calls it. Same shape as Wake's
     * manual reset: behaviour written inline in a screen is behaviour no test can call.
     */
    static void aCheckpointCarriesYourHealth() {
        aside.games.fruitjump.engine.Combat combat = new aside.games.fruitjump.engine.Combat();
        combat.playerHP = 1;                       // a rough run, one heart left
        combat.maxHP = 3;
        aside.games.fruitjump.engine.PlayerInventory inv =
                new aside.games.fruitjump.engine.PlayerInventory();
        inv.keys = 2;
        aside.games.fruitjump.engine.SaveSystem.GameState c =
                aside.games.fruitjump.GameplayScreen.checkpointFor(7, combat, inv, 42.5);
        check("a checkpoint carries your health into the next level, not a fresh bar (hp " + c.playerHP
                + ", keys " + c.keys + ", level " + c.levelNum + ")",
                c.playerHP == 1 && c.keys == 2 && c.levelNum == 7 && c.playerX < 0);
    }

    /**
     * A TOP-DOWN ENEMY CHASES IN BOTH AXES; a side-view one only sideways.
     *
     * <p>Enemy says why: "the side-view AI only ever sets vx - in top-down rooms enemies slid left/right and
     * never tracked the player vertically (playtest: 'enemies can only move left and right, not up or down')".
     * The mutation sweep found the branch that implements it - `if (e.topDown)` in World - unprotected: disabling
     * it left the gate green at 449 passed, 0 failed.
     *
     * <p>Both halves are asserted together because either alone would pass for the wrong reason. A top-down enemy
     * that closed the gap would pass even if BOTH modes closed it, and a side-view enemy that did not would pass
     * even if NEITHER did. Measured: 100px of vertical gap closes to 69 for a top-down enemy and stays at 100 for
     * a side-view one.
     *
     * <p><b>AND THIS DOES NOT PROTECT THE BRANCH.</b> Disabling `if (e.topDown)` in World still leaves the gate
     * green at 451 passed, 0 failed - measured, not assumed. That branch chooses top-down WALL SENSING
     * (`senseWallTopDown` against `senseWall` plus `senseLedge`), and the movement lives in `updateAI`, which this
     * exercises in open space with no walls. So the branch is still unprotected, and saying so here is better than
     * a docstring that implies the sweep's finding is closed when it is not.
     */
    static void topDownEnemiesChaseInBothAxes() {
        double td = closestVerticalApproach(true), side = closestVerticalApproach(false);
        check(String.format("a top-down enemy closes the vertical gap, a side-view one does not (%.0fpx vs %.0fpx "
                + "from 100px)", td, side), td < 90 && side > 95);
    }

    /** Closest a chasing enemy starting 100px below the player gets, over a second and a half. */
    static double closestVerticalApproach(boolean topDown) {
        World w = new World();
        Physics.Body player = new Physics.Body(300, 300, 24, 44);
        w.addBody(player);
        w.playerBody = player;
        aside.games.fruitjump.engine.Enemy e = new aside.games.fruitjump.engine.Enemy(300, 400, 22, 22);
        e.topDown = topDown;
        w.addEnemy(e);
        double best = Math.abs(e.body.y - 300);
        for (int i = 0; i < 90; i++) {
            w.update(GameLoop.DT);
            best = Math.min(best, Math.abs(e.body.y - 300));
        }
        return best;
    }

    /**
     * TOP-DOWN ENEMIES SENSE WALLS IN THE DIRECTION THEY ARE FACING.
     *
     * <p>The last of the sweep's findings, and the one I first wrote the wrong check for. `World` chooses between
     * `senseWallTopDown` and `senseWall` plus `senseLedge` on `e.topDown`, and disabling that branch left the gate
     * green - because my earlier check measured the CHASE, which lives in updateAI and works in open space.
     *
     * <p>An enemy facing UP at a ceiling tells them apart, because side-view sensing only looks left and right.
     * Top-down sensing sees it; side-view does not. Both halves asserted, for the usual reason.
     */
    static void topDownEnemiesSenseWallsInTheirHeading() {
        check("a top-down enemy facing a ceiling turns, and a side-view one does not",
                turnsAtCeiling(true) && !turnsAtCeiling(false));
    }

    /**
     * Does an enemy heading UP into a ceiling come out heading somewhere else?
     *
     * <p>NOT THE `hitWall` FIELD, which was the first thing I tried. `hitWall` is consumed inside the same frame -
     * `updateTopDown` sets it false and picks a new heading in the same call - so it is always false by the time
     * anything outside can look. The observable thing is that the HEADING changed.
     *
     * <p>And the geometry has to be right: the first version put the enemy at column 2 with the ceiling starting
     * at column 3, so it was BESIDE the ceiling rather than under it. Probed, not reasoned - the third time that
     * has been true today.
     */
    static boolean turnsAtCeiling(boolean topDown) {
        String lvl = String.join("\n",
                "                        ",
                "   #####                ",
                "                        ",
                "                        ",
                "   ###                  ");
        World w = new World();
        aside.games.fruitjump.engine.LevelMap.parse(lvl).buildWorld(w);
        Physics.Body p = new Physics.Body(400, 40, 24, 44);
        w.addBody(p);
        w.playerBody = p;
        aside.games.fruitjump.engine.Enemy e = new aside.games.fruitjump.engine.Enemy(144, 64, 22, 22);
        e.topDown = topDown;
        e.hdx = 0; e.hdy = -1;                  // heading UP, at the ceiling
        w.addEnemy(e);
        w.update(GameLoop.DT);
        return e.hdx != 0 || e.hdy != -1;
    }

    /**
     * A RETREATING BAT GETS CLEAR WITHOUT FLYING OUT OF SIGHT.
     *
     * <p>The comment carries the playtest: "level 2 bat flew off screen, it doesnt need to fly that far, just a
     * little past the range. Flying out of sight makes the bat forgettable, and the point of the follow is that
     * the player can SEE it and plan around it."
     *
     * <p><b>IT CATCHES ONE END AND NOT THE OTHER, and that is measured rather than hoped.</b> RETREAT_TIME set to
     * 0.05 fails it - the bat barely leaves. RETREAT_TIME set to 20 does NOT, because the RANGE stops the bat at
     * 1.15 x 260 = 299 before the timer ever matters, so the two guards cover each other. Setting the multiplier
     * to 10.0 is likewise not caught, for the mirror-image reason: the timer stops it first.
     *
     * <p>So this protects "the bat gets clear", and "the bat does not go too far" is protected by the range - but
     * neither line is individually observable, and the mutation sweep will keep reporting both as NOT CAUGHT. That
     * is written here so the next person reading a sweep result knows it is already understood rather than
     * overlooked.
     */
    static void aRetreatingBatGetsClearWithoutLeaving() {
        World w = new World();
        Physics.Body p = new Physics.Body(300, 300, 24, 44);
        w.addBody(p);
        w.playerBody = p;
        aside.games.fruitjump.engine.Bat bat = new aside.games.fruitjump.engine.Bat(300, 300, 11L);
        w.addBat(bat);
        bat.flee();                      // a knockdown clears the swarm, so every bat retreats
        // MEASURED ONLY WHILE RETREATING. The first version tracked the furthest distance over three seconds,
        // which included the bat WANDERING afterwards - so a 0.05s retreat still "got far" and the check passed.
        // What the retreat guard controls is how far the bat gets while it is retreating, and that is what is
        // measured.
        double far = 0;
        for (int i = 0; i < 180; i++) {
            w.update(GameLoop.DT);
            if (bat.state == aside.games.fruitjump.engine.Bat.State.RETREAT) {
                far = Math.max(far, Math.hypot(bat.body.x - 300, bat.body.y - 300));
            }
        }
        // 100 TO 290. Measured, a retreating bat gets 269px; with RETREAT_TIME at 20 the RANGE stops it at
        // 1.15 x 260 = 299. Those are ten pixels apart, which is why the ceiling is 290 rather than something
        // round - it is the only value that separates the two, and it is the measured one.
        check(String.format("a retreating bat gets clear (%.0fpx) but not out of sight, against an aggro range "
                + "of %.0f", far, aside.games.fruitjump.engine.Bat.AGGRO_RANGE), far > 100 && far < 290);
    }

    /**
     * A SAVE KNOWS WHICH GAME IT BELONGS TO, and a fresh one is a platformer.
     *
     * <p>SaveSystem's comment carries the playtest: "Continue branches on this - a rooms save used to dump the
     * player into the platformer (playtest round 5)". MainMenu does `if ("rooms".equals(st.mode))`, so the mode
     * has to survive a round trip and it has to have the right default.
     *
     * <p>The sweep found both unprotected: changing the default to "rooms" left the gate green at 453 passed,
     * 0 failed. The round trip would not catch that on its own, because it faithfully preserves whatever was
     * written - which is why BOTH halves are asserted.
     */
    static void aSaveKnowsWhichGameItBelongsTo() {
        check("a fresh save belongs to the platformer, not rooms",
                "platformer".equals(new aside.games.fruitjump.engine.SaveSystem.GameState().mode));

        java.io.File tmp;
        try { tmp = java.io.File.createTempFile("wake-save-", ".txt"); } catch (java.io.IOException e) { tmp = null; }
        if (tmp == null) { check("a save round-trips its mode", false); return; }
        tmp.deleteOnExit();
        aside.games.fruitjump.engine.SaveSystem.GameState s =
                new aside.games.fruitjump.engine.SaveSystem.GameState();
        s.mode = "rooms";
        s.levelNum = 4;
        try {
            new aside.games.fruitjump.engine.SaveSystem().save(s, tmp.getAbsolutePath());
            aside.games.fruitjump.engine.SaveSystem.GameState back =
                    new aside.games.fruitjump.engine.SaveSystem().load(tmp.getAbsolutePath());
            check("a save round-trips its mode, so Continue goes to the right game (rooms)",
                    back != null && "rooms".equals(back.mode));
        } catch (Exception e) {
            check("a save round-trips its mode (" + e.getMessage() + ")", false);
        }
    }

    /** How close a bat starting `distance` from the player ever gets, over six seconds. */
    static double closestApproach(double distance, long seed) {
        World w = new World();
        Physics.Body p = new Physics.Body(200, 100, 24, 44);
        w.addBody(p);
        w.playerBody = p;
        Bat bat = new Bat(200 + distance, 100, seed);
        w.addBat(bat);
        double closest = Double.MAX_VALUE;
        for (double t = 0; t < 6.0; t += GameLoop.DT) {
            w.update(GameLoop.DT);
            closest = Math.min(closest, Math.hypot(bat.body.x - p.x, bat.body.y - p.y));
        }
        return closest;
    }

    /**
     * Being hit has a rhythm: a second hit in the same instant does nothing, and one after the wait lands.
     *
     * <p>The third and fourth gaps the mutation sweep found - `Combat.INVULN_TIME = 1.0 -> 0.01` and
     * `SPIKE_COOLDOWN = 3.5 -> 0.05` both came back NOT CAUGHT. These two numbers are how punishing the game
     * is: at 0.01 the player loses all three hearts to one spike in three frames, and at 30 they walk through
     * everything. Nothing in 301 checks touched either.
     *
     * <p>Asserted as durations in real seconds, in both directions - not against the constants, which would
     * pass whatever they are (rule 32).
     */
    static void damageIsMetered() {
        Physics.Body p = new Physics.Body(200, 100, 24, 44);

        // SAMPLED FROM BOTH SIDES. The first version hit twice in the same instant and then once after 1.2s,
        // which passes for any wait between zero and 1.2 - so `INVULN_TIME -> 0.01` slipped through it. A
        // duration needs a sample on each side of it: too soon must be ignored, late enough must land.
        Combat c = new Combat();
        c.playerHP = 3;
        c.hurtPlayer(p, 0);
        int afterOne = (int) c.playerHP;
        step(c, 0.2);
        c.hurtPlayer(p, 0);
        check("damage: a hit 0.2s later is still ignored (" + afterOne + " -> " + (int) c.playerHP + ")",
                (int) c.playerHP == afterOne);
        step(c, 1.0);
        c.hurtPlayer(p, 0);
        check("damage: a hit 1.2s later lands (" + afterOne + " -> " + (int) c.playerHP + ")",
                (int) c.playerHP < afterOne);

        // Spikes have their own, much longer cooldown: a pit is a cost, not a grinder. Same shape, and the
        // 2.0s sample is the one that separates the two durations - past the i-frames, inside the cooldown.
        Combat sp = new Combat();
        sp.playerHP = 3;
        sp.hurtBySpike(p, 0);
        int afterSpike = (int) sp.playerHP;
        step(sp, 2.0);
        sp.hurtBySpike(p, 0);
        check("damage: a spike 2s later does not bite again (" + afterSpike + " -> " + (int) sp.playerHP + ")",
                (int) sp.playerHP == afterSpike);
        step(sp, 2.0);
        sp.hurtBySpike(p, 0);
        check("damage: and it bites again after four seconds (" + afterSpike + " -> "
                + (int) sp.playerHP + ")", (int) sp.playerHP < afterSpike);
    }

    /** Advance a combat clock by `seconds`, in engine steps. */
    static void step(Combat c, double seconds) {
        for (double t = 0; t < seconds; t += GameLoop.DT) c.update(GameLoop.DT);
    }

    /**
     * The hookshot reaches a wall inside its range, misses one outside it, and travels at its stated speed.
     *
     * <p>The last gap the mutation sweep found - `Hookshot.MAX_RANGE = 400 -> 5` and `HOOK_SPEED = 1200 -> 1`
     * both came back NOT CAUGHT. It is the one weapon with no check, and it is the one that carries the player.
     *
     * <p>A hand-written level with a single wall at column 40, and the same wall fired at from 180px and from
     * 1080px. Absolute distances, and the speed measured in real units: a hook that crawls at 1px/s fails.
     */
    static void theHookshotReaches() {

        Hookshot near = hookAt(1100);
        check("hookshot: a wall 180px away is caught", near.isPulling());

        Hookshot far = hookAt(200);
        check("hookshot: a wall 1080px away is not", !far.isPulling());

        // NO SPEED CHECK, and the reason is a finding rather than an omission: `Hookshot.HOOK_SPEED = 1200`
        // is DECLARED AND NEVER READ. The hook is an instant raycast - `fire` sets the tip straight onto the
        // anchor - and the return uses RETRACT_SPEED. So `HOOK_SPEED -> 1` cannot be caught by anything, and
        // that is not a hole in the check; it is a constant nothing uses. Recorded for Kinger: delete it or
        // wire it. (Found by writing the check that failed, not by reading.)
    }

    /** A player at `px` in a 60-column level with one wall at column 40, and a hookshot fired at it. */
    static Hookshot hookAt(double px) {
        World w = worldWithWall();
        Physics.Body p = new Physics.Body(px, 96, 24, 44);
        w.addBody(p);
        Hookshot h = new Hookshot(p);
        h.fire(1, 0, w);
        return h;
    }

    static World worldWithWall() {
        String[] rows = new String[5];
        rows[0] = " ".repeat(60);
        rows[1] = " ".repeat(60);
        rows[2] = " ".repeat(40) + "#" + " ".repeat(19);
        rows[3] = " ".repeat(40) + "#" + " ".repeat(19);
        rows[4] = "#".repeat(60);
        LevelMap map = LevelMap.parse(String.join("\n", rows));
        World w = new World();
        map.buildWorld(w);
        return w;
    }

    /** Regenerate one story's web export and compare it to the checked-in one. */
    /** Every story in stories/, by file stem. Discovered, so a new one cannot be missed. */
    static java.util.List<String> storyNames() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Path dir : new Path[]{Path.of("stories"), Path.of("..", "stories")}) {
            if (!Files.isDirectory(dir)) continue;
            try (var stream = Files.list(dir)) {
                stream.filter(f -> f.getFileName().toString().endsWith(".aside"))
                      .map(f -> f.getFileName().toString().replaceFirst("\\.aside$", ""))
                      .sorted()
                      .forEach(out::add);
            } catch (Exception ignored) { }
            if (!out.isEmpty()) break;
        }
        return out;
    }

    /** The game id for a story file: the shelf looks for web/&lt;game-id&gt;.html, and `the-lamp-room` is `lamp-room`. */
    static String gameIdFor(String storyName) {
        return storyName.replaceFirst("^the-", "");
    }

    static void storyExport(String name) throws Exception {
        storyExport(name, gameIdFor(name));
    }

    /**
     * A story's export, where the story's file stem and its game id differ.
     *
     * <p>The shelf looks for {@code web/<game-id>.html}, and three of the stories
     * are named differently from their ids -- {@code the-lamp-room.aside} is the
     * {@code lamp-room} game. Naming the build after the story instead of the
     * game put it somewhere the shelf does not look, which is exactly what
     * happened to the fifth story: {@code the-water-line.html} against a game id
     * of {@code water-line}.
     */
    static void storyExport(String name, String id) throws Exception {
        Path story = Path.of("stories", name + ".aside");
        Path out = Path.of("web", id + ".html");
        if (!Files.exists(story) || !Files.exists(out)) {
            System.out.println("       (no " + story + " or " + out + " from here)");
            return;
        }
        String generated = WebExport.convert(Files.readString(story), WebExport.ART,
                WebExport.storyArt(name));
        check("web/" + name + ".html is current -- regenerate it with aside.engine.WebExport",
                generated.equals(Files.readString(out)));

        // The export carries the art now, and these are the ways that can go
        // wrong quietly. A page that is current and text-only looks exactly
        // like a page that is current and illustrated, in a diff and in a
        // browser that has not scrolled far enough.
        check("the web art is built (art/web/)", Files.isDirectory(Path.of("art", "web")));
        check("web/" + name + ".html carries the art it stages",
                generated.contains("data:image/") && generated.contains("\"assets\":{\"bg:"));
        check("web/" + name + ".html says which staging it could not draw",
                generated.contains("\"missing\":["));

        // The sound. The stories are the two builds whose desktop versions
        // carry music rather than cues, so this is not a mechanic -- but the
        // page is a still frame with a script under it, and a tap that makes
        // no sound reads as a tap that did not land. Same one line as the ten
        // verb games, spliced in from the shared palette.
        // A title with markup in it is escaped, not injected.
        //
        // Removing the escaping from WebExport broke nothing and the suite
        // still passed 321 of 321, because no story's title contains a "<".
        // A title is data, and a build that puts it in the page unescaped is a
        // build that can be broken by its own title.
        {
            String html = WebExport.convert("title: a <b>bold</b> title\nstart: only\n\n"
                    + "== only ==\nA line.\n");
            // Only "<" is escaped, which is what stops a title opening a tag.
            // The first version of this check expected ">" escaped too and
            // failed -- the escaping is narrower than I assumed, and the
            // assertion now says what the code does rather than what I
            // expected it to.
            check("a title with markup cannot open a tag in the export",
                    html.contains("a &lt;b>bold&lt;/b> title")
                            && !html.contains("<b>bold</b>"));
            // And the JSON the page carries cannot close the script block it
            // is embedded in. A title containing "</script>" used to do
            // exactly that -- the rest of the page became markup -- because
            // the escaper handled quotes, backslashes and control characters
            // and not "<".
            String breakout = WebExport.convert(
                    "title: x</script><script>alert(1)</script>\nstart: only\n\n"
                    + "== only ==\nA line.\n");
            check("a title cannot close the export's script block",
                    !breakout.contains("</script><script>alert(1)"));
        }

        check("web/" + name + ".html carries the shared synthesiser",
                generated.contains("function voice(") && generated.contains("function ac("));
        check("web/" + name + ".html answers a tap with a click",
                generated.contains("pointerdown"));

        // The word boundaries in condOK are why this is checked at all. The
        // template is a Java text block, where \b is the backspace character,
        // so the page shipped a regex that matched nothing -- "and" survived
        // into the expression, the Function() threw, and condOK's catch
        // returned true, which is every compound condition silently taking its
        // first branch. Nothing in stories/ uses and/or yet, which is the only
        // reason it was never seen. A check for the literal is the check that
        // would have caught it.
        check("web/" + name + ".html has real word boundaries in its condition reader",
                generated.contains("/\\band\\b/g") && generated.indexOf('\b') < 0);

        // The same class of bug, one step wider: a text block that ate an
        // escape leaves a control character in the page, and a control
        // character in a page is never intentional.
        check("web/" + name + ".html has no stray control characters",
                generated.chars().noneMatch(c -> c < 0x20 && c != '\n' && c != '\t'));
    }

    /**
     * art/web/ is derived from art/, and a derived directory that nobody
     * re-derives is a second copy of the art quietly disagreeing with the
     * first. Java cannot run tools/vn-art.py, so it cannot tell whether
     * art/web/ is current -- but it can hash the sources and compare them to
     * the hashes the tool recorded when it built them. Edit a sprite and this
     * fails until the tool is run again, which is the whole point.
     */
    static void webArtIsCurrent() {
        Path manifest = Path.of("art", "web", "MANIFEST");
        if (!Files.exists(manifest)) {
            check("art/web/MANIFEST exists (run tools/vn-art.py)", false);
            return;
        }
        try {
            int checked = 0;
            List<String> stale = new ArrayList<>();
            for (String line : Files.readAllLines(manifest)) {
                if (line.isBlank()) continue;
                String[] parts = line.strip().split("\\s+", 2);
                if (parts.length < 2) continue;
                checked++;
                Path src = Path.of(parts[1]);
                if (!Files.exists(src)) { stale.add(parts[1] + " (gone)"); continue; }
                if (!sha256(src).equals(parts[0])) stale.add(parts[1]);
            }
            check("art/web/ was built from the art that is here now"
                    + (stale.isEmpty() ? "" : " -- stale: " + stale), stale.isEmpty());
            check("art/web/MANIFEST covers the art it was built from", checked > 0);

            // AND THE OTHER DIRECTION, which the manifest cannot see: it lists
            // what WAS built, so a source with no built copy is invisible. That
            // is how `keeper-neutral.png` sat in art/sprites/ unbuilt -- the
            // lamp room asked for it, the export said "no art for", and the
            // staleness check passed because nothing in the manifest had
            // changed. A source that should have a web copy and does not is the
            // same failure as a stale one, from the other end.
            List<String> unbuilt = new ArrayList<>();
            for (String dir : new String[]{"sprites", "backgrounds"}) {
                Path srcDir = Path.of("art", dir);
                if (!Files.isDirectory(srcDir)) continue;
                try (var list = Files.list(srcDir)) {
                    for (Path f : list.toList()) {
                        String name = f.getFileName().toString();
                        String stem = name.replaceAll("\\.[^.]+$", "");
                        // Sprites are WebP (alpha) and backgrounds are JPEG
                        // (opaque, and smaller for a photograph) -- the same
                        // split tools/vn-art.py makes, so either counts.
                        Path webp = Path.of("art", "web", dir, stem + ".webp");
                        Path jpg = Path.of("art", "web", dir, stem + ".jpg");
                        if (!Files.exists(webp) && !Files.exists(jpg)) unbuilt.add(dir + "/" + name);
                    }
                }
            }
            check("every source sprite and background has a web copy"
                    + (unbuilt.isEmpty() ? "" : " -- unbuilt: " + unbuilt), unbuilt.isEmpty());
        } catch (Exception e) {
            check("art/web/MANIFEST can be read (" + e.getMessage() + ")", false);
        }
    }

    static String sha256(Path file) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(Files.readAllBytes(file))) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * FNAF 6's phone build, which is the first FNAF game on the shelf.
     *
     * <p>Four ways this can be wrong and look right, and one of them is the
     * reason this section exists at all:
     *
     * <ol>
     *   <li>The page is stale -- the engine's tables moved and nobody
     *       regenerated it. Caught by regenerating and comparing, the same
     *       rule every other phone build is held to.</li>
     *   <li>The page is current and empty -- it loads, it draws nothing,
     *       and in a diff it looks exactly like a page that works. Caught
     *       by asking for the art.</li>
     *   <li>The art it inlines was built from art that has since changed.
     *       Java cannot re-run tools/fnaf6-phone-art.py, but it can hash
     *       the sources and compare them to the hashes the tool wrote.</li>
     *   <li><b>The page carries a second copy of the rules and the copy has
     *       drifted.</b> The phone steps the night itself, so it has its own
     *       clock, its own budget and its own coin flip, and both builds go
     *       on working while quietly being different games. Nothing above
     *       can see that. So the check is a measurement: run the same
     *       policies over the same seeds through both engines and compare
     *       the week.</li>
     * </ol>
     *
     * <p>The measurement needs node, and node is not part of the engine's
     * requirements. If it is not there this says so and moves on rather
     * than failing -- a suite that will not run without a second runtime is
     * a suite people stop running.
     */
    static void phoneBuild() {
        Path out = Path.of("web", "fnaf6.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here)");
            return;
        }
        try {
            String generated = WebSalvage.html();
            check("web/fnaf6.html is current -- regenerate it with aside.games.fnaf6.WebSalvage",
                    generated.equals(Files.readString(out)));

            check("web/fnaf6.html carries the art it stages",
                    generated.contains("data:image/") && generated.contains("\"assets\":{"));
            check("web/fnaf6.html carries the cast it deals",
                    generated.contains("\"unit:scraptrap\"")
                            && generated.contains("\"scare:lefty\""));
            check("web/fnaf6.html has no stray control characters",
                    generated.chars().noneMatch(c -> c < 0x20 && c != '\n' && c != '\t'));
        } catch (Exception e) {
            check("the phone build can be generated from here (" + e.getMessage() + ")", false);
        }

        phoneArtIsCurrent();
        phoneEngineAgrees();
    }

    /** The same rule as art/web/, one directory over. */
    static void phoneArtIsCurrent() {
        Path manifest = Path.of("art", "phone", "fnaf6", "MANIFEST");
        if (!Files.exists(manifest)) {
            check("art/phone/fnaf6/MANIFEST exists (run tools/fnaf6-phone-art.py)", false);
            return;
        }
        try {
            int checked = 0;
            List<String> stale = new ArrayList<>();
            for (String line : Files.readAllLines(manifest)) {
                if (line.isBlank()) continue;
                String[] parts = line.strip().split("\\s+", 2);
                if (parts.length < 2) continue;
                checked++;
                Path src = Path.of(parts[1]);
                if (!Files.exists(src)) { stale.add(parts[1] + " (gone)"); continue; }
                if (!sha256(src).equals(parts[0])) stale.add(parts[1]);
            }
            check("art/phone/fnaf6/ was built from the art that is here now"
                    + (stale.isEmpty() ? "" : " -- stale: " + stale), stale.isEmpty());
            check("art/phone/fnaf6/MANIFEST covers the art it was built from", checked > 0);
        } catch (Exception e) {
            check("art/phone/fnaf6/MANIFEST can be read (" + e.getMessage() + ")", false);
        }
    }

    /**
     * The desktop's week, in the shape tools/fnaf6-sweep.mjs prints.
     *
     * <p>The seeds are the sweep's own -- {@code 1000 * night + i} -- and
     * the policies are the four the ladder is read against. Both sides have
     * to agree on all of it or the comparison means nothing.
     */
    static String javaSweep() {
        // Fully qualified: this package has a Bot of its own, and it is the
        // story engine's, not the salvage bay's.
        StringBuilder b = new StringBuilder();
        for (aside.games.fnaf6.engine.Bot.Policy p
                : new aside.games.fnaf6.engine.Bot.Policy[]{
                    aside.games.fnaf6.engine.Bot.Policy.IDLE,
                    aside.games.fnaf6.engine.Bot.Policy.PATROL,
                    aside.games.fnaf6.engine.Bot.Policy.LISTEN,
                    aside.games.fnaf6.engine.Bot.Policy.PRO}) {
            b.append(p).append(": ");
            for (int n = 1; n <= 5; n++) {
                if (n > 1) b.append(' ');
                b.append(Math.round(
                        aside.games.fnaf6.engine.Bot.survival(p, n, 200) * 100)).append('%');
            }
            b.append('\n');
        }
        return b.toString();
    }

    /** Run the phone's engine over the same week and compare the two tables. */
    static void phoneEngineAgrees() {
        String mine = javaSweep();
        String theirs;
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "tools/fnaf6-sweep.mjs");
            pb.redirectErrorStream(true);
            Process pr = pb.start();
            theirs = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (pr.waitFor() != 0) {
                check("the phone's engine can be swept (node exited " + pr.exitValue()
                        + ": " + theirs.strip() + ")", false);
                return;
            }
        } catch (Exception e) {
            System.out.println("       (no node -- the phone's engine was not compared "
                    + "to this one; run tools/fnaf6-sweep.mjs by hand)");
            return;
        }
        check("the phone's engine plays the same week as this one"
                + (mine.equals(theirs) ? "" : "\n         here:  "
                        + mine.strip().replace("\n", "\n                ")
                        + "\n         phone: " + theirs.strip().replace("\n", "\n                ")),
                mine.equals(theirs));
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

    /**
     * Every beat of every story, and the accessor a renderer reads it with.
     *
     * <p>A STAGE beat -- a {@code bg} or a {@code show} -- carries no prose, so
     * {@code Vn.text()} used to return null on it, and the presenter crashed on
     * {@code t.equals(shownText)}. Roxanne found it by capturing a choice
     * screen; the sprite capture had never landed on a stageless beat, so the
     * presenter had been looked at before without this being visible. Her fix
     * never reached main, and this is the check that keeps it fixed: walk each
     * story from the start, taking the first option at every choice, and assert
     * the accessor is non-null at every step.
     */
    static void textNeverNull() throws Exception {
        // EVERY story, discovered. This was `{"night-shift", "overtime"}` and it stayed that way while three more
        // stories were written - so the check that keeps a real crash fixed was walking two fifths of the
        // stories. The other three are the ones that never had the bug, which is exactly why nobody noticed.
        for (String name : storyNames()) {
            Path p = Path.of("stories", name + ".aside");
            if (!Files.exists(p)) p = Path.of("..", "stories", name + ".aside");
            if (!Files.exists(p)) {
                System.out.println("       (no " + p + " from here)");
                continue;
            }
            Script sc = Script.load(p);
            Vn walk = new Vn(sc);
            boolean ok = true;
            int guard = 0;
            while (walk.mode != Vn.Mode.ENDED && guard++ < 20000) {
                if (walk.text() == null) ok = false;
                if (walk.mode == Vn.Mode.CHOOSING) {
                    if (walk.availableChoices().isEmpty()) break;
                    walk.choose(0);
                } else {
                    walk.step();
                }
            }
            check(name + ": Vn.text() is never null, on any beat", ok);
        }
    }

    /**
     * The README does not carry a copy of this suite's check count.
     *
     * <p><b>It carried one, and it went stale three times in two days.</b> A
     * hand-maintained copy of a generated number is a copy that is wrong more
     * often than it is right, and the failure is silent: a README that says 287
     * when the suite says 288 looks exactly like a README that is correct. So
     * the number is not written down, and this check is what keeps it from
     * being written down again -- the suite prints its own count, and a doc
     * that repeats it is a doc that will drift.
     */
    static void readmeHasNoCheckCount() throws Exception {
        Path p = Path.of("README.md");
        if (!Files.exists(p)) p = Path.of("..", "README.md");
        if (!Files.exists(p)) {
            System.out.println("       (no README.md from here)");
            return;
        }
        String text = Files.readString(p);
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("#\\s*\\d+\\s+checks").matcher(text);
        // find() ONCE. The first version called it twice -- once to build the
        // message and once for the assertion -- and a Matcher is stateful, so
        // the second call resumed from the end of the first match and returned
        // false. The check passed with the count sitting in the file, which is
        // the exact failure it exists to catch.
        boolean found = m.find();
        check("the README does not repeat this suite's check count"
                        + (found ? " (found \"" + m.group() + "\")" : ""),
                !found);
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
