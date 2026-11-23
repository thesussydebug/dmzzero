package com.dmzzero.potential;

import com.dmzzero.DMZZero;
import com.dmzzero.Titles;
import com.dmzzero.ZeroConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Set;

// once per character, the first master who draws out your hidden power raises your potential by one
public final class Awakening {
    public static final Set<String> MASTERS = Set.of("guru", "oldkai", "babidi");
    private static final String ROOT = "dmzzero";
    private static final String AWAKENED = "awakened";

    private Awakening() {
    }

    public static int raised(int potential) {
        return Math.min(Potential.MAX, potential + 1);
    }

    public static boolean canAwaken(boolean enabled, boolean awakened, int potential) {
        return enabled && !awakened && Potential.isValid(potential);
    }

    public static boolean awakened(CompoundTag persisted) {
        return persisted.getCompound(ROOT).getBoolean(AWAKENED);
    }

    public static void setAwakened(CompoundTag persisted, boolean awakened) {
        CompoundTag root = persisted.getCompound(ROOT);
        if (awakened) root.putBoolean(AWAKENED, true);
        else root.remove(AWAKENED);
        persisted.put(ROOT, root);
    }

    public static boolean awakened(Player player) {
        return awakened(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG));
    }

    public static void setAwakened(Player player, boolean awakened) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        setAwakened(persisted, awakened);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static boolean enabled() {
        return ZeroConfig.read(ZeroConfig.SERVER.awakening, true);
    }

    // called once dmz has finished the master's service
    public static boolean awaken(ServerPlayer player, String master) {
        if (player == null || !MASTERS.contains(master)) return false;
        PotentialService.ensureRolled(player);
        int before = PotentialData.of(player);
        if (!canAwaken(enabled(), awakened(player), before)) return false;
        setAwakened(player, true);
        int after = raised(before);
        if (after != before) PotentialService.set(player, after);
        DMZZero.LOG.info("{} awakened by {}: Potential {} -> {}", player.getGameProfile().getName(), master, before, after);
        celebrate(player, master, before, after);
        return true;
    }

    private static void celebrate(ServerPlayer player, String master, int before, int after) {
        Component who = Component.translatableWithFallback("entity.dragonminez.master_" + master, name(master));
        if (after > before) {
            player.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.awakening.raised",
                    "%s draws out your hidden power. Your Potential rises from %s to %s.",
                    who, PotentialService.number(before), PotentialService.number(after)));
            Titles.show(player,
                    Component.translatableWithFallback("title.dmzzero.awakening", "Hidden Power Awakened").withStyle(ChatFormatting.GOLD),
                    Component.translatableWithFallback("title.dmzzero.awakening.sub", "Potential %s → %s",
                            PotentialService.number(before), PotentialService.number(after)), 10, 60, 20);
        } else {
            player.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.awakening.peak",
                    "%s draws out your hidden power, but your Potential is already at its peak.", who));
        }
        ServerLevel level = player.serverLevel();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + i * 0.4, z, 6, 0.25, 0.1, 0.25, 0.02);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1, z, 60, 0.5, 0.8, 0.5, 0.4);
        level.sendParticles(ParticleTypes.FLASH, x, y + 1, z, 1, 0, 0, 0, 0);
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    private static String name(String master) {
        return switch (master.toLowerCase(Locale.ROOT)) {
            case "guru" -> "Guru";
            case "oldkai" -> "Elder Kai";
            case "babidi" -> "Babidi";
            default -> master;
        };
    }
}
