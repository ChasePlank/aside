package aside.games.fnaf;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Characters screen. Each animatronic is unlocked by dying to them —
 * their entry (portrait, behaviour, how to survive) appears once met.
 * R also resets all progress (beaten nights + unlocks).
 */
public class InfoScreen extends UiScreen {
    static final int W = 1280, H = 720;

    private int focusIndex = 0;
    private boolean drawRequested = false;
    private String notice = "";
    private double noticeTimer = 0;
    private boolean confirmingReset = false;

    // name, role, portrait sprite, behaviour, how to survive
    private static final String[][] DATA = {
        {"Monty", "Bonnie role — left door",
         "Gator. Lives on the Show Stage and drifts down the West Hall\n"
         + "toward your LEFT door. Camera-shy: watching his room on the\n"
         + "monitor freezes him in place.",
         "Check cam 1/2/4/5. If he's in the West Hall corner, close the\n"
         + "left door. Watching him on camera stalls him — free time."},
        {"Roxanne", "Chica role — right door",
         "Wolf. Works the right side: Restrooms, Kitchen, then the East\n"
         + "Hall. Doesn't stall on camera — she keeps coming.",
         "Light the right doorway often. Close the right door the moment\n"
         + "she appears in the corner; you get a short grace window."},
        {"Chica", "Freddy role — power-out",
         "Chicken. Right side, but slower. She's the one who comes for\n"
         + "you in the blackout — the music box plays, then goes quiet.",
         "Keep power above zero. If you black out, the box plays for a\n"
         + "while — a late blackout can still reach 6 AM."},
        {"Freddy", "Foxy role — Kid's Cove sprint",
         "Bear. Hides in Kid's Cove (cam 3) behind the curtain. Watch\n"
         + "him in stages: empty cove, peeking, emerged, then GONE — and\n"
         + "GONE means he's already running.",
         "Check cam 3 regularly. The moment it says GONE, slam the LEFT\n"
         + "door. A closed door repels him and costs you power."},
    };

    public InfoScreen(UiManager ui) {
        super(ui);
    }
    @Override public void enter() { drawRequested = true; }

    @Override
    public void handleKey(KeyEvent e) {
        if (confirmingReset) {
            if (e.getCode() == KeyCode.Y) {
                FnafGame.progress.reset();
                notice = "Progress reset — night 1 is all you have again.";
                noticeTimer = 3.0;
                confirmingReset = false;
                drawRequested = true;
            } else if (e.getCode() == KeyCode.N || e.getCode() == KeyCode.ESCAPE) {
                confirmingReset = false;
                drawRequested = true;
            }
            return;
        }
        switch (e.getCode()) {
            case UP, LEFT -> { focusIndex = (focusIndex - 1 + DATA.length) % DATA.length; drawRequested = true; }
            case DOWN, RIGHT -> { focusIndex = (focusIndex + 1) % DATA.length; drawRequested = true; }
            case R -> { confirmingReset = true; drawRequested = true; }
            case ESCAPE -> { ui.replace(new MainMenu(ui)); e.consume(); }
            default -> {}
        }
    }

    public void tick(double dt) {
        if (noticeTimer > 0) {
            noticeTimer -= dt;
            if (noticeTimer <= 0) notice = "";
            drawRequested = true;
        }
        if (drawRequested) { render(); drawRequested = false; }
    }

    void render() {
        gc.setFill(Color.web("#0A0A12"));
        gc.fillRect(0, 0, W, H);

        gc.setFill(Color.web("#E94560"));
        gc.setFont(Font.font("Arial", 34));
        gc.fillText("Characters", 40, 55);

        Progress p = FnafGame.progress;

        // Left column: list of four
        for (int i = 0; i < DATA.length; i++) {
            boolean met = p != null && p.met[i];
            boolean focused = (i == focusIndex);
            double y = 120 + i * 70;

            gc.setFill(focused ? Color.web("#2A2A4E") : Color.web("#161628"));
            gc.fillRect(30, y - 30, 250, 60);
            gc.setStroke(focused ? Color.web("#FFD700") : Color.web("#333355"));
            gc.setLineWidth(focused ? 2 : 1);
            gc.strokeRect(30, y - 30, 250, 60);

            // Portrait (locked = silhouette)
            javafx.scene.image.Image img = sprite(i);
            if (met) {
                gc.drawImage(img, 40, y - 26, 48, 48);
            } else {
                gc.setFill(Color.web("#22223A"));
                gc.fillRect(40, y - 26, 48, 48);
                gc.setFill(Color.web("#555577"));
                gc.setFont(Font.font("Arial", 26));
                gc.fillText("?", 55, y + 10);
            }

            gc.setFill(met ? (focused ? Color.web("#FFD700") : Color.web("#CCCCCC"))
                           : Color.web("#44445A"));
            gc.setFont(Font.font("Arial", 18));
            gc.fillText(met ? DATA[i][0] : "???", 100, y + 2);

            gc.setFill(Color.web("#555577"));
            gc.setFont(Font.font("Arial", 10));
            gc.fillText(met ? DATA[i][1] : "not yet met", 100, y + 18);
        }

        // Right panel: detail
        boolean met = p != null && p.met[focusIndex];
        double px = 330, py = 90;
        gc.setFill(Color.web("#161628"));
        gc.fillRect(px, py, W - px - 40, 430);
        gc.setStroke(Color.web("#333355"));
        gc.strokeRect(px, py, W - px - 40, 430);

        if (met) {
            gc.setFill(Color.web("#FFD700"));
            gc.setFont(Font.font("Arial", 30));
            gc.fillText(DATA[focusIndex][0], px + 25, py + 55);
            gc.setFill(Color.web("#8888AA"));
            gc.setFont(Font.font("Arial", 14));
            gc.fillText(DATA[focusIndex][1], px + 25, py + 80);

            gc.setFill(Color.web("#CCCCCC"));
            gc.setFont(Font.font("Arial", 15));
            drawWrapped(DATA[focusIndex][2], px + 25, py + 120, W - px - 90);

            gc.setFill(Color.web("#3CB043"));
            gc.setFont(Font.font("Arial", 15));
            gc.fillText("How to survive:", px + 25, py + 265);
            gc.setFill(Color.web("#CCCCCC"));
            drawWrapped(DATA[focusIndex][3], px + 25, py + 295, W - px - 90);

            gc.drawImage(sprite(focusIndex), px + W - px - 200, py + 300, 150, 150);
        } else {
            gc.setFill(Color.web("#44445A"));
            gc.setFont(Font.font("Arial", 26));
            gc.fillText("Not yet met.", px + 25, py + 60);
            gc.setFont(Font.font("Arial", 15));
            gc.setFill(Color.web("#555577"));
            drawWrapped("Survive a night with this one, or let them catch you — "
                    + "either way, meeting them unlocks their file here.",
                    px + 25, py + 100, W - px - 90);
        }

        // Footer
        gc.setFill(Color.web("#44445A"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText("↑↓ browse   R reset all progress   ESC back", 40, H - 40);

        int met_count = 0;
        for (int i = 0; i < 4; i++) if (p != null && p.met[i]) met_count++;
        gc.fillText("Met " + met_count + "/4", W - 120, H - 40);

        if (confirmingReset) {
            gc.setFill(Color.rgb(0, 0, 0, 0.85));
            gc.fillRect(0, 0, W, H);
            gc.setFill(Color.web("#E94560"));
            gc.setFont(Font.font("Arial", 34));
            gc.fillText("Reset ALL progress?", W/2 - 180, H/2 - 40);
            gc.setFill(Color.web("#CCCCCC"));
            gc.setFont(Font.font("Arial", 17));
            gc.fillText("Nights beaten and character unlocks will be erased.", W/2 - 230, H/2 + 10);
            gc.setFill(Color.web("#FFD700"));
            gc.setFont(Font.font("Arial", 20));
            gc.fillText("Y — yes, reset      N — cancel", W/2 - 160, H/2 + 70);
        } else if (!notice.isEmpty()) {
            gc.setFill(Color.web("#3CB043"));
            gc.setFont(Font.font("Arial", 14));
            gc.fillText(notice, 40, H - 65);
        }
    }

    /** Prefer the real render; fall back to the pixel sprite. */
    javafx.scene.image.Image sprite(int i) {
        Assets as = Assets.A;
        if (as != null && i >= 0 && i < 4 && as.charMain[i] != null) return as.charMain[i];
        return switch (i) {
            case 0 -> Sprites.monty;
            case 1 -> Sprites.roxanne;
            case 2 -> Sprites.chica;
            default -> Sprites.freddy;
        };
    }

    void drawWrapped(String text, double x, double y, double maxW) {
        for (String line : text.split("\n")) {
            gc.fillText(line, x, y);
            y += 22;
        }
    }
}
