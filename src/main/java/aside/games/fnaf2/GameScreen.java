package aside.games.fnaf2;

import aside.games.fnaf2.engine.Animatronic;
import aside.games.fnaf2.engine.Clicks;
import aside.games.fnaf2.engine.Game;
import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The FNAF 2 office.
 *
 * Three openings and no doors:
 *
 *     [ LEFT VENT ]   [   HALL   ]   [ RIGHT VENT ]
 *
 * Controls:
 *   Q      hall light (also the flashlight -- it repels Withered Foxy)
 *   Z / C  left / right vent light
 *   M      Freddy mask
 *   SPACE  monitor
 *   W      hold, on CAM 11, to wind the music box
 *   1-9    cameras 1-9
 *   0      camera 10      -      camera 11
 *   ESC    pause
 *
 * And the same thing with a mouse, because FNAF is a point-and-click game
 * and this one was keyboard-only:
 *
 *   click an opening        look there (same as Q / Z / C)
 *   click MASK / MONITOR    the two buttons along the bottom
 *   click a camera row      the strip down the right of the monitor
 *   click the music box bar wind it (a click, not a hold -- see below)
 *   click anywhere masked   take the mask off
 *
 * Where a click lands is decided by {@link MouseMap}, which is pure geometry
 * so SelfTest can check every region without a display.
 *
 * The office is drawn as a whole view per light state, not as one picture
 * with lit rectangles on top: in FNAF 2 the view swaps. See {@link Assets}.
 */
public class GameScreen extends UiScreen {

    final Game game;
    final int night;

    /**
     * Source office art is 1600x768; the canvas is 1280x720. Fitting the
     * width keeps both vent openings on screen -- covering the canvas crops
     * 117px off each side in source space, which is most of the left vent.
     *
     * The numbers live in {@link MouseMap} now, because the mouse layer has
     * to hit-test against exactly what this draws. Two copies of a rectangle
     * is how a button ends up clickable somewhere it is not drawn.
     */
    static final double OW = MouseMap.OW;
    static final double OH = MouseMap.OH;
    static final double OX = MouseMap.OX;
    static final double OY = MouseMap.OY;

    double flash = 0;          // white blink on a jumpscare
    double scareT = 0;

    public GameScreen(UiManager ui, int night) {
        super(ui);
        this.night = night;
        this.game = new Game(night, System.nanoTime());
        devHook();
    }

    /**
     * Dev hooks, for verifying frames that a 90-frame snapshot cannot
     * reach on its own.
     *
     *   -Daside.fnaf2.stage=<name>   put an animatronic in its opening
     *   -Daside.fnaf2.scare=<name>   jump straight to the jumpscare
     *   -Daside.fnaf2.win=1          jump straight to 6 AM
     *   -Daside.fnaf2.freeze=1       stop the engine ticking
     *
     * A click is not one of these: the engine has -Daside.click=x,y and
     * -Daside.mouse=x,y (see Main), which work on any screen and fire after
     * the keys, so a click can land on the state the keys produced.
     *
     * The stage hook is the one that earns its keep: a threat that is
     * only visible for a window is impossible to screenshot by playing,
     * and "I could not see him" is exactly the report it answers.
     *
     * It needs the freeze to be worth anything. Without it the snapshot
     * lands a second after the keys, by which time the hall light has
     * already repelled Foxy and the frame proves nothing -- the first
     * attempt at this looked like proof of a visual bug and was actually
     * proof the repel worked.
     */
    boolean frozen = false;

    void devHook() {
        frozen = System.getProperty("aside.fnaf2.freeze") != null;
        String stage = System.getProperty("aside.fnaf2.stage");
        if (stage != null) {
            for (Animatronic x : game.visitors()) {
                if (matches(x.name, stage)) {
                    x.pathIndex = x.path.length;
                    x.officeTimer = 0;
                }
            }
            if (matches("Withered Foxy", stage) || matches("Foxy", stage)) {
                game.witheredFoxy.stages = 3;
            }
        }
        String scare = System.getProperty("aside.fnaf2.scare");
        if (scare != null) {
            for (Animatronic x : game.cast()) {
                if (matches(x.name, scare)) game.jumpscareBy = x;
            }
            if (game.jumpscareBy == null) game.jumpscareBy = game.puppet;
            game.status = Game.Status.JUMPSCARED;
            scareT = 2.0;
        }
        if (System.getProperty("aside.fnaf2.win") != null) {
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
            case Q -> { game.toggleHallLight(); e.consume(); }
            case Z -> { game.toggleVentLLight(); e.consume(); }
            case C -> { game.toggleVentRLight(); e.consume(); }
            case M -> { game.toggleMask(); e.consume(); }
            case SPACE -> { game.toggleCamera(); e.consume(); }
            case W -> { if (game.cameraUp && game.currentCam == Game.MUSIC_BOX_CAM) {
                            game.setWinding(true); e.consume(); } }
            case ESCAPE -> { ui.push(new PauseScreen(ui, this)); e.consume(); }
            default -> {
                // Read the KEY CODE, not the typed character. A synthetic
                // key event (the engine's own snapshot mode, and any
                // automation) carries no text, so a getText() camera
                // selector silently does nothing there and the render
                // comes back looking like the game ignored the key.
                if (game.cameraUp) {
                    int cam = switch (e.getCode()) {
                        case DIGIT1 -> 1; case DIGIT2 -> 2; case DIGIT3 -> 3;
                        case DIGIT4 -> 4; case DIGIT5 -> 5; case DIGIT6 -> 6;
                        case DIGIT7 -> 7; case DIGIT8 -> 8; case DIGIT9 -> 9;
                        case DIGIT0 -> 10;
                        case MINUS, SUBTRACT -> 11;
                        default -> 0;
                    };
                    if (cam > 0) { game.setCam(cam); e.consume(); }
                }
            }
        }
    }

    @Override
    public void handleKeyReleased(KeyEvent e) {
        if (e.getCode() == KeyCode.W) game.setWinding(false);
    }

    // ---- Mouse ----

    /** Where the pointer is, in canvas pixels. -1 means nowhere. */
    double hoverX = -1, hoverY = -1;

    @Override
    public void handleMouse(double x, double y, boolean pressed) {
        hoverX = x;
        hoverY = y;
        if (!pressed) return;
        Clicks.apply(game, MouseMap.hit(x, y, game.cameraUp, game.maskOn, game.currentCam));
    }

    /** What the pointer is over right now, for the hover highlight. */
    MouseMap.Hit hover() {
        if (hoverX < 0) return MouseMap.Hit.NONE;
        return MouseMap.hit(hoverX, hoverY, game.cameraUp, game.maskOn, game.currentCam);
    }

    /** "Balloon Boy" matches "balloonboy" -- a dev hook should not make
     *  you remember where the spaces are. */
    static boolean matches(String name, String arg) {
        return name.replace(" ", "").equalsIgnoreCase(arg.replace(" ", ""));
    }

    // ---- Rendering ----

    void render() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        Assets a = Assets.A;
        if (a != null) drawOfficeView(a);

        if (game.cameraUp) {
            drawCameraView();
            drawCamStrip();
            drawOfficeWarning();
        }

        if (!game.maskOn) drawButtons();
        drawHUD();
        drawHover();

        if (game.status == Game.Status.JUMPSCARED) drawJumpscare();
        if (game.status == Game.Status.SURVIVED) drawWin();
    }

    // ---- Mouse affordances ----

    /**
     * The two buttons along the bottom, in both views.
     *
     * MASK and the monitor toggle. The toggle is one button wearing the
     * label for what it will do next, and it is live in both views because
     * the two things a player reaches for while the monitor is up are the
     * two things that take it away.
     *
     * Drawn rather than styled: a Region background does not paint on this
     * GPU, which is why every screen draws itself onto a Canvas (UiScreen).
     */
    void drawButtons() {
        MouseMap.Hit h = hover();
        button(MouseMap.MASK_BTN, "MASK", h.kind() == MouseMap.Kind.MASK);
        button(MouseMap.MONITOR_BTN, game.cameraUp ? "LOWER" : "MONITOR",
                h.kind() == MouseMap.Kind.MONITOR);
    }

    void button(double[] r, String label, boolean hot) {
        gc.setFill(Color.rgb(0, 0, 0, hot ? 0.78 : 0.55));
        gc.fillRect(r[0], r[1], r[2], r[3]);
        gc.setStroke(hot ? Color.web("#E94560") : Color.web("#555566"));
        gc.setLineWidth(hot ? 3 : 2);
        gc.strokeRect(r[0], r[1], r[2], r[3]);
        gc.setFill(hot ? Color.WHITE : Color.web("#CCCCCC"));
        gc.setFont(Font.font("Arial", 20));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(label, r[0] + r[2] / 2, r[1] + r[3] / 2 + 7);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * The camera strip, down the right of the monitor.
     *
     * It is also the map the game never had: the keyboard selector was
     * invisible, so the eleven cameras existed only for a player who had
     * been told they did.
     */
    void drawCamStrip() {
        MouseMap.Hit h = hover();
        for (int cam = 1; cam <= MouseMap.MUSIC_BOX_CAM; cam++) {
            double[] r = MouseMap.camRow(cam);
            boolean here = cam == game.currentCam;
            boolean hot = h.kind() == MouseMap.Kind.CAM && h.cam() == cam;
            gc.setFill(Color.rgb(0, 0, 0, here ? 0.85 : hot ? 0.72 : 0.5));
            gc.fillRect(r[0], r[1], r[2], r[3]);
            gc.setStroke(here ? Color.web("#E94560")
                    : hot ? Color.web("#8888AA") : Color.web("#333344"));
            gc.setLineWidth(here || hot ? 2 : 1);
            gc.strokeRect(r[0], r[1], r[2], r[3]);
            gc.setFill(here ? Color.WHITE : Color.web("#9999AA"));
            gc.setFont(Font.font("Monospaced", 15));
            gc.fillText("CAM " + (cam < 10 ? "0" + cam : "" + cam), r[0] + 12, r[1] + 30);
        }
    }

    /**
     * A thin outline on whatever the pointer is over, so the openings read
     * as clickable before anything is clicked. Buttons and camera rows
     * highlight themselves, so this only covers the openings and the bar.
     */
    void drawHover() {
        if (game.status != Game.Status.PLAYING) return;
        double[] r = switch (hover().kind()) {
            case HALL   -> MouseMap.rect(MouseMap.HALL_SRC);
            case VENT_L -> MouseMap.rect(MouseMap.VENT_L_SRC);
            case VENT_R -> MouseMap.rect(MouseMap.VENT_R_SRC);
            case WIND   -> MouseMap.WIND_BTN;
            default -> null;
        };
        if (r == null) return;
        gc.setStroke(Color.rgb(233, 69, 96, 0.85));
        gc.setLineWidth(3);
        gc.strokeRect(r[0], r[1], r[2], r[3]);
    }

    /** Which whole-office view the current light state calls for. */
    Image officeView(Assets a) {
        if (game.lightsDisabled) return a.office;
        if (game.hallLightOn) return a.officeHall != null ? a.officeHall : a.office;
        if (game.ventLLightOn) return a.officeVentL != null ? a.officeVentL : a.office;
        if (game.ventRLightOn) return a.officeVentR != null ? a.officeVentR : a.office;
        return a.office;
    }

    void drawOfficeView(Assets a) {
        Image view = officeView(a);
        if (view != null) {
            gc.drawImage(view, OX, OY, OW, OH);
        } else {
            gc.setFill(Color.web("#0A0A12"));
            gc.fillRect(0, 0, W, H);
        }

        // Whoever is standing in a lit opening. Contained inside the
        // opening and anchored low: a figure drawn edge to edge reads as a
        // jumpscare already in progress rather than someone standing in a
        // doorway.
        if (!game.lightsDisabled) {
            if (game.hallLightOn) {
                for (Animatronic x : game.visitors()) {
                    if (x.atOpening() && x.opening == Animatronic.Opening.HALL) {
                        drawFigure(a, x.name, MouseMap.HALL_SRC);
                    }
                }
                // Foxy is staged rather than a visitor -- he does not walk
                // a path to the office, he waits in the cove and then runs
                // -- so he is not in visitors() and has to be drawn here.
                if (game.witheredFoxy.stages >= 3) {
                    drawFigure(a, game.witheredFoxy.name, MouseMap.HALL_SRC);
                }
            } else if (game.ventLLightOn) {
                for (Animatronic x : game.visitors()) {
                    if (x.atOpening() && x.opening == Animatronic.Opening.VENT_L) {
                        drawFigure(a, x.name, MouseMap.VENT_L_SRC);
                    }
                }
            } else if (game.ventRLightOn) {
                for (Animatronic x : game.visitors()) {
                    if (x.atOpening() && x.opening == Animatronic.Opening.VENT_R) {
                        drawFigure(a, x.name, MouseMap.VENT_R_SRC);
                    }
                }
            }
        }

        // The mask goes over everything: it is what you see instead of
        // the room.
        if (game.maskOn && a.mask != null) {
            drawMask(a.mask);
        }
    }

    void drawFigure(Assets a, String name, double[] src) {
        int i = Assets.index(name);
        if (i < 0) return;
        Image img = a.figure[i];
        if (img == null) return;

        double[] r = rect(src);
        double scale = (r[3] / img.getHeight()) * 0.98;
        double dw = img.getWidth() * scale;
        double dh = img.getHeight() * scale;
        double dx = r[0] + (r[2] - dw) / 2;
        double dy = r[1] + r[3] - dh;      // anchored low

        gc.save();
        gc.beginPath();
        gc.rect(r[0], r[1], r[2], r[3]);
        gc.clip();
        gc.drawImage(img, dx, dy, dw, dh);
        gc.restore();
    }

    void drawMask(Image mask) {
        double iw = mask.getWidth(), ih = mask.getHeight();
        double s = Math.max(W / iw, H / ih);
        gc.setGlobalAlpha(0.94);
        gc.drawImage(mask, (W - iw * s) / 2, (H - ih * s) / 2, iw * s, ih * s);
        gc.setGlobalAlpha(1);
        gc.setFill(Color.rgb(0, 0, 0, 0.28));
        gc.fillRect(0, 0, W, H);
    }

    static double[] rect(double[] src) {
        return MouseMap.rect(src);
    }

    // ---- Camera ----

    /**
     * The monitor's warning: someone is at the office and the monitor is
     * hiding them.
     *
     * Red, at the edges of the frame, and it does not say where. The point is
     * to make you lower the monitor and look, which is the decision the game
     * is about -- so it must not make that decision for you. It pulses,
     * because a steady bar reads as part of the feed's furniture.
     *
     * This is the other half of the arrival cue. The cue tells you it
     * happened; this tells you it is still happening, which is the state you
     * are in when you are holding W on CAM 11.
     */
    void drawOfficeWarning() {
        if (!game.someoneAtTheOffice()) return;
        double pulse = 0.35 + 0.25 * Math.sin(game.time * 9.0);
        gc.setFill(Color.rgb(233, 69, 96, pulse));
        gc.fillRect(0, 0, W, 10);
        gc.fillRect(0, H - 10, W, 10);
        gc.setFill(Color.rgb(0, 0, 0, 0.62));
        gc.fillRect(W / 2 - 265, 26, 530, 54);
        gc.setStroke(Color.web("#E94560"));
        gc.setLineWidth(2);
        gc.strokeRect(W / 2 - 265, 26, 530, 54);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 30));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("SOMETHING IS IN THE OFFICE", W / 2, 63);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    void drawCameraView() {
        gc.setFill(Color.web("#050508"));
        gc.fillRect(0, 0, W, H);

        Assets a = Assets.A;
        int cam = game.currentCam;
        Image feed = (a != null && cam >= 1 && cam <= 11) ? a.room[cam] : null;

        if (feed != null) {
            // Fitted, not covered: the feeds are 1600x768 (2.08:1) against
            // a 1280x720 canvas, so covering crops ~17% off the sides and
            // reads as zoomed in.
            drawFit(feed);
        }

        // Animatronics in this room, as the feed shows them.
        //
        // Spread across the frame rather than stacked: at 12 AM every
        // visitor is on the Show Stage at once, and six figures drawn at
        // the same coordinates make a collage, not a room.
        if (a != null) {
            java.util.List<Image> here = new java.util.ArrayList<>();
            for (Animatronic x : game.cast()) {
                if (x.staged) continue;             // Foxy's cove is a stage tell, not a figure
                if (x.currentRoom() != cam || x.atOpening()) continue;
                int i = Assets.index(x.name);
                if (i >= 0 && a.figure[i] != null) here.add(a.figure[i]);
            }
            int n = here.size();
            for (int k = 0; k < n; k++) {
                Image img = here.get(k);
                double dh = n > 2 ? 320 : 400;
                double dw = img.getWidth() * (dh / img.getHeight());
                double slot = W * (k + 1.0) / (n + 1.0);
                gc.setGlobalAlpha(0.92);
                gc.drawImage(img, slot - dw / 2, H - dh - 40, dw, dh);
                gc.setGlobalAlpha(1);
            }
        }

        // Kid's Cove: the curtain state is the Foxy tell.
        if (cam == Game.COVE_CAM) {
            String tell = switch (game.witheredFoxy.stages) {
                case 0 -> null;
                case 1 -> "SOMETHING MOVED";
                case 2 -> "IT'S ME";
                default -> "GONE";
            };
            if (tell != null) {
                gc.setFill(Color.web("#E94560"));
                gc.setFont(Font.font("Arial", 34));
                gc.fillText(tell, W / 2 - 80, 130);
            }
        }

        drawCamFrame(cam);

        if (cam == Game.MUSIC_BOX_CAM) drawMusicBoxPanel();
    }

    void drawCamFrame(int cam) {
        java.util.Random noise = new java.util.Random();
        gc.setFill(Color.rgb(255, 255, 255, 0.05));
        for (int i = 0; i < 420; i++) {
            gc.fillRect(noise.nextInt(W), noise.nextInt(H), 2, 2);
        }

        gc.setFill(Color.rgb(0, 0, 0, 0.55));
        gc.fillRect(20, 20, 380, 44);
        gc.setFill(Color.web("#CCCCCC"));
        gc.setFont(Font.font("Monospaced", 19));
        gc.fillText("CAM " + (cam < 10 ? "0" + cam : "" + cam) + " — " + roomName(cam), 34, 50);

        gc.setFill(Color.rgb(0, 0, 0, 0.15));
        for (int y = 0; y < H; y += 4) gc.fillRect(0, y, W, 2);
    }

    /** The music box is the real clock, so it gets a panel rather than a
     *  number in a corner.
     *
     *  It sits high because the bottom strip of the screen belongs to the
     *  buttons in both views -- see {@link MouseMap}. */
    void drawMusicBoxPanel() {
        double bw = MouseMap.MUSIC_BOX_BAR_W, bh = MouseMap.MUSIC_BOX_BAR_H;
        double bx = MouseMap.MUSIC_BOX_BAR_X, by = MouseMap.MUSIC_BOX_BAR_Y;

        gc.setFill(Color.rgb(0, 0, 0, 0.62));
        double[] panel = MouseMap.MUSIC_BOX_PANEL;
        gc.fillRect(panel[0], panel[1], panel[2], panel[3]);

        gc.setFill(Color.web("#CCCCCC"));
        gc.setFont(Font.font("Arial", 20));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("MUSIC BOX", W / 2, by - 22);

        gc.setFill(Color.web("#1A1A22"));
        gc.fillRect(bx, by, bw, bh);

        double frac = Math.max(0, Math.min(1, game.musicBox / Game.MUSIC_BOX_MAX));
        gc.setFill(frac > 0.5 ? Color.web("#3CB043")
                : frac > 0.25 ? Color.web("#FFD700") : Color.web("#E94560"));
        gc.fillRect(bx, by, bw * frac, bh);

        gc.setStroke(Color.web("#555566"));
        gc.setLineWidth(2);
        gc.strokeRect(bx, by, bw, bh);

        gc.setFill(game.winding ? Color.web("#3CB043") : Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 18));
        gc.fillText(game.winding ? "WINDING — click to stop" : "click the bar to wind",
                W / 2, by + bh + 30);
        gc.setTextAlign(TextAlignment.LEFT);

        if (game.puppetComing) {
            gc.setFill(Color.web("#E94560"));
            gc.setFont(Font.font("Arial", 40));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("IT'S OUT", W / 2, H / 2);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    static String roomName(int cam) {
        return switch (cam) {
            case 1 -> "Show Stage";
            case 2 -> "Game Area";
            case 3 -> "Kid's Cove";
            case 4 -> "Main Hall";
            case 5 -> "Party Room 1";
            case 6 -> "Party Room 2";
            case 7 -> "Right Air Vent";
            case 8 -> "Party Room 3";
            case 9 -> "Party Room 4";
            case 10 -> "Left Air Vent";
            case 11 -> "Prize Corner";
            default -> "?";
        };
    }

    // ---- HUD ----

    void drawHUD() {
        // Time, top right
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 30));
        int h = game.hour == 0 ? 12 : game.hour;
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(h + " AM", W - 30, 48);
        gc.setFont(Font.font("Arial", 18));
        gc.fillText("Night " + game.night, W - 30, 76);
        gc.setTextAlign(TextAlignment.LEFT);

        // Music box, bottom left -- always visible, because it is the
        // clock. Except on its own camera, where the big panel says it.
        if (!game.cameraUp) drawMusicBoxBar();
    }

    void drawMusicBoxBar() {
        double bw = 260, bh = 16;
        double bx = 30, by = H - 46;
        gc.setFill(Color.rgb(0, 0, 0, 0.5));
        gc.fillRect(bx - 6, by - 26, bw + 12, bh + 40);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 14));
        gc.fillText("MUSIC BOX", bx, by - 8);
        gc.setFill(Color.web("#1A1A22"));
        gc.fillRect(bx, by, bw, bh);
        double frac = Math.max(0, Math.min(1, game.musicBox / Game.MUSIC_BOX_MAX));
        gc.setFill(frac > 0.5 ? Color.web("#3CB043")
                : frac > 0.25 ? Color.web("#FFD700") : Color.web("#E94560"));
        gc.fillRect(bx, by, bw * frac, bh);

        // Lights taken
        if (game.lightsDisabled) {
            gc.setFill(Color.web("#E94560"));
            gc.setFont(Font.font("Arial", 22));
            gc.fillText("THE LIGHTS ARE GONE", bx, by - 44);
        }

        // Controls
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 13));
        gc.setTextAlign(TextAlignment.RIGHT);
        if (game.cameraUp) {
            gc.fillText("click a camera    SPACE = lower    click the bar = wind (CAM 11)",
                    W - 30, H - 26);
        } else if (game.maskOn) {
            gc.fillText("click anywhere to take the mask off    M", W - 30, H - 26);
        } else {
            gc.fillText("click a light    Q hall    Z/C vents    M mask    SPACE monitor",
                    W - 30, H - 26);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ---- Endings ----

    void drawJumpscare() {
        Assets a = Assets.A;
        Image img = null;
        if (a != null && game.jumpscareBy != null) {
            int i = Assets.index(game.jumpscareBy.name);
            if (i >= 0) img = a.jumpscare[i];
        }
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);
        if (img != null) {
            double iw = img.getWidth(), ih = img.getHeight();
            double s = Math.max(W / iw, H / ih);
            // A little shake, then hold.
            double jitter = scareT < 0.6 ? (Math.random() - 0.5) * 26 : 0;
            gc.drawImage(img, (W - iw * s) / 2 + jitter, (H - ih * s) / 2 + jitter,
                    iw * s, ih * s);
        } else {
            gc.setFill(Color.web("#E94560"));
            gc.setFont(Font.font("Arial", 64));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(game.jumpscareBy == null ? "?" : game.jumpscareBy.name, W / 2, H / 2);
            gc.setTextAlign(TextAlignment.LEFT);
        }
        if (scareT > 1.2) {
            gc.setFill(Color.rgb(0, 0, 0, 0.65));
            gc.fillRect(0, H - 90, W, 90);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", 24));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("ESC — back to the library", W / 2, H - 36);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    void drawWin() {
        gc.setFill(Color.rgb(0, 0, 0, 0.78));
        gc.fillRect(0, 0, W, H);
        gc.setFill(Color.web("#FFD700"));
        gc.setFont(Font.font("Arial", 72));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("6 AM", W / 2, H / 2 - 10);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 24));
        gc.fillText("Night " + game.night + " survived", W / 2, H / 2 + 46);
        gc.setFill(Color.web("#8888AA"));
        gc.setFont(Font.font("Arial", 18));
        gc.fillText("ESC — back to the library", W / 2, H / 2 + 100);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
