package com.dmzzero.beam;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.Contact;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Party members' beams fired together and aimed the same way combine where they meet: the strongest carries on,
 * bigger, and the others end at the meeting point.
 */
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class CombinedBeams {
    private static final int COST = TickCost.id("beams.combined", () -> CombinedBeams.FIRED.size() + CombinedBeams.GROUPS.size() + CombinedBeams.CAPS.size());
    /** How far out along both beams they have to meet, so a joined beam still shows as a stub. */
    public static final double AHEAD = 3.0;
    private static final double EDGE_BLEND = 0.5;

    // when each beam was first seen firing (game time), and in which world
    private record Fired(ServerLevel level, long time) {
    }

    private static final Map<UUID, Fired> FIRED = new HashMap<>();
    private static final List<Group> GROUPS = new ArrayList<>();
    // joined beams held at their meeting point; read by the KiWaveEntity.tick mixin
    private static final Map<KiWaveEntity, Double> CAPS = new IdentityHashMap<>();
    // beams overhaul's team clash already folded together: they never combine again, or the helpers would count twice
    private static final java.util.Set<UUID> SPENT = new java.util.HashSet<>();
    // every ki wave in each server world, kept as they join, so the tick never walks every entity
    private static final Map<ResourceKey<Level>, List<KiWaveEntity>> WAVES = new HashMap<>();
    private static String lastNews = "";

    public static final class Group {
        final ServerLevel level;
        KiWaveEntity leader;
        final List<KiWaveEntity> helpers = new ArrayList<>();
        double damageBonus;
        double sizeBonus;
        int ownBorder;
        // overhaul's team clash took over: it counts the helpers itself
        boolean handedOver;

        Group(ServerLevel level, KiWaveEntity leader) {
            this.level = level;
            this.leader = leader;
            this.ownBorder = leader.getColorBorder();
        }

        public KiWaveEntity leader() {
            return leader;
        }

        public List<KiWaveEntity> helpers() {
            return helpers;
        }

        public boolean handedOver() {
            return handedOver;
        }

        int size() {
            return helpers.size() + 1;
        }

        long firstFired() {
            long first = fireTime(leader);
            for (KiWaveEntity h : helpers) first = Math.min(first, fireTime(h));
            return first;
        }

        boolean contains(KiWaveEntity beam) {
            return leader == beam || helpers.contains(beam);
        }
    }

    private CombinedBeams() {
    }

    public static boolean enabled() {
        return ZeroConfig.read(ZeroConfig.SERVER.combinedBeams, true);
    }

    private static int window() {
        return ZeroConfig.read(ZeroConfig.SERVER.beamFireWindow, 20);
    }

    private static int maxAngle() {
        return ZeroConfig.read(ZeroConfig.SERVER.beamMaxAngle, 75);
    }

    private static int maxBeams() {
        return ZeroConfig.read(ZeroConfig.SERVER.beamMaxCombined, 4);
    }

    private static double damageShare() {
        return ZeroConfig.read(ZeroConfig.SERVER.beamDamageShare, 1.0);
    }

    private static double sizeShare() {
        return ZeroConfig.read(ZeroConfig.SERVER.beamSizeShare, 0.5);
    }

    private static boolean overhaul() {
        return ModList.get().isLoaded("dmzrevamp");
    }

    /** The KiWaveEntity.tick mixin: a joined beam's length is held at its meeting point. */
    public static float capLength(KiWaveEntity beam, float length) {
        Double cap = CAPS.get(beam);
        return cap == null ? length : Math.min(length, cap.floatValue());
    }

    public static Group groupOf(KiWaveEntity beam) {
        for (Group g : GROUPS) if (g.contains(beam)) return g;
        return null;
    }

    public static Double capOf(KiWaveEntity beam) {
        return CAPS.get(beam);
    }

    static long fireTime(KiWaveEntity beam) {
        Fired f = FIRED.get(beam.getUUID());
        return f == null ? Long.MAX_VALUE / 4 : f.time();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        long start = TickCost.start();
        levelTick(event);
        TickCost.add(COST, start);
    }

    private static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (!enabled()) {
            if (!GROUPS.isEmpty()) dissolveAll(level);
            return;
        }
        List<KiWaveEntity> known = WAVES.get(level.dimension());
        if (known != null) known.removeIf(w -> w.isRemoved() || w.level() != level);
        if ((known == null || known.isEmpty()) && GROUPS.isEmpty() && FIRED.isEmpty()) return;
        List<KiWaveEntity> firing = new ArrayList<>();
        if (known != null) for (KiWaveEntity w : known) if (w.isFiring() && w.getOwner() instanceof Player) firing.add(w);
        tick(level, firing, level.getGameTime());
    }

    /** One tick of combining for these firing beams; the self-test calls it directly too. */
    public static void tick(ServerLevel level, List<KiWaveEntity> firing, long now) {
        for (KiWaveEntity w : firing) FIRED.putIfAbsent(w.getUUID(), new Fired(level, now));
        if (!firing.isEmpty() || inLevel(level)) {
            keepUp(level);
            findNew(level, firing);
        }
        // forget beams of this world that are gone
        FIRED.entrySet().removeIf(e -> e.getValue().level() == level && level.getEntity(e.getKey()) == null);
        SPENT.removeIf(id -> !FIRED.containsKey(id));
    }

    /** Whether the tick sees this beam in this world. */
    public static boolean tracking(ServerLevel level, KiWaveEntity beam) {
        List<KiWaveEntity> known = WAVES.get(level.dimension());
        return known != null && known.contains(beam);
    }

    private static boolean inLevel(ServerLevel level) {
        for (Group g : GROUPS) if (g.level == level) return true;
        return false;
    }

    // lowest priority, so a join another mod cancels is never kept
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof KiWaveEntity w && event.getLevel() instanceof ServerLevel level) {
            WAVES.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(w);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) WAVES.remove(level.dimension());
    }

    private static boolean live(KiWaveEntity beam) {
        if (beam.isRemoved() || !beam.isFiring() || !(beam.getOwner() instanceof Player owner) || !owner.isAlive()) return false;
        return !StatsProvider.get(StatsCapability.INSTANCE, owner).map(d -> d.getStatus().isKnockedDown()).orElse(false);
    }

    private static boolean allies(KiWaveEntity a, KiWaveEntity b) {
        return a.getOwner() instanceof Player pa && b.getOwner() instanceof Player pb && pa != pb
                && TargetHelper.areFriendlyPlayers(pa, pb) && TargetHelper.areFriendlyPlayers(pb, pa);
    }

    // drop helpers that stopped, end groups whose leader stopped, hand clashes to overhaul
    private static void keepUp(ServerLevel level) {
        for (Iterator<Group> it = GROUPS.iterator(); it.hasNext(); ) {
            Group g = it.next();
            if (g.level != level) continue;
            if (!live(g.leader)) {
                release(g);
                it.remove();
                continue;
            }
            boolean dropped = g.helpers.removeIf(h -> {
                boolean gone = !live(h) || !allies(g.leader, h);
                if (gone) CAPS.remove(h);
                return gone;
            });
            if (dropped && !g.helpers.isEmpty()) colour(g);
            if (g.handedOver) {
                // overhaul's clash is over: the beams carry on on their own
                if (!g.leader.isClashLocked()) {
                    SPENT.add(g.leader.getUUID());
                    for (KiWaveEntity h : g.helpers) SPENT.add(h.getUUID());
                    release(g);
                    it.remove();
                }
                continue;
            }
            if (g.leader.isClashLocked() && overhaul()) {
                g.handedOver = true;
                apply(g, 0, 0);
                continue;
            }
            if (g.helpers.isEmpty()) {
                release(g);
                it.remove();
                continue;
            }
            applyBonus(g);
        }
    }

    private static void findNew(ServerLevel level, List<KiWaveEntity> firing) {
        double maxAngle = maxAngle();
        int window = window();
        for (int i = 0; i < firing.size(); i++) {
            for (int j = i + 1; j < firing.size(); j++) {
                KiWaveEntity a = firing.get(i), b = firing.get(j);
                if (a.isClashLocked() || b.isClashLocked() || SPENT.contains(a.getUUID()) || SPENT.contains(b.getUUID())) continue;
                if (!live(a) || !live(b) || !allies(a, b)) continue;
                Group ga = groupOf(a), gb = groupOf(b);
                if (ga != null && gb != null) continue;
                // only a group's leader takes on new beams; a joined beam is held at its meeting point
                if ((ga != null && ga.leader != a) || (gb != null && gb.leader != b)) continue;
                Group group = ga != null ? ga : gb;
                KiWaveEntity joining = ga != null ? b : a;
                KiWaveEntity other = ga != null ? a : b;
                if (group != null && (group.handedOver || group.size() >= maxBeams())) continue;
                long otherFired = group != null ? group.firstFired() : fireTime(other);
                if (!BeamMath.firedTogether(fireTime(joining), otherFired, window)) continue;
                if (BeamMath.angleDeg(dir(joining), dir(other)) > maxAngle) continue;
                Contact c = contact(joining, other);
                if (c == null) continue;
                join(level, group, other, joining, c);
            }
        }
    }

    static V dir(KiWaveEntity beam) {
        return BeamMath.direction(beam.getClashPitch(), beam.getClashYaw());
    }

    static V origin(KiWaveEntity beam) {
        return new V(beam.getX(), beam.getY(), beam.getZ());
    }

    /** Where {@code a} touches {@code b}: alongA is along a, alongB along b. */
    static Contact contact(KiWaveEntity a, KiWaveEntity b) {
        return BeamMath.contact(origin(a), dir(a), a.getClashBeamLength(), a.getSize(),
                origin(b), dir(b), b.getClashBeamLength(), b.getSize(), AHEAD);
    }

    // c is joining-vs-other: alongA along the joining beam, alongB along the other
    private static void join(ServerLevel level, Group group, KiWaveEntity other, KiWaveEntity joining, Contact c) {
        boolean joiningLeads = BeamMath.leader(new double[]{other.getKiDamage(), joining.getKiDamage()},
                new long[]{fireTime(other), fireTime(joining)}) == 1;
        if (group == null) {
            KiWaveEntity leader = joiningLeads ? joining : other;
            KiWaveEntity helper = joiningLeads ? other : joining;
            group = new Group(level, leader);
            GROUPS.add(group);
            addHelper(group, helper, joiningLeads ? c.alongB() : c.alongA());
        } else if (joiningLeads) {
            // the stronger newcomer carries on; the old leader feeds into it
            apply(group, 0, 0);
            KiWaveEntity old = group.leader;
            old.setColors(old.getColor(), group.ownBorder, old.getColorOutline());
            group.leader = joining;
            group.ownBorder = joining.getColorBorder();
            addHelper(group, old, c.alongB());
        } else {
            addHelper(group, joining, c.alongA());
        }
        applyBonus(group);
        colour(group);
        announce(level, group, c.point());
    }

    private static void addHelper(Group g, KiWaveEntity helper, double cap) {
        g.helpers.add(helper);
        CAPS.put(helper, cap);
        helper.setBeamLength((float) Math.min(helper.getBeamLength(), cap));
    }

    private static void applyBonus(Group g) {
        double[] damages = new double[g.helpers.size()];
        double[] sizes = new double[g.helpers.size()];
        for (int i = 0; i < damages.length; i++) {
            damages[i] = g.helpers.get(i).getKiDamage();
            sizes[i] = g.helpers.get(i).getSize();
        }
        apply(g, BeamMath.bonus(damages, damageShare()), BeamMath.bonus(sizes, sizeShare()));
    }

    // swaps the bonus on the leader, leaving anything else that changed its damage or size alone
    private static void apply(Group g, double damageBonus, double sizeBonus) {
        KiWaveEntity leader = g.leader;
        if (damageBonus != g.damageBonus) {
            leader.setKiDamage((float) Math.max(0, BeamMath.reapply(leader.getKiDamage(), g.damageBonus, damageBonus)));
            g.damageBonus = damageBonus;
        }
        if (sizeBonus != g.sizeBonus) {
            leader.setSize((float) Math.max(0.1, BeamMath.reapply(leader.getSize(), g.sizeBonus, sizeBonus)));
            g.sizeBonus = sizeBonus;
        }
    }

    // the combined beam's edge takes on the helpers' colour
    private static void colour(Group g) {
        int[] mains = new int[g.helpers.size()];
        for (int i = 0; i < mains.length; i++) mains[i] = g.helpers.get(i).getColor();
        int edge = BeamMath.blend(g.ownBorder, BeamMath.average(mains), EDGE_BLEND);
        g.leader.setColors(g.leader.getColor(), edge, g.leader.getColorOutline());
    }

    private static void release(Group g) {
        for (KiWaveEntity h : g.helpers) CAPS.remove(h);
        if (!g.leader.isRemoved()) {
            if (!g.handedOver) apply(g, 0, 0);
            g.leader.setColors(g.leader.getColor(), g.ownBorder, g.leader.getColorOutline());
        }
        g.helpers.clear();
    }

    private static void dissolveAll(ServerLevel level) {
        GROUPS.removeIf(g -> {
            if (g.level != level) return false;
            release(g);
            return true;
        });
    }

    private static void announce(ServerLevel level, Group g, V at) {
        level.sendParticles(ParticleTypes.FLASH, at.x(), at.y(), at.z(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x(), at.y(), at.z(), 2, 0.4, 0.4, 0.4, 0);
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24);
            level.sendParticles(ParticleTypes.END_ROD, at.x(), at.y(), at.z(), 0, Math.cos(a), 0, Math.sin(a), 0.35);
        }
        level.playSound(null, at.x(), at.y(), at.z(), MainSounds.KI_KAME_FIRE.get(), SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, at.x(), at.y(), at.z(), MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.PLAYERS, 0.8F, 1.2F);

        List<String> names = new ArrayList<>();
        names.add(nameOf(g.leader));
        for (KiWaveEntity h : g.helpers) names.add(nameOf(h));
        Component line = Component.translatableWithFallback("message.dmzzero.beams.combined", "%s combine their beams!", listNames(names));
        lastNews = line.getString();
        double newsRange = ZeroConfig.SERVER.newsRange.get();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at.x(), at.y(), at.z()) <= newsRange * newsRange) p.displayClientMessage(line, true);
        }
    }

    public static String lastNews() {
        return lastNews;
    }

    private static String nameOf(KiWaveEntity beam) {
        return beam.getOwner() == null ? "?" : beam.getOwner().getName().getString();
    }

    /** "A and B", "A, B and C". */
    public static String listNames(List<String> names) {
        if (names.size() <= 1) return names.isEmpty() ? "" : names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** For the self-test: forget a beam's fire time so a test can fire it "now". */
    public static void setFireTime(KiWaveEntity beam, long time) {
        if (beam.level() instanceof ServerLevel level) FIRED.put(beam.getUUID(), new Fired(level, time));
    }

    public static void clearAll() {
        for (Group g : GROUPS) release(g);
        GROUPS.clear();
        CAPS.clear();
        FIRED.clear();
        SPENT.clear();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        GROUPS.clear();
        CAPS.clear();
        FIRED.clear();
        SPENT.clear();
        WAVES.clear();
    }
}
