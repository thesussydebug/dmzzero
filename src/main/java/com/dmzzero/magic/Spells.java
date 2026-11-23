package com.dmzzero.magic;

import com.dmzzero.ZeroConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every Glind spell, in the order Second Function steps through them. */
public final class Spells {
    private static final Map<String, Spell> ALL = new LinkedHashMap<>();

    static {
        add(Telekinesis.SPELL);
        add(RockLift.SPELL);
        add(TelekineticWave.SPELL);
        add(Mend.SPELL);
    }

    private Spells() {
    }

    private static void add(Spell spell) {
        ALL.put(spell.id(), spell);
    }

    public static List<String> ids() {
        return List.copyOf(ALL.keySet());
    }

    public static Spell get(String id) {
        return id == null ? null : ALL.get(id);
    }

    /** Switched on, read the server's way on either side. */
    public static boolean on(String id) {
        Spell spell = get(id);
        return spell != null && ZeroConfig.read(spell.toggle(), false);
    }

    public static boolean anyOn() {
        return ALL.keySet().stream().anyMatch(Spells::on);
    }

    public static boolean holding(Entity entity) {
        return ALL.values().stream().anyMatch(s -> s.holds(entity));
    }

    public static Component name(String id) {
        Spell spell = get(id);
        return Component.translatableWithFallback("spell.dmzzero." + id, spell == null ? id : spell.english());
    }
}
