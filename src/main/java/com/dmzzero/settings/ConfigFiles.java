package com.dmzzero.settings;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

// dmzzero's toml files all live in config/dmzzero/; versions before 0.13 kept them elsewhere
public final class ConfigFiles {
    public static final String CLIENT = "dmzzero/client.toml";
    public static final String SERVER = "dmzzero/server.toml";
    /** Where 0.12 and older kept the client file, in config/. */
    static final String OLD_CLIENT = "dmzzero-client.toml";
    /** Where 0.12 and older kept the server file, in each world's serverconfig/. */
    public static final String OLD_SERVER = "dmzzero-server.toml";

    private ConfigFiles() {
    }

    /** Moves config/dmzzero-client.toml to config/dmzzero/client.toml, unless that file already exists. */
    public static boolean moveOldClientFile(Path configDir) throws IOException {
        Path old = configDir.resolve(OLD_CLIENT);
        Path now = configDir.resolve(CLIENT);
        if (!Files.isRegularFile(old) || Files.exists(now)) return false;
        Files.createDirectories(now.getParent());
        Files.move(old, now);
        return true;
    }

    /**
     * Copies a world's 0.12 file while every setting in the spec is still its default, so the first world started
     * after updating hands its settings over, in whichever launch that is. -1 when nothing was copied.
     */
    public static int copyIfUntouched(Path old, ForgeConfigSpec spec) throws IOException {
        if (!Files.isRegularFile(old)) return -1;
        for (SettingsTree.Item item : SettingsTree.of(spec)) {
            if (!Objects.equals(item.value().get(), item.value().getDefault())) return -1;
        }
        return copyFile(old, spec);
    }

    /** Reads a 0.12 toml file and copies its values with {@link #copyValues}. A file that isn't valid toml throws. */
    public static int copyFile(Path old, ForgeConfigSpec spec) throws IOException {
        try (Reader in = Files.newBufferedReader(old)) {
            return copyValues(TomlFormat.instance().createParser().parse(in), spec);
        } catch (ParsingException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** Sets each value in the old config that the spec accepts, and returns how many it set. */
    public static int copyValues(UnmodifiableConfig old, ForgeConfigSpec spec) {
        int copied = 0;
        for (SettingsTree.Item item : SettingsTree.of(spec)) {
            Object raw = old.get(item.path());
            ForgeConfigSpec.ValueSpec rule = spec.getSpec().get(item.path());
            if (raw == null || !rule.test(raw)) continue;
            Object value = asType(raw, item.type());
            if (value == null) continue;
            set(item.value(), value);
            copied++;
        }
        return copied;
    }

    // toml reads 4 as a whole number; a decimal setting needs it as 4.0
    private static Object asType(Object value, Class<?> type) {
        if (type == Double.class && value instanceof Number n) return n.doubleValue();
        if (type == Integer.class && (value instanceof Integer || value instanceof Long)) return ((Number) value).intValue();
        return type.isInstance(value) ? value : null;
    }

    @SuppressWarnings("unchecked")
    private static void set(ForgeConfigSpec.ConfigValue<?> value, Object v) {
        ((ForgeConfigSpec.ConfigValue<Object>) value).set(v);
    }
}
