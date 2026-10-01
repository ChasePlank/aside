package aside.games.tell;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * tell, drawn.
 *
 * The house is on the left and the reading is on the right, and the split is
 * the design. The left is the thing you are doing; the right is the thing that
 * is being done to you, and it is the only place the number lives.
 *
 * THE NUMBERS ON THE FLOOR ARE THE GAME. Every room shows how many times you
 * have left it each way, and the doors it expects are marked in red on the
 * room's own edge. That is not a hint system bolted onto a hidden model -- it
 * *is* the model, drawn where you are standing. A game about being read has no
 * business keeping the reading secret; the difficulty is not in guessing what
 * it thinks, it is in the fact that you can see exactly what it thinks and
 * still have to get to the door.
 */
public class TellScreen extends UiScreen {

    static final Color BG = Color.web("#08070A");
    static final Color ROOM = Color.rgb(24, 22, 30, 0.9);
    static final Color ROOM_LIT = Color.rgb(58, 48, 34, 0.95);
    static final Color EDGE = Color.web("#2A2733");
    static final Color INK = Color.web("#E8E8F0");
    static final Color DIM = Color.web("#9A9AAE");
    static final Color FAINT = Color.web("#63636F");
    static final Color GOLD = Color.web("#F2C14E");
    static final Color RED = Color.web("#E94560");
    static final Color GOOD = Color.web("#6FCF97");
    static final Color DOOR = Color.web("#7FB2E5");
    static final Color PAPER = Color.rgb(20, 19, 26, 0.85);

    static final Font F_TITLE = Font.font("Georgia", 44);
    static final Font F_SCENE = Font.font("Georgia", 17);
    static final Font F_BIG = Font.font("Georgia", 26);
    static final Font F_SMALL = Font.font("Arial", 14);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_COUNT = Font.font("Arial", 11);

    static final double M = 70;
    static final double HOUSE_X = 70, HOUSE_Y = 128, HOUSE_W = 660, HOUSE_H = 470;
    static final double PANEL_X = 790, PANEL_W = 420;

    enum Phase { OPEN, PLAY, REPORT }

    final Tell tell;
    final Path save;
    Phase phase = Phase.OPEN;
    String notice = "";
    double noticeTimer = 0;

    public TellScreen(UiManager ui, Tell tell, Path save) {
        super(ui);
        this.tell = tell;
        this.save = save;
        if (tell.done()) phase = Phase.REPORT;
        else if (tell.turn > 0) phase = Phase.PLAY;
    }

    @Override
    public void enter() {
        Audio a = Audio.A;
        if (a != null) a.stopAll();
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (phase == Phase.OPEN) {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.PLAY; sfx(); }
        } else if (phase == Phase.PLAY) {
            play(c);
        } else {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                if (tell.won && tell.night + 1 < Tell.NIGHTS) {
                    tell.nextNight();
                    persist();
                    phase = Phase.PLAY;
                } else {
                    ui.replace(new TellScreen(ui, Tell.of(), save));
                }
                sfx();
            }
        }
        e.consume();
    }

    void play(KeyCode c) {
        int dir = -1;
        if (c == KeyCode.UP || c == KeyCode.W) dir = 0;
        else if (c == KeyCode.RIGHT || c == KeyCode.D) dir = 1;
        else if (c == KeyCode.DOWN || c == KeyCode.S) dir = 2;
        else if (c == KeyCode.LEFT || c == KeyCode.A) dir = 3;
        if (dir < 0) return;
        if (!tell.canMove(dir)) { warn(Tell.NO_SUCH_DOOR); return; }
        tell.move(dir);
        sfx();
        persist();
        if (tell.done()) phase = Phase.REPORT;
    }

    void warn(String text) {
        notice = text;
        noticeTimer = 2.0;
    }

    void persist() {
        try { tell.save(save); } catch (Exception ignored) { }
    }

    static void sfx() {
        if (Audio.A != null) Audio.A.sfx("choice_select", "choice_select");
    }

    @Override
    public void tick(double dt) {
        if (noticeTimer > 0) noticeTimer -= dt;
        draw();
    }

    // ---------------------------------------------------------------- draw

    void draw() {
        gc.setFill(BG);
        gc.fillRect(0, 0, W, H);

        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText(Tell.WORDMARK, M, 72);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = switch (phase) {
            case OPEN -> Tell.WHERE;
            case PLAY -> "night " + Tell.nightName(tell.night) + " of five";
            case REPORT -> Tell.WHERE_REPORT;
        };
        gc.fillText(right, W - M - 160, 72);

        switch (phase) {
            case OPEN -> drawOpen();
            case PLAY -> drawPlay();
            case REPORT -> drawReport();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(RED);
            gc.fillText(notice, M, H - 64);
        }
    }

    void drawOpen() {
        double y = 140;
        for (String p : Tell.OPENING) {
            for (String line : wrap(p, F_SCENE, 620)) {
                gc.setFont(F_SCENE);
                gc.setFill(INK);
                gc.fillText(line, M, y);
                y += 26;
            }
            y += 12;
        }

        panel(PANEL_X, 122, PANEL_W, 320);
        double x = PANEL_X + 28, ry = 154;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Tell.RULES_HEADING, x, ry);
        ry += 30;
        for (String[] rule : Tell.RULES) {
            gc.setFont(F_SMALL);
            gc.setFill(GOLD);
            gc.fillText(rule[0], x, ry);
            ry += 20;
            for (String line : wrap(rule[1], F_TINY, PANEL_W - 60)) {
                gc.setFont(F_TINY);
                gc.setFill(DIM);
                gc.fillText(line, x, ry);
                ry += 17;
            }
            ry += 14;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Tell.START_LINE, M, H - 74);
        hint("M mute    [ quieter    ] louder");
    }

    void drawPlay() {
        drawHouse();
        drawPanel();
        hint(Tell.HINT);
    }

    void drawHouse() {
        int n = tell.size;
        double cell = Math.min(HOUSE_W / n, HOUSE_H / n);
        double gw = cell * n;
        double ox = HOUSE_X + (HOUSE_W - gw) / 2;
        double oy = HOUSE_Y + (HOUSE_H - gw) / 2;

        for (int gy = 0; gy < n; gy++) {
            for (int gx = 0; gx < n; gx++) {
                double x = ox + gx * cell, y = oy + gy * cell;
                int i = tell.idx(gx, gy);
                boolean lit = tell.lamp[i];
                boolean isDoor = gx == tell.ex && gy == tell.ey;
                boolean isPlayer = gx == tell.px && gy == tell.py;

                gc.setFill(lit ? ROOM_LIT : ROOM);
                gc.fillRoundRect(x + 2, y + 2, cell - 4, cell - 4, 8, 8);
                gc.setStroke(EDGE);
                gc.setLineWidth(1);
                gc.strokeRoundRect(x + 2, y + 2, cell - 4, cell - 4, 8, 8);

                if (isDoor) {
                    gc.setStroke(DOOR);
                    gc.setLineWidth(2);
                    gc.strokeRoundRect(x + 2, y + 2, cell - 4, cell - 4, 8, 8);
                }

                // The counts, at the edge each one belongs to. This is its
                // model of you, drawn where you are standing -- and the doors
                // it expects are the red numbers and the red lines.
                List<Integer> exp = isPlayer ? tell.expected() : List.of();
                gc.setFont(F_COUNT);
                for (int d = 0; d < Tell.DIRS; d++) {
                    int c = tell.hist[i][d];
                    boolean wanted = exp.contains(d);
                    if (c == 0 && !wanted) continue;
                    String s = c == 0 ? "\u00b7" : String.valueOf(c);
                    double tx, ty;
                    switch (d) {
                        case 0 -> { tx = x + cell / 2 - 3; ty = y + 15; }
                        case 1 -> { tx = x + cell - 14; ty = y + cell / 2 + 4; }
                        case 2 -> { tx = x + cell / 2 - 3; ty = y + cell - 8; }
                        default -> { tx = x + 8; ty = y + cell / 2 + 4; }
                    }
                    gc.setFill(wanted ? RED : FAINT);
                    gc.fillText(s, tx, ty);
                }

                if (isPlayer) {
                    gc.setStroke(RED);
                    gc.setLineWidth(3);
                    for (int d : tell.expected()) {
                        switch (d) {
                            case 0 -> gc.strokeLine(x + 10, y + 4, x + cell - 10, y + 4);
                            case 1 -> gc.strokeLine(x + cell - 4, y + 10, x + cell - 4, y + cell - 10);
                            case 2 -> gc.strokeLine(x + 10, y + cell - 4, x + cell - 10, y + cell - 4);
                            default -> gc.strokeLine(x + 4, y + 10, x + 4, y + cell - 10);
                        }
                    }
                }

                if (isPlayer) {
                    gc.setFill(GOLD);
                    gc.fillOval(x + cell / 2 - 7, y + cell / 2 - 7, 14, 14);
                } else if (isDoor) {
                    gc.setFill(DOOR);
                    gc.fillRect(x + cell / 2 - 8, y + cell / 2 - 10, 16, 20);
                }
            }
        }
    }

    void drawPanel() {
        panel(PANEL_X, 122, PANEL_W, 470);
        double x = PANEL_X + 28;
        double y = 158;

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Tell.READ_HEAD, x, y);
        y += 14;

        double barW = PANEL_W - 56;
        gc.setFill(Color.rgb(50, 46, 60, 0.9));
        gc.fillRoundRect(x, y, barW, 16, 8, 8);
        int pct = tell.readPct();
        gc.setFill(pct >= 75 ? RED : (pct >= 45 ? GOLD : GOOD));
        gc.fillRoundRect(x, y, Math.max(4, barW * pct / 100.0), 16, 8, 8);
        y += 34;
        gc.setFont(F_TINY);
        gc.setFill(DIM);
        gc.fillText(pct + " of 100", x, y);
        y += 30;

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Tell.EXPECT_HEAD, x, y);
        y += 22;
        gc.setFont(F_SMALL);
        gc.setFill(RED);
        for (String line : wrap(Tell.cap(Tell.join(tell.expected())), F_SMALL, barW)) {
            gc.fillText(line, x, y);
            y += 20;
        }
        y += 16;

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Tell.DOOR_HEAD, x, y);
        y += 22;
        gc.setFont(F_SMALL);
        gc.setFill(DOOR);
        gc.fillText(tell.dist(tell.px, tell.py) + " rooms away", x, y);
        y += 22;
        gc.setFont(F_TINY);
        gc.setFill(DIM);
        gc.fillText("turn " + tell.turn + " of " + tell.limit(), x, y);
        y += 30;

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String covers = tell.reach() == 1 ? "the one door you use most here"
                : "the " + Tell.word(tell.reach()) + " doors you use most here";
        for (String line : wrap("It covers " + covers + ". Going anywhere else costs "
                + "it " + tell.missGain() + "; going where it expects costs you "
                + tell.hitCost() + ".", F_TINY, barW)) {
            gc.fillText(line, x, y);
            y += 17;
        }
    }

    void drawReport() {
        boolean won = tell.won;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(won ? Tell.REPORT_HEAD : Tell.CAUGHT_HEAD, M, 128);

        gc.setFont(F_BIG);
        gc.setFill(won ? GOOD : RED);
        double y = 176;
        String head = won
                ? "You reached the far door on night " + Tell.nightName(tell.night) + "."
                : Tell.caughtLine(tell.read);
        for (String line : wrap(head, F_BIG, 620)) {
            gc.fillText(line, M, y);
            y += 32;
        }

        y += 10;
        gc.setFont(F_SMALL);
        gc.setFill(DIM);
        gc.fillText(Tell.readLine(tell.read) + "  (" + tell.readPct() + " of 100)", M, y);
        y += 24;
        gc.fillText("It took you " + tell.turn + " turns to be read that far.", M, y);
        y += 34;

        for (String line : wrap(Tell.closing(won, tell.read, tell.turn), F_SCENE, 620)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 24;
        }

        drawHouse();

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        String next = (won && tell.night + 1 < Tell.NIGHTS) ? Tell.NEXT_NIGHT : Tell.AGAIN;
        gc.fillText(next, M, H - 74);
        hint(Tell.HINT_DONE);
    }

    // ------------------------------------------------------------ helpers

    void panel(double x, double y, double w, double h) {
        gc.setFill(PAPER);
        gc.fillRoundRect(x, y, w, h, 10, 10);
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, w, h, 10, 10);
    }

    void hint(String text) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 165, 0.6));
        gc.fillText(text, M, H - 36);
    }

    static List<String> wrap(String text, Font font, double maxWidth) {
        List<String> out = new ArrayList<>();
        Text probe = new Text();
        probe.setFont(font);
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { out.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String w : para.split(" ")) {
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
}
