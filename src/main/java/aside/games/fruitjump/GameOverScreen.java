package aside.games.fruitjump;

import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** Game over: final stats, retry the level you died on, or quit to menu. */
public class GameOverScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Arial", 72);
    static final Font F_STAT = Font.font("Arial", 30);
    static final Font F_HINT = Font.font("Arial", 22);

    final int levelReached;
    final double playTime;

    public GameOverScreen(UiManager ui, int levelReached, double playTime) {
        super(ui);
        this.levelReached = levelReached;
        this.playTime = playTime;
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            // Retry the level you died on rather than the whole run --
            // with procedural levels, sending someone back to level 1
            // after dying on level 7 is brutal for no reason.
            ui.replace(new GameplayScreen(ui, Math.max(1, levelReached)));
        } else if (e.getCode() == KeyCode.ESCAPE) {
            ui.replace(new MainMenu(ui));
        }
        e.consume();
    }

    @Override
    public void tick(double dt) {
        // THE SAME HORIZON AS THE TITLE SCREEN, later in the day. This screen was a flat #1a1a2e with a red
        // heading - the same treatment the title screen had, and the same two menu colours that appear nowhere
        // else in the game. It is also the screen a player sees MOST, because dying is what a platformer does.
        //
        // So: the sun is down, the sky has gone cold and grey, and THE ROCK IS EMPTY. The climber is not standing
        // on it. That is the whole difference between this screen and the title, and it needs no words.
        Skyline.paint(gc, W, H, Skyline.Mood.NIGHT, false, true);

        gc.setFill(Color.web("#e94560"));
        gc.setFont(F_TITLE);
        gc.fillText("GAME OVER", 90, 200);

        gc.setFill(Color.web("#c9c9d6"));
        gc.setFont(F_STAT);
        gc.fillText("Reached level " + levelReached, 94, 290);
        gc.fillText(String.format("Time: %.1f seconds", playTime), 94, 336);

        gc.setFont(F_HINT);
        gc.setFill(Color.web("#f5a623"));
        gc.fillText("ENTER  retry level " + Math.max(1, levelReached), 94, 420);
        gc.setFill(Color.web("#8a8aa0"));
        gc.fillText("ESC  main menu", 94, 460);
    }
}