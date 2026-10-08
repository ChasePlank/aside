package aside.games.fruitjump;

import aside.games.fruitjump.engine.AudioSystem;
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

    final String line;
    final String enterHint;
    final java.util.function.Supplier<UiScreen> onEnter;
    final double playTime;

    /** The climb: ENTER carries on from the next level. */
    public VictoryScreen(UiManager ui, int levelsClimbed, double playTime) {
        this(ui, "Climbed " + levelsClimbed + " levels, and the sun went down on the way",
                "ENTER  keep climbing",
                () -> new GameplayScreen(ui, levelsClimbed + 1), playTime);
    }

    /**
     * Rooms mode, which has its own way home.
     *
     * <p><b>THE SAME SCREEN, BECAUSE IT IS THE SAME SENTENCE.</b> The two modes differ in what "a way home" means -
     * getting to the top of the climb, or finding the way out of a grid of rooms - and not in what it looks like,
     * so they share the horizon and differ in one line and one key. A second ending screen would have been two
     * copies of a picture that already exists twice in this project.
     */
    public static VictoryScreen roomsOut(UiManager ui, int roomsVisited, double playTime) {
        return new VictoryScreen(ui, "Found the way out, through " + roomsVisited + " rooms",
                "ENTER  another way out", () -> new RoomsScreen(ui, 1), playTime);
    }

    private VictoryScreen(UiManager ui, String line, String enterHint,
                          java.util.function.Supplier<UiScreen> onEnter, double playTime) {
        super(ui);
        this.line = line;
        this.enterHint = enterHint;
        this.onEnter = onEnter;
        this.playTime = playTime;
        // The fourth track, and the one that most needed an ending to exist: this screen is the only place
        // `victory-theme` belongs, and until this hour there was no such screen and no such file.
        if (aside.ui.Audio.A != null) aside.ui.Audio.A.music(AudioSystem.Music.VICTORY.track());
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            // The run continues past its own ending - the way home is a place you can leave again rather than a
            // full stop. What ENTER does differs by mode and is supplied by whoever built the screen.
            ui.replace(onEnter.get());
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
        gc.fillText(line, 94, 290);
        gc.fillText(String.format("Time: %.1f seconds", playTime), 94, 336);

        gc.setFont(F_HINT);
        gc.setFill(Color.web("#f5a623"));
        gc.fillText(enterHint, 94, 420);
        gc.setFill(Color.web("#8a8aa0"));
        gc.fillText("ESC  main menu", 94, 460);
    }
}
