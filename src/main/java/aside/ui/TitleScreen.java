package aside.ui;

import aside.engine.Script;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Title screen: start the story, continue a save, or quit. */
public class TitleScreen extends UiScreen {

    static final Font F_BIG = Font.font("Georgia", 62);
    static final Font F_SUB = Font.font("Georgia", 22);
    static final Font F_ITEM = Font.font("Georgia", 24);
    static final Font F_TINY = Font.font("Arial", 13);

    String storyPath;
    Script script;
    String loadError;
    int index = 0;
    final java.util.List<String> items = new java.util.ArrayList<>();

    public TitleScreen(UiManager ui) {
        super(ui);
        storyPath = System.getProperty("aside.story", "stories/overtime.aside");
        File f = new File(storyPath);
        if (!f.exists()) f = new File("stories", "overtime.aside");
        try {
            script = Script.load(f.toPath());
        } catch (Exception e) {
            loadError = f + ": " + e.getMessage();
        }
        items.add("Begin");
        if (Files.exists(Path.of("saves", "quicksave.txt"))) items.add("Continue");
        items.add("Quit");
    }

    @Override
    public void enter() { index = 0; }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.UP) index = (index - 1 + items.size()) % items.size();
        else if (c == KeyCode.DOWN) index = (index + 1) % items.size();
        else if (c == KeyCode.ENTER || c == KeyCode.SPACE) select();
        e.consume();
    }

    void select() {
        if (loadError != null) return;
        String item = items.get(index);
        switch (item) {
            case "Begin" -> {
                VnScreen vs = new VnScreen(ui, script, script.title);
                // Dev shortcut: -Daside.scene=n3_notice jumps straight there
                String jump = System.getProperty("aside.scene");
                if (jump != null) vs.vn.startAt(jump);
                ui.replace(vs);
            }
            case "Continue" -> {
                try {
                    var vn = aside.engine.Vn.load(script, Path.of("saves", "quicksave.txt"));
                    ui.replace(new VnScreen(ui, vn, script.title));
                } catch (Exception ex) {
                    loadError = "could not read save: " + ex.getMessage();
                }
            }
            default -> javafx.application.Platform.exit();
        }
    }

    boolean dumped = false;

    @Override
    public void tick(double dt) {
        draw();
        if (!dumped) {
            dumped = true;
            try {
                var img = root.snapshot(null, null);
                int mid = img.getPixelReader().getArgb(640, 360);
                int top = img.getPixelReader().getArgb(640, 200);
                System.out.printf("[debug] canvas snapshot %dx%d  mid=%08X  top=%08X  sceneChildren=%d%n",
                        (int) img.getWidth(), (int) img.getHeight(), mid, top,
                        root.getChildren().size());
            } catch (Exception ex) {
                System.out.println("[debug] snapshot failed: " + ex);
            }
        }
    }

    void draw() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        var cover = Assets.A == null ? null : Assets.A.background("title");
        if (cover != null) {
            gc.setGlobalAlpha(0.45);
            drawCover(cover);
            gc.setGlobalAlpha(1);
            gc.setFill(Color.rgb(0, 0, 0, 0.45));
            gc.fillRect(0, 0, W, H);
        }

        gc.setFill(Color.web("#F2C14E"));
        gc.setFont(F_BIG);
        String t = script != null ? script.title : "Aside";
        gc.fillText(t, (W - t.length() * 32) / 2, 220);

        gc.setFill(Color.web("#B9B9C6"));
        gc.setFont(F_SUB);
        String by = script != null && !script.author.isBlank() ? "by " + script.author : "";
        gc.fillText(by, (W - by.length() * 11) / 2, 262);

        gc.setFont(F_ITEM);
        double y = 400;
        for (int i = 0; i < items.size(); i++) {
            boolean sel = i == index;
            gc.setFill(sel ? Color.web("#F2C14E") : Color.web("#9A9AAE"));
            String label = (sel ? "▶  " : "   ") + items.get(i);
            gc.fillText(label, W / 2 - 100, y);
            y += 46;
        }

        gc.setFont(F_TINY);
        gc.setFill(Color.web("#7A7A90"));
        if (loadError != null) {
            gc.setFill(Color.web("#E94560"));
            gc.fillText(loadError, 60, H - 40);
        } else {
            String info = "story: " + storyPath
                    + "     sprites: " + (Assets.A == null ? 0 : Assets.A.spriteCount())
                    + "     backgrounds: " + (Assets.A == null ? 0 : Assets.A.backgroundCount());
            gc.fillText(info, 40, H - 40);
        }
        gc.setFill(Color.web("#7A7A90"));
        gc.fillText("↑↓ select    ENTER confirm", 40, H - 18);
    }
}
