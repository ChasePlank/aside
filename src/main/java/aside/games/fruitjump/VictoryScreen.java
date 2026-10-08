package aside.games.fruitjump;

import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * The way home: the end of a run.
 *
 * <p><b>THE SAME HORIZON AS THE GAME OVER SCREEN, AND ONE THING DIFFERENT.</b> That screen paints the night sky
 * with {@code climber = false} and its own comment explains why: "THE ROCK IS EMPTY. The climber is not standing
 * on it. That is the whole difference between this screen and the title, and it needs no words." This screen is
 * that image with the climber standing on the rock - which is what "a way home" means, and it also needs no words.
 *
 * <p>The run has been sinking towards this since level one: the sky deepens and the sun goes down over
 * {@code LevelGen.FINAL_LEVEL} levels, so arriving here is arriving after sunset rather than at an arbitrary
 * score.
 *
 * <p><b>IT IS A DOOR, NOT A WALL.</b> ENTER keeps climbing - the endless run is still there for anyone who wants
 * it - and ESC goes to the menu. The ending gives the climb a shape without taking the treadmill away.
 */
public class VictoryScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Arial", 72);
    static final Font F_STAT = Font.font("Arial", 30);
    static final Font F_HINT = Font.font("Arial", 22);

    final int levelsClimbed;
    final double playTime;

    public VictoryScreen(UiManager ui, int levelsClimbed, double playTime) {
        super(ui);
        this.levelsClimbed = levelsClimbed;
        this.playTime = playTime;
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            // On to the next level, and the run continues past its own ending - the way home is a place you can
            // leave again rather than a full stop.
            ui.replace(new GameplayScreen(ui, levelsClimbed + 1));
        } else if (e.getCode() == KeyCode.ESCAPE) {
            ui.replace(new MainMenu(ui));
        }
        e.consume();
    }

    @Override
    public void tick(double dt) {
        // climber = true. The sun is down, the sky has gone cold, and somebody is standing on the rock.
        Skyline.paint(gc, W, H, Skyline.Mood.NIGHT, true, true);

        gc.setFill(Color.web("#f5a623"));
        gc.setFont(F_TITLE);
        gc.fillText("HOME", 90, 200);

        gc.setFill(Color.web("#c9c9d6"));
        gc.setFont(F_STAT);
        gc.fillText("Climbed " + levelsClimbed + " levels, and the sun went down on the way", 94, 290);
        gc.fillText(String.format("Time: %.1f seconds", playTime), 94, 336);

        gc.setFont(F_HINT);
        gc.setFill(Color.web("#f5a623"));
        gc.fillText("ENTER  keep climbing", 94, 420);
        gc.setFill(Color.web("#8a8aa0"));
        gc.fillText("ESC  main menu", 94, 460);
    }
}
