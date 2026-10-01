package aside.games.fnaf8;

import aside.games.fnaf8.engine.Meeting;
import aside.games.fnaf8.engine.Meeting.Setting;
import aside.games.fnaf8.engine.Meeting.Side;
import aside.games.fnaf8.engine.Unit;
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
 * The FNAF 8 office.
 *
 * <pre>
 *   [ NIGHT 3   12 AM          [ how far apart they are ] ]          the band
 *   -----------------------------------------------------------------
 *   [ two doorways, one lamp, and two things that are not yours ]    the room
 *   -----------------------------------------------------------------
 *   [ LOOK L ][ LOOK R ][ OFF ][ PUSH L ][ PUSH R ]                  the strip
 * </pre>
 *
 * <p>Controls:
 * <pre>
 *   A / LEFT    look at the left hall        (dim: cheap, shows you where it is)
 *   D / RIGHT   look at the right hall
 *   Z           push the left hall back      (bright: expensive, moves it)
 *   C           push the right hall back
 *   SPACE       lamp out
 *   ESC         pause
 * </pre>
 *
 * <p><b>The distance readout is the game, and it is drawn as a bar with the
 * threshold marked on it rather than as a number.</b> A number would say
 * how far apart they are; the bar says how far apart they are <i>against
 * the only distance that matters</i>, which is the one where they can see
 * each other. Everything else on the screen is a fact about them. This is
 * the one fact about the night.
 *
 * <p>The doorway is drawn as a pair of eyes and never as a figure, and the
 * lamp does not change that. A unit standing in a doorway is the loudest
 * fact in the office -- it is the thing the whole night is spent avoiding
 * -- and it is drawn the way the franchise has always drawn it: two points
 * of light in a hole, at the height a head would be.
 */
public class GameScreen extends UiScreen {

    /** The doorways, matching tools/fnaf8-art.py. */
    static final double DOOR_W = 0.26 * W;
    static final double DOOR_L = 0.03 * W;
    static final double DOOR_R = W - 0.03 * W - DOOR_W;
    static final double DOOR_TOP = 0.14 * H;
    static final double DOOR_BOTTOM = 0.80 * H;

    final Meeting game;
    final int night;

    double scareT = 0;
    double pulse = 0;
    double hoverX = -1, hoverY = -1;
    /** Set for a moment after a push lands. */
    double pushT = 0;
    /** Shake, in pixels, while they are finding each other. */
    double shake = 0;

    /** Dev hooks: freeze the loop so a staged frame stays staged. */
    boolean frozen = false;

    public GameScreen(UiManager ui, int night) {
        super(ui);
        this.night = night;
        this.game = new Meeting(night, System.nanoTime());
        devHook();
    }

    /**
     * Dev hooks, for verifying frames a 90-frame snapshot cannot reach on
     * its own. Every one of them is additive and off by default.
     *
     * <p>`-Daside.fnaf8.freeze=1` stops the loop, because a staged frame
     * without it is a second out of date by the time the snapshot lands --
     * the mistake FNAF 2, 3, 4, 5, 6 and 7 have each already paid for once.
     */
    void devHook() {
        frozen = System.getProperty("aside.fnaf8.freeze") != null;

        String l = System.getProperty("aside.fnaf8.left");
        if (l != null) game.d[Side.LEFT.ordinal()] = Integer.parseInt(l);
        String r = System.getProperty("aside.fnaf8.right");
        if (r != null) game.d[Side.RIGHT.ordinal()] = Integer.parseInt(r);

        String lamp = System.getProperty("aside.fnaf8.lamp");
        if (lamp != null) {
            String[] parts = lamp.split(":");
            game.lampSide = parts[0].equalsIgnoreCase("left") ? Side.LEFT : Side.RIGHT;
            game.lamp = parts.length > 1
                    ? (parts[1].equalsIgnoreCase("bright") ? Setting.BRIGHT : Setting.DIM)
                    : Setting.DIM;
        }
        String sw = System.getProperty("aside.fnaf8.swivel");
        if (sw != null) {
            game.swivel = Double.parseDouble(sw);
            game.swivelTo = game.lampSide.other();
            game.lamp = Setting.OFF;
        }
        if (System.getProperty("aside.fnaf8.scare") != null) {
            game.status = Meeting.Status.MET;
            game.metSide = Side.LEFT;
            scareT = 1.0;
        }
        if (System.getProperty("aside.fnaf8.win") != null) {
            game.status = Meeting.Status.SURVIVED;
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
        if (pushT > 0) pushT = Math.max(0, pushT - frame);
        if (game.status == Meeting.Status.MET) scareT += frame;
        shake = game.status == Meeting.Status.MET ? 10 : 0;
        render();
    }

    /** One cue, with the right fallback. */
    static void play(String cue) {
        switch (cue) {
            // One cue for both halls, and that is the game rather than a
            // shortcut: a footfall carries no direction, because if it did
            // the lamp would stop being the only way to know where either
            // of them is. FNAF 7 owns step_left and step_right and they are
            // deliberately two different sounds; this game must not use
            // them.
            case "f8_step" -> Audio.A.sfx("f8_step", "footstep");
            case "f8_back" -> Audio.A.sfx("f8_back", "footstep");
            case "f8_push" -> Audio.A.sfx("f8_push", "lamp_push");
            case "f8_door" -> Audio.A.sfx("f8_door", "door_knock");
            case "f8_gives_up" -> Audio.A.sfx("f8_gives_up", "f8_back");
            case "f8_swivel" -> Audio.A.sfx("f8_swivel", "f8_set");
            case "f8_set" -> Audio.A.sfx("f8_set", "light_click");
            case "f8_dim" -> Audio.A.sfx("f8_dim", "light_click");
            case "f8_bright" -> Audio.A.sfx("f8_bright", "light_click");
            case "f8_off" -> Audio.A.sfx("f8_off", "light_click");
            case "f8_met" -> Audio.A.sfx("f8_met", "scare_sprint");
            default -> Audio.A.sfx(cue);
        }
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case A, LEFT -> { look(Side.LEFT); e.consume(); }
            case D, RIGHT -> { look(Side.RIGHT); e.consume(); }
            case Z -> { push(Side.LEFT); e.consume(); }
            case C -> { push(Side.RIGHT); e.consume(); }
            case SPACE -> { game.dark(); e.consume(); }
            case ESCAPE -> { ui.push(new PauseScreen(ui, this)); e.consume(); }
            default -> { }
        }
    }

    void look(Side s) { game.aim(s, Setting.DIM); }

    void push(Side s) {
        if (game.aim(s, Setting.BRIGHT)) pushT = 0.25;
    }

    // ---- Mouse ----

    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        hoverX = x;
        hoverY = y;
        if (!pressed) return;
        switch (MouseMap.hit(x, y).kind()) {
            case LOOK_L -> look(Side.LEFT);
            case LOOK_R -> look(Side.RIGHT);
            case OFF -> game.dark();
            case PUSH_L -> push(Side.LEFT);
            case PUSH_R -> push(Side.RIGHT);
            default -> { }
        }
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
        drawUnit(Side.LEFT);
        drawUnit(Side.RIGHT);
        drawEyes(Side.LEFT);
        drawEyes(Side.RIGHT);
        gc.restore();

        drawBand();
        drawStrip();

        if (game.status == Meeting.Status.MET) drawScare();
        else if (game.status == Meeting.Status.SURVIVED) drawSixAm();
    }

    /** The lamp's pool on the hall it is pointed at. */
    void drawHallGlow() {
        if (game.lamp == Setting.OFF || game.swivel > 0) return;
        double x = game.lampSide == Side.LEFT ? DOOR_L : DOOR_R;
        double alpha = game.lamp == Setting.BRIGHT ? 0.46 : 0.22;
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
     * One of them, in its hall, at the distance the engine says.
     *
     * <p>Scale is the whole readout. A unit one step from the doorway fills
     * it; a unit at the far end is a small dark shape at the back of a lit
     * corridor. That is the one thing the player has to be able to judge
     * without reading a number, and it is why there is no number.
     */
    void drawUnit(Side s) {
        if (!game.visible(s)) return;
        Assets a = Assets.A;
        if (a == null) return;
        Unit u = game.pair.unit(s);
        Image img = a.unit(u.key());
        if (img == null) return;

        int d = game.distance(s);
        double x = s == Side.LEFT ? DOOR_L : DOOR_R;
        double avail = DOOR_BOTTOM - DOOR_TOP;
        // 1.00 at the doorway, 0.55 at the far end.
        double depth = 1.0 - 0.1125 * (d - 1);
        double box = avail * depth;
        double sc = Math.min(box / img.getHeight(), DOOR_W * depth / img.getWidth());
        double w = img.getWidth() * sc, h = img.getHeight() * sc;
        double dx = x + (DOOR_W - w) / 2;
        double dy = DOOR_BOTTOM - h;

        gc.setGlobalAlpha(0.30 + 0.70 * depth);
        gc.drawImage(img, dx, dy, w, h);
        gc.setGlobalAlpha(1);
    }

    /**
     * Two points of light in a doorway.
     *
     * <p>Drawn whether or not the lamp is on that hall, and that is the
     * point: <b>the doorway is the one thing in this office you never have
     * to spend a look on.</b> What you have to spend a look on is how far
     * away the other one is, and the other one is the one that decides
     * whether the eyes in the doorway are a problem or a countdown.
     */
    void drawEyes(Side s) {
        if (!game.waiting(s)) return;
        double x = s == Side.LEFT ? DOOR_L : DOOR_R;
        double cx = x + DOOR_W / 2;
        double cy = DOOR_TOP + (DOOR_BOTTOM - DOOR_TOP) * 0.30;
        double sep = DOOR_W * 0.16;
        double r = 5.5 + 1.2 * Math.sin(pulse * 3.1);
        double left = game.patienceLeft(s);
        // The eyes dim as the patience runs out: the thing in the doorway
        // is losing interest, and the player has to be able to see that
        // without being told a number.
        double glow = 0.45 + 0.55 * left;
        for (int i = -1; i <= 1; i += 2) {
            double ex = cx + i * sep;
            gc.setFill(new RadialGradient(0, 0, ex, cy, r * 4.5, false,
                    CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(255, 90, 70, 0.55 * glow)),
                    new Stop(1.0, Color.rgb(255, 90, 70, 0.0))));
            gc.fillOval(ex - r * 4.5, cy - r * 4.5, r * 9, r * 9);
            gc.setFill(Color.rgb(255, 226, 200, glow));
            gc.fillOval(ex - r / 2, cy - r / 2, r, r);
        }
    }

    /** The band: night, clock, and how far apart they are. */
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

        drawApart();
    }

    /**
     * How far apart they are, against the only distance that matters.
     *
     * <p>The bar is the sum of the two distances from the doors, and the
     * red band at the left is where they can see each other. A player who
     * watches nothing else on the screen can play this game off this bar
     * alone -- which is the point of drawing it, and also the reason the
     * night is hard: <b>the bar tells you the sum, and the sum is not the
     * thing you can act on.</b> You can only move one of the two numbers,
     * and which one you move is the whole game.
     */
    void drawApart() {
        double[] r = MouseMap.APART;
        gc.setFill(Color.rgb(255, 255, 255, 0.05));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(Color.rgb(233, 69, 96, 0.45));
        gc.strokeRect(r[0], r[1], r[2], r[3]);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("#777790"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText("HOW FAR APART THEY ARE", r[0] + 12, r[1] + 17);

        double x0 = r[0] + 12, y0 = r[1] + 26, bw = r[2] - 24, bh = 12;
        double full = 2.0 * Meeting.MAX;
        gc.setFill(Color.rgb(255, 255, 255, 0.08));
        gc.fillRect(x0, y0, bw, bh);
        // The danger band: at or below MEET they can see each other.
        double meetW = bw * (Meeting.MEET / full);
        gc.setFill(Color.rgb(233, 69, 96, 0.30));
        gc.fillRect(x0, y0, meetW, bh);
        gc.setStroke(Color.rgb(233, 69, 96, 0.75));
        gc.strokeLine(x0 + meetW, y0 - 3, x0 + meetW, y0 + bh + 3);

        int apart = game.apart();
        double w = bw * Math.min(1.0, apart / full);
        gc.setFill(apart <= Meeting.MEET + 1 ? Color.web("#E94560")
                : apart <= Meeting.MEET + 3 ? Color.web("#FFD27F")
                : Color.web("#7FD1AE"));
        gc.fillRect(x0, y0, w, bh);
        gc.setStroke(Color.rgb(255, 255, 255, 0.22));
        gc.strokeRect(x0, y0, bw, bh);

        gc.setFill(Color.web("#555570"));
        gc.setFont(Font.font("Arial", 10));
        gc.fillText("TOO CLOSE", x0, y0 + bh + 14);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText("APART", x0 + bw, y0 + bh + 14);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** The strip: the five things you can do, and what the lamp is doing. */
    void drawStrip() {
        gc.setFill(Color.rgb(4, 4, 8, 0.90));
        gc.fillRect(0, 596, W, 124);
        gc.setStroke(Color.rgb(233, 69, 96, 0.35));
        gc.strokeLine(0, 596, W, 596);

        boolean dimL = game.lamp == Setting.DIM && game.lampSide == Side.LEFT && game.swivel <= 0;
        boolean dimR = game.lamp == Setting.DIM && game.lampSide == Side.RIGHT && game.swivel <= 0;
        boolean briL = game.lamp == Setting.BRIGHT && game.lampSide == Side.LEFT && game.swivel <= 0;
        boolean briR = game.lamp == Setting.BRIGHT && game.lampSide == Side.RIGHT && game.swivel <= 0;

        button(MouseMap.LOOK_L, "LOOK LEFT", dimL, false);
        button(MouseMap.LOOK_R, "LOOK RIGHT", dimR, false);
        button(MouseMap.OFF, "LAMP OFF", game.lamp == Setting.OFF && game.swivel <= 0, false);
        button(MouseMap.PUSH_L, "PUSH LEFT", briL, false);
        button(MouseMap.PUSH_R, "PUSH RIGHT", briR, false);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#8A8FA8"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText(whatTheLampIsDoing(), W / 2, 618);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText("A / D  look      Z / C  push      SPACE  lamp out      ESC  pause",
                W / 2, 700);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    String whatTheLampIsDoing() {
        if (game.swivel > 0) return "the lamp is crossing";
        if (game.lamp == Setting.OFF) return "the lamp is out -- both of them are walking";
        String side = game.lampSide.quiet;
        if (game.lamp == Setting.BRIGHT) {
            return "pushing the " + side + " hall -- the other one is hurrying";
        }
        return "looking at the " + side + " hall";
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
        Unit u = game.pair.unit(game.metSide == null ? Side.LEFT : game.metSide);
        Image img = a.scare(u.key());
        if (img == null) return;
        double t = Math.min(1.0, scareT * 3);
        gc.setGlobalAlpha(t);
        gc.drawImage(img, 0, 0, W, H);
        gc.setGlobalAlpha(1);
        // A scrim top and bottom rather than over the whole frame: the face
        // is the picture, and a caption across it is a caption that makes
        // the one frame the game has spent the whole night earning
        // unreadable.
        gc.setFill(Color.rgb(0, 0, 0, 0.72 * t));
        gc.fillRect(0, H - 190, W, 190);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 36));
        gc.fillText("THEY FOUND EACH OTHER", W / 2, H - 108);
        gc.setFill(Color.web("#9A9AAE"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText("It was never about you.", W / 2, H - 70);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText("ESC to pause, then Quit to Menu", W / 2, H - 44);
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
        gc.fillText("They never found each other. The building is empty again.",
                W / 2, H / 2 + 34);
        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText("ESC to pause, then Quit to Menu", W / 2, H / 2 + 70);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
