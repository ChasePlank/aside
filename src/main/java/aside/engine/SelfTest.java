package aside.engine;

import aside.game.PhoneShelf;
import aside.game.Game;
import aside.game.Games;
import aside.ui.LibraryLayout;

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
public class SelfTest {
    static int pass = 0, fail = 0;

    /** One decimal place, for a number a person has to read in a report. */

    static double round1(double d) { return Math.round(d * 10) / 10.0; }


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
            }
        } catch (Exception e) {
            check("the retired list can be read from here (" + e.getMessage() + ")", false);
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
