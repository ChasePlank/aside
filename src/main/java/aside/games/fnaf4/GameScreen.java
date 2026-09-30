package aside.games.fnaf4;

import aside.games.fnaf4.engine.Game;
import aside.games.fnaf4.engine.Room;
import aside.games.fnaf4.engine.Threat;
import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The FNAF 4 room.
 *
 * One picture at a time, and which picture it is depends on two things:
 * where you are standing and whether the light is on. There is no monitor
 * and there are no cameras -- the whole screen is the one place you are
 * looking at, which is the point of the game.
 *
 * <pre>
 *   [ the station you are at, dark or lit ]
 *   ------------------------------------------------
 *   [ the bed ][ left ][ right ][ closet ]
 * </pre>
 *
 * Controls:
 *   A / D / W / S or the arrow keys   look left, right, ahead, behind
 *   1 2 3 4                           the bed, left, right, closet
 *   SPACE                             the flashlight
 *   ESC                               pause
 *
 * And the same thing with a mouse, because FNAF is a point-and-click game:
 *
 *   click a station in the strip   walk there
 *   click anywhere in the room     the flashlight
 *
 * Where a click lands is decided by {@link MouseMap}, which is pure
 * geometry so the self-test can check every region without a display.
 *
 * THE EDGE GLOW. The game's real channel is the audio: each threat has its
 * own breath and its own step, and learning them is how you play. But a
 * game whose only warning is a sound file is a game that is unplayable
 * with the sound off and unplayable if the file is missing, so the same
 * information is also drawn -- a slow red pulse along the edge of the
 * screen nearest whatever is standing in a doorway, and a fainter amber
 * one for whatever is one move out. It is not a HUD: it says nothing
 * about what is there or how long you have, only that the room is not
 * empty, which is what a child in a dark bedroom would actually feel.
 */
public class GameScreen extends UiScreen {

    final Game game;
    final int night;

    double scareT = 0;
    double pulse = 0;
    double hoverX = -1, hoverY = -1;
    /** Set for a moment after a flash that found nothing. */
    double wasteT = 0;

    /** Dev hooks: freeze the loop so a staged frame stays staged. */
    boolean frozen = false;

    public GameScreen(UiManager ui, int night) {
        super(ui);
        this.night = night;
        this.game = new Game(night, System.nanoTime());
        devHook();
    }

    /**
     * Dev hooks, for verifying frames that a 90-frame snapshot cannot
     * reach on its own. Every one of them is additive and off by default.
     *
     * `-Daside.fnaf4.freeze=1` stops the loop, because a staged frame
     * without it is a second out of date by the time the snapshot lands --
     * the mistake FNAF 2 and FNAF 3 have each already paid for once.
     */
    void devHook() {
        frozen = System.getProperty("aside.fnaf4.freeze") != null;

        String at = System.getProperty("aside.fnaf4.at");
        if (at != null) {
            Room.Where w = where(at);
            if (w != null) game.where = w;
        }
        if (System.getProperty("aside.fnaf4.lit") != null) game.lit = 99;

        String here = System.getProperty("aside.fnaf4.here");
        if (here != null) {
            Threat t = threat(here);
            if (t != null) { t.distance = 0; t.hereFor = 0; }
        }
        String near = System.getProperty("aside.fnaf4.near");
        if (near != null) {
            Threat t = threat(near);
            if (t != null) t.distance = 1;
        }
        String fb = System.getProperty("aside.fnaf4.fredbear");
        if (fb != null) {
            Room.Where w = where(fb);
            if (w != null) { game.fredbearAt = w; game.fredbearHere = 0; }
        }
        String noise = System.getProperty("aside.fnaf4.noise");
        if (noise != null) game.noise = Double.parseDouble(noise);

        if (System.getProperty("aside.fnaf4.scare") != null) {
            game.status = Game.Status.JUMPSCARED;
            game.killer = System.getProperty("aside.fnaf4.scare", "Nightmare Freddy");
            if (game.killer.isEmpty() || game.killer.equals("1")) {
                game.killer = "Nightmare Freddy";
            }
            scareT = 1.0;
        }
        if (System.getProperty("aside.fnaf4.win") != null) {
            game.status = Game.Status.SURVIVED;
            game.hour = 6;
        }
    }

    /** A station by name. Spaces and case do not matter -- a dev hook
     *  should not make you remember where the spaces are. */
    static Room.Where where(String name) {
        String n = name.replace(" ", "").toLowerCase();
        for (Room.Where w : Room.ALL) {
            if (w.name().toLowerCase().equals(n)) return w;
        }
        return switch (n) {
            case "leftdoor" -> Room.Where.LEFT;
            case "rightdoor" -> Room.Where.RIGHT;
            case "room" -> Room.Where.BED;
            default -> null;
        };
    }

    Threat threat(String key) {
        String k = key.replace(" ", "").toLowerCase();
        for (Threat t : game.threats) {
            if (t.key.equals(k) || t.name.replace(" ", "").toLowerCase().contains(k)) return t;
        }
        return null;
    }

    @Override public Parent getRoot() { return root; }

    @Override
    public void tick(double dt) {
        double frame = Math.min(dt, 0.25);
        if (!frozen) game.update(frame);
        for (String cue : game.drainCues()) play(cue);

        pulse += frame;
        if (wasteT > 0) wasteT = Math.max(0, wasteT - frame);
        if (game.status == Game.Status.JUMPSCARED) scareT += frame;
        render();
    }

    /**
     * One cue, with the right fallback.
     *
     * The per-character names are the point -- a breath you cannot tell
     * apart from another breath carries no information -- but a cue that
     * resolves to nothing is worse than a cue that resolves to the wrong
     * file, so each family falls back to the generic sound it came from.
     */
    static void play(String cue) {
        if (cue.startsWith("breath_")) Audio.A.sfx(cue, "at_door");
        else if (cue.startsWith("step_")) Audio.A.sfx(cue, "footstep");
        else Audio.A.sfx(cue);
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case SPACE -> { flash(); e.consume(); }
            case A, LEFT -> { game.moveTo(Room.Where.LEFT); e.consume(); }
            case D, RIGHT -> { game.moveTo(Room.Where.RIGHT); e.consume(); }
            case W, UP -> { game.moveTo(Room.Where.CLOSET); e.consume(); }
            case S, DOWN -> { game.moveTo(Room.Where.BED); e.consume(); }
            case ESCAPE -> { ui.push(new PauseScreen(ui, this)); e.consume(); }
            default -> {
                // Read the KEY CODE, not the typed character. A synthetic
                // key event carries no text, so a getText() selector
                // silently does nothing under the engine's snapshot mode.
                Room.Where w = switch (e.getCode()) {
                    case DIGIT1 -> Room.Where.BED;
                    case DIGIT2 -> Room.Where.LEFT;
                    case DIGIT3 -> Room.Where.RIGHT;
                    case DIGIT4 -> Room.Where.CLOSET;
                    default -> null;
                };
                if (w != null) { game.moveTo(w); e.consume(); }
            }
        }
    }

    void flash() {
        boolean found = game.flash();
        if (!found && game.flashes > 0) wasteT = 0.5;
    }

    // ---- Mouse ----

    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        hoverX = x;
        hoverY = y;
        if (!pressed) return;
        MouseMap.Hit h = MouseMap.hit(x, y);
        switch (h.kind()) {
            case STATION -> game.moveTo(Room.ALL[h.index()]);
            case SCENE -> flash();
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

        Assets a = Assets.A;
        if (a != null) drawRoom(a);
        drawEdges();
        drawTopBand();
        drawStrip();
        if (game.status == Game.Status.JUMPSCARED) drawJumpscare(a);
        if (game.status == Game.Status.SURVIVED) drawWin();
    }

    /**
     * The station you are looking at.
     *
     * While you are walking you are looking at where you are going, dark,
     * because the light is off and both hands are on the floor. That is
     * not a rendering shortcut -- it is the cost of the move, made visible.
     */
    void drawRoom(Assets a) {
        Room.Where view = game.moving() ? game.heading : game.where;
        boolean lit = game.lit > 0 && !game.moving();

        boolean fredbearHere = lit && game.fredbearAt == view;
        boolean occupied = lit && (fredbearHere || game.reachable());

        Assets.State st = !lit ? Assets.State.DARK
                : occupied ? Assets.State.HERE : Assets.State.LIT;
        Image img = a.scene(view, st);
        if (img == null) img = a.scene(view, Assets.State.DARK);
        if (img != null) gc.drawImage(img, 0, 0, W, MouseMap.SCENE[3]);

        // Fredbear is drawn over the empty room rather than as a scene of
        // his own, because he is the one thing in the game that is not tied
        // to a place. The room does not change when he arrives; he is just
        // in it.
        if (fredbearHere && a.fredbear != null) {
            Image empty = a.scene(view, Assets.State.LIT);
            if (empty != null) gc.drawImage(empty, 0, 0, W, MouseMap.SCENE[3]);
            double h = MouseMap.SCENE[3] * 0.92;
            double w = a.fredbear.getWidth() * h / a.fredbear.getHeight();
            gc.drawImage(a.fredbear, (W - w) / 2, MouseMap.SCENE[3] - h, w, h);
        }

        // A flash that found nothing is worth showing, because the whole
        // cost of the game is paid in flashes that found nothing.
        if (wasteT > 0) {
            gc.setFill(Color.rgb(255, 255, 255, 0.05 * (wasteT / 0.5)));
            gc.fillRect(0, 0, W, MouseMap.SCENE[3]);
        }

        if (game.moving()) drawMoveBar();
    }

    /** How far through the walk you are. */
    void drawMoveBar() {
        double p = game.busyTotal <= 0 ? 0 : 1 - (game.busy / game.busyTotal);
        double w = 320, x = (W - w) / 2, y = MouseMap.SCENE[3] - 26;
        gc.setFill(Color.rgb(0, 0, 0, 0.55));
        gc.fillRect(x, y, w, 6);
        gc.setFill(Color.web("#8A8AA8"));
        gc.fillRect(x, y, w * Math.max(0, Math.min(1, p)), 6);
    }

    /**
     * The edge glow.
     *
     * One edge per station, and the pulse is slow on purpose: a fast one
     * reads as a timer and this is meant to read as a room. Red for
     * something standing there, amber for something one move out, and
     * nothing at all for the places that are quiet.
     */
    void drawEdges() {
        double beat = 0.5 + 0.5 * Math.sin(pulse * 4.4);
        for (Threat t : game.threats) {
            if (t.distance > 1) continue;
            boolean here = t.distance == 0;
            double alpha = (here ? 0.50 : 0.24) * (0.55 + 0.45 * beat);
            Color c = here ? Color.web("#E94560") : Color.web("#C9A227");
            edge(t.home, c, alpha);
        }
        if (game.fredbearAt != null) {
            edge(game.fredbearAt, Color.web("#B03A8A"), 0.38 * (0.55 + 0.45 * beat));
        }
    }

    /** A gradient along the edge of the scene nearest a station. */
    void edge(Room.Where w, Color c, double alpha) {
        double top = 0, left = 0, wdt = W, hgt = MouseMap.SCENE[3];
        double depth = 150;
        LinearGradient g;
        switch (w) {
            case LEFT -> {
                g = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)),
                        new Stop(1, Color.TRANSPARENT));
                gc.setFill(g);
                gc.fillRect(0, 0, depth, hgt);
            }
            case RIGHT -> {
                g = new LinearGradient(1, 0, 0, 0, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)),
                        new Stop(1, Color.TRANSPARENT));
                gc.setFill(g);
                gc.fillRect(W - depth, 0, depth, hgt);
            }
            case CLOSET -> {
                g = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)),
                        new Stop(1, Color.TRANSPARENT));
                gc.setFill(g);
                gc.fillRect(0, 0, W, depth);
            }
            case BED -> {
                g = new LinearGradient(0, 1, 0, 0, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)),
                        new Stop(1, Color.TRANSPARENT));
                gc.setFill(g);
                gc.fillRect(0, hgt - depth, W, depth);
            }
        }
    }

    void drawTopBand() {
        // A scrim, because the room behind the numbers is sometimes a lit
        // closet and the clock has to be readable in every one of them.
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 0, 0, 0.62)),
                new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(0, 0, W, 96);

        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 22));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("NIGHT " + night, 40, 44);

        String clock = switch (Math.min(game.hour, 6)) {
            case 0 -> "12 AM"; case 1 -> "1 AM"; case 2 -> "2 AM";
            case 3 -> "3 AM"; case 4 -> "4 AM"; case 5 -> "5 AM";
            default -> "6 AM";
        };
        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 22));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(clock, W - 40, 44);
        gc.setTextAlign(TextAlignment.LEFT);

        // The noise meter. It is drawn because Fredbear's arrival rate is
        // the one number in the game the player can move, and a cost you
        // cannot see is not a decision.
        double[] r = MouseMap.NOISE;
        gc.setFill(Color.web("#555566"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText("NOISE", r[0], r[1] - 5);
        gc.setFill(Color.rgb(255, 255, 255, 0.10));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        double p = game.noise / Game.NOISE_MAX;
        gc.setFill(p > 0.6 ? Color.web("#E94560") : Color.web("#C9A227"));
        gc.fillRect(r[0], r[1], r[2] * p, r[3]);
    }

    /** The strip: where you are, and where you could be. */
    void drawStrip() {
        gc.setFill(Color.web("#07070C"));
        gc.fillRect(MouseMap.BAR[0], MouseMap.BAR[1], MouseMap.BAR[2], MouseMap.BAR[3]);

        MouseMap.Hit h = hover();
        for (int i = 0; i < Room.ALL.length; i++) {
            Room.Where w = Room.ALL[i];
            double[] r = MouseMap.station(i);
            boolean on = w == game.where;
            boolean going = game.moving() && w == game.heading;
            boolean hot = h.kind() == MouseMap.Kind.STATION && h.index() == i;

            gc.setFill(on ? Color.rgb(233, 69, 96, 0.20)
                    : hot ? Color.rgb(255, 255, 255, 0.08)
                    : Color.rgb(255, 255, 255, 0.03));
            gc.fillRect(r[0], r[1], r[2], r[3]);
            gc.setStroke(on ? Color.web("#E94560")
                    : going ? Color.web("#8A8AA8") : Color.web("#2A2A38"));
            gc.setLineWidth(on || hot ? 2 : 1);
            gc.strokeRect(r[0], r[1], r[2], r[3]);

            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(on ? Color.web("#FFD700") : Color.web("#9A9AAE"));
            gc.setFont(Font.font("Arial", 17));
            gc.fillText(label(w), r[0] + r[2] / 2, r[1] + 24);
            gc.setFill(Color.web("#555566"));
            gc.setFont(Font.font("Arial", 12));
            gc.fillText(hint(w), r[0] + r[2] / 2, r[1] + 43);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    static String label(Room.Where w) {
        return switch (w) {
            case BED -> "THE BED";
            case LEFT -> "LEFT DOOR";
            case RIGHT -> "RIGHT DOOR";
            case CLOSET -> "THE CLOSET";
        };
    }

    static String hint(Room.Where w) {
        return switch (w) {
            case BED -> "S";
            case LEFT -> "A";
            case RIGHT -> "D";
            case CLOSET -> "W";
        };
    }

    void drawJumpscare(Assets a) {
        gc.setFill(Color.rgb(0, 0, 0, Math.min(1, scareT * 1.4)));
        gc.fillRect(0, 0, W, H);
        Image face = a == null ? null : a.scare(game.killer);
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
        gc.setFill(Color.rgb(0, 0, 0, 0.82));
        gc.fillRect(0, 0, W, H);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#FFD700"));
        gc.setFont(Font.font("Arial", 52));
        gc.fillText("6 AM", W / 2, H / 2 - 60);
        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText("Night " + night + " survived.", W / 2, H / 2 - 14);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText(game.flashes + " flashes, " + game.wastedFlashes
                + " of them at nothing.", W / 2, H / 2 + 26);
        gc.fillText("ESC to the menu", W / 2, H / 2 + 58);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
