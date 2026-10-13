package aside.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;

import aside.games.fruitjump.GameOverScreen;
import aside.games.fruitjump.GameplayScreen;
import aside.games.fruitjump.MainMenu;

/**
 * Death, and the two doors out of it: ENTER retries the level you died on, ESC goes to the menu.
 *
 * <p><b>THE RELEASE HAS HAD THIS SINCE 12 OCTOBER AND THIS REPOSITORY HAD NOTHING.</b> Every edition gap closed
 * that week ran the other way - the engine had a check and the release did not (the ferry, the particles, the
 * up-current, the checkpoint, level validity, the exit room). Then the game-over screen turned out to be driven in
 * the release and NOWHERE here, which means a regression introduced in this repository - the source of truth, where
 * the editing happens - would only be caught after being ported. This is the first gap of that shape.
 *
 * <p>IT CANNOT BE CHECKED HEADLESS: the decision lives in {@code handleKey}, which needs a {@link UiManager} and a
 * display. The harness below is the same one {@code Main} builds - a UiManager in a StackPane, keys routed to it -
 * so what is being driven is the screen's own behaviour, not a stand-in for it.
 *
 * <p>THE LEVEL IS DELIBERATELY NOT 1. A retry that restarts the run from level 1 is the bug this screen exists to
 * prevent, and a test that dies on level 1 cannot tell the difference.
 */
public class GameOverRobotTest extends Application {
    private UiManager ui;
    private int step = 0;
    private int failures = 0;
    private static final int DIED_ON = 7;

    @Override
    public void start(Stage stage) {
        String root = Main.resolveRoot();
        Assets.load(root);
        Audio.load(root);

        ui = new UiManager(root);
        StackPane host = new StackPane(ui.getContainer());
        Scene scene = new Scene(host, 1280, 720, Color.BLACK);
        scene.setOnKeyPressed(ui::handleKey);
        scene.setOnKeyReleased(ui::handleKeyReleased);
        stage.setScene(scene);
        stage.show();

        // THE TICKER IS NOT OPTIONAL AND ITS ABSENCE FAILED THIS TEST THE FIRST TIME. Unlike the release's
        // screens, which drive their own update, THIS ENGINE'S SCREENS DO NOTHING UNTIL `UiManager.tick(dt)` IS
        // CALLED - Main's AnimationTimer does that. Without it the player could be killed and the death branch never
        // ran, so three checks failed and one passed for the wrong reason ("ENTER retries into gameplay" while the
        // screen had never left gameplay). The harness below is Main's, timer included.
        javafx.animation.AnimationTimer ticker = new javafx.animation.AnimationTimer() {
            long last = -1;
            @Override public void handle(long now) {
                double dt = last < 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
                last = now;
                ui.tick(dt);
            }
        };
        ticker.start();

        // Straight into level 7 - no menu, so the only thing under test is the death path.
        ui.push(new GameplayScreen(ui, DIED_ON));
        nextStep();
    }

    private void check(String name, boolean ok) {
        System.out.printf("%-54s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private int levelOf(GameplayScreen g) {
        try {
            java.lang.reflect.Field f = GameplayScreen.class.getDeclaredField("levelNum");
            f.setAccessible(true);
            return (int) f.get(g);
        } catch (Exception e) { return -1; }
    }

    /** Kill the player the way the game does, so the screen's own death branch runs. */
    private void killThePlayer() {
        try {
            Object top = ui.peek();
            java.lang.reflect.Field f = GameplayScreen.class.getDeclaredField("combat");
            f.setAccessible(true);
            aside.games.fruitjump.engine.Combat c = (aside.games.fruitjump.engine.Combat) f.get(top);
            c.playerHP = 0;
        } catch (Exception e) {
            check("the player could be killed for the test: " + e, false);
        }
    }

    private void nextStep() {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(400));
        wait.setOnFinished(e -> {
            step++;
            Robot robot = new Robot();
            switch (step) {
                case 1 -> {
                    check("the run starts on the level we chose (" + levelOf((GameplayScreen) ui.peek()) + ")",
                            levelOf((GameplayScreen) ui.peek()) == DIED_ON);
                    killThePlayer();
                }
                case 2 -> {
                    check("losing the last heart reaches GAME OVER", ui.peek() instanceof GameOverScreen);
                    robot.keyType(KeyCode.ENTER);
                }
                case 3 -> {
                    Object top = ui.peek();
                    check("ENTER retries into gameplay", top instanceof GameplayScreen);
                    int lvl = top instanceof GameplayScreen g ? levelOf(g) : -1;
                    check("AND RETRIES THE LEVEL YOU DIED ON, not level 1 (" + lvl + ")", lvl == DIED_ON);
                    killThePlayer();
                }
                case 4 -> {
                    check("dying again reaches GAME OVER again", ui.peek() instanceof GameOverScreen);
                    robot.keyType(KeyCode.ESCAPE);
                }
                case 5 -> {
                    check("ESC leaves for the main menu, and does not leave a blank screen",
                            ui.peek() instanceof MainMenu);
                    finish();
                }
                default -> finish();
            }
            if (step < 5) nextStep();
        });
        wait.play();
    }

    private void finish() {
        System.out.println(failures == 0
                ? "SUCCESS: death offers both doors, and the retry is the level you died on"
                : "FAILURE: " + failures + " check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
