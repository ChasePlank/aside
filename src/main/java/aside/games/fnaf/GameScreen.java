package aside.games.fnaf;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import aside.games.fnaf.engine.*;
import javafx.animation.AnimationTimer;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * The office. Player view: desk, two doorways (left/right), buttons
 * for doors/lights/camera. FNAF 1 layout.
 *
 * Controls:
 *   A / D  — left / right door
 *   Q / E  — left / right light
 *   SPACE  — toggle camera
 *   1-9,0  — select camera room (0 = cam 10)
 *   ESC    — pause
 */
public class GameScreen extends UiScreen {
    static final int W = 1280, H = 720;

    final Game game;
    final int night;

    double doorLFlash = 0, doorRFlash = 0;

    final int[] customLevels;   // non-null only for Custom Night

    public GameScreen(UiManager ui, int night) {
        this(ui, night, null);
    }

    /** Custom Night: explicit AI levels (0-20 per animatronic). */
    public GameScreen(UiManager ui, int[] customLevels) {
        this(ui, 6, customLevels);
    }

    public GameScreen(UiManager ui, int night, int[] customLevels) {
        super(ui);
        this.night = night;
        this.customLevels = customLevels;
        this.game = new Game(night, System.nanoTime(), customLevels);  // fresh seed each run
    }


    @Override
    public Parent getRoot() { return root; }

    @Override
    public void tick(double dt) {
        double frame = Math.min(dt, 0.25);
        game.update(frame);
        if (game.status != Game.Status.PLAYING
                && game.status != Game.Status.POWER_OUT) {
            recordOutcome();
        }
        syncAudio();
        doorLFlash = Math.max(0, doorLFlash - frame);
        doorRFlash = Math.max(0, doorRFlash - frame);
        render();
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case A -> { game.toggleLeftDoor(); doorLFlash = 0.3; e.consume(); }
            case D -> { game.toggleRightDoor(); doorRFlash = 0.3; e.consume(); }
            case Q -> { game.toggleLeftLight(); e.consume(); }
            case E -> { game.toggleRightLight(); e.consume(); }
            case SPACE -> { game.toggleCamera(); e.consume(); }
            case ESCAPE -> { ui.push(new PauseScreen(ui, this)); e.consume(); }
            default -> {
                // number keys select camera rooms 1..10
                if (game.cameraUp) {
                    String n = e.getText();
                    if (!n.isEmpty() && Character.isDigit(n.charAt(0))) {
                        int d = Integer.parseInt(n);
                        if (d == 0) d = 10;
                        if (d >= 1 && d <= 10) { game.setCam(d); e.consume(); }
                    }
                }
            }
        }
    }

    void render() {
        // ---- Office view ----
        // Dark room, desk at bottom, two doorways left/right
        gc.setFill(Color.web("#0A0A12"));
        gc.fillRect(0, 0, W, H);

        // Real office art (the supplied FNAF 1 office, 1200x540 source).
        // Fitted to width so both doorways stay visible, letterboxed
        // above and below rather than cropped at the edges.
        Assets a = Assets.A;
        if (a != null && a.office != null) {
            gc.drawImage(a.office, OFFICE_X, OFFICE_Y, OFFICE_W, OFFICE_H);
        } else {
            // Fallback if the art is missing: flat room + spinning fan
            gc.setFill(Color.web("#1A1A2E"));
            gc.fillRect(160, 80, W - 320, H - 200);
            gc.setFill(Color.web("#12122A"));
            gc.fillRect(160, H - 220, W - 320, 120);
            gc.setFill(Color.web("#2A2A3E"));
            gc.fillRect(W/2 - 200, H - 140, 400, 100);
            drawFan();
        }

        // Doorway overlays: light glow, closed slab, buttons
        drawDoorway(-1);
        drawDoorway(1);

        // Whoever is standing in a lit doorway
        drawDoorwayOccupants();

        // Camera overlay
        if (game.cameraUp) drawCameraView();

        // HUD (after camera so power/usage/time stay visible)
        drawHUD();

        // Status overlays
        if (game.status == Game.Status.POWER_OUT) drawBlackout();
        if (game.status == Game.Status.JUMPSCARED) drawJumpscare();
        if (game.status == Game.Status.SURVIVED) drawWin();
    }

    void drawFan() {
        double cx = W/2 + 260, cy = H - 180;
        gc.save();
        gc.setFill(Color.web("#3A3A4E"));
        gc.fillOval(cx - 30, cy - 30, 60, 60);
        // spinning blades (fake it: rotate by time)
        double rot = (System.nanoTime() / 1000000.0) % 360;
        gc.translate(cx, cy);
        gc.rotate(rot);
        gc.setFill(Color.web("#5A5A6E"));
        for (int i = 0; i < 3; i++) {
            gc.rotate(120);
            gc.fillOval(0, -24, 48, 12);
        }
        gc.restore();
    }

    // Office art geometry (source 1200x540 drawn at 1280x576, y+72)
    static final double OFFICE_X = 0, OFFICE_Y = 72, OFFICE_W = 1280, OFFICE_H = 576;
    // Doorway openings inside that art, in canvas coords
    static final double[] DOOR_L = {0, 150, 200, 420};
    static final double[] DOOR_R = {1080, 150, 200, 420};

    static double[] doorRect(int side) { return side < 0 ? DOOR_L : DOOR_R; }

    void drawDoorway(int side) {
        double[] r = doorRect(side);
        boolean lit = side < 0 ? game.leftLightOn : game.rightLightOn;
        boolean closed = side < 0 ? game.leftDoorClosed : game.rightDoorClosed;

        // Lit view: brighten the doorway so the hall beyond is visible
        if (lit && !closed) {
            gc.setGlobalAlpha(0.45);
            gc.setFill(Color.web("#B8C4D8"));
            gc.fillRect(r[0], r[1], r[2], r[3]);
            gc.setGlobalAlpha(1);
        }

        // Closed door: steel slab drops over the opening
        if (closed) {
            gc.setFill(Color.web("#3C3C4E"));
            gc.fillRect(r[0], r[1], r[2], r[3]);
            gc.setFill(Color.web("#4A4A5E"));
            for (int i = 0; i < 7; i++) {
                gc.fillRect(r[0], r[1] + i * (r[3] / 7.0), r[2], 8);
            }
            gc.setStroke(Color.web("#22222E"));
            gc.setLineWidth(3);
            gc.strokeRect(r[0], r[1], r[2], r[3]);
        }

        // Buttons, mounted inside the doorway edge
        double bx = side < 0 ? r[0] + r[2] + 12 : r[0] - 52;
        double by = 250;
        gc.setFill(closed ? Color.web("#E94560") : Color.web("#5A2A3A"));
        gc.fillOval(bx, by, 40, 40);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 9));
        gc.fillText("DOOR", bx + 4, by + 62);

        gc.setFill(lit ? Color.web("#FFD700") : Color.web("#5A502A"));
        gc.fillOval(bx, by + 90, 40, 40);
        gc.fillText("LIGHT", bx + 3, by + 152);

        // Door warning: pixel sprite pulses when someone is at this door
        Animatronic atDoor = animatronicAt(side);
        if (atDoor != null) {
            double pulse = 0.55 + 0.45 * Math.sin(System.nanoTime() / 150_000_000.0);
            gc.setGlobalAlpha(pulse);
            gc.drawImage(spriteFor(atDoor), bx - 4, by + 180, 48, 48);
            gc.setGlobalAlpha(1);
        }
    }

    /** Which animatronic (if any) is waiting at the given door. */
    Animatronic animatronicAt(int side) {
        for (Animatronic a : new Animatronic[]{game.monty, game.roxanne, game.chica}) {
            if (a.atOffice() && a.doorSide == side && !a.officeEntryResolved) return a;
        }
        if (game.freddy.stages >= 3 && game.freddy.doorSide == side && !game.freddy.sprintResolved) {
            return game.freddy;
        }
        return null;
    }

    /** Whoever is in a doorway, but only visible with the light on.
     *  Real character renders (white background keyed out at load),
     *  clipped to the doorway opening so they loom without spilling. */
    void drawDoorwayOccupants() {
        for (Animatronic a : new Animatronic[]{game.monty, game.roxanne, game.chica}) {
            if (!a.atOffice()) continue;
            boolean lit = a.doorSide < 0 ? game.leftLightOn : game.rightLightOn;
            boolean closed = a.doorSide < 0 ? game.leftDoorClosed : game.rightDoorClosed;
            if (!lit || closed) continue;

            javafx.scene.image.Image img = charImage(a);
            if (img == null) continue;

            double[] r = doorRect(a.doorSide);
            // Fill the doorway height, anchored low, then clip
            double scale = r[3] / img.getHeight() * 1.15;
            double dw = img.getWidth() * scale;
            double dx = r[0] + (r[2] - dw) / 2;
            double dy = r[1] + r[3] - img.getHeight() * scale;

            gc.save();
            gc.beginPath();
            gc.rect(r[0], r[1], r[2], r[3]);
            gc.clip();
            // Slight darkening so they read as being in the dark hallway
            gc.setGlobalAlpha(0.92);
            gc.drawImage(img, dx, dy, dw, img.getHeight() * scale);
            gc.setGlobalAlpha(1);
            gc.restore();
        }
    }

    /** Real render for an animatronic — alternates poses so the same
     *  character doesn't look static across a night. */
    javafx.scene.image.Image charImage(Animatronic a) {
        Assets as = Assets.A;
        if (as == null) return null;
        int i = charIndex(a);
        if (i < 0) return null;
        boolean alt = ((int) (game.time) / 7) % 2 == 1;
        javafx.scene.image.Image img = alt ? as.charAlt[i] : as.charMain[i];
        return img != null ? img : as.charMain[i];
    }

    static int charIndex(Animatronic a) {
        return switch (a.name) {
            case "Monty" -> 0;
            case "Roxanne" -> 1;
            case "Chica" -> 2;
            case "Freddy" -> 3;
            default -> -1;
        };
    }

    javafx.scene.image.Image spriteFor(Animatronic a) {
        return switch (a.name) {
            case "Monty" -> Sprites.monty;
            case "Roxanne" -> Sprites.roxanne;
            case "Chica" -> Sprites.chica;
            default -> Sprites.freddy;
        };
    }

    void drawHUD() {
        // Power (bottom-left)
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 22));
        gc.fillText("Power: " + (int) Math.ceil(game.power) + "%", 30, H - 30);
        // Usage bars
        int bars = game.usageBars;
        StringBuilder barStr = new StringBuilder();
        for (int i = 0; i < bars; i++) barStr.append("■");
        gc.setFill(bars <= 2 ? Color.web("#3CB043") : bars <= 4 ? Color.web("#FFD700") : Color.web("#E94560"));
        gc.fillText("Usage: " + barStr, 30, H - 60);

        // Time (top-right)
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 28));
        int displayHour = game.hour == 0 ? 12 : game.hour;
        String am = "AM";
        gc.fillText(displayHour + " " + am, W - 110, 50);
        // Night (top-right, second line)
        gc.setFont(Font.font("Arial", 20));
        gc.fillText("Night " + game.night, W - 110, 80);

        // Controls hint (bottom-right, changes based on camera state)
        gc.setFont(Font.font("Arial", 13));
        gc.setFill(Color.web("#8888AA"));
        if (game.cameraUp) {
            gc.fillText("1-9,0 = cams   SPACE = lower", W - 300, H - 30);
        } else {
            gc.fillText("A/D doors  Q/E lights  SPACE camera", W - 330, H - 30);
        }
    }

    void drawCameraView() {
        gc.setFill(Color.web("#050508"));
        gc.fillRect(0, 0, W, H);

        int cam = game.currentCam;
        Assets as = Assets.A;

        // Kitchen: FNAF 1 disables this camera on purpose — audio only.
        if (cam == 8) {
            gc.setFill(Color.web("#000000"));
            gc.fillRect(0, 0, W, H);
            gc.setFill(Color.web("#8888AA"));
            gc.setFont(Font.font("Monospaced", 22));
            gc.fillText("CAMERA DISABLED", W/2 - 110, H/2 - 10);
            gc.setFont(Font.font("Monospaced", 16));
            gc.fillText("AUDIO ONLY", W/2 - 60, H/2 + 20);
            // Ambient pot-clanking hint (audio comes later)
            drawCamFrame(cam, "Kitchen");
            return;
        }

        javafx.scene.image.Image feed = null;
        if (cam == 3 && as != null) {
            // Kid's Cove: curtain state IS the Foxy-role tell
            feed = (game.freddy.stages <= 0) ? as.coveClosed : as.coveOpen;
        } else if (as != null && cam >= 1 && cam <= 10) {
            feed = as.room[cam];
        }

        if (feed != null) {
            drawFeedCover(feed);
        } else {
            gc.setFill(Color.web("#0A0A14"));
            gc.fillRect(0, 0, W, H);
        }

        // Animatronics standing in this room, composited over the feed
        for (Animatronic a : new Animatronic[]{game.monty, game.roxanne, game.chica}) {
            if (a.currentRoom() != cam) continue;
            javafx.scene.image.Image img = charImage(a);
            if (img == null) continue;
            double dh = 420, dw = img.getWidth() * (dh / img.getHeight());
            gc.setGlobalAlpha(0.9);
            gc.drawImage(img, W/2 - dw/2 + 90, H - dh - 60, dw, dh);
            gc.setGlobalAlpha(1);
        }

        // Cove tell (Foxy-role stage) when not covered by the curtain art
        if (cam == 3) {
            String stage = switch (game.freddy.stages) {
                case 0 -> null;                    // closed curtain says it
                case 1 -> "SOMETHING MOVED";
                case 2 -> "IT'S ME";
                default -> "GONE";
            };
            if (stage != null && game.freddy.stages >= 3) {
                gc.setFill(Color.web("#E94560"));
                gc.setFont(Font.font("Arial", 34));
                gc.fillText(stage, W/2 - 70, 120);
            }
        }

        drawCamFrame(cam, roomName(cam));
    }

    /** Fit a feed image to cover the frame (crop overflow), centred. */
    void drawFeedCover(javafx.scene.image.Image img) {
        double iw = img.getWidth(), ih = img.getHeight();
        double scale = Math.max(W / iw, H / ih);
        double dw = iw * scale, dh = ih * scale;
        gc.drawImage(img, (W - dw) / 2, (H - dh) / 2, dw, dh);
    }

    /** Label, static, scan lines, map — the "you're looking at a
     *  monitor" layer that sits on every feed. */
    void drawCamFrame(int cam, String roomName) {
        java.util.Random noise = new java.util.Random();
        gc.setFill(Color.rgb(255, 255, 255, 0.06));
        for (int i = 0; i < 500; i++) {
            gc.fillRect(noise.nextInt(W), noise.nextInt(H), 2, 2);
        }

        gc.setFill(Color.rgb(0, 0, 0, 0.55));
        gc.fillRect(20, 20, 360, 44);
        gc.setFill(Color.web("#CCCCCC"));
        gc.setFont(Font.font("Monospaced", 19));
        gc.fillText("CAM " + cam + " — " + roomName, 34, 50);

        drawCamMap();

        gc.setFill(Color.rgb(0, 0, 0, 0.16));
        for (int y = 0; y < H; y += 4) gc.fillRect(0, y, W, 2);
    }

    String roomName(int room) {
        return switch (room) {
            case 0 -> "Office";
            case 1 -> "Show Stage";
            case 2 -> "Backstage";
            case 3 -> "Kid's Cove";  // Freddy's cove (Foxy role)
            case 4 -> "West Hall";
            case 5 -> "W. Hall Corner";
            case 6 -> "Supply Closet";
            case 7 -> "Restrooms";
            case 8 -> "Kitchen";
            case 9 -> "E. Hall Corner";
            case 10 -> "E. Hall";
            default -> "???";
        };
    }

    String camOccupant() {
        for (Animatronic a : new Animatronic[]{game.monty, game.roxanne, game.chica}) {
            if (a.currentRoom() == game.currentCam) return a.name;
        }
        if (game.currentCam == 3) {
            if (game.freddy.stages >= 3) return "GONE";
            if (game.freddy.stages == 2) return "EMERGED";
            if (game.freddy.stages == 1) return "PEEKING";
            return "empty cove";
        }
        return null;
    }

    Animatronic camAnimatronic() {
        for (Animatronic a : new Animatronic[]{game.monty, game.roxanne, game.chica}) {
            if (a.currentRoom() == game.currentCam) return a;
        }
        return null;
    }

    void drawCamMap() {
        // FNAF-style building map (bottom-right) — rooms as labelled
        // boxes, connected by hall lines. Clickable in a real build;
        // here it's reference + current-cam highlight.
        double mx = W - 280, my = H - 240;
        gc.setFill(Color.rgb(0, 0, 0, 0.7));
        gc.fillRect(mx - 15, my - 15, 265, 225);

        // room positions: id -> {x, y, w, h} on the mini-map
        int[][] rooms = {
            {1, 0, 0, 44, 30},   // Show Stage (top-left)
            {2, 0, 34, 44, 30},  // Backstage
            {3, 48, 0, 44, 30},  // Kid's Cove (Foxy)
            {4, 48, 34, 44, 30}, // West Hall
            {5, 48, 68, 44, 30}, // W. Hall Corner
            {6, 0, 68, 44, 30},  // Supply Closet
            {7, 96, 0, 44, 30},  // Restrooms
            {8, 96, 34, 44, 30}, // Kitchen
            {9, 96, 68, 44, 30}, // E. Hall Corner
            {10, 144, 68, 44, 30}, // E. Hall
        };

        // Hall connections (lines between rooms)
        gc.setStroke(Color.web("#333355"));
        gc.setLineWidth(2);
        int[][] halls = {
            {1, 2}, {1, 3}, {2, 4}, {3, 4}, {4, 5}, {5, 6},
            {3, 7}, {4, 8}, {7, 8}, {8, 9}, {5, 9}, {9, 10},
        };
        for (int[] h : halls) {
            int ax = rooms[h[0]-1][1] + rooms[h[0]-1][3]/2;
            int ay = rooms[h[0]-1][2] + rooms[h[0]-1][4]/2;
            int bx = rooms[h[1]-1][1] + rooms[h[1]-1][3]/2;
            int by = rooms[h[1]-1][2] + rooms[h[1]-1][4]/2;
            gc.strokeLine(mx + ax, my + ay, mx + bx, my + by);
        }

        // Room boxes
        for (int[] r : rooms) {
            double x = mx + r[1], y = my + r[2];
            boolean sel = game.currentCam == r[0];
            gc.setStroke(sel ? Color.web("#FFD700") : Color.web("#555577"));
            gc.setLineWidth(sel ? 3 : 1);
            gc.strokeRect(x, y, r[3], r[4]);
            gc.setFill(sel ? Color.web("#FFD700") : Color.web("#8888AA"));
            gc.setFont(Font.font("Monospaced", 10));
            gc.fillText(String.valueOf(r[0]), x + 4, y + 14);
        }

        // Office (bottom-right of map)
        double ox = mx + 200, oy = my + 68;
        gc.setStroke(Color.web("#E94560"));
        gc.setLineWidth(2);
        gc.strokeRect(ox, oy, 30, 30);
        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 11));
        gc.fillText("YOU", ox + 4, oy + 20);
        // Hall line from office to E. Hall
        gc.setStroke(Color.web("#333355"));
        gc.strokeLine(mx + 144 + 44, my + 68 + 30, ox, oy + 30);
    }

    void drawBlackout() {
        // Everything dark except faint music box glow
        gc.setFill(Color.rgb(0, 0, 0, 0.92));
        gc.fillRect(0, 0, W, H);
        if (game.blackoutMusicPlaying) {
            // faint glow + music text
            gc.setFill(Color.web("#222233"));
            gc.fillOval(W/2 - 100, H/2 - 100, 200, 200);
            gc.setFill(Color.web("#555577"));
            gc.setFont(Font.font("Arial", 20));
            gc.fillText("♪", W/2 - 8, H/2 + 8);
        }
        // no text after music stops — pure darkness (the player just waits)
    }

    void drawJumpscare() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        Animatronic a = game.jumpscareBy != null ? game.jumpscareBy : game.chica;
        int i = charIndex(a);
        Assets as = Assets.A;
        javafx.scene.image.Image img = (as != null && i >= 0) ? as.jumpscare[i] : null;

        if (img != null) {
            // Zoom past the background: crop to the face and blow it up
            // to fill the frame. Chica's source is a lit room and
            // Roxanne's is the atrium — neither can be keyed out, so
            // cropping + a vignette does the work instead.
            double[] c = Assets.JSCARE_CROP[i];
            double cx = c[0], cy = c[1], cw = c[2], ch = c[3];

            // Slow push-in over the first ~0.35s, then hold
            double t = Math.min(1.0, (System.nanoTime() % 4_000_000_000L) / 4e9);
            double zoom = 1.0;
            double shakeX = (Math.random() - 0.5) * 34;
            double shakeY = (Math.random() - 0.5) * 26;

            double scale = Math.max(W / cw, H / ch) * zoom;
            double dw = cw * scale, dh = ch * scale;
            double dx = (W - dw) / 2 + shakeX;
            double dy = (H - dh) / 2 + shakeY;

            gc.save();
            gc.beginPath();
            gc.rect(0, 0, W, H);
            gc.clip();
            // Draw the crop region of the source, scaled up
            gc.drawImage(img, cx, cy, cw, ch, dx, dy, dw, dh);
            gc.restore();

            // Vignette so any remaining background falls away to black
            var vg = new javafx.scene.paint.RadialGradient(
                    0, 0, W/2, H/2, Math.max(W, H) * 0.62, false,
                    javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0.0, Color.rgb(0, 0, 0, 0)),
                    new javafx.scene.paint.Stop(0.55, Color.rgb(0, 0, 0, 0)),
                    new javafx.scene.paint.Stop(1.0, Color.rgb(0, 0, 0, 1)));
            gc.setFill(vg);
            gc.fillRect(0, 0, W, H);
        } else {
            // Fallback: pixel sprite blown up
            double shake = (Math.random() - 0.5) * 30;
            gc.drawImage(spriteFor(a), W/2 - 256 + shake, H/2 - 256, 512, 512);
        }

        // Heavy static
        java.util.Random noise = new java.util.Random();
        gc.setFill(Color.rgb(255, 255, 255, 0.10));
        for (int k = 0; k < 420; k++) {
            gc.fillRect(noise.nextInt(W), noise.nextInt(H), 3, 3);
        }

        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 38));
        gc.fillText(a.name + " got you.", 40, H - 88);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 19));
        gc.fillText("ENTER — retry night " + night + "     ESC — menu", 40, H - 50);
        if (FnafGame.progress != null && !FnafGame.progress.met[i < 0 ? 2 : i]) {
            gc.setFill(Color.web("#FFD700"));
            gc.setFont(Font.font("Arial", 15));
            gc.fillText("Character file unlocked — see Characters on the menu.", 40, H - 22);
        }
    }

    void drawWin() {
        gc.setFill(Color.rgb(0, 0, 0, 0.85));
        gc.fillRect(0, 0, W, H);
        gc.setFill(Color.web("#FFD700"));
        gc.setFont(Font.font("Arial", 60));
        gc.fillText("6 AM", W/2 - 80, H/2 - 40);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 26));
        String msg = night >= 5 ? "You survived all five nights." : "Night " + night + " complete.";
        gc.fillText(msg, W/2 - 120, H/2 + 20);
        gc.setFont(Font.font("Arial", 20));
        if (night < 5) {
            gc.fillText("ENTER — night " + (night + 1), W/2 - 100, H/2 + 80);
        } else {
            gc.fillText("ENTER — credits / menu", W/2 - 120, H/2 + 80);
        }
        gc.fillText("ESC — menu", W/2 - 60, H/2 + 110);
    }

    /** Post-death / post-win key handling. Called from Main when the
     *  game is in an end state — ENTER retries/continues, ESC exits. */
    void handleEndKeys(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            if (game.status == Game.Status.SURVIVED) {
                // Custom Night is a one-off; normal nights progress
                if (customLevels != null) {
                    ui.replace(new MainMenu(ui));
                } else if (night < 5) {
                    ui.replace(new GameScreen(ui, night + 1));
                } else {
                    ui.replace(new MainMenu(ui));
                }
            } else {
                ui.replace(new GameScreen(ui, night, customLevels));
            }
        } else if (e.getCode() == KeyCode.ESCAPE) {
            ui.replace(new MainMenu(ui));
        }
    }

    /** Record outcomes into progress exactly once (called from the tick
     *  loop when the game first leaves PLAYING). */
    void recordOutcome() {
        if (recorded) return;
        recorded = true;
        Progress p = FnafGame.progress;
        if (p == null) return;
        if (game.status == Game.Status.SURVIVED) {
            if (customLevels == null) p.beat(night);
        } else if (game.status == Game.Status.JUMPSCARED && game.jumpscareBy != null) {
            p.meet(game.jumpscareBy.name);
        }
    }

    private boolean recorded = false;

    // ---- audio ----
    private Game.Status prevStatus = null;
    private boolean prevCameraUp = false;
    private boolean prevLeftLight = false, prevRightLight = false;
    private int prevMontyRoom = -1, prevRoxyRoom = -1, prevChicaRoom = -1, prevFreddyStage = -1;
    private boolean prevSomeoneLeft = false, prevSomeoneRight = false;

    /**
     * Drive sound from state changes rather than hooking every action.
     *
     * Comparing this frame to the last means a cue fires exactly once
     * per change no matter which code path caused it -- and it catches
     * changes the engine makes on its own (power failing, an animatronic
     * moving) which a key handler would never see.
     */
    void syncAudio() {
        Audio a = Audio.A;
        if (a == null) return;

        // Office ambience is the fan; the camera replaces it with static.
        if (prevStatus == null) a.music("fan_hum");
        if (game.status == Game.Status.POWER_OUT) {
            if (prevStatus != Game.Status.POWER_OUT) {
                a.stopMusic();
                a.sfx("power_down");
            }
        } else if (game.cameraUp) {
            a.music("static");
        } else {
            a.music("fan_hum");
        }

        if (prevStatus != null && prevStatus != game.status) {
            if (game.status == Game.Status.JUMPSCARED) {
                a.stopMusic();
                String who = game.jumpscareBy != null ? game.jumpscareBy.name : null;
                a.scream(who);
            } else if (game.status == Game.Status.SURVIVED) {
                a.stopMusic();
                a.sfx("chime_6am");
            } else if (game.status == Game.Status.PLAYING
                    && prevStatus == Game.Status.POWER_OUT) {
                a.sfx("power_up");
            }
        }

        if (prevStatus != null) {
            if (game.cameraUp != prevCameraUp) {
                a.sfx(game.cameraUp ? "camera_up" : "camera_down", "camera_up");
            }
            if (game.leftLightOn != prevLeftLight) a.sfx("light_click");
            if (game.rightLightOn != prevRightLight) a.sfx("light_click");
        }

        // Someone arriving at a door is its own sound -- the moment the
        // player has to decide, before the light confirms it.
        if (prevStatus != null) {
            Animatronic left = animatronicAt(-1), right = animatronicAt(1);
            boolean leftNow = left != null, rightNow = right != null;
            if (leftNow != prevSomeoneLeft || rightNow != prevSomeoneRight) {
                a.sfx("at_door");
            }
            prevSomeoneLeft = leftNow;
            prevSomeoneRight = rightNow;
        }

        // Freddy's staged sprint down the west hall.
        if (prevStatus != null && game.freddy.stages >= 3
                && prevFreddyStage < 3) {
            a.sfx("sprint");
        }

        // Movement: a footstep when someone changes room or advances a
        // staged approach. This is why the sound layer watches state
        // instead of input.
        if (prevMontyRoom >= 0) {
            if (moved(prevMontyRoom, game.monty.currentRoom())
                    || moved(prevRoxyRoom, game.roxanne.currentRoom())
                    || moved(prevChicaRoom, game.chica.currentRoom())
                    || moved(prevFreddyStage, game.freddy.stages)) {
                a.sfx("footstep");
            }
            if (game.currentCam == 8) a.sfx("pot_clank");
        }

        prevStatus = game.status;
        prevCameraUp = game.cameraUp;
        prevLeftLight = game.leftLightOn;
        prevRightLight = game.rightLightOn;
        prevMontyRoom = game.monty.currentRoom();
        prevRoxyRoom = game.roxanne.currentRoom();
        prevChicaRoom = game.chica.currentRoom();
        prevFreddyStage = game.freddy.stages;
    }

    static boolean moved(int before, int now) { return before >= 0 && before != now; }
}
