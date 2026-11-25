package com.dmzzero.beam;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.heal.WorldHealing;
import com.dmzzero.impact.ImpactWorld;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainGameRules;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// piccolo's special beam cannon bores through what it hits instead of bursting on it. dmz's laser already hurts everyone
// along its length; when it reaches a block it calls explodeAndDie, and the mixin there asks this class first
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class CannonDrill {
    private static final Map<UUID, Integer> USED = new HashMap<>();
    private static final int COST = TickCost.id("beams.cannon", USED::size);
    // the hit point sits on the block's face; this puts the walk's start inside the block
    private static final double NUDGE = 1.0E-3;
    private static int drilled;

    private CannonDrill() {
    }

    /** True when the beam bored on through, so DMZ's explosion is skipped and the beam keeps growing next tick. */
    public static boolean onBlockHit(KiLaserEntity laser, Vec3 hit) {
        if (!(laser.level() instanceof ServerLevel level) || !ZeroConfig.SERVER.cannonDrill.get()) return false;
        if (!DrillMath.drills(laser.getTechniqueId()) || !(laser.getOwner() instanceof ServerPlayer player)) return false;
        if (!ImpactWorld.destructionAllowed(player)) return false;
        long start = TickCost.start();
        try {
            int used = USED.getOrDefault(laser.getUUID(), 0);
            int left = ZeroConfig.SERVER.cannonDepth.get() - used;
            if (left <= 0) return false;
            Vec3 dir = Vec3.directionFromRotation(laser.getFixedPitch(), laser.getFixedYaw());
            int n = bore(level, player, hit.add(dir.scale(NUDGE)), dir, left);
            if (n > 0) USED.put(laser.getUUID(), used + n);
            return n > 0;
        } finally {
            TickCost.add(COST, start);
        }
    }

    /** Breaks up to max blocks along the line, through one solid run only. Stops at the first block that won't give. */
    public static int bore(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 dir, int max) {
        double hardest = ZeroConfig.SERVER.cannonHardness.get();
        List<int[]> cells = DrillMath.cells(from.x, from.y, from.z, dir.x, dir.y, dir.z, DrillMath.MAX_CELLS);
        int n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] c : cells) {
            if (n >= max) break;
            pos.set(c[0], c[1], c[2]);
            if (!level.isLoaded(pos)) break;
            BlockState state = level.getBlockState(pos);
            if (state.getCollisionShape(level, pos).isEmpty()) {
                // the beam already passes through air, plants and water; reaching open space ends this tunnel
                if (n > 0) break;
                continue;
            }
            if (state.hasBlockEntity() || !DrillMath.breaks(state.getDestroySpeed(level, pos), hardest)
                    || !MainGameRules.canKiGrief(level, pos, player)) break;
            BlockPos at = pos.immutable();
            if (!level.destroyBlock(at, false, player)) break;
            WorldHealing.record(level, at, state, level.getBlockState(at), true, player.getUUID());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    6, 0.25, 0.25, 0.25, 0.05);
            if (n == 0) level.playSound(null, at, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 0.6f);
            n++;
        }
        drilled += n;
        return n;
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof KiLaserEntity) USED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        USED.clear();
    }

    public static int drilled() {
        return drilled;
    }
}
