package aside.games.ninetydays;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Ninety Days, as a game module.
 *
 * <p>A lost-property office on disposal day. Unclaimed property is held ninety days and then it goes, and the
 * person who runs the office has been logging other people's things for eleven years. Five things on the shelf,
 * each a small story about somebody who did not come back for it, and one more that is not in the book at all.
 *
 * <p>THE EIGHTH REGISTER AND THE FIRST WITH A DIFFERENT SHAPE. The seven before it are chains: a scene leads to
 * two or three and the ending is whichever branch you took. This one is a HUB - one room, a shelf, you choose
 * what to look at and you come back - and the ending does not depend on WHICH item you chose but on HOW MANY you
 * bothered to, which is a different question and the reason the shape is worth having.
 *
 * <p>It is also the story that taught the auditor something. Gating the hub on a single `seen` counter made the
 * whole second half invisible to the traversal, because the auditor buckets counters and `seen=0` and `seen=1`
 * are one state - so it never re-expanded the room. A hub needs a flag per item, not a counter, and that is
 * written up in the script.
 *
 * <p>TWO BACKGROUNDS and no sprites, for the same reason as every story but Two of Everything: there is nobody
 * else in it to draw.
 */
public class NinetyDaysGame implements Game {

    static final String STORY = "stories/ninety-days.aside";

    @Override public String id() { return "ninety-days"; }

    @Override public String title() { return "Ninety Days"; }

    @Override public String blurb() {
        return "The last night in a signal box, and a bell code that is not in the book.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "ninety-days");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
