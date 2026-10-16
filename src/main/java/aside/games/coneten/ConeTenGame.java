package aside.games.coneten;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Cone Ten, as a game module.
 *
 * <p>An updraught kiln older than the potter, a firing of fourteen hours and a ton and a half of wood, and four big
 * jars that are the whole reason for it. Two ways up, a wind that arrives at four exactly as forecast, and a decision
 * about whether to hold the rate, let it drop, or soak it - each with a different consequence in the glaze a week
 * later, and one of them branching on the ramp chosen at midnight.
 *
 * <p>THE DENSEST STORY HERE: 122 words per scene against a library median of 82. Written to be short and dense, and
 * the measurement says it came out dense and ordinary-length, which is what it is.
 */
public class ConeTenGame implements Game {

    static final String STORY = "stories/cone-ten.aside";

    @Override public String id() { return "cone-ten"; }

    @Override public String title() { return "Cone Ten"; }

    @Override public String blurb() {
        return "A kiln firing, one night of wind, and a decision that ruins the pots if taken early.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "cone-ten");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
