package com.dmzzero.magic;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.racial.RacialRegistry;
import com.dragonminez.common.racial.impl.GlindDivinity;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.skills.Skills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

// glinds know dmz's instant transmission at its top level. checked every few seconds, so a new character, a skill
// reset or a race change picks it up; levels already granted stay if the setting is turned off later
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class GlindTransmission {
    public static final String SKILL = "instant_transmission";
    private static final int EVERY = 40;

    private GlindTransmission() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER || event.player.tickCount % EVERY != 0) return;
        if (event.player instanceof ServerPlayer player) grant(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) grant(player);
    }

    /** Raises a Glind's Instant Transmission to DMZ's top level. True when it changed anything. */
    public static boolean grant(ServerPlayer player) {
        if (!ZeroConfig.SERVER.glindTransmission.get()) return false;
        StatsData data = SpellKit.stats(player);
        if (data == null || !data.getStatus().isHasCreatedCharacter()) return false;
        if (RacialRegistry.forPlayer(data).filter(a -> a instanceof GlindDivinity).isEmpty()) return false;
        Skills skills = data.getSkills();
        // dmz only works out a skill's top level once the player has an entry for it
        boolean had = skills.hasSkill(SKILL);
        if (!had) skills.setSkillLevel(SKILL, 0);
        int max = skills.getMaxSkillLevel(SKILL);
        if (max <= 0) {
            if (!had) skills.removeSkill(SKILL);
            return false;
        }
        if (skills.getSkillLevel(SKILL) >= max) return false;
        skills.setSkillLevel(SKILL, max);
        SpellKit.sync(player);
        return true;
    }
}
