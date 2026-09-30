package aside.ui;

import javafx.scene.input.KeyEvent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayDeque;

/** Screen stack: title, visual novel, history overlay, pause overlay. */
public class UiManager {
    private final ArrayDeque<UiScreen> stack = new ArrayDeque<>();
    private final StackPane container = new StackPane();
    private String projectRoot = ".";

    // ---- audio controls -------------------------------------------------
    // They live on the manager rather than in each game so one control works
    // everywhere. The indicator is a Canvas because Region/Node backgrounds do
    // not paint on this GPU - a Canvas does.
    static final double TOAST_W = 380, TOAST_H = 46;
    private final Canvas toast = new Canvas(TOAST_W, TOAST_H);
    private double toastTimer = 0;
    private String toastText = "";

    public UiManager(String projectRoot) {
        this.projectRoot = projectRoot;
        container.setStyle("-fx-background-color: #000000;");
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, javafx.geometry.Pos.BOTTOM_RIGHT);
        toast.setTranslateX(-18);
        toast.setTranslateY(-14);
        container.getChildren().add(toast);
    }

    /** Say something in the corner for a moment. */
    public void toast(String text) {
        toastText = text;
        toastTimer = 1.8;
    }

    void drawToast(double dt) {
        GraphicsContext g = toast.getGraphicsContext2D();
        g.clearRect(0, 0, TOAST_W, TOAST_H);
        if (toastTimer <= 0) return;
        toastTimer -= dt;
        g.setFill(Color.web("#000000", 0.74));
        g.fillRoundRect(0, 0, TOAST_W, TOAST_H, 10, 10);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Arial", 15));
        g.fillText(toastText, 14, 29);
    }

    public String root() { return projectRoot; }

    public Pane getContainer() { return container; }

    public void push(UiScreen s) {
        if (!stack.isEmpty()) stack.peek().pause();
        stack.push(s);
        if (!container.getChildren().contains(s.getRoot())) container.getChildren().add(s.getRoot());
        s.enter();
        toast.toFront();
    }

    public void pop() {
        UiScreen top = stack.poll();
        if (top != null) {
            top.exit();
            container.getChildren().remove(top.getRoot());
        }
        if (!stack.isEmpty()) {
            UiScreen next = stack.peek();
            if (!container.getChildren().contains(next.getRoot())) {
                container.getChildren().add(next.getRoot());
            }
            next.resume();
        }
    }

    public void replace(UiScreen s) {
        UiScreen top = stack.poll();
        if (top != null) {
            top.exit();
            container.getChildren().remove(top.getRoot());
        }
        stack.push(s);
        if (!container.getChildren().contains(s.getRoot())) container.getChildren().add(s.getRoot());
        s.enter();
        toast.toFront();
    }

    public void handleKeyReleased(KeyEvent e) {

        if (!stack.isEmpty()) stack.peek().handleKeyReleased(e);

    }


    public void handleKey(KeyEvent e) {
        if (!stack.isEmpty()) stack.peek().handleKey(e);
        // Audio is global, but only for keys the screen did not want. Loona's
        // fnaf2 uses M for the mask and MINUS for a camera, so delegating FIRST
        // and checking isConsumed keeps those working while M still mutes
        // everywhere else.
        if (e.isConsumed() || Audio.A == null) return;
        switch (e.getCode()) {
            case M -> { toast(Audio.A.toggleMute()); e.consume(); }
            case OPEN_BRACKET, MINUS, COMMA -> { toast(Audio.A.volume(false)); e.consume(); }
            case CLOSE_BRACKET, EQUALS, PERIOD -> { toast(Audio.A.volume(true)); e.consume(); }
            default -> { }
        }
    }

    public void tick(double dt) {
        drawToast(dt);
        if (stack.isEmpty()) return;
        UiScreen top = stack.peek();
        top.refit();
        top.tick(dt);
    }

    public UiScreen peek() { return stack.peek(); }
}
