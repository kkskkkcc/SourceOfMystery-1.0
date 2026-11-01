package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Sampled local poses, with IK and append transforms baked against this PMX rig. */
final class OutsiderMotion {
    private final int bones;
    private final int frames;
    private final int fps;
    private final float[] values;
    private final Quaternionf nextRotation = new Quaternionf();

    private OutsiderMotion(int bones, int frames, int fps, float[] values) {
        this.bones = bones;
        this.frames = frames;
        this.fps = fps;
        this.values = values;
    }

    static OutsiderMotion load(String name, int expectedBones) {
        Path local = FMLPaths.CONFIGDIR.get().resolve("sourceofmystery/outsider/" + name + ".bin");
        if (Files.isRegularFile(local)) {
            try (DataInputStream in = new DataInputStream(Files.newInputStream(local))) {
                OutsiderMotion motion = read(in, expectedBones);
                SourceOfMystery.LOGGER.info("Outsider: loaded local {} motion from {}", name, local);
                return motion;
            } catch (IOException e) {
                SourceOfMystery.LOGGER.warn("Outsider: invalid local motion {}, using bundled motion", local, e);
            }
        }
        ResourceLocation path = new ResourceLocation(SourceOfMystery.MOD_ID,
                "animations/entity/outsider/" + name + ".bin");
        try (DataInputStream in = new DataInputStream(Minecraft.getInstance()
                .getResourceManager().getResourceOrThrow(path).open())) {
            return read(in, expectedBones);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + path, e);
        }
    }

    private static OutsiderMotion read(DataInputStream in, int expectedBones) throws IOException {
        if (readInt(in) != 0x4F414E31 || readInt(in) != 3) {
            throw new IOException("Invalid motion header");
        }
        int bones = readInt(in), frames = readInt(in), fps = readInt(in);
        if (bones != expectedBones || frames < 2 || frames > 10000 || fps <= 0 || fps > 240) {
            throw new IOException("Invalid motion dimensions");
        }
        float[] values = new float[bones * frames * 7];
        for (int i = 0; i < values.length; i++) {
            values[i] = Float.intBitsToFloat(readInt(in));
            if (!Float.isFinite(values[i])) throw new IOException("Non-finite motion value");
        }
        if (in.read() != -1) throw new IOException("Trailing motion data");
        return new OutsiderMotion(bones, frames, fps, values);
    }

    private static int readInt(DataInputStream in) throws IOException {
        return Integer.reverseBytes(in.readInt());
    }

    void sample(int bone, float seconds, Vector3f position, Quaternionf rotation) {
        sample(bone, seconds, true, position, rotation);
    }

    void sample(int bone, float seconds, boolean loop, Vector3f position, Quaternionf rotation) {
        float frame = Math.max(0, seconds) * fps;
        frame = loop ? frame % (frames - 1) : Math.min(frame, frames - 1);
        int a = (int) frame;
        float fraction = frame - a;
        int offset = (a * bones + bone) * 7;
        int next = (Math.min(a + 1, frames - 1) * bones + bone) * 7;
        position.set(values[offset], values[offset + 1], values[offset + 2]);
        position.x += (values[next] - position.x) * fraction;
        position.y += (values[next + 1] - position.y) * fraction;
        position.z += (values[next + 2] - position.z) * fraction;
        rotation.set(values[offset + 3], values[offset + 4], values[offset + 5], values[offset + 6]);
        nextRotation.set(values[next + 3], values[next + 4], values[next + 5], values[next + 6]);
        rotation.slerp(nextRotation, fraction).normalize();
    }
}
