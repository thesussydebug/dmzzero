package com.dmzzero.kishare;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.server.events.players.KiShareService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// the animals, villagers and plants around a charging spirit bomb lend it a little energy, once in a while
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class NatureGenki {
    private static final int COST = TickCost.id("ki.nature", () -> NatureGenki.CHARGES.size());
    private static final int PLANT_SAMPLES = 160;
    private static final int STREAMS = 10;
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private static int gifts;
    private static int resting;

    private static final class Charge {
        final boolean allowed;
        double left;
        boolean started;

        Charge(boolean allowed, double budget) {
            this.allowed = allowed;
            this.left = budget;
        }
    }

    public record Gift(int animals, int villagers, int plants, double bars) {
    }

    private NatureGenki() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        if (!ZeroConfig.SERVER.nature.get()) {
            CHARGES.clear();
            return;
        }
        Set<UUID> live = new HashSet<>();
        for (ServerPlayer caster : event.getServer().getPlayerList().getPlayers()) {
            KiBlastEntity bomb = KiShareService.chargingGenki(caster);
            if (bomb == null) continue;
            live.add(bomb.getUUID());
            feed(caster, bomb, caster.serverLevel().getGameTime());
        }
        CHARGES.keySet().retainAll(live);
    }

    // one second of nature giving; null when the area is resting or has nothing more to give
    public static Gift feed(ServerPlayer caster, KiBlastEntity bomb, long now) {
        ServerLevel level = caster.serverLevel();
        BlockPos at = caster.blockPosition();
        NatureCooldowns store = NatureCooldowns.get(level);
        Charge c = CHARGES.get(bomb.getUUID());
        if (c == null) {
            long rest = store.remaining(at, now, ZeroConfig.SERVER.natureArea.get());
            c = rest > 0 ? new Charge(false, 0) : new Charge(true, ZeroConfig.SERVER.natureBars.get());
            CHARGES.put(bomb.getUUID(), c);
            if (rest > 0) {
                resting++;
                caster.displayClientMessage(Component.translatableWithFallback("message.dmzzero.genki.resting",
                        "The land here is still recovering. (%s)", NatureMath.timeLeft(rest)).withStyle(ChatFormatting.GRAY), true);
            }
        }
        if (!c.allowed || c.left <= 0) return null;

        int range = ZeroConfig.SERVER.natureRange.get();
        AABB box = caster.getBoundingBox().inflate(range);
        List<LivingEntity> life = new ArrayList<>();
        int animals = 0, villagers = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e.distanceToSqr(caster) <= range * range)) {
            if (e instanceof Animal) animals++;
            else if (e instanceof AbstractVillager) villagers++;
            else continue;
            life.add(e);
        }
        List<BlockPos> plants = new ArrayList<>();
        int hits = samplePlants(level, caster, range, plants);
        int plantCount = Math.round(NatureMath.MAX_PLANTS * hits / (float) PLANT_SAMPLES);
        double bars = NatureMath.give(c.left, NatureMath.perSecond(animals, villagers, plantCount));
        if (bars <= 0) return null;
        if (!bomb.absorbGenkiDonation((float) bars)) {
            // dmz's own cap on how big donations can make it
            c.left = 0;
            return null;
        }
        if (!c.started) {
            c.started = true;
            gifts++;
            store.mark(at, now, ZeroConfig.SERVER.natureCooldown.get() * 1200L);
            caster.displayClientMessage(Component.translatableWithFallback("message.dmzzero.genki.nature",
                    "The world lends you its energy.").withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        c.left -= bars;
        streams(level, life, plants, bomb.position().add(0, bomb.getBbHeight() / 2.0, 0));
        return new Gift(animals, villagers, plantCount, bars);
    }

    private static int samplePlants(ServerLevel level, ServerPlayer caster, int range, List<BlockPos> found) {
        int hits = 0;
        int top = (int) caster.getY() + 12;
        for (int i = 0; i < PLANT_SAMPLES; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(level.random.nextDouble()) * range;
            int x = (int) Math.floor(caster.getX() + Math.cos(a) * r), z = (int) Math.floor(caster.getZ() + Math.sin(a) * r);
            BlockPos pos = topPlant(level, x, top, z);
            if (pos == null) continue;
            hits++;
            if (found.size() < STREAMS) found.add(pos);
        }
        return hits;
    }

    // the first non-air block from above, if it's alive
    private static BlockPos topPlant(ServerLevel level, int x, int top, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int i = 0; i < 28; i++, pos.move(0, -1, 0)) {
            if (!level.isLoaded(pos)) return null;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            return plant(state) ? pos.immutable() : null;
        }
        return null;
    }

    public static boolean plant(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS)
                || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN)
                || s.is(Blocks.LARGE_FERN) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.VINE) || s.is(Blocks.LILY_PAD);
    }

    // thin threads of light rising from what gave toward the bomb
    private static void streams(ServerLevel level, List<LivingEntity> life, List<BlockPos> plants, Vec3 bomb) {
        int n = 0;
        for (LivingEntity e : life) {
            if (n++ >= STREAMS) break;
            thread(level, e.position().add(0, e.getBbHeight() * 0.6, 0), bomb);
        }
        for (BlockPos p : plants) {
            if (n++ >= STREAMS * 2) break;
            thread(level, Vec3.atCenterOf(p).add(0, 0.6, 0), bomb);
        }
    }

    private static void thread(ServerLevel level, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int points = (int) Math.min(14, Math.max(3, length / 2));
        double arc = StreamMath.arc(length) * 0.5;
        for (int i = 0; i < points; i++) {
            double t = (i + level.random.nextDouble()) / points;
            double[] p = StreamMath.point(from.x, from.y, from.z, to.x, to.y, to.z, t, arc);
            level.sendParticles(ParticleTypes.END_ROD, p[0], p[1], p[2], 1, 0.03, 0.03, 0.03, 0.005);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, from.x, from.y, from.z, 1, 0.2, 0.2, 0.2, 0);
    }

    public static void forgetCharge(UUID bomb) {
        CHARGES.remove(bomb);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CHARGES.clear();
    }

    public static int gifts() {
        return gifts;
    }

    public static int resting() {
        return resting;
    }
}
