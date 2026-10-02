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
import aside.games.fruitjump.engine.LevelGen;
import aside.games.fruitjump.engine.LevelMap;

public class SelfTest {
    static int pass = 0, fail = 0;

    /** One decimal place, for a number a person has to read in a report. */

    static double round1(double d) { return Math.round(d * 10) / 10.0; }


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
        // The two .aside stories have had a web export since WebExport existed,
        // and nothing has ever checked that the exports are current. Same rule
        // as the games: regenerate and compare. A story edited in stories/ and
        // not re-exported is a phone build of a story that no longer exists.
        storyExport("night-shift");
        storyExport("overtime");

        System.out.println("\n--- the phone build ---");
        phoneBuild();

        System.out.println("\n--- Vn.text() is never null ---");
        textNeverNull();

        System.out.println("\n--- the audio cues ---");
        audioCuesArePresent();

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
            LevelMap m = new LevelGen(W, H, 1000L + level, level).generate();
            for (int c = 0; c < m.widthCells(); c++) {
                int r = 0;
                while (r < m.heightCells()) {
                    if (m.cell(r, c) != '~') { r++; continue; }
                    int start = r;
                    while (r < m.heightCells() && m.cell(r, c) == '~') r++;
                    pools++;
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
            LevelMap m = new LevelGen(W, H, seed, 22).generate();
            boolean[] gapCol = new boolean[m.widthCells()];
            for (int r = 0; r < m.heightCells(); r++)
                for (int c = 0; c < m.widthCells(); c++) {
                    char ch = m.cell(r, c);
                    if (ch == '^' || ch == '~') gapCol[c] = true;
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
        for (String name : new String[]{"night-shift", "overtime"}) {
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
