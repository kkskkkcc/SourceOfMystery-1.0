package com.sourceofmystery.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sourceofmystery.SourceOfMystery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * First-person Tang Dao clips from {@code first_person/tang_dao.json}, written by
 * {@code tools/tang_dao/author.py}. Each key is one row of channels (see {@link TangDaoRig});
 * sampling runs a Hermite curve through the keys with Catmull-Rom tangents.
 */
final class TangDaoClips {
    static final ResourceLocation RESOURCE = new ResourceLocation(SourceOfMystery.MOD_ID, "first_person/tang_dao.json");
    private static final String[][] CHANNELS = {
            {"hand", "3"}, {"blade", "3"}, {"edge", "3"}, {"dev", "1"}, {"off_hand", "3"}, {"off_from", "3"},
            {"off_roll", "1"}, {"reach", "1"}, {"along", "1"}, {"travel", "1"}, {"drop", "1"}};
    static final int WIDTH = 21;

    record Rig(float bladeScale, float guard, float fistFront, float tip, float mouth, float dropX, float dropY, float dropZ) {}

    static final class Clip {
        final String name;
        final int impact;
        final int chain;
        final int anchor;
        final boolean reachBlade;
        final float[] times;
        final float[][] keys;

        private Clip(String name, JsonObject json) {
            this.name = name;
            impact = json.has("impact") ? json.get("impact").getAsInt() : -1;
            chain = json.has("chain") ? json.get("chain").getAsInt() : -1;
            anchor = json.has("scabbard_anchor") ? json.get("scabbard_anchor").getAsInt() : -1;
            reachBlade = json.has("reach_to") && "blade".equals(json.get("reach_to").getAsString());
            JsonArray rows = json.getAsJsonArray("keys");
            times = new float[rows.size()];
            keys = new float[rows.size()][WIDTH];
            for (int i = 0; i < rows.size(); i++) {
                JsonObject key = rows.get(i).getAsJsonObject();
                times[i] = key.get("t").getAsFloat();
                if (i > 0 && times[i] <= times[i - 1]) throw new IllegalArgumentException(name + ": unordered keys");
                int column = 0;
                for (String[] channel : CHANNELS) {
                    if (channel[1].equals("1")) {
                        keys[i][column++] = key.get(channel[0]).getAsFloat();
                    } else {
                        JsonArray values = key.getAsJsonArray(channel[0]);
                        for (int j = 0; j < 3; j++) keys[i][column++] = values.get(j).getAsFloat();
                    }
                }
            }
        }

        boolean attack() { return impact >= 0; }
        int duration() { return Math.round(times[times.length - 1]); }

        /** Channels at time t; {@code start} replaces the first key when the clip continues another. */
        float[] sample(float t, float[] start, float[] out) {
            int last = times.length - 1;
            if (t <= times[0] || last == 0) return copy(start != null ? start : keys[0], out);
            if (t >= times[last]) return copy(keys[last], out);
            int i = 0;
            while (t >= times[i + 1]) i++;
            float span = times[i + 1] - times[i];
            float u = (t - times[i]) / span;
            float h00 = 2*u*u*u - 3*u*u + 1, h10 = u*u*u - 2*u*u + u, h01 = -2*u*u*u + 3*u*u, h11 = u*u*u - u*u;
            for (int c = 0; c < WIDTH; c++) {
                float m0 = tangent(i, c, start) * span, m1 = tangent(i + 1, c, start) * span;
                out[c] = h00*value(i, c, start) + h10*m0 + h01*value(i + 1, c, start) + h11*m1;
            }
            return out;
        }

        private float value(int i, int c, float[] start) {
            return i == 0 && start != null ? start[c] : keys[i][c];
        }

        private float tangent(int i, int c, float[] start) {
            if (i == 0 || i == times.length - 1) return 0;
            return (value(i + 1, c, start) - value(i - 1, c, start)) / (times[i + 1] - times[i - 1]);
        }

        private static float[] copy(float[] from, float[] out) {
            System.arraycopy(from, 0, out, 0, WIDTH);
            return out;
        }
    }

    private static Rig rig;
    private static Map<String, Clip> clips = Map.of();
    private static List<String> combo = List.of();

    private TangDaoClips() {}

    static void load(ResourceManager resources) {
        try (var reader = resources.openAsReader(RESOURCE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported version");
            JsonObject r = root.getAsJsonObject("rig");
            JsonArray drop = r.getAsJsonArray("scabbard_drop");
            Rig parsedRig = new Rig(r.get("blade_scale").getAsFloat(), r.get("guard").getAsFloat(),
                    r.get("fist_front").getAsFloat(), r.get("tip").getAsFloat(), r.get("scabbard_mouth").getAsFloat(),
                    drop.get(0).getAsFloat(), drop.get(1).getAsFloat(), drop.get(2).getAsFloat());
            Map<String, Clip> parsed = new HashMap<>();
            for (var entry : root.getAsJsonObject("clips").entrySet()) {
                parsed.put(entry.getKey(), new Clip(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
            for (String required : new String[]{"ready", "sheathed", "draw", "sheathe", "inspect"}) {
                if (!parsed.containsKey(required)) throw new IllegalArgumentException("Missing clip " + required);
            }
            List<String> order = root.getAsJsonArray("combo").asList().stream().map(e -> e.getAsString()).toList();
            if (order.isEmpty() || !order.stream().allMatch(name -> parsed.containsKey(name) && parsed.get(name).attack())) {
                throw new IllegalArgumentException("Invalid combo");
            }
            rig = parsedRig;
            clips = Map.copyOf(parsed);
            combo = order;
        } catch (Exception ex) {
            rig = null;
            clips = Map.of();
            combo = List.of();
            SourceOfMystery.LOGGER.error("Cannot load the Tang Dao first-person clips {}", RESOURCE, ex);
        }
    }

    static boolean ready() { return rig != null; }
    static Rig rig() { return rig; }
    static Clip clip(String name) { return clips.get(name); }
    static List<String> combo() { return combo; }
}
