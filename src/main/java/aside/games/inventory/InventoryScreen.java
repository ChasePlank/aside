package aside.games.inventory;

import aside.ui.Audio;
import aside.ui.LibraryScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * inventory, drawn.
 *
 * Two columns, and the split is the whole design. The left is the object in
 * front of you: what it looks like, what was in the box with it, and the
 * three names the card will take. The right is the catalogue: everything you
 * have already decided, including the readings you did not take.
 *
 * The right column is the one that matters, and it is the one the player is
 * least inclined to read. It is the only place the collection says anything
 * about itself -- two cards in a set must name the same use, and the readings
 * you passed over are still on the card for you to compare. A player who
 * treats each object as its own small problem writes eight reasonable cards
 * and gets several of them wrong, and nothing on the left column would ever
 * have told them.
 *
 * Three beats:
 *
 *   OPEN    the premise and the one rule, once
 *   CARD    an object, and the name that becomes it
 *   REPORT  what the survey did with every card, as written
 *
 * The one thing this screen deliberately never does is tell you whether a
 * card was right. It cannot: the object is gone, and a card is not a
 * description of a thing.
 */
public class InventoryScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_HEAD = Font.font("Georgia", 24);
    static final Font F_SCENE = Font.font("Georgia", 19);
    static final Font F_ITEM = Font.font("Georgia", 19);
    static final Font F_NOTE = Font.font("Georgia", FontPosture.ITALIC, 18);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_MONO = Font.font("Consolas", 16);
    static final Font F_MONO_S = Font.font("Consolas", 13);

    static final double M = 84;
    static final double LEFT_W = 660;
    static final double RIGHT_X = 800;
    static final double RIGHT_W = 396;

    static final Color BG = Color.web("#0C0A08");
    static final Color PAPER = Color.web("#15120E");
    static final Color EDGE = Color.web("#2A2419");
    static final Color GOLD = Color.web("#D9A441");
    static final Color INK = Color.web("#EDE7DE");
    static final Color DIM = Color.web("#A79C8E");
    static final Color FAINT = Color.web("#6E655A");
    static final Color NOTE = Color.web("#C9B98F");
    static final Color RED = Color.web("#C4553F");
    static final Color GREEN = Color.web("#7FA88A");

    enum Phase { OPEN, CARD, REPORT }

    final Inventory inv;
    final Path save;

    Phase phase = Phase.OPEN;
    String notice = "";
    double noticeTimer = 0;

    public InventoryScreen(UiManager ui, Inventory inv, Path save) {
        super(ui);
        this.inv = inv;
        this.save = save;
        // A finished inventory has no card to open on. Without this the
        // player who hands the collection over and comes back lands on an
        // empty bench with no way to see what they did.
        if (inv.finished()) phase = Phase.REPORT;
        // And a half-written inventory opens on the next object, not on the
        // premise again.
        else if (inv.written() > 0) phase = Phase.CARD;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (phase) {
            case OPEN -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.CARD; sfx("choice_select"); }
            }
            case CARD -> card(c);
            case REPORT -> {
                if (c == KeyCode.R) {
                    reset();
                    phase = Phase.CARD;
                    sfx("choice_select");
                } else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    ui.replace(new LibraryScreen(ui));
                }
            }
        }
        e.consume();
    }

    void card(KeyCode c) {
        if (inv.finished()) { phase = Phase.REPORT; return; }
        int slot = switch (c) {
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            default -> 0;
        };
        if (slot == 0) return;
        if (!inv.nameBySlot(slot)) {
            notice = Inventory.NOTHING_ON_BENCH;
            noticeTimer = 4;
            sfx("door_close");
            return;
        }
        persist();
        if (inv.finished()) {
            phase = Phase.REPORT;
            sfx("door_close");
        } else {
            sfx("choice_move");
        }
    }

    void reset() {
        Inventory fresh = Inventory.of();
        inv.things.clear();
        inv.things.addAll(fresh.things);
        inv.next = 0;
        persist();
    }

    void persist() {
        try { inv.save(save); }
        catch (Exception ex) { notice = "the card could not be filed: " + ex.getMessage(); noticeTimer = 6; }
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

        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText(Inventory.WORDMARK, M, 76);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = switch (phase) {
            case OPEN -> Inventory.WHERE_OPEN;
            case CARD -> Inventory.cardWhere(inv.next + 1, inv.things.size());
            case REPORT -> Inventory.WHERE_REPORT;
        };
        gc.fillText(right, W - M - 200, 76);

        switch (phase) {
            case OPEN -> drawOpen();
            case CARD -> drawCard();
            case REPORT -> drawReport();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(RED);
            gc.fillText(notice, M, H - 66);
        }
    }

    // ---------------------------------------------------------------- open

    void drawOpen() {
        double y = 148;
        for (String p : Inventory.OPENING) {
            for (String line : wrap(p, F_SCENE, LEFT_W)) {
                gc.setFont(F_SCENE);
                gc.setFill(INK);
                gc.fillText(line, M, y);
                y += 26;
            }
            y += 14;
        }

        drawRulesPanel();

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Inventory.START_LINE, M, H - 76);
    }

    void drawRulesPanel() {
        panel(RIGHT_X - 18, 128, RIGHT_W + 36, 330);
        double x = RIGHT_X, y = 158;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Inventory.RULES_HEADING, x, y);
        y += 28;

        for (String[] r : Inventory.RULES) {
            if (!r[0].isEmpty()) {
                gc.setFont(F_MONO_S);
                gc.setFill(GOLD);
                gc.fillText(r[0], x, y);
            }
            // The key column is 78 wide, not 46: "1 / 2 / 3" in Consolas 13
            // is 65px, so at 46 the label ran straight into the sentence it
            // was labelling.
            for (String line : wrap(r[1], F_SMALL, RIGHT_W - 78)) {
                gc.setFont(F_SMALL);
                gc.setFill(DIM);
                gc.fillText(line, x + 78, y);
                y += 18;
            }
            y += 14;
        }
    }

    // ---------------------------------------------------------------- card

    void drawCard() {
        Inventory.Thing t = inv.current();
        if (t == null) { drawReport(); return; }

        double y = 168;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Inventory.IN_FRONT, M, y);
        y += 30;

        for (String line : wrap(t.form, F_SCENE, LEFT_W)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 26;
        }

        if (t.hasNote()) {
            y += 22;
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(Inventory.THE_NOTE, M, y);
            y += 26;
            for (String line : wrap(t.note, F_NOTE, LEFT_W)) {
                gc.setFont(F_NOTE);
                gc.setFill(NOTE);
                gc.fillText(line, M, y);
                y += 24;
            }
        }

        y += 30;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Inventory.WHAT_DO_YOU_WRITE, M, y);
        y += 38;

        for (int i = 0; i < t.offered.length; i++) {
            Inventory.Use u = t.offered[i];
            gc.setFont(F_MONO);
            gc.setFill(GOLD);
            gc.fillText(String.valueOf(i + 1), M, y);
            gc.setFont(F_ITEM);
            gc.setFill(INK);
            gc.fillText(u.label, M + 44, y);
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(u.does, M + 210, y);
            y += 46;
        }

        drawCatalogue();
        hint("1 / 2 / 3 to write the card. ESC for the library.");
    }

    void drawCatalogue() {
        panel(RIGHT_X - 18, 128, RIGHT_W + 36, 420);
        double x = RIGHT_X, y = 158;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Inventory.THE_CATALOGUE, x, y);
        gc.fillText(Inventory.writtenOf(inv.written(), inv.things.size()),
                x + RIGHT_W - 62, y);
        y += 30;

        if (inv.written() == 0) {
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(Inventory.NOTHING_WRITTEN, x, y);
            return;
        }

        for (Inventory.Thing t : inv.things) {
            if (!t.named()) continue;
            gc.setFont(F_MONO_S);
            gc.setFill(FAINT);
            gc.fillText(t.number + ".", x, y);
            gc.setFont(F_ITEM);
            gc.setFill(INK);
            gc.fillText(t.label(), x + 30, y);
            y += 20;
            // The readings you did not take. This is the only place the
            // collection says anything about itself: two cards in a set must
            // name the same use, and the overlap between their offered names
            // is where that shows up.
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(Inventory.ALSO_READ_AS + t.otherReadings(), x + 30, y);
            y += 30;
        }
    }

    // ---------------------------------------------------------------- report

    void drawReport() {
        double y = 122;
        gc.setFont(F_HEAD);
        gc.setFill(inv.wrong() == 0 ? GREEN : GOLD);
        gc.fillText(inv.headline(), M, y);

        y = 174;
        for (String line : wrap(inv.verdict(), F_SCENE, W - 2 * M)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 26;
        }

        y += 26;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Inventory.WHAT_YOU_WROTE, M, y);
        gc.fillText(Inventory.WHAT_THE_SURVEY_DID, M + 320, y);
        y += 12;
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(M, y, W - M, y);
        y += 26;

        for (int i = 0; i < inv.things.size(); i++) {
            Inventory.Thing t = inv.things.get(i);
            gc.setFont(F_MONO_S);
            gc.setFill(FAINT);
            gc.fillText(t.number + ".", M, y);
            gc.setFont(F_SMALL);
            gc.setFill(t.right() ? GREEN : RED);
            gc.fillText(t.label(), M + 30, y);
            gc.setFont(F_SMALL);
            gc.setFill(t.right() ? DIM : Color.web("#D8C3B4"));
            gc.fillText(inv.outcomeFor(i), M + 320, y);
            y += 26;
        }

        y += 20;
        for (String line : wrap(inv.closing(), F_SCENE, W - 2 * M)) {
            if (line.isEmpty()) { y += 12; continue; }   // a paragraph break is not a line
            gc.setFont(F_SCENE);
            gc.setFill(Color.web("#B9AFA2"));
            gc.fillText(line, M, y);
            y += 24;
        }

        hint("R to clear the bench and start again. ENTER for the library.");
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
        gc.setFill(Color.rgb(150, 140, 125, 0.6));
        gc.fillText(text, M, H - 40);
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
