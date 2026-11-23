package com.dmzzero.garden;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

// after buu the namekians replanted their new planet with ajissa trees. a sapling of dmz's ajissa a namekian plants grows
// through the sapling's own growing, within an in-game day; one with no room tries again every in-game hour for 3 days
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class AjissaGrowth {
    private static final ResourceLocation SAPLING = new ResourceLocation("dragonminez", "namek_ajissa_sapling");
    private static final long RETRY = 1_000;
    private static final long GIVE_UP = 3 * 24_000;
    private static final Set<ResourceKey<Level>> GROWING = new HashSet<>();
    private static final int COST = TickCost.id("world.ajissa", GROWING::size);

    private AjissaGrowth() {
    }

    static Block sapling() {
        return ForgeRegistries.BLOCKS.getValue(SAPLING);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer p)) return;
        if (!event.getPlacedBlock().is(sapling()) || !ZeroConfig.SERVER.ajissaGrowth.get() || !namekian(p)) return;
        long now = level.getGameTime();
        long due = AjissaMath.due(now, AjissaMath.delay(ZeroConfig.SERVER.ajissaHours.get(), level.getRandom().nextDouble()));
        if (AjissaSave.get(level).add(new AjissaSave.Sapling(event.getPos().asLong(), due, AjissaMath.due(due, GIVE_UP)))) GROWING.add(level.dimension());
    }

    static boolean namekian(ServerPlayer p) {
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        return d != null && d.getStatus().isHasCreatedCharacter() && "namekian".equals(d.getCharacter().getRace());
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        if (!GROWING.contains(level.dimension()) || !ZeroConfig.SERVER.ajissaGrowth.get()) return;
        long start = TickCost.start();
        grow(level);
        TickCost.add(COST, start);
    }

    /** Once a second: due saplings in loaded chunks grow. One that's gone is forgotten. */
    public static int grow(ServerLevel level) {
        AjissaSave save = AjissaSave.get(level);
        if (save.size() == 0) {
            GROWING.remove(level.dimension());
            return 0;
        }
        long now = level.getGameTime();
        Block sapling = sapling();
        int out = 0;
        for (AjissaSave.Sapling s : save.all()) {
            BlockPos pos = BlockPos.of(s.pos());
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(sapling) || !(state.getBlock() instanceof SaplingBlock block)) {
                save.forget(s.pos());
                continue;
            }
            if (now < s.due()) continue;
            // a sapling grows in two steps, the second one growing the tree
            for (int i = 0; i < 2 && level.getBlockState(pos).is(sapling); i++) block.advanceTree(level, pos, level.getBlockState(pos), level.getRandom());
            if (!level.getBlockState(pos).is(sapling)) {
                save.forget(s.pos());
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.6, 0.6, 0.6, 0);
                out++;
            } else if (now > s.until()) {
                save.forget(s.pos());
            } else {
                save.setDue(s.pos(), now + RETRY);
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (ServerLevel l : event.getServer().getAllLevels()) if (AjissaSave.get(l).size() > 0) GROWING.add(l.dimension());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GROWING.clear();
    }
}
