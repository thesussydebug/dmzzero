package com.dmzzero.impact;

import com.dmzzero.ZeroConfig;
import com.dmzzero.heal.WorldHealing;
import com.dragonminez.common.init.MainGameRules;
import com.dragonminez.common.init.MainParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ImpactWorld {
    // how many water columns have sprayed, read by the dev tests
    private static int waterHits;

    private ImpactWorld() {
    }

    // top of the ground in this column, searched from topY down
    public static BlockPos groundAt(ServerLevel level, int x, int topY, int z, int depth) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, topY, z);
        for (int i = 0; i <= depth; i++, pos.move(Direction.DOWN)) {
            if (!level.isLoaded(pos)) return null;
            BlockState state = level.getBlockState(pos);
            if (!state.isFaceSturdy(level, pos, Direction.UP)) continue;
            BlockPos above = pos.above();
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) continue;
            return pos.immutable();
        }
        return null;
    }

    public static BlockPos groundNear(ServerLevel level, ServerPlayer player, double minRadius, double maxRadius) {
        return groundNear(level, player.position(), minRadius, maxRadius);
    }

    public static BlockPos groundNear(ServerLevel level, Vec3 center, double minRadius, double maxRadius) {
        return groundNear(level, center, minRadius, maxRadius, 5);
    }

    public static BlockPos groundNear(ServerLevel level, Vec3 center, double minRadius, double maxRadius, int depth) {
        RandomSource random = level.random;
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = minRadius + random.nextDouble() * (maxRadius - minRadius);
        int x = Mth.floor(center.x + Math.cos(angle) * dist);
        int z = Mth.floor(center.z + Math.sin(angle) * dist);
        BlockPos ground = groundAt(level, x, Mth.floor(center.y + 0.01) + 1, z, depth);
        if (ground == null) return null;
        BlockState state = level.getBlockState(ground);
        if (state.hasBlockEntity() || state.getRenderShape() != RenderShape.MODEL || state.getDestroySpeed(level, ground) < 0) return null;
        return ground;
    }

    public static boolean strikeLightning(ServerLevel level, ServerPlayer player, int tier) {
        return strikeLightning(level, player.position(), tier);
    }

    // visual only: no fire, no damage, and never on copper or rods since even a visual bolt changes those
    public static boolean strikeLightning(ServerLevel level, Vec3 center, int tier) {
        RandomSource random = level.random;
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 3 + tier + random.nextDouble() * 4;
            int x = Mth.floor(center.x + Math.cos(angle) * dist);
            int z = Mth.floor(center.z + Math.sin(angle) * dist);
            BlockPos ground = groundAt(level, x, Mth.floor(center.y) + 3, z, 9);
            if (ground == null) continue;
            BlockState state = level.getBlockState(ground);
            if (state.is(Blocks.LIGHTNING_ROD) || state.getBlock() instanceof WeatheringCopper) continue;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) return false;
            bolt.setVisualOnly(true);
            bolt.moveTo(x + 0.5, ground.getY() + 1, z + 0.5);
            level.addFreshEntity(bolt);
            return true;
        }
        return false;
    }

    public static boolean fragile(BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        if (state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES)) return true;
        if (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS)) return true;
        return state.is(BlockTags.REPLACEABLE) && !state.is(BlockTags.FIRE) && !state.is(Blocks.LIGHT) && !state.is(Blocks.STRUCTURE_VOID);
    }

    public static boolean destructionAllowed(ServerPlayer player) {
        return ZeroConfig.SERVER.destruction.get() && !ImpactData.destructionOff(player)
                && player.mayBuild() && !player.isSpectator();
    }

    private static boolean breakFragile(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!fragile(state) || !MainGameRules.canKiGrief(level, pos, player)) return false;
        boolean broken = level.destroyBlock(pos, false, player);
        if (broken) WorldHealing.record(level, pos, state, level.getBlockState(pos), false);
        return broken;
    }

    public static int scatter(ServerLevel level, ServerPlayer player, double radius, int samples, int maxBreaks) {
        return scatter(level, player, player.position().add(0, 1.0, 0), radius, samples, maxBreaks);
    }

    // random spots so things give way a few at a time; the player is who the grief rules are checked for
    public static int scatter(ServerLevel level, ServerPlayer player, Vec3 center, double radius, int samples, int maxBreaks) {
        if (radius < 1.0) return 0;
        RandomSource random = level.random;
        int broken = 0;
        for (int i = 0; i < samples && broken < maxBreaks; i++) {
            double dx = (random.nextDouble() * 2 - 1) * radius;
            double dy = (random.nextDouble() * 2 - 1) * radius;
            double dz = (random.nextDouble() * 2 - 1) * radius;
            if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
            BlockPos pos = BlockPos.containing(center.x + dx, center.y + dy, center.z + dz);
            if (level.isLoaded(pos) && breakFragile(level, player, pos)) broken++;
        }
        return broken;
    }

    public static int shatterAll(ServerLevel level, ServerPlayer player, double radius, int cap) {
        return shatterAll(level, player, player.position().add(0, 1.0, 0), radius, cap);
    }

    // everything fragile in range, closest first
    public static int shatterAll(ServerLevel level, ServerPlayer player, Vec3 at, double radius, int cap) {
        BlockPos center = BlockPos.containing(at);
        int r = Mth.ceil(radius);
        double r2 = radius * radius;
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isLoaded(pos) && fragile(level.getBlockState(pos))) found.add(pos.immutable());
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        int broken = 0;
        for (BlockPos p : found) {
            if (broken >= cap) break;
            if (breakFragile(level, player, p)) broken++;
        }
        return broken;
    }

    public static boolean degrade(ServerLevel level, ServerPlayer player, BlockPos pos) {
        // a crack can outlast the player's stay nearby; reading an unloaded spot would load or generate its chunk
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        String next = Degrade.next(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (next == null) return false;
        ResourceLocation id = ResourceLocation.tryParse(next);
        Block block = id != null ? BuiltInRegistries.BLOCK.get(id) : Blocks.AIR;
        if (block == Blocks.AIR || !MainGameRules.canKiGrief(level, pos, player)) return false;
        level.levelEvent(2001, pos, Block.getId(state));
        BlockState after = block.defaultBlockState();
        boolean changed = level.setBlock(pos, after, Block.UPDATE_ALL);
        if (changed) WorldHealing.record(level, pos, state, after, false);
        return changed;
    }

    public static void shockwave(ServerLevel level, ServerPlayer player, ImpactProfile p) {
        shockwave(level, player.position().add(0, 0.15, 0), p);
    }

    public static void shockwave(ServerLevel level, Vec3 at, ImpactProfile p) {
        double x = at.x, y = at.y, z = at.z;
        int tier = p.tier();
        int n = 20 + 8 * tier;
        double speed = 0.25 + 0.07 * tier;
        ParticleOptions ring = p.has(ImpactProfile.DARK) ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double cos = Math.cos(a), sin = Math.sin(a);
            level.sendParticles(ring, x + cos * 0.6, y, z + sin * 0.6, 0, cos, 0.02, sin, speed);
        }
        double spread = ImpactMath.shockwaveRadius(tier) * 0.3;
        level.sendParticles(MainParticles.DUST.get(), x, y, z, 20 + 10 * tier, spread, 0.2, spread, 0.1);
        if (tier >= 4) level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.5, z, tier, 1.5, 0.4, 1.5, 0.0);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.3f + 0.12f * tier, 1.5f - 0.12f * tier);
        Leaves.tear(level, at, ImpactMath.shockwaveRadius(tier), Math.min(1f, tier / 5f));
    }

    public static void lightPulse(ServerLevel level, ServerPlayer player, int tier, boolean big) {
        double x = player.getX(), y = player.getY() + player.getBbHeight() * 0.6, z = player.getZ();
        level.sendParticles(ParticleTypes.FLASH, x, y, z, big ? 2 : 1, 0.1, 0.1, 0.1, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, big ? 30 + 10 * tier : 6 + 2 * tier, 0.3, 0.6, 0.3, big ? 0.35 : 0.12);
    }

    public static void darkPulse(ServerLevel level, ServerPlayer player, int tier, boolean big) {
        double x = player.getX(), y = player.getY() + player.getBbHeight() * 0.5, z = player.getZ();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, big ? 30 + 8 * tier : 6 + 2 * tier, 0.6, 0.8, 0.6, big ? 0.15 : 0.03);
        if (big) level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 20 + 6 * tier, 0.4, 0.6, 0.4, 0.25);
    }

    // the top water block of a column with open air above it
    public static BlockPos waterSurface(ServerLevel level, int x, int topY, int z, int depth) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, topY, z);
        for (int i = 0; i <= depth; i++, pos.move(Direction.DOWN)) {
            if (!level.isLoaded(pos)) return null;
            if (!level.getFluidState(pos).is(FluidTags.WATER)) continue;
            BlockPos above = pos.above();
            if (!level.getBlockState(above).isAir()) return null;
            return pos.immutable();
        }
        return null;
    }

    // charging over water: spray kicked up from the surface around the player, stronger closer to full power
    public static int spray(ServerLevel level, Vec3 center, double radius, int samples, float strength) {
        RandomSource random = level.random;
        int hits = 0;
        for (int i = 0; i < samples; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * radius;
            double x = center.x + Math.cos(angle) * dist, z = center.z + Math.sin(angle) * dist;
            BlockPos surface = waterSurface(level, Mth.floor(x), Mth.floor(center.y) + 1, Mth.floor(z), 6);
            if (surface == null) continue;
            hits++;
            double y = surface.getY() + 1.0;
            int count = 2 + (int) (6 * strength);
            level.sendParticles(ParticleTypes.SPLASH, x, y, z, count, 0.3, 0.05, 0.3, 0.2 + 0.4 * strength);
            level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 1 + count / 2, 0.3, 0.02, 0.3, 0.02);
            if (strength > 0.6f) {
                level.sendParticles(ParticleTypes.FALLING_WATER, x, y + 0.6 + strength, z, 2, 0.2, 0.3, 0.2, 0.0);
            }
        }
        if (hits > 0) waterHits += hits;
        return hits;
    }

    // reaching a form over water: a ring of spray racing out across the surface
    public static boolean splashRing(ServerLevel level, Vec3 center, int tier) {
        BlockPos surface = null;
        for (int i = 0; i < 6 && surface == null; i++) {
            surface = waterSurface(level, Mth.floor(center.x) + level.random.nextInt(3) - 1, Mth.floor(center.y) + 1,
                    Mth.floor(center.z) + level.random.nextInt(3) - 1, 6);
        }
        if (surface == null) return false;
        double y = surface.getY() + 1.0;
        int n = 24 + 8 * tier;
        double speed = 0.3 + 0.08 * tier;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double cos = Math.cos(a), sin = Math.sin(a);
            level.sendParticles(ParticleTypes.SPLASH, center.x + cos, y, center.z + sin, 0, cos, 0.4, sin, speed * 2);
            level.sendParticles(ParticleTypes.FISHING, center.x + cos * 0.8, y, center.z + sin * 0.8, 0, cos, 0.0, sin, speed);
        }
        level.sendParticles(ParticleTypes.FALLING_WATER, center.x, y + 1.5, center.z, 20 + 10 * tier, 1.5, 1.0, 1.5, 0.0);
        level.playSound(null, center.x, y, center.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.6f + 0.1f * tier, 0.8f);
        waterHits++;
        return true;
    }

    // charging under water: the water boils with bubbles
    public static void boil(ServerLevel level, ServerPlayer player, float strength) {
        level.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 1.0, player.getZ(), 3 + (int) (8 * strength),
                0.8, 1.0, 0.8, 0.1 + 0.2 * strength);
        if (strength > 0.5f) {
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, player.getX(), player.getY(), player.getZ(), 4, 0.6, 0.2, 0.6, 0.1);
        }
        waterHits++;
    }

    public static int waterHits() {
        return waterHits;
    }
}
