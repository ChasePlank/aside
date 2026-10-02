package aside.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * The phone builds' room art, as one piece of CSS.
 *
 * <p><b>Why this exists.</b> The desktop reads a game's room out of
 * {@code src/main/resources/&lt;game&gt;/room.png}. A phone build is one file
 * with no side files, so the room has to be inlined -- and the PNG base64s to
 * more than a megabyte, which is most of a shelf. {@code tools/verb-phone-art.py}
 * writes a WebP at the phone's own size for about a tenth of that, and this
 * splices it in as a data URI. Residue's room is 970 KB of PNG and 11 KB of
 * WebP: the art is dark and smooth, which is the case WebP is best at.
 *
 * <p><b>Why it is one file and not ten.</b> The same argument the generators
 * are built on -- ten copies of a background rule is ten copies that drift.
 * Each template carries a {@code /*__ART__*}{@code /} marker inside its
 * {@code <style>} block and its generator splices this in.
 *
 * <p><b>What it is not.</b> It is not a general asset pipeline. It knows one
 * thing: a game may have a room, and the room is a backdrop. A game with no
 * room gets nothing and draws exactly what it drew before, which is why a
 * missing file is not an error here.
 */
public final class WebArt {

    /** The marker every phone template carries, and the generator replaces. */
    public static final String MARKER = "/*__ART__*/";

    /** Where the tool writes, relative to the repository root. */
    static Path file(String game) {
        return Path.of("art", "phone", game, "room.webp");
    }

    /**
     * The CSS for a game's room, or the empty string when it has none.
     *
     * The scrim is the same one the desktop draws: light, because the rooms
     * are already dark and this is only here so the words keep their contrast
     * on a bright screen.
     */
    public static String css(String game) {
        Path p = file(game);
        if (!Files.exists(p)) return "";
        try {
            String b64 = Base64.getEncoder().encodeToString(Files.readAllBytes(p));
            // NOT z-index:-1. A negative z-index child paints behind the
            // body's own background, so the room vanishes and the page looks
            // exactly like a page with no art in it. It sits at 0 and the
            // content is lifted above it instead.
            return "#room{position:fixed; inset:0; pointer-events:none; z-index:0;"
                 + " background:#07070B url(data:image/webp;base64," + b64 + ")"
                 + " center bottom / 100% auto no-repeat}"
                 + "#room::after{content:\"\"; position:absolute; inset:0;"
                 + " background:rgba(7,7,11,.22)}"
                 // The band is 100% wide and 16:9, so its top edge sits
                 // 100vw*9/16 above the bottom. Fading that edge is what
                 // stops the room reading as a pasted-on strip.
                 + "#room::before{content:\"\"; position:absolute; left:0; right:0;"
                 + " bottom:calc(100vw * 9 / 16 - 150px); height:150px;"
                 + " background:linear-gradient(to bottom, #07070B 0%, rgba(7,7,11,0) 100%)}"
                 + "#glow{z-index:1} .wrap{z-index:2} .bar{z-index:2}";
        } catch (Exception e) {
            System.err.println("WebArt: could not read " + p + " (" + e + ")");
            return "";
        }
    }
}
