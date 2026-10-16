package aside.games.uptheentry;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Up the Entry, as a game module.
 *
 * <p>A coal round in the last week before Christmas, a nineteen-year-old on his second winter, and three small
 * decisions that accumulate: which drop to take first with the light going, what to say when he asks whether leaving
 * two hundredweight in a doorway is all right, and how to get two hundredweight up a thirty-one-inch entry with eleven
 * steps and two turns when the cart cannot get near it.
 *
 * <p>THREE DECISION POINTS AND NINE OPTIONS, written because ten stories in a row before it had a single crux - and a
 * story with one decision has an inert middle. Four endings.
 */
public class UpTheEntryGame implements Game {

    static final String STORY = "stories/up-the-entry.aside";

    @Override public String id() { return "up-the-entry"; }

    @Override public String title() { return "Up the Entry"; }

    @Override public String blurb() {
        return "A coal round, three small decisions, and eleven steps in the fog.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "up-the-entry");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
