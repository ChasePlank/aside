package aside.game;

import aside.games.bearings.BearingsGame;
import aside.games.corroboration.CorroborationGame;
import aside.games.drift.DriftGame;
import aside.games.attribution.AttributionGame;
import aside.games.fnaf.FnafGame;
import aside.games.fnaf2.Fnaf2Game;
import aside.games.fnaf3.Fnaf3Game;
import aside.games.fnaf4.Fnaf4Game;
import aside.games.fnaf5.Fnaf5Game;
import aside.games.fruitjump.FruitJumpGame;
import aside.games.handoff.HandoffGame;
import aside.games.interval.IntervalGame;
import aside.games.inventory.InventoryGame;
import aside.games.ledger.LedgerGame;
import aside.games.lesson.LessonGame;
import aside.games.outside.OutsideGame;
import aside.games.omission.OmissionGame;
import aside.games.overtime.OvertimeGame;
import aside.games.promise.PromiseGame;
import aside.games.redaction.RedactionGame;
import aside.games.relay.RelayGame;
import aside.games.residue.ResidueGame;
import aside.games.testimony.TestimonyGame;
import aside.games.vigil.VigilGame;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The list of games the engine can start, and shared per-game paths. */
public final class Games {

    public static List<Game> all() {
        List<Game> g = new ArrayList<>();
        g.add(new OvertimeGame());
        g.add(new FnafGame());
        g.add(new Fnaf2Game());
        g.add(new Fnaf3Game());
        g.add(new Fnaf4Game());
        g.add(new Fnaf5Game());
        g.add(new FruitJumpGame());
        g.add(new ResidueGame());
        g.add(new LedgerGame());
        g.add(new TestimonyGame());
        g.add(new HandoffGame());
        g.add(new OutsideGame());
        g.add(new BearingsGame());
        g.add(new InventoryGame());
        g.add(new VigilGame());
        g.add(new CorroborationGame());
        g.add(new AttributionGame());
        g.add(new RedactionGame());
        g.add(new DriftGame());
        g.add(new RelayGame());
        g.add(new IntervalGame());
        g.add(new LessonGame());
        g.add(new PromiseGame());
        g.add(new OmissionGame());
        return g;
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
