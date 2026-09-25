package aside.games.fnaf;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Pixel-art Glamrock animatronics (Security Breach cast) as char grids.
 * 16x16 grids, scaled 4x for display. These are my renditions — the
 * real 3D models can't be extracted, so this is the honest version.
 *
 * Palette per character:
 *  Monty (gator):    G=green body, D=dark green, W=white teeth, Y=yellow eyes
 *  Roxanne (wolf):    L=light grey, K=dark grey, Y=yellow eyes, P=purple hair
 *  Chica (chicken):   W=white body, P=pink accents, Y=yellow beak/eyes
 *  Freddy (bear):     O=orange body, B=brown, T=top hat blue, Y=yellow accents
 */
public class Sprites {
    // G = green, D = dark, W = white/teeth, Y = yellow eyes, R = red
    static String[] MONTY = {
        "     DGGGGD     ",
        "    GGGGGGGG    ",
        "   GGYDGGDYYG   ",
        "   GGYDGGDYYG   ",
        "   GGGGGGGGGG   ",
        "   GDGGGGGGDG   ",
        "   GWGGGGGGWG   ",
        "   GGWWWWWWGG   ",
        "    GGGGGGGG    ",
        "   GGGGGGGGGG   ",
        "  DGGGGGGGGGGD  ",
        "  DGGGGGGGGGGD  ",
        "  DGG DGG DGGD  ",
        "  DDD DGG DDD   ",
        "      DGGD      ",
        "      DDDD      ",
    };

    // L = light grey, K = dark grey, Y = yellow eyes, P = purple hair
    static String[] ROXANNE = {
        "     PLLLLP     ",
        "    PLLLLLLLP   ",
        "   LLLLLLLLLLL  ",
        "   LLYLLLLLYLL  ",
        "   LLLLLLLLLLL  ",
        "   LLLLLLLLLLL  ",
        "   LLLLLLLLLLL  ",
        "   LLLLLLLLLLL  ",
        "    LLLLLLLL    ",
        "   LLLLLLLLLL   ",
        "  KLLLLLLLLLLK  ",
        "  KLLLLLLLLLLK  ",
        "  KLL KLL KLLK  ",
        "  KKK KLL KKK   ",
        "      KLLK      ",
        "      KKKK      ",
    };

    // W = white, P = pink, Y = yellow beak
    static String[] CHICA = {
        "      YYYY      ",
        "     YYYYYY     ",
        "    WYYYYYYW    ",
        "    WWYYYYWW    ",
        "    WWWWWWWW    ",
        "    WWWWWWWW    ",
        "   PWWWWWWWWP   ",
        "   PPWWWWWWPP   ",
        "   PPWWWWWWPP   ",
        "   PWWWWWWWWP   ",
        "    WWWWWWWW    ",
        "   WWWWWWWWWW   ",
        "   WW WWW WWW W ",
        "   YY WWW WWW YY",
        "      WWWWW     ",
        "      YYYYY     ",
    };

    // O = orange, B = brown, T = blue hat, Y = yellow eyes
    static String[] FREDDY = {
        "      TTTT      ",
        "     TTTTTT     ",
        "    OOOOOOOO    ",
        "   OOOOOOOOOO   ",
        "   OOYOOOOYOO   ",
        "   OOOOOOOOOO   ",
        "   OOOOOOOOOO   ",
        "   OOOOOOOOOO   ",
        "    OOOOOOOO    ",
        "   OOOOOOOOOO   ",
        "  BOOOOOOOOOOB  ",
        "  BOOOOOOOOOOB  ",
        "  BOO BOO BOOB  ",
        "  BBB BOO BBB   ",
        "      BOOB      ",
        "      BBBB      ",
    };

    public static Image monty, roxanne, chica, freddy;
    public static final int SCALE = 8;

    static {
        monty = build(MONTY, SCALE);
        roxanne = build(ROXANNE, SCALE);
        chica = build(CHICA, SCALE);
        freddy = build(FREDDY, SCALE);
    }

    /** Char grid -> Image, scaled n×. */
    static Image build(String[] grid, int scale) {
        int h = grid.length, w = grid[0].length();
        WritableImage img = new WritableImage(w * scale, h * scale);
        PixelWriter px = img.getPixelWriter();
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                javafx.scene.paint.Color col = color(grid[r].charAt(c));
                for (int sy = 0; sy < scale; sy++)
                    for (int sx = 0; sx < scale; sx++)
                        px.setColor(c * scale + sx, r * scale + sy, col);
            }
        }
        return img;
    }

    static javafx.scene.paint.Color color(char ch) {
        return switch (ch) {
            case 'G' -> javafx.scene.paint.Color.web("#3CB043");
            case 'D' -> javafx.scene.paint.Color.web("#1F6B24");
            case 'W' -> javafx.scene.paint.Color.web("#F5F5F5");
            case 'Y' -> javafx.scene.paint.Color.web("#FFD700");
            case 'R' -> javafx.scene.paint.Color.web("#E94560");
            case 'L' -> javafx.scene.paint.Color.web("#B8B8C8");
            case 'K' -> javafx.scene.paint.Color.web("#4A4A5A");
            case 'P' -> javafx.scene.paint.Color.web("#9B59B6");
            case 'O' -> javafx.scene.paint.Color.web("#E8853D");
            case 'B' -> javafx.scene.paint.Color.web("#8B5A2B");
            case 'T' -> javafx.scene.paint.Color.web("#2E5A88");
            case ' ' -> javafx.scene.paint.Color.TRANSPARENT;
            default -> javafx.scene.paint.Color.MAGENTA;
        };
    }
}
