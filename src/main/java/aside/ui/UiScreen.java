package aside.ui;

import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

/**
 * Base for every presenter screen.
 *
 * The window is resizable; the canvas is not. Everything is drawn at
 * a fixed 1280x720 and then scaled to fit, which keeps layout maths
 * simple and means one coordinate space for art, text and hit-testing.
 */
public abstract class UiScreen {
    public static final int W = 1280, H = 720;

    protected final UiManager ui;
    protected final Canvas canvas;
    protected final GraphicsContext gc;
    protected final StackPane root;

    protected UiScreen(UiManager ui) {
        this.ui = ui;
        this.canvas = new Canvas(W, H);
        this.gc = canvas.getGraphicsContext2D();
        this.root = new StackPane(canvas);
        // NOTE: Region backgrounds do not paint on Kinger's GPU -- draw
        // everything on the canvas instead of styling containers.
        root.setStyle("-fx-background-color: #000000;");
        canvas.setManaged(false);
        fitToWindow(root, canvas);
    }

    public Parent getRoot() { return root; }

    public void enter() {}
    public void pause() {}
    public void resume() {}
    public void exit() {}

    public abstract void handleKey(KeyEvent e);
    /** Called once per frame with seconds elapsed. */
    public abstract void tick(double dt);

    static void fitToWindow(StackPane parent, Canvas canvas) {
        javafx.beans.value.ChangeListener<Number> l = (o, a, b) -> fit(parent, canvas);
        parent.widthProperty().addListener(l);
        parent.heightProperty().addListener(l);
        fit(parent, canvas);
    }

    static int fitLogs = 0;

    static void fit(StackPane parent, Canvas canvas) {
        double pw = parent.getWidth(), ph = parent.getHeight();
        if (fitLogs < 6) {
            System.out.println("[fit] parent=" + pw + "x" + ph
                    + " canvas=" + canvas.getWidth() + "x" + canvas.getHeight());
            fitLogs++;
        }
        if (pw <= 0 || ph <= 0) return;
        double s = Math.min(pw / canvas.getWidth(), ph / canvas.getHeight());
        canvas.setScaleX(s);
        canvas.setScaleY(s);
        canvas.setTranslateX((pw - canvas.getWidth() * s) / 2);
        canvas.setTranslateY((ph - canvas.getHeight() * s) / 2);
    }

    /** Draw an image to cover the whole frame, cropping overflow. */
    protected void drawCover(javafx.scene.image.Image img) {
        if (img == null) return;
        double iw = img.getWidth(), ih = img.getHeight();
        double s = Math.max(W / iw, H / ih);
        gc.drawImage(img, (W - iw * s) / 2, (H - ih * s) / 2, iw * s, ih * s);
    }

    /** Draw an image fitted inside the frame with bars, not cropped. */
    protected void drawFit(javafx.scene.image.Image img) {
        if (img == null) return;
        double iw = img.getWidth(), ih = img.getHeight();
        double s = Math.min(W / iw, H / ih);
        gc.drawImage(img, (W - iw * s) / 2, (H - ih * s) / 2, iw * s, ih * s);
    }
}
