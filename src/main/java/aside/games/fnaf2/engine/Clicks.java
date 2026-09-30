package aside.games.fnaf2.engine;

import aside.games.fnaf2.MouseMap;

/**
 * What a click does to the game.
 *
 * Split out of the screen so the wiring can be exercised without a display.
 * The geometry is checked by {@link MouseMap} and the effect is checked here,
 * and between the two of them nothing about the mouse is left to a
 * screenshot -- which matters, because a screenshot of a click that did
 * nothing looks exactly like a screenshot of a click that was never made.
 *
 * It lives in the engine package rather than beside the screen so that the
 * "no UI dependency" rule is structural: nothing here can reach JavaFX, so
 * SelfTest can run it with `java -cp classes` and no module path.
 */
public final class Clicks {

    private Clicks() {}

    /**
     * Apply one click.
     *
     * Winding is a toggle rather than a hold. Winding is gated in
     * {@link Game#update} on being on CAM 11 with the monitor up and the mask
     * off, so a toggle costs exactly what a hold costs -- you still cannot
     * wind and watch the office -- and it does not ask a hand to stay down
     * for five seconds. {@link Game#toggleCamera} and {@link Game#toggleMask}
     * clear the flag when they take the camera away, so it cannot be left on
     * and resume by itself.
     */
    public static void apply(Game game, MouseMap.Hit h) {
        if (game == null || h == null) return;
        if (game.status != Game.Status.PLAYING) return;
        switch (h.kind()) {
            case HALL    -> game.toggleHallLight();
            case VENT_L  -> game.toggleVentLLight();
            case VENT_R  -> game.toggleVentRLight();
            case MASK    -> game.toggleMask();
            case MONITOR -> game.toggleCamera();
            case WIND    -> game.setWinding(!game.winding);
            case CAM     -> game.setCam(h.cam());
            case NONE    -> { }
        }
    }
}
