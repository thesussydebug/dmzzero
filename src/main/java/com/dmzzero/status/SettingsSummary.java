package com.dmzzero.status;

import com.dmzzero.settings.SettingsTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The on/off settings in a config (how many, which are off) and every setting that differs from its default. */
public record SettingsSummary(int switches, List<String> off, List<Changed> changed) {
    public record Changed(String path, String value, String fallback) {
    }

    public static SettingsSummary of(List<SettingsTree.Item> items) {
        int switches = 0;
        List<String> off = new ArrayList<>();
        List<Changed> changed = new ArrayList<>();
        for (SettingsTree.Item item : items) {
            Object value = item.value().get();
            Object fallback = item.value().getDefault();
            if (!Objects.equals(value, fallback)) changed.add(new Changed(item.path(), String.valueOf(value), String.valueOf(fallback)));
            if (!(value instanceof Boolean b)) continue;
            switches++;
            if (!b) off.add(item.path());
        }
        return new SettingsSummary(switches, List.copyOf(off), List.copyOf(changed));
    }
}
