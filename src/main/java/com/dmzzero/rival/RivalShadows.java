package com.dmzzero.rival;

import com.dmzzero.DMZZero;
import com.dmzzero.Titles;
import com.dmzzero.ZeroConfig;
import com.dmzzero.kishare.StreamMath;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.IBattlePower;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.network.C2S.SummonPlayerShadowDummyC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// dmz's shadow sparring partner, built from your rival's stats instead of your own
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class RivalShadows {
    private static final int COST = TickCost.id("growth.rivals", () -> RivalShadows.ACTIVE.size() + RivalShadows.COOLDOWN.size());
    public static final String TAG = "dmzzero_rival_shadow";
    private static final double LEASH = 48;
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static int faded;

    private record Active(UUID shadow, ServerLevel level, long until, String rivalName, ServerPlayer owner) {
    }

    private RivalShadows() {
    }

    public static String summon(ServerPlayer player) {
        if (!ZeroConfig.SERVER.rivals.get() || !ZeroConfig.SERVER.rivalShadows.get()) return say(player, "disabled", "Rival shadows are turned off on this server.");
        CompoundTag persisted = RivalData.persisted(player);
        if (RivalData.rival(persisted) == null) return say(player, "no_rival", "You have no rival to face.");
        String rivalName = RivalData.name(persisted);
        RivalData.Snapshot snap = RivalData.snapshot(persisted);
        if (snap == null) return say(player, "no_snapshot", "Fight %s once first, so you know what you're up against.", rivalName);
        if (ACTIVE.containsKey(player.getUUID())) return say(player, "active", "Your rival's shadow is already out.");
        long now = player.server.getTickCount();
        Long ready = COOLDOWN.get(player.getUUID());
        if (ready != null && now < ready) return say(player, "cooldown", "You can call up a shadow again in %s min.", (ready - now + 1199) / 1200);

        ServerLevel level = player.serverLevel();
        ShadowDummyEntity dummy = MainEntities.SHADOW_DUMMY.get().create(level);
        if (dummy == null) return say(player, "failed", "The shadow wouldn't take shape.");
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 at = player.position().add(look.scale(3));
        dummy.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180f, 0f);
        apply(dummy, snap.scaled(ZeroConfig.SERVER.shadowStrength.get()));
        dummy.setOwner(player);
        CompoundTag tag = dummy.getPersistentData();
        tag.putString("dmz_quest_owner", player.getStringUUID());
        tag.putBoolean(SummonPlayerShadowDummyC2S.TAG_PLAYER_SHADOW, true);
        tag.putInt("dmz_shadow_percent", 75);
        tag.putString(TAG, RivalData.rival(persisted).toString());
        dummy.setCustomName(Component.translatableWithFallback("entity.dmzzero.rival_shadow", "Shadow of %s", rivalName));
        dummy.setCustomNameVisible(true);
        if (!level.addFreshEntity(dummy)) return say(player, "failed", "The shadow wouldn't take shape.");
        dummy.setTarget(player);
        ACTIVE.put(player.getUUID(), new Active(dummy.getUUID(), level, now + ZeroConfig.SERVER.shadowMinutes.get() * 1200L, rivalName, player));
        // kept through a logout; the ones that have run out go here, so the map holds only waits still running
        COOLDOWN.values().removeIf(end -> end <= now);
        COOLDOWN.put(player.getUUID(), now + ZeroConfig.SERVER.shadowCooldown.get() * 1200L);

        float[] rgb = StreamMath.rgb(snap.aura());
        level.sendParticles(new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), 2f), at.x, player.getY() + 1, at.z, 40, 0.4, 0.9, 0.4, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, player.getY() + 0.5, at.z, 20, 0.4, 0.5, 0.4, 0.02);
        level.playSound(null, at.x, player.getY(), at.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.35f, 1.6f);
        player.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.shadow.summoned", "The shadow of %s steps out.",
                Component.literal(rivalName).withStyle(ChatFormatting.BOLD)).withStyle(ChatFormatting.DARK_PURPLE));
        DMZZero.LOG.info("{} called up the shadow of their rival {}", player.getGameProfile().getName(), rivalName);
        return "ok";
    }

    // the same fields dmz sets for your own shadow, from the snapshot and without its 25-75% clamp
    static void apply(ShadowDummyEntity dummy, RivalData.Snapshot s) {
        var hp = dummy.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Math.max(1.0, s.maxHealth()));
        dummy.setHealth((float) (s.maxHealth() + s.defense()));
        if (dummy instanceof IBattlePower bp) bp.setBattlePower((int) Math.min(Integer.MAX_VALUE, s.battlePower()));
        var attack = dummy.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(s.melee() * 0.75);
        dummy.setKiBlastDamage((float) (s.ki() * 0.75));
        dummy.getPersistentData().putBoolean("dmz_stats_configured", true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty() || event.getServer().getTickCount() % 10 != 0) return;
        tick(event.getServer().getTickCount());
    }

    public static void tick(long now) {
        for (Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Active> e = it.next();
            Active a = e.getValue();
            Entity shadow = a.level.getEntity(a.shadow);
            ServerPlayer owner = a.owner.isRemoved() ? null : a.owner;
            if (shadow == null || !shadow.isAlive()) {
                // dmz dismisses the shadow once it knocks you down
                if (owner != null && knockedDown(owner)) {
                    owner.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.shadow.won",
                            "The shadow of %s wins this one.", a.rivalName).withStyle(ChatFormatting.DARK_PURPLE));
                }
                it.remove();
                continue;
            }
            boolean leash = owner == null || owner.level() != a.level || owner.distanceToSqr(shadow) > LEASH * LEASH;
            if (now >= a.until || leash) {
                fade(shadow);
                if (owner != null) owner.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.shadow.faded",
                        "The shadow of %s fades.", a.rivalName).withStyle(ChatFormatting.GRAY));
                it.remove();
            }
        }
    }

    private static boolean knockedDown(ServerPlayer p) {
        return StatsProvider.get(StatsCapability.INSTANCE, p).map(d -> d.getStatus().isKnockedDown()).orElse(false);
    }

    private static void fade(Entity shadow) {
        if (shadow.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, shadow.getX(), shadow.getY() + 1, shadow.getZ(), 24, 0.3, 0.8, 0.3, 0.02);
        }
        faded++;
        if (shadow instanceof ShadowDummyEntity dummy) SummonPlayerShadowDummyC2S.dismissByDummy(dummy);
        else shadow.discard();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ShadowDummyEntity dummy) || !dummy.getPersistentData().contains(TAG)) return;
        ServerPlayer killer = Rivals.attacker(event.getSource());
        UUID owner = null;
        for (Map.Entry<UUID, Active> e : ACTIVE.entrySet()) if (e.getValue().shadow.equals(dummy.getUUID())) owner = e.getKey();
        if (owner == null) return;
        Active a = ACTIVE.remove(owner);
        if (killer == null || !killer.getUUID().equals(owner)) return;
        Titles.show(killer, Component.translatableWithFallback("title.dmzzero.shadow.defeated", "Shadow Defeated").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                Component.literal(a.rivalName), 10, 50, 20);
        killer.serverLevel().playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 0.8f);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Active a = ACTIVE.remove(event.getEntity().getUUID());
        if (a != null) fadeShadow(a);
    }

    // a stopping server saves the world without logging anyone out: a shadow still out would be kept, with no time limit
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.values().forEach(RivalShadows::fadeShadow);
        ACTIVE.clear();
    }

    private static void fadeShadow(Active a) {
        Entity shadow = a.level.getEntity(a.shadow);
        if (shadow != null) fade(shadow);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        COOLDOWN.clear();
    }

    private static String say(ServerPlayer player, String code, String text, Object... args) {
        player.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.shadow." + code, text, args).withStyle(ChatFormatting.GRAY));
        return code;
    }

    public static ShadowDummyEntity shadowOf(ServerPlayer player) {
        Active a = ACTIVE.get(player.getUUID());
        return a != null && a.level.getEntity(a.shadow) instanceof ShadowDummyEntity d ? d : null;
    }

    public static void dismiss(ServerPlayer player) {
        Active a = ACTIVE.remove(player.getUUID());
        if (a != null) fadeShadow(a);
        COOLDOWN.remove(player.getUUID());
    }

    // the self-test runs out a shadow's time without waiting five minutes
    public static void expire(ServerPlayer player) {
        Active a = ACTIVE.get(player.getUUID());
        if (a != null) ACTIVE.put(player.getUUID(), new Active(a.shadow, a.level, 0, a.rivalName, a.owner));
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static int faded() {
        return faded;
    }
}
