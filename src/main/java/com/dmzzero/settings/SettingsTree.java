package com.dmzzero.settings;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// every setting in a config spec, in the order the config file lists them, with what the screen needs to edit it
public final class SettingsTree {
    public record Item(String section, String key, String path, Class<?> type, Object min, Object max, String comment,
                       ForgeConfigSpec.ConfigValue<?> value) {
    }

    private SettingsTree() {
    }

    public static List<Item> of(ForgeConfigSpec spec) {
        List<Item> out = new ArrayList<>();
        walk(spec.getSpec(), spec.getValues(), "", out);
        return out;
    }

    private static void walk(UnmodifiableConfig specs, UnmodifiableConfig values, String prefix, List<Item> out) {
        for (Map.Entry<String, Object> e : specs.valueMap().entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object spec = e.getValue();
            Object value = values.get(e.getKey());
            if (spec instanceof UnmodifiableConfig sub && value instanceof UnmodifiableConfig subValues) {
                walk(sub, subValues, path, out);
            } else if (spec instanceof ForgeConfigSpec.ValueSpec vs && value instanceof ForgeConfigSpec.ConfigValue<?> cv) {
                ForgeConfigSpec.Range<?> range = vs.getRange();
                Class<?> type = vs.getDefault() == null ? Object.class : vs.getDefault().getClass();
                String section = prefix.isEmpty() ? "general" : prefix;
                out.add(new Item(section, e.getKey(), path, type, range == null ? null : range.getMin(), range == null ? null : range.getMax(),
                        comment(vs.getComment()), cv));
            }
        }
    }

    // the config file's comment without the range line forge adds; the screen shows the range itself
    static String comment(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("Range:") || l.startsWith("Allowed Values:")) continue;
            if (out.length() > 0) out.append(' ');
            out.append(l);
        }
        return out.toString();
    }
}
