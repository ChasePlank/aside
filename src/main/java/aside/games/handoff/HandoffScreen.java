package aside.games.handoff;

import aside.ui.Audio;
import aside.ui.LibraryScreen;
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
 * Handoff, drawn.
 *
 * The station is the game and this is only a window onto it -- the same
 * station that runs headless in the standalone repo, with the same save
 * file. If this screen ever needed to hold state the station didn't have,
 * the station would be wrong.
 *
 * Five beats:
 *
 *   WATCH       a night, what you can see, Bell's orders, and three ways
 *   OUTCOME     what your way cost, and the line it taught you
 *   WRITING     three slots and nine lines
 *   SUCCESSION  his first night, played against what you wrote
 *   DONE        the orders you left, and what they did
 *
 * The orders are drawn as a panel rather than as prose because the whole
 * game is about a written thing sitting next to a situation that has
 * moved. The panel does not change across the five watches. Only the
 * night does.
 */
public class HandoffScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_HEAD = Font.font("Georgia", 23);
    static final Font F_SCENE = Font.font("Georgia", 19);
    static final Font F_BODY = Font.font("Georgia", 18);
    static final Font F_ITEM = Font.font("Georgia", 19);
    static final Font F_POOL = Font.font("Georgia", 16);
    static final Font F_LABEL = Font.font("Arial", 12);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);

    static final Color BG = Color.web("#07070B");
    static final Color ACCENT = Color.web("#F2C14E");
    static final Color BODY = Color.web("#E8E8EF");
    static final Color DIM = Color.web("#B9B9C6");
    static final Color FAINT = Color.rgb(150, 150, 170, 0.62);
    static final Color LINE = Color.rgb(242, 193, 78, 0.30);
    static final Color BAD = Color.web("#C86B5A");

    static final double M = 84;

    final Handoff game;
    final Path save;

    int index = 0;
    /** On the succession beat: has the night been played out yet? */
    boolean revealed = false;

    public HandoffScreen(UiManager ui, Handoff game, Path save) {
        super(ui);
        this.game = game;
        this.save = save;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (game.phase) {
            case WATCH -> {
                int n = game.watch().options.size();
                if (c == KeyCode.UP) { index = (index - 1 + n) % n; sfx("choice_move"); }
                else if (c == KeyCode.DOWN) { index = (index + 1) % n; sfx("choice_move"); }
                else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    game.choose(index); index = 0; persist(); sfx("choice_select");
                } else {
                    int d = digit(c);
                    if (d >= 1 && d <= n) { game.choose(d - 1); index = 0; persist(); sfx("choice_select"); }
                }
            }
            case OUTCOME -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) { game.advance(); index = 0; persist(); }
            }
            case WRITING -> {
                List<Handoff.Order> pool = game.pool();
                if (c == KeyCode.UP) { index = (index - 1 + pool.size()) % pool.size(); sfx("choice_move"); }
                else if (c == KeyCode.DOWN) { index = (index + 1) % pool.size(); sfx("choice_move"); }
                else if (c == KeyCode.BACK_SPACE) { game.unwrite(); persist(); sfx("choice_move"); }
                else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    if (game.written.size() >= Handoff.ORDERS_HELD) {
                        if (game.seal()) { index = 0; persist(); sfx("choice_select"); }
                    } else if (game.write(pool.get(index))) {
                        persist(); sfx("choice_select");
                    }
                }
            }
            case SUCCESSION -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    if (!revealed) { revealed = true; sfx("choice_select"); }
                    else { game.finish(); persist(); }
                }
            }
            case DONE -> {
                if (c == KeyCode.R) { game.reset(); revealed = false; index = 0; persist(); }
                else if (c == KeyCode.ENTER || c == KeyCode.SPACE) ui.replace(new LibraryScreen(ui));
            }
        }
        e.consume();
    }

    void persist() {
        try { game.save(save); } catch (Exception ignored) { }
    }

    static void sfx(String name) {
        if (Audio.A != null) Audio.A.sfx(name, "choice_select");
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

    @Override public void tick(double dt) { draw(); }

    // ---------------------------------------------------------------- draw

    void draw() {
        gc.setFill(BG);
        gc.fillRect(0, 0, W, H);
        drawGlow();

        gc.setFill(ACCENT);
        gc.setFont(F_TITLE);
        gc.fillText("handoff", M, 76);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText("a light on a rock", M + 132, 76);

        switch (game.phase) {
            case WATCH      -> drawWatch();
            case OUTCOME    -> drawOutcome();
            case WRITING    -> drawWriting();
            case SUCCESSION -> drawSuccession();
            case DONE       -> drawDone();
        }
    }

    /**
     * A cold light off to the right, the way the lamp sits off the corner
     * of your eye in the room. Cheapest way to make the station feel like
     * it has a temperature rather than a contents list.
     */
    void drawGlow() {
        double cx = W * 1.02, cy = H * 0.06;
        for (int i = 10; i >= 1; i--) {
            double r = 78 * i;
            gc.setFill(Color.rgb(242, 193, 78, 0.006));
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    // ------------------------------------------------------------- WATCH

    void drawWatch() {
        Handoff.Watch w = game.watch();

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText((game.watchIndex + 1) + " of " + Handoff.WATCHES.size(), W - M - 60, 76);

        double y = 122;
        gc.setFont(F_HEAD);
        gc.setFill(BODY);
        gc.fillText(w.heading, M, y);
        gc.setFill(FAINT);
        gc.setFont(F_BODY);
        gc.fillText("-- " + w.title, M + 150, y);
        y += 34;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(214, 214, 226, 0.92));
        for (String line : wrap(w.scene, F_SCENE, 660)) {
            gc.fillText(line, M, y);
            y += 27;
        }

        y += 26;
        label("WHAT YOU CAN SEE", M, y);
        y += 26;
        gc.setFont(F_BODY);
        for (String s : w.signals) {
            gc.setFill(DIM);
            gc.fillText("\u00B7  " + Handoff.signalText(s), M + 10, y);
            y += 25;
        }

        drawOrdersPanel(766, 122, 430);

        // The three ways, along the bottom.
        double by = 566;
        label("WHAT YOU DO", M, by);
        by += 30;
        Handoff.Order applying = game.applying();
        for (int i = 0; i < w.options.size(); i++) {
            Handoff.Option o = w.options.get(i);
            boolean sel = i == index;
            gc.setFont(F_ITEM);
            gc.setFill(sel ? ACCENT : DIM);
            String tag = switch (o.kind) {
                case FOLLOW -> "as written";
                case JUDGE -> "as it needs";
                case HOLD -> "nothing";
            };
            String shown = (sel ? "\u25B6  " : "   ") + (i + 1) + ".  " + o.text;
            gc.fillText(shown, M + 8, by);
            double tagX = M + 8 + textWidth(shown, F_ITEM) + 20;
            gc.setFont(F_TINY);
            gc.setFill(Color.rgb(150, 150, 170, 0.5));
            gc.fillText(tag, tagX, by - 2);
            by += 30;
        }

        hint("\u2191\u2193 choose     ENTER do it     ESC leave the light");
    }

    /** Bell's four, fixed, with the one that applies to tonight marked. */
    void drawOrdersPanel(double x, double y, double w) {
        Handoff.Order applying = game.applying();
        double h = 300;
        panel(x, y, w, h);
        label("THE ORDERS", x + 22, y + 34);
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText("B. kept this light before you", x + 22, y + 54);

        double ly = y + 92;
        for (Handoff.Order o : Handoff.BELL) {
            boolean on = applying != null && applying.id.equals(o.id);
            if (on) {
                gc.setFill(Color.rgb(242, 193, 78, 0.07));
                gc.fillRect(x + 12, ly - 20, w - 24, 62);
                gc.setFill(ACCENT);
                gc.fillRect(x + 12, ly - 20, 3, 62);
            }
            gc.setFont(F_POOL);
            gc.setFill(on ? ACCENT : Color.rgb(150, 150, 170, 0.75));
            double ty = ly;
            for (String line : wrap(o.text, F_POOL, w - 60)) {
                gc.fillText(line, x + 26, ty);
                ty += 21;
            }
            if (on) {
                gc.setFont(F_TINY);
                gc.setFill(Color.rgb(242, 193, 78, 0.8));
                gc.fillText("this one applies tonight", x + 26, ty + 2);
                ty += 18;
            }
            ly = ty + 22;
        }
    }

    // ----------------------------------------------------------- OUTCOME

    void drawOutcome() {
        Handoff.Watch w = game.watch();
        Handoff.Option opt = w.options.get(Math.max(0, game.lastOption));

        double y = 132;
        gc.setFont(F_HEAD);
        gc.setFill(BODY);
        gc.fillText(w.heading + " -- " + w.title, M, y);
        y += 44;

        gc.setFont(F_BODY);
        gc.setFill(FAINT);
        gc.fillText("You " + lowerFirst(opt.text), M, y);
        y += 42;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(214, 214, 226, 0.92));
        for (String line : wrap(opt.outcome, F_SCENE, 760)) {
            gc.fillText(line, M, y);
            y += 28;
        }

        y += 34;
        if (opt.unlock != null) {
            panel(M, y, 760, 96);
            label("YOU LEARNED A LINE", M + 22, y + 32);
            gc.setFont(F_POOL);
            gc.setFill(ACCENT);
            double ty = y + 58;
            for (String line : wrap(opt.unlock.text, F_POOL, 700)) {
                gc.fillText(line, M + 22, ty);
                ty += 22;
            }
        } else if (opt.kind == Handoff.Kind.FOLLOW) {
            gc.setFont(F_BODY);
            gc.setFill(FAINT);
            gc.fillText("You learned nothing you did not already have.", M, y + 20);
            gc.fillText("Bell's line was already in the book.", M, y + 46);
        } else {
            gc.setFont(F_BODY);
            gc.setFill(FAINT);
            gc.fillText("You learned nothing. Nothing was written down, so nothing carries.", M, y + 20);
        }

        hint("ENTER -- on to the next watch");
    }

    // ----------------------------------------------------------- WRITING

    void drawWriting() {
        double y = 122;
        gc.setFont(F_HEAD);
        gc.setFill(BODY);
        gc.fillText("What you leave him", M, y);
        y += 32;
        gc.setFont(F_BODY);
        gc.setFill(Color.rgb(200, 200, 215, 0.8));
        for (String line : wrap("Five watches are behind you. He gets three lines and no "
                + "nights of his own. You do not know what his night will be.", F_BODY, 1100)) {
            gc.fillText(line, M, y);
            y += 26;
        }

        // Three columns, and they have to be three: the pool and the slots
        // are the same width of content and the earned lines are the long
        // ones. Two columns plus a panel put the panel on top of the lines.
        double colW = 356;
        double lx = M;
        double rx = M + colW + 20;
        double sx = M + (colW + 20) * 2 + 20;

        double labelY = y + 34;
        label("BELL'S FOUR", lx, labelY);
        label("WHAT YOU LEARNED", rx, labelY);
        label("WHAT YOU LEAVE HIM", sx, labelY);

        double ly = labelY + 30, ry = labelY + 30;
        List<Handoff.Order> pool = game.pool();
        for (int i = 0; i < pool.size(); i++) {
            Handoff.Order o = pool.get(i);
            boolean sel = i == index;
            boolean held = game.written.contains(o);
            double x = o.bell ? lx : rx;
            double yy = o.bell ? ly : ry;

            if (sel) {
                gc.setFill(Color.rgb(242, 193, 78, 0.07));
                gc.fillRect(x - 8, yy - 17, colW, 52);
                gc.setFill(ACCENT);
                gc.fillRect(x - 8, yy - 17, 3, 52);
            }
            gc.setFont(F_POOL);
            gc.setFill(held ? ACCENT : (sel ? BODY : Color.rgb(160, 160, 178, 0.85)));
            double ty = yy;
            for (String line : wrap(o.text, F_POOL, colW - 24)) {
                gc.fillText(line, x, ty);
                ty += 21;
            }
            if (held) {
                gc.setFont(F_TINY);
                gc.setFill(ACCENT);
                gc.fillText("written", x, ty + 1);
                ty += 16;
            }
            if (o.bell) ly = ty + 20; else ry = ty + 20;
        }
        if (game.unlocked.isEmpty()) {
            gc.setFont(F_POOL);
            gc.setFill(FAINT);
            for (String line : wrap("Nothing. You never did anything but what you were told.",
                    F_POOL, colW - 24)) {
                gc.fillText(line, rx, ry + 8);
                ry += 21;
            }
        }

        drawSlots(sx, labelY + 30, colW);
        hint(game.written.size() >= Handoff.ORDERS_HELD
                ? "\u2191\u2193 look     ENTER seal the orders     BACKSPACE take one back"
                : "\u2191\u2193 choose     ENTER write it down     BACKSPACE take one back");
    }

    /**
     * The three slots, in the order you wrote them -- because he reads them
     * in that order, and the order is the only part of this he cannot
     * guess.
     */
    void drawSlots(double x, double y, double w) {
        double h = 92;
        for (int i = 0; i < Handoff.ORDERS_HELD; i++) {
            panel(x, y, w, h);
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText("line " + (i + 1), x + 16, y + 22);
            if (i < game.written.size()) {
                gc.setFont(F_POOL);
                gc.setFill(ACCENT);
                double ty = y + 46;
                for (String line : wrap(game.written.get(i).text, F_POOL, w - 32)) {
                    gc.fillText(line, x + 16, ty);
                    ty += 21;
                }
            } else {
                gc.setFont(F_POOL);
                gc.setFill(Color.rgb(120, 120, 140, 0.55));
                gc.fillText("--", x + 16, y + 50);
            }
            y += h + 10;
        }
    }

    // -------------------------------------------------------- SUCCESSION

    void drawSuccession() {
        double y = 122;
        gc.setFont(F_HEAD);
        gc.setFill(BODY);
        gc.fillText(Handoff.SUCCESSION_HEADING, M, y);
        gc.setFont(F_BODY);
        gc.setFill(FAINT);
        gc.fillText("-- " + Handoff.SUCCESSION_TITLE, M + 280, y);
        y += 34;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(214, 214, 226, 0.92));
        for (String line : wrap(Handoff.SUCCESSION_SCENE, F_SCENE, 660)) {
            gc.fillText(line, M, y);
            y += 27;
        }

        y += 24;
        drawWrittenPanel(766, 122, 430);

        // The reveal replaces the column rather than adding to it. What he
        // could see and what he did are the same beat, one moment apart,
        // and stacking them ran the third line off the bottom of the canvas.
        if (!revealed) {
            label("WHAT HE COULD SEE", M, y);
            y += 26;
            gc.setFont(F_BODY);
            for (String sig : Handoff.SUCCESSION_SIGNALS) {
                gc.setFill(DIM);
                gc.fillText("\u00B7  " + Handoff.signalText(sig), M + 10, y);
                y += 24;
            }
            hint("ENTER -- let his night happen");
            return;
        }

        label("WHAT HE DID", M, y);
        y += 30;
        Handoff.Succession s = game.succession();
        for (Handoff.Event ev : s.events) {
            String mark = ev.by == null ? "  " : (ev.good ? "\u2713 " : "\u2717 ");
            gc.setFont(F_BODY);
            gc.setFill(ev.by == null ? Color.rgb(150, 150, 170, 0.78)
                                     : (ev.good ? BODY : BAD));
            for (String line : wrap(mark + ev.text, F_BODY, 680)) {
                gc.fillText(line, M + 6, y);
                y += 25;
            }
            if (ev.by != null) {
                gc.setFont(F_TINY);
                gc.setFill(FAINT);
                gc.fillText("because you wrote: " + ev.by.id, M + 30, y);
                y += 20;
            }
            y += 12;
        }

        hint("ENTER -- what your orders did");
    }

    /** The three lines you left, in the order you left them. */
    void drawWrittenPanel(double x, double y, double w) {
        double h = 322;
        panel(x, y, w, h);
        label("WHAT YOU LEFT HIM", x + 22, y + 34);
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText("read in this order", x + 22, y + 54);

        double ly = y + 86;
        for (int i = 0; i < game.written.size(); i++) {
            Handoff.Order o = game.written.get(i);
            gc.setFont(F_TINY);
            gc.setFill(ACCENT);
            gc.fillText((i + 1) + ".", x + 22, ly);
            gc.setFont(F_POOL);
            gc.setFill(o.bell ? Color.rgb(160, 160, 178, 0.9) : BODY);
            double ty = ly;
            for (String line : wrap(o.text, F_POOL, w - 70)) {
                gc.fillText(line, x + 44, ty);
                ty += 20;
            }
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(o.origin, x + 44, ty + 2);
            ly = ty + 26;
        }
    }

    // --------------------------------------------------------------- DONE

    void drawDone() {
        Handoff.Succession s = game.succession();

        double y = 122;
        gc.setFont(F_HEAD);
        gc.setFill(BODY);
        gc.fillText("The orders you left", M, y);
        y += 40;

        gc.setFont(F_BODY);
        for (int i = 0; i < game.written.size(); i++) {
            Handoff.Order o = game.written.get(i);
            gc.setFill(ACCENT);
            gc.fillText((i + 1) + ".", M + 6, y);
            gc.setFill(o.bell ? Color.rgb(160, 160, 178, 0.9) : BODY);
            double ty = y;
            for (String line : wrap(o.text, F_BODY, 900)) {
                gc.fillText(line, M + 32, ty);
                ty += 26;
            }
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(o.origin, M + 32, ty - 4);
            gc.setFont(F_BODY);
            y = ty + 22;
        }

        y += 20;
        // The count is not a failure by itself. It only reads red when the
        // night raised something nothing answered.
        gc.setFill(s.good >= s.raised ? ACCENT : BAD);
        gc.setFont(F_BODY);
        gc.fillText(s.verdict, M, y);
        y += 40;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(232, 232, 239, 0.95));
        for (String line : wrap(s.closing, F_SCENE, 900)) {
            gc.fillText(line, M, y);
            y += 28;
        }

        y += 30;
        panel(M, y, 1000, 92);
        gc.setFont(F_BODY);
        gc.setFill(Color.rgb(200, 200, 215, 0.85));
        double ty = y + 34;
        for (String line : wrap("A standing order is a judgment with the judge taken out of "
                + "it, sent on ahead to a night nobody has seen.", F_BODY, 940)) {
            gc.fillText(line, M + 22, ty);
            ty += 26;
        }

        hint("R -- keep the watch again     ENTER -- back to the library");
    }

    // ------------------------------------------------------------ helpers

    void label(String text, double x, double y) {
        gc.setFont(F_LABEL);
        gc.setFill(Color.rgb(242, 193, 78, 0.72));
        gc.fillText(text, x, y);
    }

    void panel(double x, double y, double w, double h) {
        gc.setFill(Color.rgb(255, 255, 255, 0.030));
        gc.fillRect(x, y, w, h);
        gc.setStroke(LINE);
        gc.setLineWidth(1);
        gc.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
    }

    void hint(String text) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 170, 0.6));
        gc.fillText(text, M, H - 40);
    }

    /** Real font metrics, for the one place a label has to follow text. */
    static double textWidth(String s, Font font) {
        Text probe = new Text(s);
        probe.setFont(font);
        return probe.getLayoutBounds().getWidth();
    }

    static String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
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
