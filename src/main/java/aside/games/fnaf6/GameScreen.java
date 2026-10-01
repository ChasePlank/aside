package aside.games.fnaf6;

import aside.games.fnaf6.engine.Salvage;
import aside.games.fnaf6.engine.Unit;
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
 * The FNAF 6 salvage bay.
 *
 * <pre>
 *   [ NIGHT 3      12 AM      [ agitation ]   [ SHOCK x1 ] ]   the band
 *   ----------------------------------------------------------
 *   [ the room, the chair, and whatever is in it ]            the dark
 *   ----------------------------------------------------------
 *   [ LAMP ]   what the lamp is showing                       the strip
 * </pre>
 *
 * <p>Controls:
 * <pre>
 *   L or click LAMP    turn the lamp on or off
 *   SPACE or SHOCK     the controlled shock
 *   ESC                pause
 * </pre>
 *
 * <p><b>The screen is dark on purpose, and the darkness is information.</b>
 * In the dark you can see that there is something in the chair and you
 * cannot see what it is doing. The lamp is the only thing that changes
 * that, and the lamp is the thing the unit is waiting for -- so the
 * picture the player spends most of the night looking at is the one that
 * tells them the least.
 *
 * <p>The pose is <b>drawn, not photographed</b>: the unit is scaled up and
 * raised out of the chair as it climbs, and tilted while it is still
 * slumped. That is why there is one picture per unit rather than five.
 * The one thing the player has to read at a glance is <i>is it higher
 * than it was</i>, and that is a fact about position, which the engine
 * knows exactly.
 */
public class GameScreen extends UiScreen {

    final Salvage game;
    final int night;

    double scareT = 0;
    double pulse = 0;
    double hoverX = -1, hoverY = -1;
    /** Set for a moment after a shock that found nothing. */
    double wasteT = 0;
    /** Set for a moment after a shock that landed. */
    double hitT = 0;

    /** Dev hooks: freeze the loop so a staged frame stays staged. */
    boolean frozen = false;

    public GameScreen(UiManager ui, int night) {
        super(ui);
        this.night = night;
        this.game = new Salvage(night, System.nanoTime());
        devHook();
    }

    /**
     * Dev hooks, for verifying frames a 90-frame snapshot cannot reach on
     * its own. Every one of them is additive and off by default.
     *
     * <p>`-Daside.fnaf6.freeze=1` stops the loop, because a staged frame
     * without it is a second out of date by the time the snapshot lands --
     * the mistake FNAF 2, 3, 4 and 5 have each already paid for once.
     */
    void devHook() {
        frozen = System.getProperty("aside.fnaf6.freeze") != null;

        String pose = System.getProperty("aside.fnaf6.pose");
        if (pose != null) game.pose = Integer.parseInt(pose);
        if (System.getProperty("aside.fnaf6.hostile") != null) game.hostile = true;
        if (System.getProperty("aside.fnaf6.lit") != null) {
            game.lit = true;
            // Warm it, so a frozen frame shows a legible pose rather than
            // a lamp that has not come up yet.
            game.litFor = Salvage.LAMP_WARM + 0.1;
        }
        String ag = System.getProperty("aside.fnaf6.agitation");
        if (ag != null) game.agitation = Double.parseDouble(ag);
        String shocks = System.getProperty("aside.fnaf6.shocks");
        if (shocks != null) game.shocks = Integer.parseInt(shocks);
        String unit = System.getProperty("aside.fnaf6.unit");
        if (unit != null) {
            for (Unit u : new Unit[]{Unit.SCRAPTRAP, Unit.SCRAP_BABY,
                    Unit.MOLTEN_FREDDY, Unit.LEFTY}) {
                if (u.key().equalsIgnoreCase(unit) || u.name().equalsIgnoreCase(unit)) {
                    game.forceUnit(u);
                }
            }
        }

        if (System.getProperty("aside.fnaf6.scare") != null) {
            game.status = Salvage.Status.LUNGED;
            game.killer = game.unit.name();
            scareT = 1.0;
        }
        if (System.getProperty("aside.fnaf6.win") != null) {
            game.status = Salvage.Status.SURVIVED;
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
        if (wasteT > 0) wasteT = Math.max(0, wasteT - frame);
        if (hitT > 0) hitT = Math.max(0, hitT - frame);
        if (game.status == Salvage.Status.LUNGED) scareT += frame;
        render();
    }

    /**
     * One cue, with the right fallback.
     *
     * <p>The fallbacks are right -- a cue that resolves to nothing is worse
     * than a cue that resolves to the wrong file -- but the per-family
     * names are the point: a drag you cannot tell apart from a creak
     * carries no information, and in this game the drag is the only free
     * information there is.
     */
    static void play(String cue) {
        switch (cue) {
            case "drag" -> Audio.A.sfx("drag", "footstep");
            case "creak_a", "creak_b" -> Audio.A.sfx(cue, "pot_clank");
            case "lamp_on" -> Audio.A.sfx("lamp_on", "light_click");
            case "lamp_off" -> Audio.A.sfx("lamp_off", "light_click");
            case "shock" -> Audio.A.sfx("shock", "power_down");
            case "shock_hit" -> Audio.A.sfx("shock_hit", "power_down");
            case "shock_miss" -> Audio.A.sfx("shock_miss", "static");
            case "lunge" -> Audio.A.sfx("lunge", "scare_sprint");
            default -> Audio.A.sfx(cue);
        }
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case L -> { lamp(); e.consume(); }
            case SPACE, S -> { shock(); e.consume(); }
            case ESCAPE -> { ui.push(new PauseScreen(ui, this)); e.consume(); }
            default -> { }
        }
    }

    void lamp() {
        game.toggleLamp();
    }

    void shock() {
        boolean found = game.shock();
        if (found) hitT = 0.7;
        else if (game.wasted) wasteT = 0.7;
    }

    // ---- Mouse ----

    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        hoverX = x;
        hoverY = y;
        if (!pressed) return;
        switch (MouseMap.hit(x, y).kind()) {
            case LAMP -> lamp();
            case SHOCK -> shock();
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

        drawRoom();
        drawUnit();
        drawDesk();
        drawBand();
        drawStrip();
        if (game.status == Salvage.Status.LUNGED) drawJumpscare();
        if (game.status == Salvage.Status.SURVIVED
                || game.status == Salvage.Status.DESTROYED) drawWin();
    }

    /**
     * The room.
     *
     * <p>In the dark it is a photograph at a fifth of its brightness, which
     * is enough to see a chair and not enough to see what is in it. Lit,
     * a warm pool opens over the chair -- and the pool is drawn rather
     * than photographed, because the lamp is a thing the player switches
     * on and off and the light has to move with it.
     */
    void drawRoom() {
        double[] r = MouseMap.SCENE;
        Assets a = Assets.A;
        double lit = lightLevel();

        if (a != null && a.room != null) {
            gc.setGlobalAlpha(0.20 + 0.80 * lit);
            gc.drawImage(a.room, r[0], r[1], r[2], r[3]);
            gc.setGlobalAlpha(1);
        } else {
            gc.setFill(Color.web("#07080C"));
            gc.fillRect(r[0], r[1], r[2], r[3]);
        }

        // The pool of light. It is centred on the chair, and it is the
        // only warm thing on the screen.
        if (lit > 0.01) {
            double cx = W / 2, cy = r[1] + r[3] * 0.62;
            gc.setFill(new RadialGradient(0, 0, cx, cy, 520, false,
                    CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(255, 214, 150, 0.30 * lit)),
                    new Stop(0.55, Color.rgb(255, 190, 120, 0.10 * lit)),
                    new Stop(1, Color.TRANSPARENT)));
            gc.fillRect(r[0], r[1], r[2], r[3]);
        }

        // The vignette. The room has no other light in it.
        gc.setFill(new RadialGradient(0, 0, W / 2, r[1] + r[3] / 2, 780, false,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(1, Color.rgb(0, 0, 0, 0.85))));
        gc.fillRect(r[0], r[1], r[2], r[3]);
    }

    /**
     * How lit the room is, 0 to 1.
     *
     * <p>Ramps over {@link Salvage#LAMP_WARM} rather than snapping, so the
     * player can see the lamp coming on and knows the pose is about to
     * become legible. A lamp that snaps is a lamp whose warm-up is
     * invisible, and an invisible warm-up is a rule the player has to be
     * told rather than one they can see.
     */
    double lightLevel() {
        if (!game.lit) return 0;
        return Math.min(1, game.litFor / Salvage.LAMP_WARM);
    }

    /**
     * The thing in the chair.
     *
     * <p>Scaled and raised with the pose, tilted while it is still slumped.
     * In the dark it is a silhouette; the lamp makes it a picture. The
     * pose is only <i>named</i> on the strip once the lamp has warmed,
     * because that is the rule: a flash shows you nothing.
     */
    void drawUnit() {
        Assets a = Assets.A;
        Image img = a == null ? null : a.unit(game.unit.key());
        if (img == null) return;

        double t = Math.min(1.0, game.pose / (double) Salvage.SHOCK_MIN);
        double scale = 0.45 + 0.55 * t;
        double tilt = -8.0 * (1.0 - t);
        // The feet sit below the desk line, so the desk covers them and
        // what the player sees is a figure rising rather than a figure
        // standing on the table.
        double bottom = MouseMap.SCENE[1] + MouseMap.SCENE[3] + 26;
        double h = 470 * scale;
        double w = h * (img.getWidth() / img.getHeight());

        double lit = lightLevel();
        double alpha = 0.16 + 0.84 * lit;

        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.translate(W / 2, bottom);
        gc.rotate(tilt);
        gc.drawImage(img, -w / 2, -h, w, h);
        gc.restore();
        gc.setGlobalAlpha(1);

        // The eyes catch the lamp before anything else does. A machine
        // that is about to get up is a machine whose eyes are lit.
        if (lit > 0.5 && game.pose >= Salvage.SHOCK_MIN - 1) {
            double beat = 0.5 + 0.5 * Math.sin(pulse * 3.0);
            gc.setFill(Color.rgb(233, 69, 96, 0.16 * beat * (lit - 0.5) * 2));
            gc.fillRect(0, MouseMap.SCENE[1], W, MouseMap.SCENE[3]);
        }
    }

    /**
     * The desk, over the unit.
     *
     * <p>This is the whole reason the desk is a separate image. The unit is
     * behind it, so the desk is what turns "a figure at a scale" into "a
     * figure rising out of a chair" -- and the amount of it that is above
     * the desk line is the only thing the player has to read at a glance.
     */
    void drawDesk() {
        Assets a = Assets.A;
        if (a == null || a.desk == null) return;
        double lit = lightLevel();
        gc.setGlobalAlpha(0.55 + 0.45 * lit);
        gc.drawImage(a.desk, 0, MouseMap.SCENE[1] + MouseMap.SCENE[3] - a.desk.getHeight(),
                a.desk.getWidth(), a.desk.getHeight());
        gc.setGlobalAlpha(1);
    }

    /** The band: night, clock, agitation, and the shock. */
    void drawBand() {
        gc.setFill(Color.web("#05060A"));
        gc.fillRect(0, 0, W, MouseMap.BAND[3]);
        gc.setStroke(Color.web("#1B1B26"));
        gc.setLineWidth(1);
        gc.strokeLine(0, MouseMap.BAND[3], W, MouseMap.BAND[3]);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText("NIGHT " + night, 26, 44);

        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 12));
        gc.fillText("IN THE CHAIR", 150, 26);
        gc.setFill(Color.web("#FFD700"));
        gc.setFont(Font.font("Arial", 19));
        gc.fillText(game.unit.name().toUpperCase(), 150, 48);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText(game.clock(), W / 2, 44);
        gc.setTextAlign(TextAlignment.LEFT);

        drawMeter();
        drawShockButton();
    }

    /**
     * The agitation meter.
     *
     * <p>This is the only number on the screen the player has to watch, and
     * it is the one the game is about: it is what the lamp costs, and it
     * is what kills you when it runs out. It turns red near the top
     * because a player who is watching the chair is not watching this.
     */
    void drawMeter() {
        double[] r = MouseMap.METER;
        double v = Math.min(1, game.agitation / Salvage.AGITATION_MAX);
        gc.setFill(Color.rgb(255, 255, 255, 0.05));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        Color c = v > 0.75 ? Color.web("#E94560")
                : v > 0.45 ? Color.web("#C9A227") : Color.web("#7FE3B0");
        gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.75));
        gc.fillRect(r[0], r[1], r[2] * v, r[3]);
        gc.setStroke(Color.web("#2A2A38"));
        gc.setLineWidth(1);
        gc.strokeRect(r[0], r[1], r[2], r[3]);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText("AGITATION", r[0], r[1] - 5);
        if (v > 0.75) {
            double beat = 0.5 + 0.5 * Math.sin(pulse * 7.0);
            gc.setFill(Color.rgb(233, 69, 96, 0.35 * beat));
            gc.fillRect(r[0], r[1], r[2], r[3]);
        }
    }

    void drawShockButton() {
        double[] r = MouseMap.SHOCK;
        boolean hot = hover().kind() == MouseMap.Kind.SHOCK;
        boolean live = game.canShock();
        gc.setFill(live ? Color.rgb(233, 69, 96, hot ? 0.30 : 0.18)
                : Color.rgb(255, 255, 255, 0.04));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(live ? Color.web("#E94560") : Color.web("#2A2A38"));
        gc.setLineWidth(hot ? 2 : 1);
        gc.strokeRect(r[0], r[1], r[2], r[3]);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(live ? Color.web("#FFD700") : Color.web("#555566"));
        gc.setFont(Font.font("Arial", 17));
        gc.fillText(live ? "SHOCK  x1" : "SHOCK  SPENT", r[0] + r[2] / 2, r[1] + 25);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** The strip: the lamp, and what it is showing. */
    void drawStrip() {
        gc.setFill(Color.web("#05060A"));
        gc.fillRect(0, MouseMap.BAR[1], W, MouseMap.BAR[3]);

        drawLampButton();

        // What the lamp is showing, or why it is showing nothing.
        Salvage.Pose seen = game.seen();
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 12));
        gc.fillText("THE LAMP IS SHOWING", 270, MouseMap.BAR[1] + 30);

        String line;
        Color colour;
        if (seen != null) {
            line = seen.label.toUpperCase();
            colour = game.pose >= Salvage.SHOCK_MIN ? Color.web("#E94560")
                    : Color.web("#FFD700");
        } else if (game.lit) {
            line = "warming\u2026";
            colour = Color.web("#555577");
        } else {
            line = "nothing. it is dark.";
            colour = Color.web("#555577");
        }
        gc.setFill(colour);
        gc.setFont(Font.font("Arial", 26));
        gc.fillText(line, 270, MouseMap.BAR[1] + 62);

        // The one line of instruction that matters, and it changes with
        // the state of the night.
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 12));
        String hint;
        if (game.status != Salvage.Status.PLAYING) {
            hint = "ESC to the menu";
        } else if (game.pose >= Salvage.SHOCK_MIN) {
            hint = "it is up. the shock reaches it now -- and not before.";
        } else if (game.lit) {
            hint = "the lamp is what it is waiting for. every second of it "
                    + "is charged.";
        } else if (game.agitation > 0.6) {
            hint = "the lamp has cost a lot. it does not have to be lit to be "
                    + "listened to.";
        } else {
            hint = "L lamp     SPACE shock     ESC pause     "
                    + "a drag is the sound of it moving; a creak is the building.";
        }
        gc.fillText(hint, 270, MouseMap.BAR[1] + 92);

        // The two counters, at the right, where a player can ignore them.
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFill(Color.web("#3A3A4C"));
        gc.setFont(Font.font("Arial", 12));
        gc.fillText("looks " + game.looks + "     drags heard " + game.drags
                + "     silent " + game.silentSteps, W - 26, MouseMap.BAR[1] + 92);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawLampButton() {
        double[] r = MouseMap.LAMP;
        boolean hot = hover().kind() == MouseMap.Kind.LAMP;
        boolean on = game.lit;
        gc.setFill(on ? Color.rgb(255, 214, 150, hot ? 0.32 : 0.20)
                : Color.rgb(255, 255, 255, hot ? 0.12 : 0.05));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(on ? Color.web("#FFD700") : Color.web("#3A3A4C"));
        gc.setLineWidth(hot ? 2 : 1);
        gc.strokeRect(r[0], r[1], r[2], r[3]);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(on ? Color.web("#FFD700") : Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 18));
        gc.fillText(on ? "LAMP  ON" : "LAMP  OFF", r[0] + r[2] / 2, r[1] + 36);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawJumpscare() {
        Assets a = Assets.A;
        gc.setFill(Color.rgb(0, 0, 0, Math.min(1, scareT * 1.4)));
        gc.fillRect(0, 0, W, H);
        Image face = a == null ? null : a.scare(game.unit.key());
        if (face != null) {
            double s = Math.max(W / face.getWidth(), H / face.getHeight()) * 1.1;
            double w = face.getWidth() * s, h = face.getHeight() * s;
            gc.drawImage(face, (W - w) / 2, (H - h) / 2, w, h);
        }
        gc.setFill(Color.rgb(233, 69, 96, 0.22));
        gc.fillRect(0, 0, W, H);
        if (scareT > 1.4) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(Color.web("#E94560"));
            gc.setFont(Font.font("Arial", 44));
            gc.fillText("GAME OVER", W / 2, H / 2);
            gc.setFill(Color.web("#8888AA"));
            gc.setFont(Font.font("Arial", 16));
            gc.fillText("ESC to the menu", W / 2, H / 2 + 40);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    void drawWin() {
        boolean destroyed = game.status == Salvage.Status.DESTROYED;
        gc.setFill(Color.rgb(0, 0, 0, 0.84));
        gc.fillRect(0, 0, W, H);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web(destroyed ? "#E94560" : "#FFD700"));
        gc.setFont(Font.font("Arial", 50));
        gc.fillText(destroyed ? "SALVAGE DESTROYED" : "6 AM", W / 2, H / 2 - 84);
        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText(destroyed
                        ? "It got up, and you were ready."
                        : "Night " + night + " survived. It never moved.",
                W / 2, H / 2 - 36);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText(game.looks + " looks, " + game.drags + " drags heard, "
                + game.silentSteps + " steps taken without a sound.",
                W / 2, H / 2 + 4);
        gc.fillText("The lamp cost " + Math.round(game.agitation * 100)
                + "% of what it takes to wake it.", W / 2, H / 2 + 30);
        gc.fillText("ESC to the menu", W / 2, H / 2 + 66);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
