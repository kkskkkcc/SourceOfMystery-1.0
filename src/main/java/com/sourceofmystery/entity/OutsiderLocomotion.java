package com.sourceofmystery.entity;

/** Client animation clock. Separate from AI so packet cadence never stops a stride. */
public final class OutsiderLocomotion {
    // Matches smooth_gait.py: 1.2s, 60% stance, +/-2.15 PMX units, scale .108.
    private static final double REFERENCE_SPEED = 0.645;
    private float blend, previousBlend, seconds, previousSeconds;
    private double filteredSpeed;
    private boolean moving;

    public void tick(double distance, boolean grounded) {
        previousBlend = blend;
        previousSeconds = seconds;
        // Ignore teleports rather than accelerating the legs for one frame.
        double measured = grounded && distance < 0.75 ? distance * 20 : 0;
        filteredSpeed += (measured - filteredSpeed) * (1 - Math.exp(-0.05 / 0.14));
        if (filteredSpeed > 0.09) moving = true;
        else if (filteredSpeed < 0.035) moving = false;
        float target = moving ? 1 : 0;
        float response = (float) (1 - Math.exp(-0.05 / (moving ? 0.18 : 0.24)));
        blend += (target - blend) * response;
        // Keep completing the current foot swing while easing into idle.
        if (blend > 0.005F) {
            double playback = Math.max(0.3, Math.min(2.0, filteredSpeed / REFERENCE_SPEED));
            seconds += (float) (0.05 * playback);
        }
        // Reset only once fully at rest. External motions have different loop
        // lengths, so never wrap the clock using the bundled clip's period.
        if (blend < 0.005F && !moving) {
            blend = 0;
            seconds = 0;
            previousSeconds = 0;
        }
    }

    public float blend(float partialTick) {
        return previousBlend + (blend - previousBlend) * partialTick;
    }

    public float seconds(float partialTick) {
        return previousSeconds + (seconds - previousSeconds) * partialTick;
    }
}
