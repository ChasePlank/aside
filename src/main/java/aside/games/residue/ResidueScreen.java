package aside.games.residue;

import aside.ui.Audio;
import aside.ui.LibraryScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Residue, drawn.
 *
 * The room is the game and this is only a window onto it — the same room
 * that runs headless in the standalone repo, with the same save file. If
 * this screen ever needed to hold state the room didn't have, the room
 * would be wrong.
 *
 * Four beats, in order, and the player cannot skip the first one:
 *
 *   ARRIVAL       what the room does to you on the way in
 *   PICK_THING    one of five things
 *   PICK_GESTURE  how you leave it
 *   DEPARTURE     what the next person will find — your thing, already
 *                 one visit older than you left it
 *
 * The arrival beat exists so the room registers before the menu appears.
 * A game about noticing should not open with a menu.
 */
public class ResidueScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_ARRIVAL = Font.font("Georgia", 24);
    static final Font F_BODY = Font.font("Georgia", 20);
    static final Font F_ITEM = Font.font("Georgia", 22);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);

    static final double MARGIN = 132;

    /**
     * The room itself, painted once and drawn behind everything.
     *
     * It is deliberately almost black -- the text is the game and the art
     * is the temperature of the room, not a thing to look at. If it ever
     * competes with the words, it is wrong.
     *
     * Missing art is not an error: the screen falls back to the flat
     * background it used before, so the game still runs from a bare jar.
     */
    static Image ROOM_ART;
    static boolean ART_TRIED;

    static Image roomArt() {
        if (!ART_TRIED) {
            ART_TRIED = true;
            try (InputStream in = ResidueScreen.class.getClassLoader()
                    .getResourceAsStream("residue/room.png")) {
                if (in != null) {
                    Image img = new Image(in);
                    if (!img.isError()) ROOM_ART = img;
                }
            } catch (Exception e) {
                System.err.println("residue: no room art (" + e + ")");
            }
        }
        return ROOM_ART;
    }

    enum Phase { ARRIVAL, PICK_THING, PICK_GESTURE, DEPARTURE }

    final Room room;
    final Path save;

    Phase phase = Phase.ARRIVAL;
    int index = 0;
    Thing chosen;
    Thing.Gesture left;
    String departureLine;
    boolean departed;

    public ResidueScreen(UiManager ui, Room room, Path save) {
        super(ui);
        this.room = room;
        this.save = save;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();

        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (phase) {
            case ARRIVAL -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.PICK_THING; index = 0; }
            }
            case PICK_THING -> {
                List<Thing> things = List.of(Thing.values());
                if (c == KeyCode.UP) { index = (index - 1 + things.size()) % things.size(); sfx("choice_move"); }
                else if (c == KeyCode.DOWN) { index = (index + 1) % things.size(); sfx("choice_move"); }
                else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    chosen = things.get(index);
                    index = 0;
                    phase = Phase.PICK_GESTURE;
                    sfx("choice_select");
                } else {
                    int d = digit(c);
                    if (d >= 1 && d <= things.size()) {
                        chosen = things.get(d - 1);
                        index = 0;
                        phase = Phase.PICK_GESTURE;
                        sfx("choice_select");
                    }
                }
            }
            case PICK_GESTURE -> {
                List<Thing.Gesture> gs = chosen.gestures;
                if (c == KeyCode.UP) { index = (index - 1 + gs.size()) % gs.size(); sfx("choice_move"); }
                else if (c == KeyCode.DOWN) { index = (index + 1) % gs.size(); sfx("choice_move"); }
                else if (c == KeyCode.BACK_SPACE) { phase = Phase.PICK_THING; index = 0; }
                else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    leave(gs.get(index));
                } else {
                    int d = digit(c);
                    if (d >= 1 && d <= gs.size()) leave(gs.get(d - 1));
                }
            }
            case DEPARTURE -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) ui.replace(new LibraryScreen(ui));
            }
        }
        e.consume();
    }

    void leave(Thing.Gesture g) {
        left = g;
        Trace t = room.leave(chosen, g.id, true);
        try {
            room.save(save);
            departed = true;
        } catch (Exception ex) {
            departureLine = "The room could not be written down: " + ex.getMessage();
            departed = false;
        }
        if (departed) {
            String next = t.describeNext();
            departureLine = next == null
                    ? Room.DEPARTURE_NOTHING_LEFT
                    : next;
        }
        phase = Phase.DEPARTURE;
        sfx("choice_select");
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
        gc.setFill(Color.web("#07070B"));
        gc.fillRect(0, 0, W, H);

        Image art = roomArt();
        if (art != null) {
            gc.drawImage(art, 0, 0, W, H);
            // A light scrim, not a heavy one. The room is already dark; this
            // only guarantees the words keep their contrast on a bright
            // monitor, where a lit lampshade can otherwise swallow the small
            // grey annotations.
            gc.setFill(Color.rgb(7, 7, 11, 0.22));
            gc.fillRect(0, 0, W, H);
        }

        drawLampLight();

        gc.setFill(Color.web("#F2C14E"));
        gc.setFont(F_TITLE);
        gc.fillText("residue", MARGIN, 84);

        gc.setFont(F_TINY);
        gc.setFill(Color.rgb(160, 160, 180, 0.55));
        gc.fillText("visit " + room.visits, W - MARGIN - 70, 84);

        switch (phase) {
            case ARRIVAL      -> drawArrival();
            case PICK_THING   -> drawPickThing();
            case PICK_GESTURE -> drawPickGesture();
            case DEPARTURE    -> drawDeparture();
        }
    }

    /**
     * The lamp is the only thing in the room whose state you can see from
     * the door. Drawing it is the cheapest way to make the room feel like
     * it has a temperature rather than a contents list.
     */
    void drawLampLight() {
        Trace lamp = room.traceOf(Thing.LAMP);
        if (lamp == null) return;
        double strength = switch (lamp.gesture) {
            case "on" -> 0.055;
            case "low" -> 0.028;
            default -> 0.0;
        };
        if (strength <= 0) return;
        // The painted lamp stands at about (0.83W, 0.47H); the glow has to
        // sit on it or the room reads as lit by nothing.
        double cx = W * 0.83, cy = H * 0.47;
        for (int i = 9; i >= 1; i--) {
            double r = 90 * i;
            gc.setFill(Color.rgb(242, 193, 78, strength / i));
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    void drawArrival() {
        gc.setFont(F_ARRIVAL);
        gc.setFill(Color.web("#E8E8EF"));
        double y = 214;
        for (String line : wrap(room.arrival(), F_ARRIVAL, W - MARGIN * 2)) {
            gc.fillText(line, MARGIN, y);
            y += 34;
        }

        y += 26;
        y = drawContents(y);

        hint("ENTER — stay a while");
    }

    void drawPickThing() {
        gc.setFont(F_BODY);
        gc.setFill(Color.rgb(200, 200, 215, 0.62));
        double y = 176;
        for (String line : wrap(room.arrival(), F_BODY, W - MARGIN * 2)) {
            gc.fillText(line, MARGIN, y);
            y += 28;
        }

        y += 18;
        y = drawContents(y);

        y += 30;
        gc.setFont(F_BODY);
        gc.setFill(Color.web("#E8E8EF"));
        gc.fillText(Room.HEAD_PICK, MARGIN, y);
        y += 42;

        List<Thing> things = List.of(Thing.values());
        gc.setFont(F_ITEM);
        for (int i = 0; i < things.size(); i++) {
            Thing t = things.get(i);
            boolean sel = i == index;
            boolean here = room.traceOf(t) != null;
            gc.setFill(sel ? Color.web("#F2C14E") : Color.web("#B9B9C6"));
            gc.fillText((sel ? "\u25B6  " : "   ") + (i + 1) + ".  " + t.id, MARGIN + 8, y);
            if (here) {
                gc.setFont(F_TINY);
                gc.setFill(Color.rgb(150, 150, 170, 0.75));
                gc.fillText(Room.ALREADY_HERE, MARGIN + 250, y - 2);
                gc.setFont(F_ITEM);
            }
            y += 37;
        }

        hint("\u2191\u2193 choose     ENTER confirm     ESC leave without leaving");
    }

    void drawPickGesture() {
        gc.setFont(F_BODY);
        gc.setFill(Color.rgb(200, 200, 215, 0.62));
        gc.fillText(Room.headLeaving(chosen), MARGIN, 168);

        double y = 236;
        gc.setFont(F_ITEM);
        List<Thing.Gesture> gs = chosen.gestures;
        for (int i = 0; i < gs.size(); i++) {
            boolean sel = i == index;
            gc.setFill(sel ? Color.web("#F2C14E") : Color.web("#B9B9C6"));
            double gy = y;
            for (String line : wrap(gs.get(i).text, F_ITEM, W - MARGIN * 2 - 40)) {
                gc.fillText((sel && gy == y ? "\u25B6  " : "   ") + line, MARGIN + 8, gy);
                gy += 32;
            }
            y = gy + 22;
        }

        gc.setFont(F_TINY);
        gc.setFill(Color.rgb(150, 150, 170, 0.75));
        gc.fillText(chosen.kindNote(), MARGIN + 8, y + 6);

        hint("\u2191\u2193 choose     ENTER leave it     BACKSPACE pick again");
    }

    void drawDeparture() {
        gc.setFont(F_ARRIVAL);
        gc.setFill(Color.web("#E8E8EF"));
        gc.fillText(Room.HEAD_DEPARTURE, MARGIN, 288);

        gc.setFont(F_BODY);
        gc.setFill(Color.rgb(200, 200, 215, 0.85));
        double y = 360;
        for (String line : wrap(departureLine, F_BODY, W - MARGIN * 2)) {
            gc.fillText(line, MARGIN, y);
            y += 30;
        }

        y += 30;
        gc.setFont(F_TINY);
        gc.setFill(Color.rgb(150, 150, 170, 0.7));
        gc.fillText("Visit " + room.visits + ". " + room.closing(), MARGIN, y);

        hint("ENTER — back to the library");
    }

    /** The room's contents, oldest first. Returns the next free y. */
    double drawContents(double y) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 170, 0.8));
        gc.fillText(room.isEmpty() ? "IN THE ROOM: NOTHING" : "IN THE ROOM", MARGIN, y);
        y += 30;

        if (room.isEmpty()) {
            gc.setFont(F_BODY);
            gc.setFill(Color.rgb(150, 150, 165, 0.7));
            gc.fillText(Room.NOTHING_SURVIVED, MARGIN, y);
            return y + 30;
        }

        for (Trace t : room.traces) {
            String d = t.describe();
            if (d == null) continue;
            gc.setFont(F_BODY);
            gc.setFill(t.mine ? Color.web("#D8D8E4") : Color.web("#9A9AAE"));
            for (String line : wrap(d, F_BODY, W - MARGIN * 2 - 120)) {
                gc.fillText(line, MARGIN, y);
                y += 28;
            }
            gc.setFont(F_TINY);
            gc.setFill(Color.rgb(140, 140, 160, 0.7));
            gc.fillText(t.author(), MARGIN + W - MARGIN * 2 - 100, y - 28);
            y += 10;
        }
        return y;
    }

    void hint(String text) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 170, 0.6));
        gc.fillText(text, MARGIN, H - 44);
    }

    // -------------------------------------------------------------- helpers

    /** Word wrap using real font metrics — a Canvas has no measureText. */
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
