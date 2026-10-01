package aside.games.fnaf7;

import aside.games.fnaf7.engine.Shift;
import aside.games.fnaf7.engine.Shift.Side;
import aside.games.fnaf7.engine.Shift.Where;
import aside.games.fnaf7.engine.Unit;
import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The FNAF 7 office.
 *
 * <pre>
 *   [ NIGHT 3   12 AM   [ filament ]  [ IT EXPECTS YOU AT: LEFT ] ]  the band
 *   -----------------------------------------------------------------
 *   [ two doorways, one bar, and whatever is in the hall ]            the room
 *   -----------------------------------------------------------------
 *   [ LIGHT L ] [ LIGHT R ]          [ BAR L ] [ BAR R ]              the strip
 * </pre>
 *
 * <p>Controls:
 * <pre>
 *   A / LEFT    point the lamp at the left hall
 *   D / RIGHT   point the lamp at the right hall
 *   Z           put the bar on the left door
 *   C           put the bar on the right door
 *   SPACE       put the lamp out
 *   ESC         pause
 * </pre>
 *
 * <p><b>The readout is the game, and it is drawn in words rather than as a
 * meter on purpose.</b> A bar would say <i>how much</i>; the sentence says
 * <i>what it thinks</i>, which is the thing the player has to argue with.
 * It is the only place the state of the record is visible, and the state
 * of the record is the only thing that decides where the unit goes.
 *
 * <p>The doorway is dark and stays dark. The lamp shows a <b>hall</b> and
 * it does not show a doorway, so a lamp that goes up late shows nothing at
 * all -- which is why the filament matters and why a look has to be spent
 * before the player knows whether it was worth spending.
 */
public class GameScreen extends UiScreen {

    /** The doorways, matching tools/fnaf7-art.py. */
    static final double DOOR_W = 0.26 * W;
    static final double DOOR_L = 0.03 * W;
    static final double DOOR_R = W - 0.03 * W - DOOR_W;
    static final double DOOR_TOP = 0.14 * H;
    static final double DOOR_BOTTOM = 0.80 * H;

    final Shift game;
    final int night;

    double scareT = 0;
    double pulse = 0;
    double hoverX = -1, hoverY = -1;
    /** Set for a moment after the bar takes a hit. */
    double hitT = 0;
    /** Set for a moment after the filament cuts out. */
    double blownT = 0;
    /** Shake, in pixels, while the thing is coming through. */
    double shake = 0;

    /** Dev hooks: freeze the loop so a staged frame stays staged. */
    boolean frozen = false;

    public GameScreen(UiManager ui, int night) {
        super(ui);
        this.night = night;
        this.game = new Shift(night, System.nanoTime());
        devHook();
    }

    /**
     * Dev hooks, for verifying frames a 90-frame snapshot cannot reach on
     * its own. Every one of them is additive and off by default.
     *
     * <p>`-Daside.fnaf7.freeze=1` stops the loop, because a staged frame
     * without it is a second out of date by the time the snapshot lands --
     * the mistake FNAF 2, 3, 4, 5 and 6 have each already paid for once.
     */
    void devHook() {
        frozen = System.getProperty("aside.fnaf7.freeze") != null;

        String where = System.getProperty("aside.fnaf7.where");
        if (where != null) {
            game.where = switch (where.toLowerCase()) {
                case "hall_left", "hall-l" -> Where.HALL_LEFT;
                case "hall_right", "hall-r" -> Where.HALL_RIGHT;
                case "door_left", "door-l" -> Where.DOOR_LEFT;
                case "door_right", "door-r" -> Where.DOOR_RIGHT;
                default -> Where.AWAY;
            };
            game.target = game.where.side() == null ? Side.LEFT : game.where.side();
        }
        String bar = System.getProperty("aside.fnaf7.bar");
        if (bar != null) game.barAt = bar.equalsIgnoreCase("left") ? Side.LEFT : Side.RIGHT;
        String lit = System.getProperty("aside.fnaf7.light");
        if (lit != null) {
            game.lit = true;
            game.lightSide = lit.equalsIgnoreCase("left") ? Side.LEFT : Side.RIGHT;
            // Warm it, so a frozen frame shows a legible hall rather than a
            // lamp that has not come up yet.
            game.litFor = Shift.LIGHT_WARM + 0.1;
        }
        String heat = System.getProperty("aside.fnaf7.heat");
        if (heat != null) game.heat = Double.parseDouble(heat);
        String attend = System.getProperty("aside.fnaf7.attend");
        if (attend != null) {
            // "left" / "right" / "none": stage the record's belief.
            switch (attend.toLowerCase()) {
                case "left" -> { game.attendL = 6; game.attendR = 0.4; }
                case "right" -> { game.attendR = 6; game.attendL = 0.4; }
                default -> { game.attendL = 0; game.attendR = 0; }
            }
        }
        String unit = System.getProperty("aside.fnaf7.unit");
        if (unit != null) {
            for (Unit u : Unit.all()) {
                if (u.key().equalsIgnoreCase(unit) || u.name().equalsIgnoreCase(unit)) {
                    game.forceUnit(u);
                }
            }
        }
        if (System.getProperty("aside.fnaf7.scare") != null) {
            game.status = Shift.Status.CAUGHT;
            game.killer = game.unit.name();
            scareT = 1.0;
        }
        if (System.getProperty("aside.fnaf7.win") != null) {
            game.status = Shift.Status.SURVIVED;
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
        if (hitT > 0) hitT = Math.max(0, hitT - frame);
        if (blownT > 0) blownT = Math.max(0, blownT - frame);
        if (game.status == Shift.Status.CAUGHT) scareT += frame;
        shake = game.status == Shift.Status.CAUGHT ? 10 : 0;
        render();
    }

    /**
     * One cue, with the right fallback.
     *
     * <p>The fallbacks are right -- a cue that resolves to nothing is worse
     * than a cue that resolves to the wrong file -- but the per-family
     * names are the point: a step you cannot place is a step that carries
     * no information, and in this game placing the step is the one thing
     * the lamp is not needed for.
     */
    static void play(String cue) {
        switch (cue) {
            case "step_left" -> Audio.A.sfx("step_left", "footstep");
            case "step_right" -> Audio.A.sfx("step_right", "footstep");
            case "at_door" -> Audio.A.sfx("at_door", "door_knock");
            case "bar_move" -> Audio.A.sfx("bar_move", "door_close");
            case "bar_set" -> Audio.A.sfx("bar_set", "door_close");
            case "repel" -> Audio.A.sfx("repel", "door_close");
            case "light_on" -> Audio.A.sfx("light_on", "light_click");
            case "light_off" -> Audio.A.sfx("light_off", "light_click");
            case "light_blown" -> Audio.A.sfx("light_blown", "power_down");
            case "light_ready" -> Audio.A.sfx("light_ready", "light_click");
            case "caught" -> Audio.A.sfx("caught", "scare_sprint");
            default -> Audio.A.sfx(cue);
        }
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case A, LEFT -> { light(Side.LEFT); e.consume(); }
            case D, RIGHT -> { light(Side.RIGHT); e.consume(); }
            case Z -> { bar(Side.LEFT); e.consume(); }
            case C -> { bar(Side.RIGHT); e.consume(); }
            case SPACE -> { game.dark(); e.consume(); }
            case ESCAPE -> { ui.push(new PauseScreen(ui, this)); e.consume(); }
            default -> { }
        }
    }

    void light(Side s) { game.aim(s); }

    void bar(Side s) {
        if (game.moveBar(s)) hitT = 0;
    }

    // ---- Mouse ----

    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        hoverX = x;
        hoverY = y;
        if (!pressed) return;
        switch (MouseMap.hit(x, y).kind()) {
            case LIGHT_L -> light(Side.LEFT);
            case LIGHT_R -> light(Side.RIGHT);
            case BAR_L -> bar(Side.LEFT);
            case BAR_R -> bar(Side.RIGHT);
            default -> { }
        }
    }

    MouseMap.Hit hover() {
        if (hoverX < 0) return MouseMap.Hit.NONE;
        return MouseMap.hit(hoverX, hoverY);
    }

    // ---- Rendering ----

    void render() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        gc.save();
        if (shake > 0) {
            gc.translate(Math.sin(scareT * 61) * shake, Math.cos(scareT * 47) * shake);
        }

        Assets a = Assets.A;
        if (a != null && a.room != null) gc.drawImage(a.room, 0, 0, W, H);
        drawHallGlow();
        drawUnit();
        drawBar();
        gc.restore();

        drawBand();
        drawStrip();

        if (game.status == Shift.Status.CAUGHT) drawScare();
        else if (game.status == Shift.Status.SURVIVED) drawSixAm();
    }

    /**
     * The lamp's pool on the hall it is pointed at.
     *
     * <p>Warming is drawn as a pool that is still coming up, because the
     * warm-up is a real cost and a cost the player cannot see is a cost
     * they will not believe in.
     */
    void drawHallGlow() {
        if (!game.lit) return;
        double x = game.lightSide == Side.LEFT ? DOOR_L : DOOR_R;
        double t = Math.min(1.0, game.litFor / Shift.LIGHT_WARM);
        double alpha = 0.16 + 0.30 * t;
        if (game.blown) alpha *= 0.3;
        double cx = x + DOOR_W / 2, cy = (DOOR_TOP + DOOR_BOTTOM) / 2;
        gc.setFill(new RadialGradient(0, 0, cx, cy,
                Math.max(DOOR_W, DOOR_BOTTOM - DOOR_TOP) * 0.62, false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(255, 238, 198, alpha)),
                new Stop(0.55, Color.rgb(255, 232, 186, alpha * 0.55)),
                new Stop(1.0, Color.rgb(255, 230, 180, 0.0))));
        gc.fillRect(x - 40, DOOR_TOP - 40, DOOR_W + 80, DOOR_BOTTOM - DOOR_TOP + 80);
    }

    /**
     * The thing in the hall, and only when the lamp is on it.
     *
     * <p>Halls only: the lamp does not show a doorway, so a unit that has
     * already stepped up to the door is a unit the player can no longer
     * see. That is not an oversight, it is the reason the warm-up exists --
     * a look has to be spent before the player knows whether it was worth
     * spending, and a look that arrives after the answer is a look that
     * bought nothing.
     */
    void drawUnit() {
        if (!game.visible()) return;
        Assets a = Assets.A;
        if (a == null) return;
        Image img = a.unit(game.unit.key());
        if (img == null) return;

        double x = game.lightSide == Side.LEFT ? DOOR_L : DOOR_R;
        double avail = DOOR_BOTTOM - DOOR_TOP;
        // Fit the doorway, not just its height: a unit wider than the
        // opening is a unit standing in the wall.
        double s = Math.min(avail / img.getHeight(), DOOR_W / img.getWidth());
        double w = img.getWidth() * s;
        double dx = x + (DOOR_W - w) / 2;
        double dy = DOOR_BOTTOM - img.getHeight() * s;

        // The lamp is a pool, so the edges of the figure fall off with it.
        double t = Math.min(1.0, game.litFor / Shift.LIGHT_WARM);
        gc.setGlobalAlpha(0.35 + 0.65 * t);
        gc.drawImage(img, dx, dy, w, img.getHeight() * s);
        gc.setGlobalAlpha(1);
    }

    /** The bar, drawn across the door it is on, and in the air while it moves. */
    void drawBar() {
        double y = DOOR_BOTTOM - 54;
        if (game.barMoving) {
            double from = game.barTo == Side.LEFT ? DOOR_R : DOOR_L;
            double to = game.barTo == Side.LEFT ? DOOR_L : DOOR_R;
            double t = 1.0 - Math.max(0, game.barTimer) / Shift.BAR_MOVE;
            double x = from + (to - from) * t;
            bar(x, y, Color.web("#8A8FA8"));
            return;
        }
        if (game.barAt == null) return;
        double x = game.barAt == Side.LEFT ? DOOR_L : DOOR_R;
        bar(x, y, hitT > 0 ? Color.web("#E94560") : Color.web("#C8CCDD"));
    }

    void bar(double x, double y, Color c) {
        gc.setFill(Color.rgb(0, 0, 0, 0.55));
        gc.fillRect(x - 6, y - 10, DOOR_W + 12, 40);
        gc.setFill(c);
        gc.fillRect(x, y, DOOR_W, 18);
        gc.setFill(Color.rgb(0, 0, 0, 0.35));
        for (double i = 0; i < DOOR_W; i += 26) gc.fillRect(x + i, y, 6, 18);
    }

    /** The band: night, clock, filament, and the readout. */
    void drawBand() {
        gc.setFill(Color.rgb(4, 4, 8, 0.86));
        gc.fillRect(0, 0, W, 70);
        gc.setStroke(Color.rgb(233, 69, 96, 0.35));
        gc.setLineWidth(1);
        gc.strokeLine(0, 70, W, 70);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText("NIGHT " + night, 24, 44);

        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText(game.clock(), 210, 44);

        // The filament.
        double[] f = MouseMap.FILAMENT;
        gc.setFill(Color.rgb(255, 255, 255, 0.10));
        gc.fillRect(f[0], f[1], f[2], f[3]);
        double left = Math.max(0, game.filament());
        gc.setFill(game.blown ? Color.web("#553344")
                : left < 0.35 ? Color.web("#E94560") : Color.web("#FFD27F"));
        gc.fillRect(f[0], f[1], f[2] * left, f[3]);
        gc.setStroke(Color.rgb(255, 255, 255, 0.22));
        gc.strokeRect(f[0], f[1], f[2], f[3]);
        gc.setFill(Color.web("#777790"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText("FILAMENT", f[0], f[1] - 4);

        drawReadout();
    }

    /**
     * The readout: what it thinks of you, in words.
     *
     * <p>It says where the record holds, and the unit comes to the other
     * side -- so the sentence is the opposite of the answer, and the player
     * has to make that turn. It is the only place the record is visible.
     */
    void drawReadout() {
        double[] r = MouseMap.READOUT;
        gc.setFill(Color.rgb(255, 255, 255, 0.05));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(Color.rgb(233, 69, 96, 0.45));
        gc.strokeRect(r[0], r[1], r[2], r[3]);

        Side b = game.belief();
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("#777790"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText("IT EXPECTS YOU AT", r[0] + 12, r[1] + 17);

        gc.setFont(Font.font("Arial", 24));
        if (b == null) {
            gc.setFill(Color.web("#666680"));
            gc.fillText("--", r[0] + 12, r[1] + 40);
            gc.setFill(Color.web("#555570"));
            gc.setFont(Font.font("Arial", 11));
            gc.fillText("it has nothing on you yet, and it will guess",
                    r[0] + 52, r[1] + 40);
        } else {
            gc.setFill(Color.web("#FFD700"));
            gc.fillText(b.shout, r[0] + 12, r[1] + 40);
            gc.setFill(Color.web("#555570"));
            gc.setFont(Font.font("Arial", 11));
            gc.fillText("so it is coming to the " + b.other().quiet + " door",
                    r[0] + 104, r[1] + 40);
        }

        // The confidence, as a thin line under the sentence. Not a bar
        // chart: one line, because the only thing worth reading is whether
        // the record has a grip at all.
        double c = game.confidence();
        gc.setFill(Color.rgb(255, 255, 255, 0.10));
        gc.fillRect(r[0] + 12, r[1] + r[3] - 6, r[2] - 24, 3);
        gc.setFill(b == null ? Color.web("#444458") : Color.web("#E94560"));
        gc.fillRect(r[0] + 12, r[1] + r[3] - 6, (r[2] - 24) * c, 3);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** The strip: the four things you can do, and what the lamp is showing. */
    void drawStrip() {
        gc.setFill(Color.rgb(4, 4, 8, 0.90));
        gc.fillRect(0, 596, W, 124);
        gc.setStroke(Color.rgb(233, 69, 96, 0.35));
        gc.strokeLine(0, 596, W, 596);

        button(MouseMap.LIGHT_L, "LIGHT LEFT", game.lit && game.lightSide == Side.LEFT,
                game.barMoving || game.blown);
        button(MouseMap.LIGHT_R, "LIGHT RIGHT", game.lit && game.lightSide == Side.RIGHT,
                game.barMoving || game.blown);
        button(MouseMap.BAR_L, "BAR LEFT", game.barAt == Side.LEFT, game.lit);
        button(MouseMap.BAR_R, "BAR RIGHT", game.barAt == Side.RIGHT, game.lit);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#8A8FA8"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText(whatTheLampShows(), W / 2, 618);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText("A / D  lamp      Z / C  bar      SPACE  lamp out      ESC  pause",
                W / 2, 700);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    String whatTheLampShows() {
        if (game.barMoving) return "the bar is in the air";
        if (game.blown) return "the filament is out";
        if (!game.lit) return "the lamp is off";
        if (game.litFor < Shift.LIGHT_WARM) return "the lamp is coming up";
        if (game.visible()) return "SOMETHING IS IN THE " + game.lightSide.shout + " HALL";
        return "the " + game.lightSide.quiet + " hall is empty";
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
        gc.setFont(Font.font("Arial", 15));
        gc.fillText(label, r[0] + r[2] / 2, r[1] + r[3] / 2 + 6);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawScare() {
        Assets a = Assets.A;
        if (a == null) return;
        Image img = a.scare(game.killer == null ? null : keyOf(game.killer));
        if (img == null) return;
        double t = Math.min(1.0, scareT * 3);
        gc.setGlobalAlpha(t);
        gc.drawImage(img, 0, 0, W, H);
        gc.setGlobalAlpha(1);
        // A scrim top and bottom rather than over the whole frame: the
        // face is the picture, and a caption across it is a caption that
        // makes the one frame the game has spent the whole night earning
        // unreadable.
        gc.setFill(Color.rgb(0, 0, 0, 0.72 * t));
        gc.fillRect(0, H - 190, W, 190);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 40));
        gc.fillText("IT CAME THROUGH THE " + (game.where.side() == null
                ? "OPEN" : game.where.side().shout) + " DOOR", W / 2, H - 108);
        gc.setFill(Color.web("#9A9AAE"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText("ESC to pause, then Quit to Menu", W / 2, H - 66);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawSixAm() {
        gc.setFill(Color.rgb(0, 0, 0, 0.72));
        gc.fillRect(0, 0, W, H);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#FFD700"));
        gc.setFont(Font.font("Arial", 76));
        gc.fillText("6 AM", W / 2, H / 2 - 10);
        gc.setFill(Color.web("#9A9AAE"));
        gc.setFont(Font.font("Arial", 16));
        gc.fillText("It never got through. The record is still yours.", W / 2, H / 2 + 34);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText("ESC to pause, then Quit to Menu", W / 2, H / 2 + 70);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** The asset key for a unit name, for the jumpscare lookup. */
    static String keyOf(String name) {
        for (Unit u : Unit.all()) if (u.name().equals(name)) return u.key();
        return null;
    }
}
