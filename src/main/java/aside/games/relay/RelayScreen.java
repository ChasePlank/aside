package aside.games.relay;

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
 * relay, drawn.
 *
 * One message at a time, three ways of carrying it, and no way back: you commit
 * and the message is gone. That is not a UI limitation, it is the game. A relay
 * that could recall a message would not be a relay, and the whole point of the
 * floor is that you have to judge it before you send, not after.
 *
 * The report is the other half. It shows, for each of the six, what the
 * rendering you chose kept and what it left behind -- because a score with no
 * account of itself is a number, and the thing worth learning here is which
 * half of a message you habitually drop.
 */
public class RelayScreen extends UiScreen {

    static final Color BG = Color.web("#0B0C10");
    static final Color GOLD = Color.web("#E8B44A");
    static final Color INK = Color.web("#E9E9F2");
    static final Color DIM = Color.web("#9C9CB0");
    static final Color FAINT = Color.web("#6B6B82");
    static final Color PAPER = Color.rgb(26, 27, 38, 0.9);
    static final Color EDGE = Color.web("#2C2D3E");
    static final Color GOOD = Color.web("#6FCF97");
    static final Color BAD = Color.web("#E05A6B");

    static final Font F_TITLE = Font.font("Georgia", 44);
    static final Font F_SUB = Font.font("Georgia", 20);
    static final Font F_WHO = Font.font("Georgia", 28);
    static final Font F_SCENE = Font.font("Georgia", 17);
    static final Font F_OPT = Font.font("Georgia", 19);
    static final Font F_BIG = Font.font("Georgia", 30);
    static final Font F_SMALL = Font.font("Arial", 14);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_ROW = Font.font("Arial", 13);

    static final double M = 70;
    static final double CARD_X = 70;
    static final double CARD_W = 1140;
    static final double CARD_TOP = 160;
    static final double CARD_H = 148;
    static final double CARD_GAP = 16;

    enum Phase { OPEN, READ, REPORT }

    final Relay relay;
    final Path save;
    Phase phase = Phase.OPEN;
    int at = 0;          // the message being carried
    int sel = 0;         // the rendering under the cursor
    String notice = "";
    double noticeTimer = 0;

    public RelayScreen(UiManager ui, Relay relay, Path save) {
        super(ui);
        this.relay = relay;
        this.save = save;
        if (relay.reported) phase = Phase.REPORT;
        else if (relay.choice[0] >= 0) { phase = Phase.READ; at = Math.max(0, relay.next()); }
    }

    @Override
    public void enter() {
        Audio a = Audio.A;
        if (a != null) a.stopAll();
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        switch (phase) {
            case OPEN -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.READ; sfx("choice_select"); }
            }
            case READ -> read(c);
            case REPORT -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) ui.pop();
                else if (c == KeyCode.R) again();
            }
        }
        e.consume();
    }

    void read(KeyCode c) {
        if (c == KeyCode.UP || c == KeyCode.W) { sel = (sel + Relay.OPTIONS - 1) % Relay.OPTIONS; sfx("text_blip"); }
        else if (c == KeyCode.DOWN || c == KeyCode.S) { sel = (sel + 1) % Relay.OPTIONS; sfx("text_blip"); }
        else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
            relay.choice[at] = sel;
            sfx("choice_select");
            int n = relay.next();
            if (n < 0) {
                relay.reported = true;
                persist();
                phase = Phase.REPORT;
            } else {
                at = n;
                sel = 0;
                persist();
            }
        }
    }

    void again() {
        Relay fresh = new Relay();
        try { fresh.save(save); } catch (Exception ignored) { }
        ui.replace(new RelayScreen(ui, fresh, save));
    }

    void persist() {
        try { relay.save(save); } catch (Exception ignored) { }
    }

    static void sfx(String name) {
        if (Audio.A != null) Audio.A.sfx(name, "choice_select");
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

        switch (phase) {
            case OPEN -> drawOpen();
            case READ -> drawRead();
            case REPORT -> drawReport();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(GOLD);
            gc.fillText(notice, M, H - 58);
        }
    }

    void drawOpen() {
        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText(Relay.OPEN_TITLE, M, 118);

        gc.setFont(F_SUB);
        gc.setFill(INK);
        gc.fillText(Relay.OPEN_SUB, M, 156);

        double y = 214;
        for (String para : Relay.OPEN_SITUATION) {
            for (String line : wrap(para, F_SCENE, 520)) {
                gc.setFont(F_SCENE);
                gc.setFill(DIM);
                gc.fillText(line, M, y);
                y += 24;
            }
            y += 16;
        }

        double rx = 700, ry = 214;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Relay.RULES_HEADING, rx, ry);
        ry += 30;
        for (String[] rule : Relay.RULES) {
            gc.setFont(F_SMALL);
            gc.setFill(GOLD);
            gc.fillText(rule[0], rx, ry);
            ry += 20;
            for (String line : wrap(rule[1], F_TINY, 500)) {
                gc.setFont(F_TINY);
                gc.setFill(DIM);
                gc.fillText(line, rx, ry);
                ry += 17;
            }
            ry += 20;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Relay.START_LINE, M, H - 74);
        hint("M mute    [ quieter    ] louder");
    }

    void drawRead() {
        Relay.Message msg = Relay.NIGHT.get(at);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(String.format(Relay.PROGRESS, at + 1, Relay.MESSAGES), M, 58);

        gc.setFont(F_WHO);
        gc.setFill(GOLD);
        gc.fillText(String.format(Relay.FROM_TO, msg.from(), msg.to()), M, 98);

        gc.setFont(F_SCENE);
        gc.setFill(DIM);
        gc.fillText(msg.situation(), M, 128);

        for (int o = 0; o < Relay.OPTIONS; o++) {
            double top = CARD_TOP + o * (CARD_H + CARD_GAP);
            boolean on = o == sel;

            gc.setFill(on ? Color.rgb(38, 40, 56, 0.95) : PAPER);
            gc.fillRoundRect(CARD_X, top, CARD_W, CARD_H, 10, 10);
            gc.setStroke(on ? GOLD : EDGE);
            gc.setLineWidth(on ? 2 : 1);
            gc.strokeRoundRect(CARD_X, top, CARD_W, CARD_H, 10, 10);

            gc.setFont(F_TINY);
            gc.setFill(on ? GOLD : FAINT);
            gc.fillText(on ? ">" : " ", CARD_X + 18, top + 30);

            double ty = top + 42;
            for (String line : wrap(msg.options().get(o).text(), F_OPT, CARD_W - 70)) {
                gc.setFont(F_OPT);
                gc.setFill(on ? INK : DIM);
                gc.fillText(line, CARD_X + 40, ty);
                ty += 27;
            }
        }

        hint(Relay.HINT_READ);
    }

    void drawReport() {
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Relay.REPORT_HEAD, M, 58);

        gc.setFont(F_BIG);
        gc.setFill(relay.score() == Relay.MAX ? GOOD : GOLD);
        gc.fillText(Relay.worthLine(relay.score()), M, 98);

        gc.setFont(F_SMALL);
        gc.setFill(relay.breaks() == 0 ? GOOD : BAD);
        gc.fillText(Relay.breaksLine(relay.breaks()), M, 126);

        double y = 168;
        for (int m = 0; m < Relay.MESSAGES; m++) {
            int o = relay.choice[m];
            int w = relay.worth(m, o);
            Relay.Message msg = Relay.NIGHT.get(m);

            gc.setFont(F_ROW);
            gc.setFill(FAINT);
            gc.fillText(String.valueOf(m + 1), M, y);
            gc.setFill(INK);
            gc.fillText(msg.from() + " \u2192 " + msg.to(), M + 26, y);
            gc.setFill(w == 2 ? GOOD : w == 1 ? GOLD : BAD);
            gc.fillText(Relay.worthWord(w), M + 210, y);

            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(Relay.KEPT_HEAD, M + 300, y);
            gc.setFill(DIM);
            gc.fillText(clip(join(relay.kept(m, o)), F_TINY, 800), M + 340, y);

            List<String> lost = relay.lost(m, o);
            gc.setFill(FAINT);
            gc.fillText(Relay.LOST_HEAD, M + 300, y + 20);
            // The lost line is the price of the choice, not a mistake, so it is
            // drawn dim even when the message arrived whole. Colouring it red on
            // a perfect night reads as a complaint about a run that has none.
            gc.setFill(lost.isEmpty() ? FAINT : DIM);
            gc.fillText(clip(lost.isEmpty() ? Relay.NOTHING_LOST : join(lost), F_TINY, 800),
                    M + 340, y + 20);

            y += 64;
        }

        y += 12;
        for (String line : wrap(Relay.closing(relay.score()), F_SCENE, 760)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 24;
        }

        hint(Relay.HINT_REPORT);
    }

    // ------------------------------------------------------------ helpers

    static String join(List<String> parts) {
        return String.join("  \u00b7  ", parts);
    }

    void hint(String text) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 165, 0.6));
        gc.fillText(text, M, H - 36);
    }

    /** Trim to fit rather than wrap: a report row is one line. */
    static String clip(String text, Font font, double maxWidth) {
        Text probe = new Text();
        probe.setFont(font);
        probe.setText(text);
        if (probe.getLayoutBounds().getWidth() <= maxWidth) return text;
        String s = text;
        while (s.length() > 4 && probe.getLayoutBounds().getWidth() > maxWidth) {
            s = s.substring(0, s.length() - 2);
            probe.setText(s + "...");
        }
        return s + "...";
    }

    /** Word wrap using real font metrics -- a Canvas has no measureText. */
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
