package aside.game;

import aside.games.fnaf.FnafGame;
import aside.games.overtime.OvertimeGame;

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