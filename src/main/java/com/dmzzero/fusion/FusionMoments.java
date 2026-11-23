package com.dmzzero.fusion;

import com.dmzzero.DMZZero;
import com.dmzzero.Titles;
import com.dmzzero.ZeroConfig;
import com.dmzzero.impact.ImpactProfile;
import com.dmzzero.impact.ImpactWorld;
import com.dmzzero.kishare.StreamMath;
import com.dmzzero.network.ImpactPulseS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// two auras pulled into one, and a smaller burst when they come apart again
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class FusionMoments {
    private static final int COST = TickCost.id("versus.fusion", () -> FusionMoments.PENDING.size() + FusionMoments.RECENT_SPLITS.size() + FusionMoments.PAIRS.size());
    private static final double RANGE = 48;
    private static final int DELAY = 2;
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<String, Long> RECENT_SPLITS = new HashMap<>();
    // who fused with whom, so a split still knows the partner's name and color after they log out.
    // values, not players, so a logged-out player isn't kept in memory
    private static final Map<UUID, Partner> PAIRS = new HashMap<>();
    // the self-test's fake players, which aren't in the player list
    private static final Map<UUID, ServerPlayer> TEST_PLAYERS = new HashMap<>();
    private static int fusions;
    private static int splits;
    private static int firsts;
    private static String lastName = "";

    private record Partner(UUID id, String name, int color) {
    }

    // partnerId is set for a split, so the pair's first-split record works with the partner offline
    private record Pending(ServerPlayer leader, LivingEntity partner, UUID partnerId, Vec3 partnerAt, int leaderColor, int partnerColor,
                           boolean split, String name, long at) {
    }

    private FusionMoments() {
    }

    // dmz posts this just before fusing; the fused name is only set once applyFusion has run
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFusion(DMZEvent.FusionEvent event) {
        if (event.isCanceled() || !ZeroConfig.SERVER.fusionMoments.get() || event.getInitiator() == null || event.getTarget() == null) return;
        ServerPlayer leader = event.getInitiator();
        LivingEntity partner = event.getTarget();
        PENDING.add(new Pending(leader, partner, null, partner.position(), color(leader), color(partner), false, null,
                leader.server.getTickCount() + DELAY));
    }

    // from the endFusion mixin, while dmz still knows who was fused with whom
    public static void splitting(ServerPlayer player, StatsData data) {
        if (!ZeroConfig.SERVER.fusionMoments.get()) return;
        var status = data.getStatus();
        if (!status.isFused() && status.getFusionPartnerUUID() == null) return;
        UUID partnerId = status.getFusionPartnerUUID();
        ServerPlayer other = partnerId != null ? find(player.server, partnerId) : null;
        Partner known = PAIRS.remove(player.getUUID());
        if (partnerId != null) PAIRS.remove(partnerId);
        if (known != null && !known.id().equals(partnerId)) known = null;
        // with the partner gone, the burst happens where this player is
        boolean leads = status.isFusionLeader() || other == null;
        ServerPlayer leader = leads ? player : other;
        ServerPlayer partner = leads ? other : player;
        long now = player.server.getTickCount();
        String key = partnerId == null ? player.getStringUUID() : FusionRecord.pair(player.getUUID(), partnerId);
        Long last = RECENT_SPLITS.get(key);
        if (last != null && now - last < 20) return;
        RECENT_SPLITS.put(key, now);
        String partnerName = partner != null ? partner.getGameProfile().getName() : known != null ? known.name() : "?";
        int partnerColor = partner != null ? color(partner) : known != null ? known.color() : color(leader);
        // first-split titles are for two players, as the first-fusion ones are. the pending split names the one who isn't the leader
        UUID titled = partner == null && known == null ? null : leads ? partnerId : player.getUUID();
        PENDING.add(new Pending(leader, partner, titled, leader.position(), color(leader), partnerColor, true,
                FusionRecord.name(status.getFusionName(), leader.getGameProfile().getName(), partnerName), now + 1));
    }

    private static ServerPlayer find(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        ServerPlayer fake = TEST_PLAYERS.get(id);
        return fake != null && !fake.isRemoved() ? fake : null;
    }

    private static Partner partner(ServerPlayer player) {
        return new Partner(player.getUUID(), player.getGameProfile().getName(), color(player));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        run(event.getServer().getTickCount());
    }

    // the self-test doesn't wait for ticks
    public static void flush() {
        run(Long.MAX_VALUE);
    }

    private static void run(long now) {
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now < p.at) continue;
            it.remove();
            if (p.leader.isRemoved()) continue;
            if (p.split) split(p);
            else fused(p);
        }
        if (now != Long.MAX_VALUE) RECENT_SPLITS.values().removeIf(t -> now - t > 200);
    }

    private static void fused(Pending p) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p.leader).orElse(null);
        // something else stopped it after the event
        if (data == null || !data.getStatus().isFused()) return;
        ServerLevel level = p.leader.serverLevel();
        Vec3 at = p.leader.position().add(0, 1, 0);
        String partnerName = p.partner instanceof Player pl ? pl.getGameProfile().getName() : p.partner.getName().getString();
        String name = FusionRecord.name(data.getStatus().getFusionName(), p.leader.getGameProfile().getName(), partnerName);
        stream(level, p.partnerAt.add(0, 1, 0), at, p.partnerColor);
        swirl(level, at, p.leaderColor, 1.4);
        swirl(level, at, p.partnerColor, 1.0);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.3, 0.5, 0.3, 0.25);
        ImpactWorld.shockwave(level, at.add(0, -0.85, 0), new ImpactProfile(4, 0));
        int mixed = StreamMath.pack(StreamMath.tint(StreamMath.rgb(p.leaderColor), new float[][]{StreamMath.rgb(p.partnerColor)}));
        ZeroNetwork.sendAround(level, at, RANGE, new ImpactPulseS2C(at.x, at.y, at.z, 0.9f, (float) RANGE, mixed, 0.35f, 0.08f));
        fusions++;
        lastName = name;
        if (p.partner instanceof ServerPlayer partner) {
            PAIRS.put(p.leader.getUUID(), partner(partner));
            PAIRS.put(partner.getUUID(), partner(p.leader));
            String key = FusionRecord.pair(p.leader.getUUID(), partner.getUUID());
            first(p.leader, FusionRecord.FUSED, key, name);
            first(partner, FusionRecord.FUSED, key, name);
        }
    }

    private static void split(Pending p) {
        ServerLevel level = p.leader.serverLevel();
        Vec3 at = p.leader.position().add(0, 1, 0);
        DustParticleOptions a = dust(p.leaderColor, 1.6f), b = dust(p.partnerColor, 1.6f);
        for (int i = 0; i < 24; i++) {
            double ang = Math.PI * 2 * i / 24;
            DustParticleOptions d = i % 2 == 0 ? a : b;
            level.sendParticles(d, at.x + Math.cos(ang) * 0.5, at.y, at.z + Math.sin(ang) * 0.5, 0, Math.cos(ang), 0.05, Math.sin(ang), 0.6);
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 16, 0.3, 0.4, 0.3, 0.15);
        ImpactWorld.shockwave(level, at.add(0, -0.85, 0), new ImpactProfile(2, 0));
        ZeroNetwork.sendAround(level, at, RANGE * 0.6, new ImpactPulseS2C(at.x, at.y, at.z, 0.4f, (float) (RANGE * 0.6), p.leaderColor, 0.12f, 0f));
        splits++;
        lastName = p.name;
        // a partner who logged out gets no title, since there's no one to show it to
        if (p.partnerId != null && !p.partnerId.equals(p.leader.getUUID())) {
            String key = FusionRecord.pair(p.leader.getUUID(), p.partnerId);
            first(p.leader, FusionRecord.SPLIT, key, p.name);
            if (p.partner instanceof ServerPlayer partner && !partner.isRemoved()) first(partner, FusionRecord.SPLIT, key, p.name);
        }
    }

    private static void first(ServerPlayer player, String kind, String key, String name) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!FusionRecord.first(persisted, kind, key)) return;
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        firsts++;
        boolean fused = FusionRecord.FUSED.equals(kind);
        Titles.show(player,
                Component.literal(name).withStyle(fused ? ChatFormatting.GOLD : ChatFormatting.AQUA, ChatFormatting.BOLD),
                fused ? Component.translatableWithFallback("title.dmzzero.fusion.first", "First fusion")
                        : Component.translatableWithFallback("title.dmzzero.fusion.first_split", "First time apart again"),
                10, 60, 20);
    }

    private static void stream(ServerLevel level, Vec3 from, Vec3 to, int color) {
        double length = from.distanceTo(to);
        if (length < 0.5) return;
        DustParticleOptions d = dust(color, 1.4f);
        int n = (int) Math.min(30, Math.max(6, length * 2));
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n;
            double[] q = StreamMath.point(from.x, from.y, from.z, to.x, to.y, to.z, t, StreamMath.arc(length));
            level.sendParticles(d, q[0], q[1], q[2], 2, 0.05, 0.05, 0.05, 0);
        }
    }

    private static void swirl(ServerLevel level, Vec3 at, int color, double radius) {
        DustParticleOptions d = dust(color, 1.5f);
        for (int i = 0; i < 20; i++) {
            double ang = Math.PI * 2 * i / 20;
            level.sendParticles(d, at.x + Math.cos(ang) * radius, at.y - 0.8 + i * 0.09, at.z + Math.sin(ang) * radius, 1, 0, 0, 0, 0);
        }
    }

    private static DustParticleOptions dust(int color, float size) {
        float[] rgb = StreamMath.rgb(color);
        return new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), size);
    }

    static int color(LivingEntity entity) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, entity).orElse(null);
        if (data == null) return 0xFFFFFF;
        return StreamMath.hex(data.getCharacter().getActiveAuraColor(), 0xFFFFFF);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        PAIRS.clear();
        RECENT_SPLITS.clear();
        TEST_PLAYERS.clear();
    }

    // the self-test: fake players aren't in the player list, so a split finds its partner here
    public static void testPlayer(ServerPlayer p) {
        TEST_PLAYERS.put(p.getUUID(), p);
    }

    public static void clearTest() {
        TEST_PLAYERS.clear();
    }

    public static int fusions() {
        return fusions;
    }

    public static int splits() {
        return splits;
    }

    public static int firsts() {
        return firsts;
    }

    public static String lastName() {
        return lastName;
    }
}
