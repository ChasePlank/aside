package aside.games.fnaf5;

import aside.games.fnaf5.engine.Game;
import aside.games.fnaf5.engine.Room;
import aside.games.fnaf5.engine.Threat;
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
 * The FNAF 5 monitor.
 *
 * One picture at a time, and it is never the room you are standing in.
 * That is the whole game stated as a rendering rule: the feed is a window
 * onto somewhere else, and the place you actually are has no picture at
 * all -- only a name in the strip and whatever you can hear.
 *
 * <pre>
 *   [ NIGHT 3        12 AM        SHOCK x4 ]   the band
 *   ------------------------------------------------
 *   [ the camera feed, 1280x526 ]              somewhere else
 *   ------------------------------------------------
 *   [ &lt; MOVE ][PARTS][BALLORA][CONTROL][CIRCUS][AUDIT.][ MOVE &gt; ]
 * </pre>
 *
 * Controls:
 *   A / D or the arrow keys   walk one room left or right
 *   1 2 3 4 5                 put the camera on that room
 *   SPACE                     the controlled shock
 *   M                         monitor up / down
 *   ESC                       pause
 *
 * And the same thing with a mouse, because FNAF is a point-and-click game:
 *
 *   click a tile in the strip   put the camera on that room
 *   click MOVE &lt; or MOVE &gt;     walk one room
 *   click SHOCK                 the controlled shock
 *
 * Where a click lands is decided by {@link MouseMap}, which is pure
 * geometry so the self-test can check every region without a display.
 *
 * THE EDGE GLOW. The game's real channel is the audio: each threat has its
 * own voice, and learning them is how you play. But a game whose only
 * warning is a sound file is a game that is unplayable with the sound off,
 * so the same information is also drawn -- a red pulse along the edge of
 * the frame for whatever is standing in the room with you, and a fainter
 * amber one for whatever is one door away. It says nothing about what is
 * there or how long you have, only that the building is not empty.
 */
public class GameScreen extends UiScreen {

    final Game game;
    final int night;

    double scareT = 0;
    double pulse = 0;
    double hoverX = -1, hoverY = -1;
    /** Set for a moment after a shock that found nothing. */
    double wasteT = 0;
    /** Set for a moment after a refused step. */
    double refuseT = 0;

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
     * `-Daside.fnaf5.freeze=1` stops the loop, because a staged frame
     * without it is a second out of date by the time the snapshot lands --
     * the mistake FNAF 2, 3 and 4 have each already paid for once.
     */
    void devHook() {
        frozen = System.getProperty("aside.fnaf5.freeze") != null;

        String at = System.getProperty("aside.fnaf5.at");
        if (at != null) {
            Room.Where w = where(at);
            if (w != null) game.where = w;
        }
        String cam = System.getProperty("aside.fnaf5.cam");
        if (cam != null) {
            Room.Where w = where(cam);
            if (w != null && w != game.where) game.camera = w;
        }
        if (System.getProperty("aside.fnaf5.monitoroff") != null) game.monitorOn = false;
        String shocks = System.getProperty("aside.fnaf5.shocks");
        if (shocks != null) game.shocks = Integer.parseInt(shocks);

        for (Threat t : game.threats) {
            String room = System.getProperty("aside.fnaf5." + t.key);
            if (room == null) continue;
            Room.Where w = where(room);
            if (w != null) t.room = w;
        }
        String heard = System.getProperty("aside.fnaf5.heard");
        if (heard != null) {
            Room.Where w = where(heard);
            if (w != null) {
                game.heardAt = w;
                game.heardKey = System.getProperty("aside.fnaf5.heardkey", "freddy");
                game.heardAge = 0;
            }
        }

        if (System.getProperty("aside.fnaf5.scare") != null) {
            game.status = Game.Status.JUMPSCARED;
            game.killer = System.getProperty("aside.fnaf5.scare", "Funtime Freddy");
            if (game.killer.isEmpty() || game.killer.equals("1")) {
                game.killer = "Funtime Freddy";
            }
            scareT = 1.0;
        }
        if (System.getProperty("aside.fnaf5.win") != null) {
            game.status = Game.Status.SURVIVED;
            game.hour = 6;
        }
    }

    /** A room by name. Spaces and case do not matter -- a dev hook should
     *  not make you remember where the spaces are. */
    static Room.Where where(String name) {
        String n = name.replace(" ", "").replace("/", "").toLowerCase();
        for (Room.Where w : Room.ALL) {
            if (w.name().toLowerCase().equals(n)) return w;
        }
        return switch (n) {
            case "parts", "partsservice" -> Room.Where.PARTS;
            case "balloragallery", "gallery" -> Room.Where.BALLORA;
            case "control", "controlmodule", "module" -> Room.Where.CONTROL;
            case "circusgallery", "circus" -> Room.Where.CIRCUS;
            case "funtimeauditorium", "auditorium" -> Room.Where.AUDITORIUM;
            default -> null;
        };
    }

    @Override public Parent getRoot() { return root; }

    @Override
    public void tick(double dt) {
        double frame = Math.min(dt, 0.25);
        if (!frozen) game.update(frame);
        for (String cue : game.drainCues()) play(cue);

        pulse += frame;
        if (wasteT > 0) wasteT = Math.max(0, wasteT - frame);
        if (refuseT > 0) refuseT = Math.max(0, refuseT - frame);
        if (game.status == Game.Status.JUMPSCARED) scareT += frame;
        render();
    }

    /**
     * One cue, with the right fallback.
     *
     * The per-character names are the point -- a step you cannot tell
     * apart from another step carries no information, and in this game the
     * step is the only warning there is -- but a cue that resolves to
     * nothing is worse than a cue that resolves to the wrong file, so each
     * family falls back to the generic sound it came from.
     */
    static void play(String cue) {
        if (cue.startsWith("here_")) Audio.A.sfx(cue, "at_door");
        else if (cue.startsWith("step_")) Audio.A.sfx(cue, "footstep");
        else if (cue.startsWith("lost_")) Audio.A.sfx(cue, "door_close");
        else if (cue.equals("shock")) Audio.A.sfx("power_down");
        else Audio.A.sfx(cue);
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case A, LEFT -> { walk(-1); e.consume(); }
            case D, RIGHT -> { walk(1); e.consume(); }
            case SPACE -> { shock(); e.consume(); }
            case M -> { toggleMonitor(); e.consume(); }
            case ESCAPE -> { ui.push(new PauseScreen(ui, this)); e.consume(); }
            default -> {
                // Read the KEY CODE, not the typed character. A synthetic
                // key event carries no text, so a getText() selector
                // silently does nothing under the engine's snapshot mode.
                Room.Where w = switch (e.getCode()) {
                    case DIGIT1 -> Room.Where.PARTS;
                    case DIGIT2 -> Room.Where.BALLORA;
                    case DIGIT3 -> Room.Where.CONTROL;
                    case DIGIT4 -> Room.Where.CIRCUS;
                    case DIGIT5 -> Room.Where.AUDITORIUM;
                    default -> null;
                };
                if (w != null) { watch(w); e.consume(); }
            }
        }
    }

    void walk(int direction) {
        if (!game.step(direction)) refuseT = 0.4;
    }

    void watch(Room.Where w) {
        game.watch(w);
    }

    void toggleMonitor() {
        if (game.monitorOn) game.monitorDown();
        else game.watch(game.camera == null ? otherRoom() : game.camera);
    }

    /** Some room that is not the one you are standing in. */
    Room.Where otherRoom() {
        for (Room.Where w : Room.ALL) if (w != game.where) return w;
        return null;
    }

    void shock() {
        boolean found = game.shock();
        if (!found && game.shocksUsed > 0) wasteT = 0.5;
    }

    // ---- Mouse ----

    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        hoverX = x;
        hoverY = y;
        if (!pressed) return;
        MouseMap.Hit h = MouseMap.hit(x, y);
        switch (h.kind()) {
            case TILE -> watch(Room.ALL[h.index()]);
            case MOVE_LEFT -> walk(-1);
            case MOVE_RIGHT -> walk(1);
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

        drawFeed();
        drawEdges();
        drawBand();
        drawStrip();
        if (game.status == Game.Status.JUMPSCARED) drawJumpscare();
        if (game.status == Game.Status.SURVIVED) drawWin();
    }

    /**
     * The camera feed.
     *
     * Which room it shows is the only thing the monitor can do, and the
     * two ways it can show nothing are both real states of the game: the
     * monitor is down, or the monitor is pointed at the room you are
     * standing in, which is a thing that happens the moment you walk into
     * the room you were watching.
     */
    void drawFeed() {
        Assets a = Assets.A;
        Room.Where view = game.view();
        double[] r = MouseMap.SCENE;

        if (view == null) {
            gc.setFill(Color.web("#05070A"));
            gc.fillRect(r[0], r[1], r[2], r[3]);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(Color.web("#2E4A3A"));
            gc.setFont(Font.font("Monospaced", 26));
            gc.fillText("NO SIGNAL", W / 2, r[1] + r[3] / 2 - 8);
            gc.setFill(Color.web("#24402F"));
            gc.setFont(Font.font("Monospaced", 14));
            gc.fillText(game.monitorOn
                            ? "the camera cannot see the room you are in"
                            : "monitor down  --  M to raise it",
                    W / 2, r[1] + r[3] / 2 + 24);
            gc.setTextAlign(TextAlignment.LEFT);
            scanlines(r);
            return;
        }

        Threat in = game.standingIn(view);
        Image img = a == null ? null : a.feed(view, in == null ? null : in.key);
        if (img != null) {
            gc.drawImage(img, r[0], r[1], r[2], r[3]);
        } else {
            gc.setFill(Color.web("#0A0F12"));
            gc.fillRect(r[0], r[1], r[2], r[3]);
        }

        scanlines(r);
        drawFeedLabel(view, in);

        // A shock that found nothing is worth showing, because the whole
        // cost of the shock is paid in shocks that found nothing.
        if (wasteT > 0) {
            gc.setFill(Color.rgb(255, 255, 255, 0.05 * (wasteT / 0.5)));
            gc.fillRect(r[0], r[1], r[2], r[3]);
        }
    }

    /** The horizontal lines of a cheap camera, drawn rather than baked. */
    void scanlines(double[] r) {
        gc.setFill(Color.rgb(0, 0, 0, 0.20));
        for (double y = r[1]; y < r[1] + r[3]; y += 3) {
            gc.fillRect(r[0], y, r[2], 1);
        }
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 0, 0, 0.45)),
                new Stop(0.5, Color.TRANSPARENT),
                new Stop(1, Color.rgb(0, 0, 0, 0.55))));
        gc.fillRect(r[0], r[1], r[2], r[3]);
    }

    /** The camera's own overlay: which feed this is, and a clock. */
    void drawFeedLabel(Room.Where view, Threat in) {
        double[] r = MouseMap.SCENE;
        gc.setFont(Font.font("Monospaced", 16));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("#7FE3B0", 0.85));
        gc.fillText("CAM " + (Room.index(view) + 1) + "  " + view.tag, r[0] + 26, r[1] + 34);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(clock(), r[0] + r[2] - 26, r[1] + 34);
        gc.setTextAlign(TextAlignment.LEFT);
        if (in != null) {
            gc.setFill(Color.web("#E94560", 0.9));
            gc.fillText("MOTION", r[0] + 26, r[1] + 58);
        }
    }

    /**
     * The edge glow.
     *
     * Red for something standing in the room with you, amber for something
     * one door away. The pulse is slow on purpose: a fast one reads as a
     * timer, and this is meant to read as a building.
     */
    void drawEdges() {
        double beat = 0.5 + 0.5 * Math.sin(pulse * 4.4);
        boolean here = game.occupied();
        boolean near = game.nextDoor();
        if (!here && !near) return;

        Color c = here ? Color.web("#E94560") : Color.web("#C9A227");
        double alpha = (here ? 0.50 : 0.22) * (0.55 + 0.45 * beat);
        double depth = 150;
        double[] r = MouseMap.SCENE;
        double y0 = r[1], h = r[3];

        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)),
                new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(0, y0, W, depth);
        gc.setFill(new LinearGradient(0, 1, 0, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)),
                new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(0, y0 + h - depth, W, depth);
        gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)),
                new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(0, y0, depth, h);
        gc.setFill(new LinearGradient(1, 0, 0, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)),
                new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(W - depth, y0, depth, h);
    }

    /** The band: night, where you are, the clock, and the shock button. */
    void drawBand() {
        gc.setFill(Color.web("#07070C"));
        gc.fillRect(0, 0, W, MouseMap.BAND[3]);
        gc.setStroke(Color.web("#1B1B26"));
        gc.setLineWidth(1);
        gc.strokeLine(0, MouseMap.BAND[3], W, MouseMap.BAND[3]);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText("NIGHT " + night, 26, 44);

        // Where you are. This is the only place the game says it, and it is
        // the single most important piece of information on the screen --
        // because the monitor is pointed somewhere else by definition.
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 12));
        gc.fillText("YOU ARE IN", 150, 26);
        gc.setFill(Color.web("#FFD700"));
        gc.setFont(Font.font("Arial", 19));
        gc.fillText(game.where.tag, 150, 48);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText(clock(), W / 2, 44);
        gc.setTextAlign(TextAlignment.LEFT);

        drawShockButton();
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
        gc.fillText("SHOCK  x" + game.shocks, r[0] + r[2] / 2, r[1] + 25);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** The strip: where you are, where the camera is, and how to walk. */
    void drawStrip() {
        gc.setFill(Color.web("#07070C"));
        gc.fillRect(0, MouseMap.BAR[1], W, MouseMap.BAR[3]);

        MouseMap.Hit h = hover();
        drawMoveButton(MouseMap.MOVE_L, "\u25C0  MOVE",
                h.kind() == MouseMap.Kind.MOVE_LEFT, Room.at(Room.index(game.where) - 1) != null);
        drawMoveButton(MouseMap.MOVE_R, "MOVE  \u25B6",
                h.kind() == MouseMap.Kind.MOVE_RIGHT, Room.at(Room.index(game.where) + 1) != null);

        for (int i = 0; i < Room.COUNT; i++) {
            Room.Where w = Room.ALL[i];
            double[] r = MouseMap.tile(i);
            boolean you = w == game.where;
            boolean cam = game.monitorOn && w == game.camera;
            boolean hot = h.kind() == MouseMap.Kind.TILE && h.index() == i;

            gc.setFill(you ? Color.rgb(233, 69, 96, 0.20)
                    : cam ? Color.rgb(127, 227, 176, 0.14)
                    : hot ? Color.rgb(255, 255, 255, 0.08)
                    : Color.rgb(255, 255, 255, 0.03));
            gc.fillRect(r[0], r[1], r[2], r[3]);
            gc.setStroke(you ? Color.web("#E94560")
                    : cam ? Color.web("#7FE3B0") : Color.web("#2A2A38"));
            gc.setLineWidth(you || cam || hot ? 2 : 1);
            gc.strokeRect(r[0], r[1], r[2], r[3]);

            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(you ? Color.web("#FFD700") : cam ? Color.web("#7FE3B0")
                    : Color.web("#9A9AAE"));
            gc.setFont(Font.font("Arial", 13));
            gc.fillText(w.tag, r[0] + r[2] / 2, r[1] + 24);
            gc.setFill(Color.web("#555566"));
            gc.setFont(Font.font("Arial", 11));
            String tag = you ? "YOU ARE HERE" : cam ? "ON CAMERA" : ("CAM " + (i + 1));
            gc.fillText(tag, r[0] + r[2] / 2, r[1] + 42);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        gc.setFill(Color.web("#555577"));
        gc.setFont(Font.font("Arial", 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("A / D walk     1-5 camera     SPACE shock     M monitor     "
                + "ESC pause", W / 2, MouseMap.BAR[1] + 116);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawMoveButton(double[] r, String label, boolean hot, boolean live) {
        gc.setFill(live ? Color.rgb(255, 255, 255, hot ? 0.12 : 0.05)
                : Color.rgb(255, 255, 255, 0.02));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(live ? (hot ? Color.web("#FFD700") : Color.web("#3A3A4C"))
                : Color.web("#22222C"));
        gc.setLineWidth(hot ? 2 : 1);
        gc.strokeRect(r[0], r[1], r[2], r[3]);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(live ? Color.web("#CCCCDD") : Color.web("#3A3A4C"));
        gc.setFont(Font.font("Arial", 14));
        gc.fillText(label, r[0] + r[2] / 2, r[1] + 32);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    String clock() {
        return switch (Math.min(game.hour, 6)) {
            case 0 -> "12 AM"; case 1 -> "1 AM"; case 2 -> "2 AM";
            case 3 -> "3 AM"; case 4 -> "4 AM"; case 5 -> "5 AM";
            default -> "6 AM";
        };
    }

    void drawJumpscare() {
        Assets a = Assets.A;
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
        gc.fillText("6 AM", W / 2, H / 2 - 74);
        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText("Night " + night + " survived.", W / 2, H / 2 - 28);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText(game.moves + " rooms walked, " + game.cameraSwitches
                + " camera moves, " + game.shocksUsed + " shocks, "
                + game.wastedShocks + " of them at nothing.", W / 2, H / 2 + 12);
        gc.fillText("Something walked into the room with you " + game.arrivals
                + " times.", W / 2, H / 2 + 38);
        gc.fillText("ESC to the menu", W / 2, H / 2 + 72);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
