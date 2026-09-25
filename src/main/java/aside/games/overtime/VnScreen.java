package aside.games.overtime;

import aside.ui.Audio;
import aside.ui.LibraryScreen;
import aside.ui.Assets;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import aside.engine.Choice;
import aside.engine.Script;
import aside.engine.Vn;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The visual novel itself.
 *
 * Layout is fixed at 1280x720: a background, up to three character
 * sprites standing on a shared floor line, and a text box across the
 * lower third. Choices appear above the box; the box is hidden while
 * choosing so the options read as the focus.
 */
public class VnScreen extends UiScreen {

    // ---- layout ----
    static final double FLOOR_Y = 706;          // where sprites' feet sit
    static final double FIGURE_H = 660;         // on-screen height of a figure
    static final double BOX_X = 56, BOX_Y = 508, BOX_W = 1168, BOX_H = 176;
    static final double TEXT_X = BOX_X + 40, TEXT_Y = BOX_Y + 62, LINE_H = 36;
    static final double ZONE_LEFT = 292, ZONE_CENTER = 640, ZONE_RIGHT = 988;

    static final Font F_SPEAKER = Font.font("Georgia", 27);
    static final Font F_TEXT = Font.font("Georgia", 23);
    static final Font F_CHOICE = Font.font("Georgia", 22);
    static final Font F_SMALL = Font.font("Arial", 14);
    static final Font F_TITLE = Font.font("Georgia", 40);

    final Vn vn;
    final String title;

    // typewriter
    double revealed = 0;
    static final double CHARS_PER_SEC = 45;
    String shownText = "";
    boolean textComplete = true;

    // choices
    int choiceIndex = 0;
    boolean menuOpen = false;
    int menuIndex = 0;
    boolean historyOpen = false;
    int historyScroll = 0;
    String toast = "";
    double toastTimer = 0;
    int lastBlipAt = 0;

    static final String[] MENU = {"Resume", "Save", "Load", "Back to library", "Quit"};

    public VnScreen(UiManager ui, Script script, String title) {
        super(ui);
        this.vn = new Vn(script);
        this.title = title;
    }

    public VnScreen(UiManager ui, Vn existing, String title) {
        super(ui);
        this.vn = existing;
        this.title = title;
        syncText();
    }

    @Override
    public void enter() { syncText(); }

    // ------------------------------------------------ input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();

        if (menuOpen) { handleMenu(c); e.consume(); return; }
        if (historyOpen) {
            if (c == KeyCode.H || c == KeyCode.ESCAPE) historyOpen = false;
            else if (c == KeyCode.UP) historyScroll = Math.max(0, historyScroll - 1);
            else if (c == KeyCode.DOWN) historyScroll++;
            e.consume();
            return;
        }

        switch (c) {
            case ESCAPE -> { menuOpen = true; menuIndex = 0; }
            case H -> { historyOpen = true; historyScroll = Integer.MAX_VALUE; }
            case F5 -> { save(true); }
            case F9 -> { load(true); }
            default -> handleStoryKey(c);
        }
        e.consume();
    }

    void handleStoryKey(KeyCode c) {
        if (vn.mode == Vn.Mode.ENDED) {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) ui.replace(new LibraryScreen(ui));
            return;
        }
        if (vn.mode == Vn.Mode.CHOOSING) {
            List<Choice> opts = vn.availableChoices();
            if (opts.isEmpty()) return;
            if (c == KeyCode.UP) {
                choiceIndex = (choiceIndex - 1 + opts.size()) % opts.size();
                Audio.A.sfx("choice_move", "choice_select");
            } else if (c == KeyCode.DOWN) {
                choiceIndex = (choiceIndex + 1) % opts.size();
                Audio.A.sfx("choice_move", "choice_select");
            }
            else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                Audio.A.sfx("choice_select");
                vn.choose(choiceIndex);
                choiceIndex = 0;
                syncText();
            } else {
                int d = digit(c);
                if (d >= 1 && d <= opts.size()) {
                    Audio.A.sfx("choice_select");
                    vn.choose(d - 1);
                    choiceIndex = 0;
                    syncText();
                }
            }
            return;
        }
        // showing text
        if (c == KeyCode.SPACE || c == KeyCode.ENTER) {
            if (!textComplete) {            // first press completes the line
                revealed = shownText.length();
                textComplete = true;
            } else {
                vn.advance();
                syncText();
            }
        }
    }

    void handleMenu(KeyCode c) {
        switch (c) {
            case UP -> menuIndex = (menuIndex - 1 + MENU.length) % MENU.length;
            case DOWN -> menuIndex = (menuIndex + 1) % MENU.length;
            case ESCAPE -> menuOpen = false;
            case ENTER, SPACE -> {
                switch (menuIndex) {
                    case 0 -> menuOpen = false;
                    case 1 -> save(false);
                    case 2 -> load(false);
                    case 3 -> { menuOpen = false; ui.replace(new LibraryScreen(ui)); }
                    case 4 -> javafx.application.Platform.exit();
                }
            }
            default -> {
                int d = digit(c);
                if (d >= 1 && d <= MENU.length) { menuIndex = d - 1; handleMenu(KeyCode.ENTER); }
            }
        }
    }

    static int digit(KeyCode c) {
        return switch (c) {
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            case DIGIT4, NUMPAD4 -> 4;
            case DIGIT5, NUMPAD5 -> 5;
            default -> -1;
        };
    }

    void syncText() {
        String t = vn.text();
        if (!t.equals(shownText)) {
            shownText = t;
            revealed = 0;
            textComplete = false;
            lastBlipAt = 0;
        }
        if (vn.mode == Vn.Mode.CHOOSING) {
            textComplete = true;
            List<Choice> opts = vn.availableChoices();
            if (choiceIndex >= opts.size()) choiceIndex = 0;
        }
    }

    // ------------------------------------------------ frame

    @Override
    public void tick(double dt) {
        syncAudio();
        if (toastTimer > 0) toastTimer -= dt;
        if (!textComplete) {
            revealed += dt * CHARS_PER_SEC;
            if (revealed >= shownText.length()) { revealed = shownText.length(); textComplete = true; }
            // Typewriter blip, every few characters rather than each one
            int shown = (int) revealed;
            if (shown >= lastBlipAt + 3 && shown < shownText.length()) {
                lastBlipAt = shown;
                Audio.A.sfx("text_blip");
            }
        }
        draw();
    }

    /** Keep the music in step with the script, and fire any one-shots
     *  the engine recorded this frame. */
    void syncAudio() {
        Audio a = Audio.A;
        if (a == null) return;
        a.music(vn.music);
        if (!vn.pendingSfx.isEmpty()) {
            for (String cue : vn.pendingSfx) a.sfx(cue);
            vn.pendingSfx.clear();
        }
    }

    // ------------------------------------------------ save / load

    Path saveFile() {
        return Path.of("saves", "quicksave.txt");
    }

    void save(boolean quiet) {
        try {
            Files.createDirectories(saveFile().getParent());
            vn.save(saveFile());
            if (quiet) say("Saved.");
        } catch (Exception ex) {
            say("Save failed: " + ex.getMessage());
        }
    }

    void load(boolean quiet) {
        if (!Files.exists(saveFile())) { say("Nothing saved yet."); return; }
        try {
            Vn loaded = Vn.load(vn.getScript(), saveFile());
            ui.replace(new VnScreen(ui, loaded, title));
        } catch (Exception ex) {
            say("Load failed: " + ex.getMessage());
        }
    }

    void say(String msg) { toast = msg; toastTimer = 2.2; }

    // ------------------------------------------------ drawing

    void draw() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        // background (cover-fit so odd aspect ratios still fill the frame)
        javafx.scene.image.Image bg = Assets.A.background(vn.background);
        if (bg != null) {
            drawCover(bg);
        } else {
            gc.setFill(Color.web("#101018"));
            gc.fillRect(0, 0, W, H);
        }

        // sprites
        for (var entry : vn.shown.entrySet()) {
            String ch = entry.getKey();
            String pose = entry.getValue();
            javafx.scene.image.Image img = Assets.A.sprite(ch, pose);
            if (img == null) continue;
            double s = FIGURE_H / 900.0;
            double dw = img.getWidth() * s, dh = img.getHeight() * s;
            double cx = zoneX(vn.position(ch));
            double dx = cx - dw / 2;
            double dy = FLOOR_Y - dh;
            // dim characters who aren't speaking
            boolean speaking = ch.equalsIgnoreCase(vn.speaker());
            gc.setGlobalAlpha(speaking || vn.speaker() == null ? 1.0 : 0.72);
            gc.drawImage(img, dx, dy, dw, dh);
            gc.setGlobalAlpha(1.0);
        }

        if (vn.mode == Vn.Mode.CHOOSING) drawChoices();
        else drawText();

        drawHints();
        if (toastTimer > 0) drawToast();
        if (historyOpen) drawHistory();
        if (menuOpen) drawMenu();
        if (vn.mode == Vn.Mode.ENDED && !menuOpen && !historyOpen) drawEnd();
    }

    static double zoneX(String pos) {
        if (pos == null) return ZONE_CENTER;
        return switch (pos.toLowerCase()) {
            case "left" -> ZONE_LEFT;
            case "right" -> ZONE_RIGHT;
            default -> ZONE_CENTER;
        };
    }

    void drawText() {
        gc.setFill(Color.rgb(8, 8, 14, 0.86));
        gc.fillRoundRect(BOX_X, BOX_Y, BOX_W, BOX_H, 14, 14);
        gc.setStroke(Color.rgb(150, 150, 170, 0.35));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(BOX_X, BOX_Y, BOX_W, BOX_H, 14, 14);

        String speaker = vn.speaker();
        if (speaker != null) {
            gc.setFont(F_SPEAKER);
            gc.setFill(Color.web("#F2C14E"));
            gc.fillText(cap(speaker), BOX_X + 40, BOX_Y + 42);
        }

        gc.setFont(F_TEXT);
        gc.setFill(Color.web("#EDEDF2"));
        String visible = shownText.substring(0, Math.min(shownText.length(), (int) revealed));
        double y = TEXT_Y;
        for (String line : wrap(visible, F_TEXT, BOX_W - 80)) {
            if (y > BOX_Y + BOX_H - 18) break;
            gc.fillText(line, TEXT_X, y);
            y += LINE_H;
        }
    }

    void drawChoices() {
        List<Choice> opts = vn.availableChoices();
        gc.setFill(Color.rgb(8, 8, 14, 0.82));
        double boxH = 34 + opts.size() * 46;
        gc.fillRoundRect(BOX_X, BOX_Y - boxH - 14, BOX_W, boxH, 14, 14);
        gc.setStroke(Color.rgb(150, 150, 170, 0.35));
        gc.strokeRoundRect(BOX_X, BOX_Y - boxH - 14, BOX_W, boxH, 14, 14);

        double oy = BOX_Y - boxH + 12;
        gc.setFont(F_CHOICE);
        for (int i = 0; i < opts.size(); i++) {
            boolean sel = i == choiceIndex;
            gc.setFill(sel ? Color.web("#F2C14E") : Color.web("#B9B9C6"));
            gc.fillText((sel ? "▶  " : "   ") + (i + 1) + ".  " + opts.get(i).text, BOX_X + 44, oy);
            oy += 46;
        }
    }

    void drawHints() {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(170, 170, 190, 0.55));
        String hint = vn.mode == Vn.Mode.CHOOSING
                ? "↑↓ choose   ENTER confirm   H history   ESC menu"
                : "SPACE advance   H history   F5 save   F9 load   ESC menu";
        gc.fillText(hint, BOX_X + 4, H - 14);
        gc.setFill(Color.rgb(170, 170, 190, 0.4));
        gc.fillText(title, W - 240, H - 14);
    }

    void drawToast() {
        gc.setFill(Color.rgb(20, 20, 28, 0.92));
        gc.fillRoundRect(W / 2 - 130, 40, 260, 44, 10, 10);
        gc.setFill(Color.web("#EDEDF2"));
        gc.setFont(F_TEXT);
        gc.fillText(toast, W / 2 - 110, 68);
    }

    void drawHistory() {
        gc.setFill(Color.rgb(4, 4, 8, 0.94));
        gc.fillRect(0, 0, W, H);
        gc.setFill(Color.web("#F2C14E"));
        gc.setFont(F_TITLE);
        gc.fillText("History", 60, 66);

        int total = vn.history.size();
        int linesPerScreen = 16;
        int maxStart = Math.max(0, total - linesPerScreen);
        int start = Math.min(historyScroll, maxStart);
        historyScroll = start;

        double y = 130;
        gc.setFont(F_TEXT);
        for (int i = start; i < Math.min(total, start + linesPerScreen); i++) {
            String[] h = vn.history.get(i);
            if (h[0] != null) {
                gc.setFill(Color.web("#F2C14E"));
                gc.fillText(cap(h[0]) + ":", 70, y);
            } else {
                gc.setFill(Color.web("#9A9AAE"));
            }
            gc.setFill(h[0] != null ? Color.web("#DADAE4") : Color.web("#9A9AAE"));
            for (String line : wrap(h[1], F_TEXT, W - 260)) {
                gc.fillText(line, 230, y);
                y += 30;
            }
            y += 6;
        }
        gc.setFill(Color.web("#8A8A9E"));
        gc.setFont(F_SMALL);
        gc.fillText("↑↓ scroll    H or ESC close", 70, H - 30);
    }

    void drawMenu() {
        gc.setFill(Color.rgb(4, 4, 8, 0.78));
        gc.fillRect(0, 0, W, H);
        gc.setFill(Color.web("#F2C14E"));
        gc.setFont(F_TITLE);
        gc.fillText("Paused", W / 2 - 90, 200);
        gc.setFont(F_CHOICE);
        for (int i = 0; i < MENU.length; i++) {
            boolean sel = i == menuIndex;
            gc.setFill(sel ? Color.web("#F2C14E") : Color.web("#B9B9C6"));
            gc.fillText((sel ? "▶  " : "   ") + MENU[i], W / 2 - 110, 290 + i * 48);
        }
    }

    void drawEnd() {
        gc.setFill(Color.rgb(4, 4, 8, 0.85));
        gc.fillRect(0, 0, W, H);
        gc.setFill(Color.web("#F2C14E"));
        gc.setFont(F_TITLE);
        gc.fillText("End", W / 2 - 40, H / 2 - 30);
        gc.setFill(Color.web("#C9C9D6"));
        gc.setFont(F_TEXT);
        gc.fillText("ENTER — back to title", W / 2 - 110, H / 2 + 30);
    }

    // ------------------------------------------------ text helpers

    /** Word wrap using real font metrics (Canvas has no measureText). */
    static java.util.List<String> wrap(String text, Font font, double maxWidth) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Text probe = new Text();
        probe.setFont(font);
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { out.add(""); continue; }
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String w : words) {
                String candidate = line.isEmpty() ? w : line + " " + w;
                probe.setText(candidate);
                if (probe.getLayoutBounds().getWidth() > maxWidth && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(w);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (!line.isEmpty()) out.add(line.toString());
        }
        return out;
    }

    static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
