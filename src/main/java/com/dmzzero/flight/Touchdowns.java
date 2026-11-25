package com.dmzzero.flight;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.flight.TouchdownMath.Event;
import com.dmzzero.flight.TouchdownMath.Feet;
import com.dmzzero.heal.WorldHealing;
import com.dmzzero.impact.ImpactWorld;
import com.dragonminez.common.init.MainGameRules;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// taking off fast throws up a ring of dust and blows the grass flat; a hard landing throws one up with a thud.
// the particles and sounds are vanilla's and dmz's, so players without dmz zero see and hear them too
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Touchdowns {
    private static final int MAX_FLATTENED = 12;
    private static final double GROUND_GAP = 0.06;
    private static final Map<UUID, Feet> FEET = new HashMap<>();
    private static int takeoffs;
    private static int landings;
    private static int flattened;
    private static Vec3 lastAt = Vec3.ZERO;

    private Touchdowns() {
    }

    public static boolean enabled() {
        return ZeroConfig.SERVER.takeoffs.get() || ZeroConfig.SERVER.landings.get();
    }

    // the on-ground flag comes from the client's packets, and the server's own tick of a dmz flyer (no gravity) clears it
    // before the end of the tick; a block right under the feet is what the server can check itself
    public static boolean grounded(ServerPlayer player) {
        return player.onGround() || !player.level().noCollision(player, player.getBoundingBox().move(0, -GROUND_GAP, 0));
    }

    // the self-test moves fake players through here by hand
    public static Event step(ServerPlayer player, Vec3 at, boolean onGround, boolean flySkill, long now) {
        if (!player.isAlive() || !TouchdownMath.tracks(player.isSpectator(), player.isPassenger(), player.isInWater(), player.getAbilities().flying, flySkill)) {
            FEET.remove(player.getUUID());
            return Event.NONE;
        }
        Feet feet = FEET.computeIfAbsent(player.getUUID(), id -> new Feet());
        double takeoffSpeed = ZeroConfig.SERVER.takeoffSpeed.get(), landingSpeed = ZeroConfig.SERVER.landingSpeed.get();
        Event event = feet.step(at.x, at.y, at.z, onGround, flySkill, now, takeoffSpeed, landingSpeed);
        if (event == Event.TAKEOFF && ZeroConfig.SERVER.takeoffs.get()) {
            takeoff(player, new Vec3(feet.groundX(), feet.groundY(), feet.groundZ()), TouchdownMath.strength(feet.speed(), takeoffSpeed));
        } else if (event == Event.LANDING && ZeroConfig.SERVER.landings.get()) {
            land(player, at, TouchdownMath.strength(feet.speed(), landingSpeed));
        }
        return event;
    }

    static void takeoff(ServerPlayer player, Vec3 ground, float strength) {
        ServerLevel level = player.serverLevel();
        takeoffs++;
        lastAt = ground;
        ring(level, ground, strength, groundUnder(level, ground));
        level.playSound(null, ground.x, ground.y, ground.z, MainSounds.ZANZOKEN.get(), SoundSource.PLAYERS, 0.4f + 0.4f * strength, 0.8f);
        if (ImpactWorld.destructionAllowed(player)) {
            double radius = TouchdownMath.ringRadius(strength) * 0.8;
            int n = flatten(level, player, ground, radius);
            flattened += n;
            DMZZero.LOG.debug("takeoff by {} at {}: strength {}, blew away {} within {}", player.getGameProfile().getName(), ground,
                    String.format("%.2f", strength), n, String.format("%.2f", radius));
        }
    }

    static void land(ServerPlayer player, Vec3 at, float strength) {
        ServerLevel level = player.serverLevel();
        landings++;
        lastAt = at;
        BlockState under = groundUnder(level, at);
        ring(level, at, strength, under);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.7f + 0.5f * strength, 0.7f);
        if (!under.isAir()) {
            level.playSound(null, at.x, at.y, at.z, under.getSoundType(level, BlockPos.containing(at.x, at.y - 0.2, at.z), player).getFallSound(),
                    SoundSource.PLAYERS, 0.8f + 0.4f * strength, 0.6f);
        }
        if (strength >= 0.75f) level.playSound(null, at.x, at.y, at.z, MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.PLAYERS, 0.35f * strength, 0.7f);
    }

    // dust rolling outward along the ground, with bits of whatever was stood on
    private static void ring(ServerLevel level, Vec3 at, float strength, BlockState under) {
        double reach = TouchdownMath.ringRadius(strength);
        int n = 14 + Math.round(18 * strength);
        double out = reach / 9.0;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + level.random.nextDouble() * 0.2;
            double cos = Math.cos(a), sin = Math.sin(a);
            level.sendParticles(MainParticles.DUST.get(), at.x + cos * 0.5, at.y + 0.1, at.z + sin * 0.5, 0, cos * out, 0.015, sin * out, 1.0);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.POOF, at.x + cos * 0.7, at.y + 0.15, at.z + sin * 0.7, 0, cos * out * 0.6, 0.02, sin * out * 0.6, 1.0);
        }
        if (!under.isAir() && under.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, 6 + Math.round(14 * strength), reach * 0.3, 0.05, reach * 0.3, 0.15);
        }
    }

    private static BlockState groundUnder(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.x, at.y - 0.2, at.z);
        return level.isLoaded(pos) ? level.getBlockState(pos) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    // grass, flowers and snow in a circle at foot height, closest first. two-block plants are left alone: healing puts back one block
    static int flatten(ServerLevel level, ServerPlayer player, Vec3 ground, double radius) {
        int r = Mth.ceil(radius);
        double r2 = radius * radius;
        BlockPos center = BlockPos.containing(ground);
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                pos.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (level.isLoaded(pos) && blowsAway(level.getBlockState(pos))) found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        int n = 0;
        for (BlockPos p : found) {
            if (n >= MAX_FLATTENED) break;
            BlockState state = level.getBlockState(p);
            if (!MainGameRules.canKiGrief(level, p, player)) continue;
            if (level.destroyBlock(p, false, player)) {
                WorldHealing.record(level, p, state, level.getBlockState(p), false, player.getUUID());
                n++;
            }
        }
        return n;
    }

    static boolean blowsAway(BlockState state) {
        if (!ImpactWorld.fragile(state) || state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES)) return false;
        return !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !state.is(BlockTags.LEAVES);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FEET.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        FEET.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        FEET.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FEET.clear();
    }

    public static void forget(UUID id) {
        FEET.remove(id);
    }

    public static int takeoffs() {
        return takeoffs;
    }

    public static int landings() {
        return landings;
    }

    public static int flattened() {
        return flattened;
    }

    public static Vec3 lastAt() {
        return lastAt;
    }
}
