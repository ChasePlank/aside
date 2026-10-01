package aside.game;

import aside.games.bearings.BearingsGame;
import aside.games.drift.DriftGame;
import aside.games.fnaf.FnafGame;
import aside.games.fnaf2.Fnaf2Game;
import aside.games.fnaf3.Fnaf3Game;
import aside.games.fnaf4.Fnaf4Game;
import aside.games.fnaf5.Fnaf5Game;
import aside.games.fnaf6.Fnaf6Game;
import aside.games.fnaf7.Fnaf7Game;
import aside.games.fnaf8.Fnaf8Game;
import aside.games.fruitjump.FruitJumpGame;
import aside.games.handoff.HandoffGame;
import aside.games.ledger.LedgerGame;
import aside.games.lesson.LessonGame;
import aside.games.outside.OutsideGame;
import aside.games.overtime.OvertimeGame;
import aside.games.redaction.RedactionGame;
import aside.games.residue.ResidueGame;
import aside.games.tell.TellGame;
import aside.games.testimony.TestimonyGame;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The list of games the engine can start, the ones it no longer starts, and
 * shared per-game paths.
 *
 * THE LIBRARY IS CURATED, NOT ACCUMULATED. Chase read the blurbs on
 * 2026-09-30 and said the games "seem repetitive and seem to follow the same
 * premise ... each game needs to be unique, not empty copies of something
 * already existing." He was right, and the reason was visible in the blurbs
 * themselves: six of them were "N nights, M things, and a constraint," and
 * four were "Two X." Eighteen games, three mechanics.
 *
 * So the rule this list is now kept by: **one game per mechanic.** A game
 * earns a place here by doing something no other game in the list does, and
 * when a new one is written that does what an old one does, the old one moves
 * to {@link #retired()} rather than the library growing a second copy of an
 * idea it already has. The franchise is the one exception -- FNAF 1 through 8
 * were asked for by name, with "each gets harder than the last," and a sequel
 * is supposed to be the same shape harder.
 *
 * That is a rule about a judgment, so it cannot be checked by a test. What
 * can be checked is that the record is honest, and that is what
 * aside.engine.SelfTest does: every retired game is out of {@link #all()},
 * its sources are under retired-games/, its phone build is out of web/, and
 * its name is in retired-games/README.md. A half-done retirement -- sources
 * moved and the registry line left behind, or the reverse -- is the failure
 * this is shaped to catch.
 */
public final class Games {

    public static List<Game> all() {
        List<Game> g = new ArrayList<>();
        g.add(new OvertimeGame());
        g.add(new FnafGame());
        g.add(new Fnaf2Game());
        g.add(new Fnaf3Game());
        g.add(new Fnaf4Game());
        g.add(new Fnaf5Game());
        g.add(new Fnaf6Game());
        g.add(new Fnaf7Game());
        g.add(new Fnaf8Game());
        g.add(new FruitJumpGame());
        g.add(new ResidueGame());
        g.add(new LedgerGame());
        g.add(new TestimonyGame());
        g.add(new HandoffGame());
        g.add(new OutsideGame());
        g.add(new BearingsGame());
        g.add(new RedactionGame());
        g.add(new DriftGame());
        g.add(new LessonGame());
        g.add(new TellGame());
        return g;
    }

    /**
     * A game that is no longer in the library, and the one line that says why.
     *
     * @param id      the game's id, which is also its directory name under
     *                retired-games/src/main/java/aside/games/
     * @param title   what the library used to call it
     * @param because the mechanic it repeated
     * @param kept    the game in {@link #all()} that already does that
     */
    public record Retired(String id, String title, String because, String kept) {}

    /**
     * The retired games, in the order they left.
     *
     * Every one of these is a second, third, fourth or fifth statement of a
     * mechanic the library already has. None of them is broken and none of
     * them was bad -- each was the best of its own family at the time. They
     * are here because a library where every game is the same game with
     * different nouns reads as churn, and Chase said so.
     *
     * The sources are under retired-games/ and are NOT compiled. Bringing one
     * back is a git mv and a line in {@link #all()}; see
     * retired-games/README.md.
     */
    public static List<Retired> retired() {
        return List.of(
            new Retired("vigil", "Vigil",
                "a fixed run and a small capacity, and choosing what to keep in it",
                "ledger"),
            new Retired("interval", "Interval",
                "a fixed run and a table of what you know, and deciding when to act",
                "ledger"),
            new Retired("promise", "Promise",
                "a budget spent across a fixed number of nights",
                "ledger"),
            new Retired("omission", "Omission",
                "a small number of slots, and choosing what goes in them",
                "ledger"),
            new Retired("corroboration", "Corroboration",
                "two sources that disagree, and deciding what to believe",
                "bearings"),
            new Retired("relay", "Relay",
                "two parties who are not speaking, and deciding what crosses",
                "bearings"),
            new Retired("attribution", "Attribution",
                "a fixed record you edit under a budget, where the edit is the point",
                "redaction"),
            new Retired("inventory", "Inventory",
                "the record you write being the thing that survives",
                "redaction")
        );
    }

    /** The retired game with this id, or null. */
    public static Retired retiredById(String id) {
        for (Retired r : retired()) if (r.id().equals(id)) return r;
        return null;
    }

    public static Game byId(String id) {
        for (Game g : all()) if (g.id().equals(id)) return g;
        return null;
    }

    /**
     * A game's private save directory: saves/<id>/.
     *
     * Each game owns its own folder so two games cannot collide on a
     * filename, and so deleting one game's progress is one directory
     * removal.
     */
    public static Path saveDir(String gameId) {
        Path p = Path.of("saves", gameId);
        try { Files.createDirectories(p); } catch (Exception ignored) { }
        return p;
    }

    /** Does this game have any saved state? */
    public static boolean hasSave(String gameId, String fileName) {
        return new File(saveDir(gameId).toFile(), fileName).exists();
    }

    private Games() {}
}
