package aside.games.fnaf9;

import aside.games.fnaf9.engine.Feed;
import aside.games.fnaf9.engine.Feed.Side;
import aside.games.fnaf9.engine.Walker;
import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The FNAF 9 office.
 *
 * <pre>
 *   [ NIGHT 3   12 AM   [ FEED 2.4s BEHIND ]  [ DOOR: CLEAR ] ]   the band
 *   -----------------------------------------------------------------
 *   [            one monitor, and one hall in it             ]    the room
 *   -----------------------------------------------------------------
 *   [ MON LEFT ][ MON RIGHT ][  DARK  ][      HOLD      ]         the strip
 * </pre>
 *
 * <p>Controls:
 * <pre>
 *   A / LEFT    watch the left hall      (the picture is old, and gets older)
 *   D / RIGHT   watch the right hall
 *   SPACE       everything off           (the feed catches up)
 *   S or HOLD   bring the door down      (takes a second, and blinds you)
 *   ESC         pause
 * </pre>
 *
 * <p><b>Everything is drawn rather than photographed, and that is the game
 * rather than a shortcut.</b> The monitor is a delay line: what it shows is
 * the hall as it was {@link Feed#feed} seconds ago, so the one thing the
 * player has to read at a glance is <i>how far up the hall is it</i> -- and
 * that is a fact about position, which the engine already knows exactly. A
 * photograph per distance would be a photograph per distance that has to
 * agree with all the others about lighting, angle and scale, and they would
 * not. It is also why the picture is a wireframe corridor with a silhouette in
 * it: a bad camera feed is the one thing in this building that is supposed to
 * look like it is not quite showing you something.
 *
 * <p><b>The feed-age bar is the game, and it is drawn as a bar rather than a
 * number.</b> The number is there too, because the competent play is to
 * correct a reading for how old it is and that is arithmetic the player should
 * not have to do in their head. But the bar is what says <i>how much of the
 * night you have spent looking</i>, which is the thing the night is actually
 * about.
 */
public class GameScreen extends UiScreen {

    final Feed game;
    final int night;

    double scareT = 0;
    double pulse = 0;
    double hoverX = -1, hoverY = -1;
    double shake = 0;
    /** Set for a moment after the door is asked to come down. */
    double shutT = 0;

    /** Dev hooks: freeze the loop so a staged frame stays staged. */
    boolean frozen = false;

    public GameScreen(UiManager ui, int night) {
        super(ui);
        this.night = night;
        this.game = new Feed(night, System.nanoTime());
        devHook();
    }

    /**
     * Dev hooks, for verifying frames a 90-frame snapshot cannot reach on its
     * own. Every one of them is additive and off by default.
     *
     * <p>`-Daside.fnaf9.freeze=1` stops the loop, because a staged frame
     * without it is a second out of date by the time the snapshot lands -- the
     * mistake FNAF 2 through 8 have each already paid for once.
     */
    void devHook() {
        frozen = System.getProperty("aside.fnaf9.freeze") != null;

        String l = System.getProperty("aside.fnaf9.left");
        if (l != null) game.d[Side.LEFT.ordinal()] = Double.parseDouble(l);
        String r = System.getProperty("aside.fnaf9.right");
        if (r != null) game.d[Side.RIGHT.ordinal()] = Double.parseDouble(r);

        String feed = System.getProperty("aside.fnaf9.feed");
        if (feed != null) game.feed = Double.parseDouble(feed);
        String circ = System.getProperty("aside.fnaf9.circuit");
        if (circ != null) game.circuit = Feed.Circuit.valueOf(circ);
        String heat = System.getProperty("aside.fnaf9.heat");
        if (heat != null) game.heat = Double.parseDouble(heat);

        if (System.getProperty("aside.fnaf9.scare") != null) {
            game.status = Feed.Status.TAKEN;
            game.takenSide = Side.LEFT;
            game.takenBy = Walker.PHANTOM_FREDDY;
            scareT = 1.0;
        }
        if (System.getProperty("aside.fnaf9.win") != null) {
            game.status = Feed.Status.SURVIVED;
            game.hour = 6;
        }
    }

    @Override public Parent getRoot() { return root; }

    @Override
    public void tick(double dt) {
        double frame = Math.min(dt, 0.25);
        if (!frozen) game.update(frame);
        for (String cue : game.drainCues()) play(cue);

        pulse += frame;
        if (shutT > 0) shutT = Math.max(0, shutT - frame);
        if (game.status == Feed.Status.TAKEN) scareT += frame;
        shake = game.status == Feed.Status.TAKEN ? 10 : 0;
        render();
    }

    /** One cue, with the right fallback. */
    static void play(String cue) {
        switch (cue) {
            // One cue for both halls, and that is the game rather than a
            // shortcut: a footfall carries no direction, because if it did the
            // monitor would stop being the only way to know where either of
            // them is. FNAF 7 owns step_left and step_right and they are
            // deliberately two different sounds; this game must not use them.
            case "f9_step" -> Audio.A.sfx("f9_step", "footstep");
            case "f9_door" -> Audio.A.sfx("f9_door", "door_knock");
            case "f9_gives_up" -> Audio.A.sfx("f9_gives_up", "f9_step");
            case "f9_switch" -> Audio.A.sfx("f9_switch", "light_click");
            case "f9_jam" -> Audio.A.sfx("f9_jam", "door_knock");
            case "f9_taken" -> Audio.A.sfx("f9_taken", "scare_sprint");
            default -> Audio.A.sfx(cue);
        }
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case A, LEFT -> { watch(Side.LEFT); e.consume(); }
            case D, RIGHT -> { watch(Side.RIGHT); e.consume(); }
            case SPACE -> { game.dark(); e.consume(); }
            case S, H -> { hold(); e.consume(); }
            case ESCAPE -> { ui.push(new PauseScreen(ui, this)); e.consume(); }
            default -> { }
        }
    }

    void watch(Side s) { game.watch(s); }

    void hold() {
        if (game.hold()) shutT = 0.25;
    }

    // ---- Mouse ----

    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        hoverX = x;
        hoverY = y;
        if (!pressed) return;
        switch (MouseMap.hit(x, y).kind()) {
            case MON_LEFT -> watch(Side.LEFT);
            case MON_RIGHT -> watch(Side.RIGHT);
            case DARK -> game.dark();
            case HOLD -> hold();
            default -> { }
        }
    }

    // ---- Rendering ----

    void render() {
        gc.setFill(Color.web("#05060A"));
        gc.fillRect(0, 0, W, H);

        gc.save();
        if (shake > 0) {
            gc.translate(Math.sin(scareT * 61) * shake, Math.cos(scareT * 47) * shake);
        }
        drawMonitor();
        gc.restore();

        drawBand();
        drawStrip();

        if (game.status == Feed.Status.TAKEN) drawScare();
        else if (game.status == Feed.Status.SURVIVED) drawSixAm();
    }

    /**
     * The monitor: a bezel, a screen, and whatever the delay line is holding.
     *
     * <p>Three things can be on the screen and the player cannot tell two of
     * them apart, which is the trap: a hall with nothing in it, a hall whose
     * walker has fallen off the end of a stale picture, and a hall that is not
     * being watched at all. The first two are drawn identically on purpose.
     */
    void drawMonitor() {
        double[] r = MouseMap.SCREEN;
        // The bezel.
        gc.setFill(Color.web("#0B0D14"));
        gc.fillRect(r[0] - 18, r[1] - 18, r[2] + 36, r[3] + 36);
        gc.setStroke(Color.rgb(233, 69, 96, 0.25));
        gc.setLineWidth(1);
        gc.strokeRect(r[0] - 18, r[1] - 18, r[2] + 36, r[3] + 36);

        gc.setFill(Color.web("#02030A"));
        gc.fillRect(r[0], r[1], r[2], r[3]);

        Side m = game.monitorSide();
        if (m == null) {
            label(Voice.MON_OFF_1, r[0] + r[2] / 2, r[1] + r[3] / 2, "#3A3A4E", 26);
            label(Voice.MON_OFF_2, r[0] + r[2] / 2,
                    r[1] + r[3] / 2 + 30, "#2A2A3A", 13);
            return;
        }

        drawCorridor(r, m);

        if (game.faded(m)) {
            // Past this walker's patience with a bad picture the monitor does
            // not hold it. Not faint -- absent. So the screen says so.
            drawStatic(r);
            label(Voice.FADED_1, r[0] + r[2] / 2, r[1] + r[3] / 2, "#6A5A2A", 22);
            label(String.format(Voice.FADED_2, game.feed),
                    r[0] + r[2] / 2, r[1] + r[3] / 2 + 28, "#4A4030", 13);
        } else {
            double d = game.shown(m);
            if (d >= 0) drawWalker(r, m, d);
        }

        // The hall's name, bottom left of the screen.
        label(String.format(Voice.HALL, m.shout),
                r[0] + 18, r[1] + r[3] - 18, "#556070", 14);
    }

    /** The corridor, in one-point perspective. */
    void drawCorridor(double[] r, Side m) {
        double cx = r[0] + r[2] / 2, cy = r[1] + r[3] * 0.46;
        double far = 0.16;
        // The floor and ceiling lines, converging on the vanishing point.
        gc.setStroke(Color.rgb(120, 140, 170, 0.30));
        gc.setLineWidth(1);
        for (int i = 0; i <= 6; i++) {
            double t = i / 6.0;
            double y = r[1] + r[3] * (0.06 + 0.88 * t);
            double half = r[2] * (far + (0.5 - far) * Math.pow(1 - t, 1.6));
            gc.setGlobalAlpha(0.10 + 0.22 * (1 - t));
            gc.strokeLine(cx - half, y, cx + half, y);
        }
        gc.setGlobalAlpha(1);
        // The walls.
        gc.setStroke(Color.rgb(120, 140, 170, 0.22));
        gc.strokeLine(cx - r[2] * 0.5, r[1] + r[3] * 0.06, cx - r[2] * far, cy);
        gc.strokeLine(cx + r[2] * 0.5, r[1] + r[3] * 0.06, cx + r[2] * far, cy);
        gc.strokeLine(cx - r[2] * 0.5, r[1] + r[3] * 0.94, cx - r[2] * far, cy);
        gc.strokeLine(cx + r[2] * 0.5, r[1] + r[3] * 0.94, cx + r[2] * far, cy);

        // The doorway, at the near end, and it is the thing the whole night
        // is about.
        gc.setStroke(Color.rgb(233, 69, 96, 0.45));
        gc.setLineWidth(2);
        gc.strokeRect(cx - r[2] * 0.30, r[1] + r[3] * 0.14,
                r[2] * 0.60, r[3] * 0.74);
    }

    /**
     * One of them, at the distance the picture says.
     *
     * <p>Scale is the whole readout. A walker at the doorway fills it; a
     * walker at the far end is a small dark shape at the back of a lit
     * corridor. That is the one thing the player has to be able to judge
     * without reading a number, and it is why there is no number.
     */
    void drawWalker(double[] r, Side m, double d) {
        double cx = r[0] + r[2] / 2;
        double cy = r[1] + r[3] * 0.46;
        double t = Math.min(1.0, Math.max(0.0, d / Feed.MAX));
        double depth = 1.0 - 0.80 * t;
        double h = r[3] * 0.86 * depth;
        double w = h * 0.42;
        double x = cx - w / 2;
        // Feet rise toward the vanishing point as it gets further away, which
        // is the half of perspective that scale alone does not give you. A
        // walker that only shrinks reads as a small walker in the same place.
        double feetNear = r[1] + r[3] * 0.88;
        double feet = cy + (feetNear - cy) * depth;
        double y = feet - h;

        gc.setGlobalAlpha(0.22 + 0.70 * depth);
        gc.setFill(Color.web("#0A0A10"));
        gc.fillRect(x, y, w, h);
        gc.setStroke(Color.rgb(150, 160, 180, 0.30 + 0.40 * depth));
        gc.setLineWidth(1);
        gc.strokeRect(x, y, w, h);
        // Two points of light where a head would be.
        double r0 = Math.max(1.5, w * 0.075);
        double ey = y + h * 0.14;
        double glow = 0.35 + 0.65 * depth;
        for (int i = -1; i <= 1; i += 2) {
            gc.setFill(Color.rgb(255, 120, 90, glow));
            gc.fillOval(cx + i * w * 0.16 - r0, ey - r0, r0 * 2, r0 * 2);
        }
        gc.setGlobalAlpha(1);
    }

    /** Scan lines and noise, for a picture the monitor will not hold. */
    void drawStatic(double[] r) {
        gc.setFill(Color.rgb(30, 30, 40, 0.55));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(Color.rgb(160, 170, 190, 0.10));
        gc.setLineWidth(1);
        for (double y = r[1]; y < r[1] + r[3]; y += 4) {
            gc.strokeLine(r[0], y, r[0] + r[2], y);
        }
        gc.setFill(Color.rgb(200, 210, 230, 0.05));
        for (int i = 0; i < 90; i++) {
            double x = r[0] + ((i * 97 + (int) (pulse * 240)) % (int) r[2]);
            double y = r[1] + ((i * 61) % (int) r[3]);
            gc.fillRect(x, y, 2, 2);
        }
    }

    /** The band: night, clock, how old the picture is, and the door sensor. */
    void drawBand() {
        gc.setFill(Color.rgb(4, 4, 8, 0.90));
        gc.fillRect(0, 0, W, 70);
        gc.setStroke(Color.rgb(233, 69, 96, 0.35));
        gc.setLineWidth(1);
        gc.strokeLine(0, 70, W, 70);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText(String.format(Voice.BAND_NIGHT, night), 24, 44);

        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText(game.clock(), 190, 44);

        drawFeed();
        drawSensor();
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * How old the picture is, against the oldest it can get.
     *
     * <p>The bar is the night's attention budget spent, and the number beside
     * it is the correction the player has to apply to anything they read off
     * the screen. A player who watches nothing else on the screen can play
     * this game off this bar alone -- which is the point of drawing it, and
     * also the reason the night is hard: <b>the bar tells you how wrong the
     * picture is, and the picture is the only thing that tells you where they
     * are.</b>
     */
    void drawFeed() {
        double[] r = MouseMap.FEED;
        gc.setFill(Color.rgb(255, 255, 255, 0.05));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(Color.rgb(233, 69, 96, 0.45));
        gc.strokeRect(r[0], r[1], r[2], r[3]);

        gc.setFill(Color.web("#777790"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText(Voice.FEED_HEAD, r[0] + 12, r[1] + 17);

        double x0 = r[0] + 12, y0 = r[1] + 26, bw = r[2] - 24, bh = 12;
        gc.setFill(Color.rgb(255, 255, 255, 0.08));
        gc.fillRect(x0, y0, bw, bh);
        double frac = game.monitorOn() ? Math.min(1.0, game.feed / Feed.AGE_MAX) : 0;
        gc.setFill(frac > 0.75 ? Color.web("#E94560")
                : frac > 0.45 ? Color.web("#FFD27F")
                : Color.web("#7FD1AE"));
        gc.fillRect(x0, y0, bw * frac, bh);
        gc.setStroke(Color.rgb(255, 255, 255, 0.22));
        gc.strokeRect(x0, y0, bw, bh);

        gc.setFill(Color.web("#9A9AAE"));
        gc.setFont(Font.font("Arial", 12));
        gc.fillText(game.feedLabel(), x0, y0 + bh + 13);
    }

    /**
     * The door sensor: live, free, one bit, and only readable while the door
     * is down.
     *
     * <p>Drawn as a lamp and a word. It is the only thing on this screen that
     * is a fact about now, and it is deliberately almost useless -- it cannot
     * say which hall, how far, or how long. What it can say is that the
     * doorway has emptied, which is the only way to know when to let go.
     */
    void drawSensor() {
        double[] r = MouseMap.SENSOR;
        gc.setFill(Color.rgb(255, 255, 255, 0.05));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(Color.rgb(233, 69, 96, 0.45));
        gc.strokeRect(r[0], r[1], r[2], r[3]);

        gc.setFill(Color.web("#777790"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText(Voice.SENSOR_HEAD, r[0] + 12, r[1] + 17);

        boolean down = game.blocking();
        boolean touching = game.sensor();
        Color lamp = !down ? Color.web("#3A3A4E")
                : touching ? Color.web("#E94560") : Color.web("#7FD1AE");
        gc.setFill(lamp);
        gc.fillOval(r[0] + 14, r[1] + 26, 14, 14);

        String word = Voice.sensorWord(game);
        gc.setFill(!down ? Color.web("#555568") : Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 14));
        gc.fillText(word, r[0] + 38, r[1] + 38);

        // How much hold the mechanism has left in it.
        double x0 = r[0] + 12, y0 = r[1] + 44, bw = r[2] - 24, bh = 5;
        gc.setFill(Color.rgb(255, 255, 255, 0.08));
        gc.fillRect(x0, y0, bw, bh);
        gc.setFill(game.holdLeft() < 0.25 ? Color.web("#E94560")
                : Color.web("#8A8FA8"));
        gc.fillRect(x0, y0, bw * game.holdLeft(), bh);
    }

    /** The strip: the four things the one circuit can do. */
    void drawStrip() {
        gc.setFill(Color.rgb(4, 4, 8, 0.92));
        gc.fillRect(0, 596, W, 124);
        gc.setStroke(Color.rgb(233, 69, 96, 0.35));
        gc.strokeLine(0, 596, W, 596);

        button(MouseMap.MON_LEFT, Voice.BTN_MON_LEFT,
                game.circuit == Feed.Circuit.MON_LEFT, false);
        button(MouseMap.MON_RIGHT, Voice.BTN_MON_RIGHT,
                game.circuit == Feed.Circuit.MON_RIGHT, false);
        button(MouseMap.DARK, Voice.BTN_DARK,
                game.circuit == Feed.Circuit.DARK, false);
        button(MouseMap.HOLD, Voice.BTN_HOLD,
                game.circuit == Feed.Circuit.HOLD,
                game.jammed);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#8A8FA8"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText(Voice.circuitLine(game), W / 2, 618);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText(Voice.KEYS, W / 2, 700);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void button(double[] r, String label, boolean on, boolean blocked) {
        boolean over = hoverX >= r[0] && hoverX <= r[0] + r[2]
                && hoverY >= r[1] && hoverY <= r[1] + r[3];
        gc.setFill(on ? Color.rgb(233, 69, 96, 0.30)
                : over ? Color.rgb(255, 255, 255, 0.10)
                : Color.rgb(255, 255, 255, 0.05));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(blocked ? Color.rgb(120, 120, 140, 0.30)
                : on ? Color.web("#E94560") : Color.rgb(255, 255, 255, 0.22));
        gc.setLineWidth(1);
        gc.strokeRect(r[0], r[1], r[2], r[3]);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(blocked ? Color.web("#555568")
                : on ? Color.web("#FFD700") : Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 16));
        gc.fillText(label, r[0] + r[2] / 2, r[1] + r[3] / 2 + 6);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawScare() {
        double t = Math.min(1.0, scareT * 3);
        gc.setFill(Color.rgb(0, 0, 0, 0.86 * t));
        gc.fillRect(0, 0, W, H);
        // A face, drawn rather than photographed: two points of light and a
        // shape around them, which is how this office has drawn everything.
        gc.setFill(Color.rgb(10, 10, 14, t));
        gc.fillRect(W / 2 - 210, 90, 420, 420);
        gc.setStroke(Color.rgb(233, 69, 96, 0.55 * t));
        gc.setLineWidth(2);
        gc.strokeRect(W / 2 - 210, 90, 420, 420);
        for (int i = -1; i <= 1; i += 2) {
            gc.setFill(Color.rgb(255, 90, 70, t));
            gc.fillOval(W / 2 + i * 78 - 26, 230 - 26, 52, 52);
        }
        gc.setFill(Color.rgb(0, 0, 0, 0.72 * t));
        gc.fillRect(0, H - 190, W, 190);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 36));
        gc.fillText(Voice.TAKEN_HEAD, W / 2, H - 108);
        gc.setFill(Color.web("#9A9AAE"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText(Voice.TAKEN_LINE, W / 2, H - 70);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText(Voice.TAKEN_HINT, W / 2, H - 44);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawSixAm() {
        gc.setFill(Color.rgb(0, 0, 0, 0.72));
        gc.fillRect(0, 0, W, H);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#FFD700"));
        gc.setFont(Font.font("Arial", 76));
        gc.fillText(Voice.WIN_HEAD, W / 2, H / 2 - 10);
        gc.setFill(Color.web("#9A9AAE"));
        gc.setFont(Font.font("Arial", 16));
        gc.fillText(Voice.WIN_LINE, W / 2, H / 2 + 34);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText(Voice.WIN_HINT, W / 2, H / 2 + 70);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void label(String text, double x, double y, String colour, double size) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web(colour));
        gc.setFont(Font.font("Arial", size));
        gc.fillText(text, x, y);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
