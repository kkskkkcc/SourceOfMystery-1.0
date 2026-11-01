package com.sourceofmystery.entity;

import net.minecraft.util.Mth;

/** Client-only expression clock: natural, desynchronised blinks without AI work. */
public final class OutsiderFace {
    private int seed;
    private int nextBlink;
    private int blinkTick = -1;
    private float blink, previousBlink;
    private float smile, previousSmile;

    public OutsiderFace(int entityId) {
        seed = entityId * 1103515245 + 12345;
        nextBlink = 45 + nextRandom(80);
    }

    public void tick(boolean walking) {
        previousBlink = blink;
        previousSmile = smile;
        if (blinkTick >= 0) {
            blinkTick++;
            // 2 ticks closing, 2 ticks closed, 3 ticks opening.
            blink = blinkTick < 2 ? (blinkTick + 1) * .5F
                    : blinkTick < 4 ? 1.0F : Math.max(0, 1.0F - (blinkTick - 3) / 3.0F);
            if (blinkTick >= 7) {
                blinkTick = -1;
                blink = 0;
                nextBlink = 55 + nextRandom(105);
                // A small chance of a natural double blink.
                if (nextRandom(5) == 0) nextBlink = 5;
            }
        } else if (--nextBlink <= 0) {
            blinkTick = 0;
        }
        // A tiny resting smile keeps the face from looking frozen. It fades
        // while walking and is intentionally far below a full smile morph.
        float targetSmile = walking ? 0.0F : 0.045F;
        smile += (targetSmile - smile) * .12F;
    }

    private int nextRandom(int bound) {
        seed = seed * 1664525 + 1013904223;
        return Math.floorMod(seed >>> 1, bound);
    }

    public float blink(float partialTick) {
        return Mth.lerp(partialTick, previousBlink, blink);
    }

    public float smile(float partialTick) {
        return Mth.lerp(partialTick, previousSmile, smile);
    }
}
