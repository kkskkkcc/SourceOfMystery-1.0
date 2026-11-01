import com.sourceofmystery.entity.OutsiderLocomotion;

/** Standalone regression scenarios for the actual client clock implementation. */
public class LocomotionCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        OutsiderLocomotion state = new OutsiderLocomotion();
        for (int i = 0; i < 100; i++) state.tick(0, true);
        check(state.blend(1) == 0 && state.seconds(1) == 0, "idle must not drift");
        double min = 1, max = 0;
        for (int i = 0; i < 140; i++) {
            float before = state.seconds(1);
            // A remote mob receiving uneven movement updates at the same mean speed.
            state.tick(i % 2 == 0 ? 0.0645 : 0, true);
            double delta = state.seconds(1) - before;
            check(delta > 0, "missing packet froze the gait");
            if (i > 60) { min = Math.min(min, delta); max = Math.max(max, delta); }
            check(state.seconds(0.5F) >= state.seconds(0), "render interpolation moved backwards");
        }
        check(max/min < 1.5, "packet cadence still dominates playback");
        check(state.blend(1) > 0.99, "steady walk not reached");
        float blend = state.blend(1);
        state.tick(0, true);
        check(state.blend(1) >= blend - .2, "stop snapped to idle");
        for (int i = 0; i < 80; i++) state.tick(0, true);
        check(state.blend(1) == 0, "stop failed to settle");
        state.tick(10, true);
        check(state.blend(1) == 0, "teleport started locomotion");
        for (int i = 0; i < 40; i++) state.tick(.03225, true);
        check(state.blend(1) > .99, "restart failed");
        System.out.printf("PASS idle, packet jitter, stop, teleport, restart; steady phase ratio %.3f%n",max/min);
    }
}
