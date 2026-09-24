package aside.ui;

import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.util.ArrayDeque;

/** Screen stack: title, visual novel, history overlay, pause overlay. */
public class UiManager {
    private final ArrayDeque<UiScreen> stack = new ArrayDeque<>();
    private final StackPane container = new StackPane();
    private String projectRoot = ".";

    public UiManager(String projectRoot) {
        this.projectRoot = projectRoot;
        container.setStyle("-fx-background-color: #000000;");
    }

    public String root() { return projectRoot; }

    public Pane getContainer() { return container; }

    public void push(UiScreen s) {
        if (!stack.isEmpty()) stack.peek().pause();
        stack.push(s);
        if (!container.getChildren().contains(s.getRoot())) container.getChildren().add(s.getRoot());
        s.enter();
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
    }

    public void handleKey(KeyEvent e) {
        if (!stack.isEmpty()) stack.peek().handleKey(e);
    }

    public void tick(double dt) {
        if (!stack.isEmpty()) stack.peek().tick(dt);
    }

    public UiScreen peek() { return stack.peek(); }
}
