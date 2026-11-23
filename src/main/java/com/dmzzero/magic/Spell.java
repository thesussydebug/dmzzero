package com.dmzzero.magic;

import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * One Glind power. A new one is a class like {@link Telekinesis}, a line in {@link Spells}, a server.toml section
 * named after its id with an "enabled" switch, and the lang key spell.dmzzero.&lt;id&gt;.
 */
public interface Spell {
    /** Its id: the server.toml section and the lang key spell.dmzzero.&lt;id&gt;. */
    String id();

    /** The English name, used when the lang file has none. */
    String english();

    /** Its server.toml switch. */
    ForgeConfigSpec.BooleanValue toggle();

    /** Server side. "ok", or why not; the spell tells the caster either way. */
    String cast(ServerPlayer caster, StatsData data, long now);

    /** Whether the caster has it going, so switching to another spell ends it. */
    default boolean busy(ServerPlayer caster) {
        return false;
    }

    /** The caster let go of the cast key. */
    default void release(ServerPlayer caster) {
    }

    /** Ends it early, when the caster switches to another spell. */
    default void end(ServerPlayer caster, long now) {
    }

    /** Whether this spell has hold of that entity. Someone held can't cast. */
    default boolean holds(Entity entity) {
        return false;
    }
}
