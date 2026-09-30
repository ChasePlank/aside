package aside.games.fnaf3;

import aside.games.fnaf3.engine.Game;
import aside.games.fnaf3.engine.House;
import aside.games.fnaf3.engine.Phantom;
import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The FNAF 3 office.
 *
 * One room, two openings, and three systems:
 *
 * <pre>
 *   [ WINDOW ]  .................  [ VENT ]
 *   ----------------------------------------
 *   [ AUDIO ]  [ VENTILATION ]  [ CAMERAS ]  [ MONITOR ]
 * </pre>
 *
 * Controls:
 *   SPACE  monitor up / down
 *   1-0    cameras 1-10 (monitor up)
 *   Q      play the audio lure in the room you are looking at
 *   A / V / C   reboot the audio / the air / the cameras
 *   ESC    pause
 *
 * And the same thing with a mouse, because FNAF is a point-and-click game:
 *
 *   click a camera row      look at that room
 *   click LURE              play the lure there
 *   click a panel           reboot that system
 *   click MONITOR           raise or lower it
 *
 * Where a click lands is decided by {@link MouseMap}, which is pure
 * geometry so the self-test can check every region without a display.
 *
 * The office is one photograph with figures drawn on top, not one view per
 * light state -- FNAF 3 has no lights to switch on. What it has instead is
 * two places somebody can be standing, and the whole tension of the game
 * is that you cannot see either of them while the monitor is up.
 */
public class GameScreen extends UiScreen {

    final Game game;
    final int night;

    double flash = 0;
    double scareT = 0;
    double hoverX = -1, hoverY = -1;

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
     * `-Daside.fnaf3.freeze=1` stops the loop, because a staged frame
     * without it is a second out of date by the time the snapshot lands --
     * the mistake FNAF 2 already paid for once.
     */
    void devHook() {
        frozen = System.getProperty("aside.fnaf3.freeze") != null;
        String at = System.getProperty("aside.fnaf3.at");
        if (at != null) {
            if (at.equalsIgnoreCase("window") || at.equalsIgnoreCase("vent")) {
                game.springtrap.atOffice = true;
                game.springtrap.atWindow = at.equalsIgnoreCase("window");
                game.springtrap.officeTimer = 0;
            } else {
                int room = Integer.parseInt(at);
                game.springtrap.atOffice = false;
                game.springtrap.room = room;
            }
        }
        if (System.getProperty("aside.fnaf3.phantom") != null) {
            game.spawnPhantom();
        }
        if (System.getProperty("aside.fnaf3.ventoff") != null) {
            game.online[Game.System.VENTILATION.ordinal()] = false;
            game.ventilation = 0;
        }
        if (System.getProperty("aside.fnaf3.scare") != null) {
            game.status = Game.Status.JUMPSCARED;
            scareT = 1.0;
        }
        if (System.getProperty("aside.fnaf3.win") != null) {
            game.status = Game.Status.SURVIVED;
            game.hour = 6;
        }
    }

    @Override public Parent getRoot() { return root; }

    @Override
    public void tick(double dt) {
        double frame = Math.min(dt, 0.25);
        if (!frozen) game.update(frame);
        for (String cue : game.drainCues()) Audio.A.sfx(cue);
        if (game.status == Game.Status.JUMPSCARED) {
            scareT += frame;
            flash = Math.max(0, flash - frame);
        }
        render();
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case SPACE -> { game.toggleCamera(); e.consume(); }
            case Q -> { if (game.cameraUp) { game.playLure(game.currentCam); e.consume(); } }
            case A -> { game.startReboot(Game.System.AUDIO); e.consume(); }
            case V -> { game.startReboot(Game.System.VENTILATION); e.consume(); }
            case C -> { game.startReboot(Game.System.CAMERAS); e.consume(); }
            case ESCAPE -> { ui.push(new PauseScreen(ui, this)); e.consume(); }
            default -> {
                // Read the KEY CODE, not the typed character. A synthetic
                // key event carries no text, so a getText() selector
                // silently does nothing under the engine's snapshot mode.
                if (game.cameraUp) {
                    int cam = switch (e.getCode()) {
                        case DIGIT1 -> 1; case DIGIT2 -> 2; case DIGIT3 -> 3;
                        case DIGIT4 -> 4; case DIGIT5 -> 5; case DIGIT6 -> 6;
                        case DIGIT7 -> 7; case DIGIT8 -> 8; case DIGIT9 -> 9;
                        case DIGIT0 -> 10;
                        default -> 0;
                    };
                    if (cam > 0) { game.setCam(cam); e.consume(); }
                }
            }
        }
    }

    // ---- Mouse ----

    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        hoverX = x;
        hoverY = y;
        if (!pressed) return;
        MouseMap.Hit h = MouseMap.hit(x, y, game.cameraUp);
        switch (h.kind()) {
            case MONITOR -> game.toggleCamera();
            case CAM_ROW -> game.setCam(h.index());
            case LURE -> game.playLure(game.currentCam);
            case AUDIO -> game.startReboot(Game.System.AUDIO);
            case VENT -> game.startReboot(Game.System.VENTILATION);
            case CAM -> game.startReboot(Game.System.CAMERAS);
            default -> { }
        }
    }

    MouseMap.Hit hover() {
        if (hoverX < 0) return MouseMap.Hit.NONE;
        return MouseMap.hit(hoverX, hoverY, game.cameraUp);
    }

    // ---- Rendering ----

    void render() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        Assets a = Assets.A;
        if (a != null) {
            if (game.cameraUp) drawMonitor(a);
            else drawOffice(a);
        }

        drawTopBand();
        drawPanels();
        drawHover();

        if (game.status == Game.Status.JUMPSCARED) drawJumpscare();
        if (game.status == Game.Status.SURVIVED) drawWin();
    }

    /** The office: one photograph, plus whoever is standing in it. */
    void drawOffice(Assets a) {
        if (a.office != null) {
            gc.drawImage(a.office, MouseMap.OX, MouseMap.OY, MouseMap.OW, MouseMap.OH);
        }
        gc.setFill(Color.rgb(0, 0, 0, 0.18));
        gc.fillRect(MouseMap.OX, MouseMap.OY, MouseMap.OW, MouseMap.OH);

        // Springtrap, if he is in here. He is drawn at the opening he came
        // through, which is the only tell the office gives you.
        if (game.springtrap.atOffice && a.springtrap != null) {
            drawFigure(a.springtrap, game.springtrap.atWindow
                    ? MouseMap.WINDOW : MouseMap.VENT);
        }

        // Phantoms. They are not a threat and are drawn like one anyway --
        // that is what a hallucination is.
        for (Phantom p : game.phantoms) {
            int i = Assets.phantomIndex(p.name);
            if (i < 0 || a.phantom[i] == null) continue;
            drawFigure(a.phantom[i], slot(p.slot));
        }
    }

    static double[] slot(Phantom.Slot s) {
        return switch (s) {
            case WINDOW -> MouseMap.WINDOW;
            case VENT -> MouseMap.VENT;
            case DESK -> new double[]{520, 300, 300, 275};
            case CORNER -> new double[]{300, 240, 260, 330};
        };
    }

    /** Fit an image inside a rect, anchored to the bottom of it. */
    void drawFigure(Image img, double[] r) {
        double iw = img.getWidth(), ih = img.getHeight();
        double s = Math.min(r[2] / iw, r[3] / ih);
        double w = iw * s, h = ih * s;
        gc.drawImage(img, r[0] + (r[2] - w) / 2, r[1] + r[3] - h, w, h);
    }

    /** The monitor: a camera feed, a strip, and the lure button. */
    void drawMonitor(Assets a) {
        gc.setFill(Color.web("#05050A"));
        gc.fillRect(0, MouseMap.OY, W, MouseMap.OH);

        if (!game.camerasOnline()) {
            drawStatic();
            return;
        }

        Image feed = a.room[Math.min(Math.max(game.currentCam, 1), House.ROOMS)];
        if (feed != null) {
            double iw = feed.getWidth(), ih = feed.getHeight();
            double s = Math.min(MouseMap.FEED_W / iw, MouseMap.FEED_H / ih);
            double w = iw * s, h = ih * s;
            double x = MouseMap.FEED_X + (MouseMap.FEED_W - w) / 2;
            double y = MouseMap.FEED_Y + (MouseMap.FEED_H - h) / 2;
            gc.drawImage(feed, x, y, w, h);
            gc.setStroke(Color.web("#2A2A38"));
            gc.setLineWidth(2);
            gc.strokeRect(x, y, w, h);

            // Springtrap, if this is the room he is in. This is the whole
            // reason to raise the monitor, and the whole reason it is
            // dangerous to keep it up.
            if (!game.springtrap.atOffice && game.springtrap.room == game.currentCam
                    && a.springtrap != null) {
                drawFigure(a.springtrap, new double[]{x + w * 0.22, y + h * 0.12,
                        w * 0.5, h * 0.8});
            }
        } else {
            drawStatic();
        }

        drawCamStrip();
        drawLureButton();
    }

    /** Snow, for a dead camera or a missing feed. */
    void drawStatic() {
        gc.setFill(Color.web("#0A0A0C"));
        gc.fillRect(0, MouseMap.OY, W, MouseMap.OH);
        gc.setFill(Color.web("#888899"));
        gc.setFont(Font.font("Arial", 22));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("NO SIGNAL", W / 2, MouseMap.OY + MouseMap.OH / 2);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawCamStrip() {
        gc.setFill(Color.rgb(0, 0, 0, 0.72));
        gc.fillRect(MouseMap.STRIP[0], MouseMap.STRIP[1], MouseMap.STRIP[2], MouseMap.STRIP[3]);
        gc.setStroke(Color.web("#2A2A38"));
        gc.setLineWidth(1);
        gc.strokeRect(MouseMap.STRIP[0], MouseMap.STRIP[1], MouseMap.STRIP[2], MouseMap.STRIP[3]);

        MouseMap.Hit h = hover();
        for (int i = 1; i <= House.ROOMS; i++) {
            double[] r = MouseMap.camRow(i);
            boolean on = i == game.currentCam;
            boolean hot = h.kind() == MouseMap.Kind.CAM_ROW && h.index() == i;
            if (on) {
                gc.setFill(Color.rgb(233, 69, 96, 0.22));
                gc.fillRect(r[0], r[1], r[2], r[3]);
            } else if (hot) {
                gc.setFill(Color.rgb(255, 255, 255, 0.08));
                gc.fillRect(r[0], r[1], r[2], r[3]);
            }
            gc.setFill(on ? Color.web("#FFD700") : Color.web("#BBBBCC"));
            gc.setFont(Font.font("Arial", 15));
            gc.fillText(String.format("CAM %02d", i), r[0] + 8, r[1] + r[3] / 2 + 5);
            gc.setFill(Color.web("#666677"));
            gc.setFont(Font.font("Arial", 13));
            gc.fillText(House.NAME[i], r[0] + 80, r[1] + r[3] / 2 + 5);
        }
    }

    void drawLureButton() {
        boolean ready = game.lureReady();
        boolean audioDown = !game.audioOnline();
        boolean hot = hover().kind() == MouseMap.Kind.LURE;
        double[] r = MouseMap.LURE_BTN;
        gc.setFill(Color.rgb(0, 0, 0, hot ? 0.8 : 0.6));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(ready ? (hot ? Color.web("#E94560") : Color.web("#7777AA"))
                : Color.web("#333340"));
        gc.setLineWidth(hot ? 3 : 2);
        gc.strokeRect(r[0], r[1], r[2], r[3]);
        gc.setFill(ready ? Color.web("#FFD700") : Color.web("#555566"));
        gc.setFont(Font.font("Arial", 18));
        gc.setTextAlign(TextAlignment.CENTER);
        String label;
        if (audioDown) label = "AUDIO DOWN";
        else if (game.rebooting == Game.System.AUDIO.ordinal()) label = "REBOOTING";
        else if (ready) label = "LURE  CAM " + String.format("%02d", game.currentCam);
        else label = "LURE  " + (int) Math.ceil(game.lureCooldown) + "s";
        gc.fillText(label, r[0] + r[2] / 2, r[1] + r[3] / 2 + 6);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ---- Bands ----

    void drawTopBand() {
        gc.setFill(Color.web("#05050A"));
        gc.fillRect(0, 0, W, MouseMap.OY);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 24));
        gc.fillText("NIGHT " + night, 40, 46);

        String clock = switch (Math.min(game.hour, 6)) {
            case 0 -> "12 AM"; case 1 -> "1 AM"; case 2 -> "2 AM";
            case 3 -> "3 AM"; case 4 -> "4 AM"; case 5 -> "5 AM";
            default -> "6 AM";
        };
        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 24));
        gc.fillText(clock, 200, 46);

        // The one warning the office gives you without a panel: the air.
        gc.setFont(Font.font("Arial", 15));
        if (!game.ventilationOnline()) {
            gc.setFill(Color.web("#E94560"));
            gc.fillText("AIR FAILURE \u2014 the phantoms come while it is off", 40, 76);
        } else if (game.ventilation < Game.VENT_MAX * 0.3) {
            gc.setFill(Color.web("#FFD700"));
            gc.fillText("the air is getting thin", 40, 76);
        } else {
            gc.setFill(Color.web("#555577"));
            gc.fillText("the air is running", 40, 76);
        }

        if (game.lureRoom > 0) {
            gc.setFill(Color.web("#FFD700"));
            gc.setFont(Font.font("Arial", 15));
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText("lure playing in " + House.NAME[game.lureRoom], W - 40, 46);
            gc.setTextAlign(TextAlignment.LEFT);
        }
        if (game.springtrap.atOffice) {
            gc.setFill(Color.web("#E94560"));
            gc.setFont(Font.font("Arial", 20));
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText("HE IS IN THE OFFICE", W - 40, 78);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    void drawPanels() {
        // The audio slot is the lure button while the monitor is up -- the
        // same control seen from two sides, so it is drawn once, by the
        // view you are actually in.
        if (!game.cameraUp) panel(MouseMap.PANEL_AUDIO, "AUDIO", Game.System.AUDIO, "A");
        panel(MouseMap.PANEL_VENT, "AIR", Game.System.VENTILATION, "V");
        panel(MouseMap.PANEL_CAM, "CAMERAS", Game.System.CAMERAS, "C");

        boolean hot = hover().kind() == MouseMap.Kind.MONITOR;
        double[] r = MouseMap.PANEL_MONITOR;
        gc.setFill(Color.rgb(0, 0, 0, hot ? 0.8 : 0.6));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(hot ? Color.web("#E94560") : Color.web("#555566"));
        gc.setLineWidth(hot ? 3 : 2);
        gc.strokeRect(r[0], r[1], r[2], r[3]);
        gc.setFill(hot ? Color.WHITE : Color.web("#CCCCCC"));
        gc.setFont(Font.font("Arial", 19));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(game.cameraUp ? "LOWER" : "MONITOR", r[0] + r[2] / 2, r[1] + r[3] / 2 + 7);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * One maintenance panel.
     *
     * Three states, and the third is the interesting one: a system that is
     * running, a system that is down and can be fixed, and a system that is
     * being fixed right now. The bar is the only progress indicator in the
     * game, and it is there because a reboot you cannot see the end of is
     * a reboot you will not start.
     */
    void panel(double[] r, String label, Game.System s, String key) {
        boolean up = game.online[s.ordinal()];
        boolean fixing = game.rebooting == s.ordinal();
        boolean hot = switch (hover().kind()) {
            case AUDIO -> s == Game.System.AUDIO;
            case VENT -> s == Game.System.VENTILATION;
            case CAM -> s == Game.System.CAMERAS;
            default -> false;
        };

        gc.setFill(Color.rgb(0, 0, 0, hot ? 0.8 : 0.6));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(fixing ? Color.web("#FFD700")
                : up ? Color.web("#445544") : Color.web("#E94560"));
        gc.setLineWidth(hot ? 3 : 2);
        gc.strokeRect(r[0], r[1], r[2], r[3]);

        gc.setFont(Font.font("Arial", 17));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#CCCCDD"));
        gc.fillText(label, r[0] + r[2] / 2, r[1] + 30);

        gc.setFont(Font.font("Arial", 15));
        if (fixing) {
            gc.setFill(Color.web("#FFD700"));
            gc.fillText("REBOOTING", r[0] + r[2] / 2, r[1] + 56);
            double p = Math.min(1, game.rebootTimer / Game.REBOOT_TIME);
            gc.setFill(Color.web("#2A2A38"));
            gc.fillRect(r[0] + 12, r[1] + r[3] - 12, r[2] - 24, 5);
            gc.setFill(Color.web("#FFD700"));
            gc.fillRect(r[0] + 12, r[1] + r[3] - 12, (r[2] - 24) * p, 5);
        } else if (up) {
            gc.setFill(Color.web("#667766"));
            gc.fillText("RUNNING", r[0] + r[2] / 2, r[1] + 56);
        } else {
            gc.setFill(Color.web("#E94560"));
            gc.fillText("DOWN \u2014 click or " + key, r[0] + r[2] / 2, r[1] + 56);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawHover() {
        MouseMap.Hit h = hover();
        if (h.kind() == MouseMap.Kind.NONE) return;
        double[] r = switch (h.kind()) {
            case MONITOR -> MouseMap.PANEL_MONITOR;
            case AUDIO -> MouseMap.PANEL_AUDIO;
            case VENT -> MouseMap.PANEL_VENT;
            case CAM -> MouseMap.PANEL_CAM;
            case LURE -> MouseMap.LURE_BTN;
            case CAM_ROW -> MouseMap.camRow(h.index());
            default -> null;
        };
        if (r == null) return;
        gc.setStroke(Color.rgb(233, 69, 96, 0.55));
        gc.setLineWidth(1);
        gc.strokeRect(r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4);
    }

    void drawJumpscare() {
        gc.setFill(Color.rgb(0, 0, 0, Math.min(1, scareT * 1.4)));
        gc.fillRect(0, 0, W, H);
        Assets a = Assets.A;
        if (a != null && a.springtrap != null) {
            double s = Math.min(W / a.springtrap.getWidth(), H / a.springtrap.getHeight()) * 1.15;
            double w = a.springtrap.getWidth() * s, h = a.springtrap.getHeight() * s;
            gc.drawImage(a.springtrap, (W - w) / 2, (H - h) / 2, w, h);
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
        gc.fillText("6 AM", W / 2, H / 2 - 30);
        gc.setFill(Color.web("#CCCCDD"));
        gc.setFont(Font.font("Arial", 20));
        gc.fillText("Night " + night + " survived.", W / 2, H / 2 + 20);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 15));
        gc.fillText("ESC to the menu", W / 2, H / 2 + 60);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
