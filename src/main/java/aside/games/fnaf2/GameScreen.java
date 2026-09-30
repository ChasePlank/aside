package aside.games.fnaf2;

import aside.games.fnaf2.engine.Animatronic;
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
 * The office is drawn as a whole view per light state, not as one picture
 * with lit rectangles on top: in FNAF 2 the view swaps. See {@link Assets}.
 */
public class GameScreen extends UiScreen {

    final Game game;
    final int night;

    /** Source office art is 1600x768; the canvas is 1280x720. Fitting the
     *  width keeps both vent openings on screen -- covering the canvas
     *  crops 117px off each side in source space, which is most of the
     *  left vent. */
    static final double OS = W / 1600.0;
    static final double OW = 1600 * OS;
    static final double OH = 768 * OS;
    static final double OX = 0;
    static final double OY = (H - OH) / 2;

    /** Opening rectangles, in source pixels. */
    static final double[] HALL_SRC   = {545, 175, 510, 465};
    static final double[] VENT_L_SRC = {40, 415, 185, 275};
    static final double[] VENT_R_SRC = {1375, 415, 185, 275};

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

        if (game.cameraUp) drawCameraView();

        drawHUD();

        if (game.status == Game.Status.JUMPSCARED) drawJumpscare();
        if (game.status == Game.Status.SURVIVED) drawWin();
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
                        drawFigure(a, x.name, HALL_SRC);
                    }
                }
                // Foxy is staged rather than a visitor -- he does not walk
                // a path to the office, he waits in the cove and then runs
                // -- so he is not in visitors() and has to be drawn here.
                if (game.witheredFoxy.stages >= 3) {
                    drawFigure(a, game.witheredFoxy.name, HALL_SRC);
                }
            } else if (game.ventLLightOn) {
                for (Animatronic x : game.visitors()) {
                    if (x.atOpening() && x.opening == Animatronic.Opening.VENT_L) {
                        drawFigure(a, x.name, VENT_L_SRC);
                    }
                }
            } else if (game.ventRLightOn) {
                for (Animatronic x : game.visitors()) {
                    if (x.atOpening() && x.opening == Animatronic.Opening.VENT_R) {
                        drawFigure(a, x.name, VENT_R_SRC);
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
        return new double[]{OX + src[0] * OS, OY + src[1] * OS, src[2] * OS, src[3] * OS};
    }

    // ---- Camera ----

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
     *  number in a corner. */
    void drawMusicBoxPanel() {
        double bw = 520, bh = 34;
        double bx = (W - bw) / 2, by = H - 120;

        gc.setFill(Color.rgb(0, 0, 0, 0.62));
        gc.fillRect(bx - 24, by - 54, bw + 48, bh + 96);

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
        gc.fillText(game.winding ? "WINDING" : "hold W to wind", W / 2, by + bh + 30);
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
            gc.fillText("1-9, 0, - = cams    SPACE = lower    W = wind (CAM 11)", W - 30, H - 26);
        } else {
            gc.fillText("Q hall   Z/C vents   M mask   SPACE monitor", W - 30, H - 26);
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
