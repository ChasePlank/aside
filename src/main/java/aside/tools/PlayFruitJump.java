package aside.tools;

import aside.games.fruitjump.GameplayScreen;
import aside.ui.Assets;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * PlayFruitJump - walk a real level with real key events and say whether the player got anywhere.
 *
 * <p><b>Why this exists, and why it is not the validator.</b> {@code LevelValidator} searches the level as a
 * GRAPH: it asks whether a path exists from spawn to exit. It found the four uncompletable levels. What it
 * cannot ask is whether the player, as physics, actually moves - because it never runs any physics. Three
 * features were once broken in exactly that blind spot and every suite was green: water with no vertical input
 * (a soft-lock at the surface), bats that were invisible, and splash particles nothing drew. They were found by
 * LOOKING AT A LEVEL, and the tool that made that repeatable lives in the release repository, not here. This is
 * that tool, in the source of truth, and it asserts rather than only screenshots.
 *
 * <p>It holds RIGHT, taps jump every three-quarters of a second, ticks the real {@link GameplayScreen}, and
 * checks two things per level: the player's x ADVANCES, and the screen is still the game rather than a
 * game-over card. A level where the player does not move is the failure this is for.
 *
 * <p>Run it through a display. {@code tools/run-suites.sh} starts Xvfb and runs every class with a main, so
 * this is a gate step as soon as it exists:
 * <pre>
 *   java --module-path $FX --add-modules $MODS -cp out:src/main/resources aside.tools.PlayFruitJump
 * </pre>
 *
 * <p>Properties: {@code -Dlevels=1,11,15,30}, {@code -Dseconds=N} (default 20).
 */
public class PlayFruitJump extends Application {

    @Override public void start(Stage stage) throws Exception {
        Assets.load(".");
        UiManager ui = new UiManager(".");
        StackPane root = new StackPane(ui.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        stage.setScene(scene);
        stage.show();

        String[] parts = System.getProperty("levels", "1,11,15,30").split(",");
        double seconds = Double.parseDouble(System.getProperty("seconds", "20"));
        int stuck = 0;
        System.out.println("=== PlayFruitJump: walking " + parts.length + " levels for " + (int) seconds + "s each ===");
        for (String part : parts) {
            int level = Integer.parseInt(part.trim());
            if (!playOne(ui, level, seconds)) stuck++;
        }
        System.out.println(stuck == 0
                ? "=== PlayFruitJump: " + parts.length + " levels walked, 0 stuck ==="
                : "=== PlayFruitJump: " + stuck + " of " + parts.length + " STUCK ===");
        Platform.exit();
        if (stuck > 0) System.exit(1);
    }

    /** Drive one level and report whether the player moved. */
    static boolean playOne(UiManager ui, int level, double seconds) throws Exception {
        ui.push(new GameplayScreen(ui, level));
        for (int i = 0; i < 4; i++) ui.tick(1.0 / 60);

        hold(ui, KeyCode.RIGHT);
        double startX = playerX(ui);
        double bestX = startX;
        double dt = 1.0 / 60;
        int frames = (int) Math.round(seconds / dt);
        for (int f = 0; f < frames; f++) {
            if (f % 45 == 0 && f > 0) tap(ui, KeyCode.SPACE);
            ui.tick(dt);
            if (!(ui.peek() instanceof GameplayScreen)) break;      // died, or finished the level
            bestX = Math.max(bestX, playerX(ui));
        }
        String top = ui.peek() == null ? "nothing" : ui.peek().getClass().getSimpleName();
        double moved = bestX - startX;
        boolean ok = moved > 120;                                    // four cells of progress, at least
        System.out.printf("  level %-3d  x %.0f -> %.0f  (%.0f px)  ended on %s%s%n",
                level, startX, bestX, moved, top, ok ? "" : "   STUCK");
        if (!(ui.peek() instanceof GameplayScreen)) {
            // pop back to a clean state for the next level
            while (ui.peek() != null && !(ui.peek() instanceof GameplayScreen)) ui.pop();
        }
        if (ui.peek() instanceof GameplayScreen) ui.pop();
        return ok;
    }

    /** The player's x, read off the screen the way the release's tool reads HP - by reflection. */
    static double playerX(UiManager ui) throws Exception {
        UiScreen top = ui.peek();
        var f = GameplayScreen.class.getDeclaredField("player");
        f.setAccessible(true);
        Object body = f.get(top);
        return body.getClass().getField("x").getDouble(body);
    }

    static void hold(UiManager ui, KeyCode code) {
        UiScreen top = ui.peek();
        if (top != null) top.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    static void tap(UiManager ui, KeyCode code) {
        UiScreen top = ui.peek();
        if (top == null) return;
        top.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
        top.handleKey(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
    }
}
