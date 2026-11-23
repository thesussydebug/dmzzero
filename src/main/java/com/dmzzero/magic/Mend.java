package com.dmzzero.magic;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dmzzero.magic.SpellKit.LILAC;
import static com.dmzzero.magic.SpellKit.pay;
import static com.dmzzero.magic.SpellKit.stats;

// a glind spell: stand still and spend ki, and every second the armor you're wearing, or a party member's you look at,
// gets a share of its durability back
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Mend implements Spell {
    public static final Mend SPELL = new Mend();
    private static final int COST = TickCost.id("magic.mend", () -> Mend.MENDING.size());
    private static final double MOVE_ALLOWED = 0.5;
    // blocks past the friend range a friend can step before the mend stops
    private static final double FRIEND_SLACK = 1.0;
    private static final Map<UUID, Mending> MENDING = new HashMap<>();
    private static String lastEnd = "";

    private Mend() {
    }

    static final class Mending {
        final ServerPlayer caster;
        // whose armor: the caster's own, or a party member's
        final ServerPlayer target;
        final ServerLevel level;
        final Vec3 start;
        long age;
        int pulses;

        Mending(ServerPlayer caster, ServerPlayer target) {
            this.caster = caster;
            this.target = target;
            this.level = caster.serverLevel();
            this.start = caster.position();
        }

        boolean friend() {
            return target != caster;
        }
    }

    @Override
    public String id() {
        return "mend";
    }

    @Override
    public String english() {
        return "Mend";
    }

    @Override
    public ForgeConfigSpec.BooleanValue toggle() {
        return ZeroConfig.SERVER.mend;
    }

    @Override
    public String cast(ServerPlayer caster, StatsData data, long now) {
        if (MENDING.remove(caster.getUUID()) != null) return tell(caster, "stopped", "You stop mending.", false);
        return start(caster, data, aimedFriend(caster));
    }

    /** Starts mending friend's armor, or the caster's own with friend null. The self-test calls it straight. */
    public static String start(ServerPlayer caster, StatsData data, ServerPlayer friend) {
        MendMath.Target who = MendMath.target(friend != null, friend != null && !damaged(friend).isEmpty(), !damaged(caster).isEmpty());
        switch (who) {
            case NOTHING -> {
                return tell(caster, "nothing", "Your armor isn't damaged.", false);
            }
            case FRIEND_FINE -> {
                return tell(caster, "friend_fine", "%s's armor isn't damaged.", false, friend.getDisplayName());
            }
            default -> {
            }
        }
        float cost = SpellMath.cost(data.getMaxEnergy(), ZeroConfig.SERVER.mendKiPerSecond.get());
        if (!caster.isCreative() && !SpellMath.affords(data.getResources().getCurrentEnergy(), cost)) return tell(caster, "no_ki", "Not enough ki.", false);
        ServerPlayer target = who == MendMath.Target.FRIEND ? friend : caster;
        MENDING.put(caster.getUUID(), new Mending(caster, target));
        caster.serverLevel().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        if (target == caster) {
            tell(caster, "started", "Mending. Stand still.", true);
            return "ok";
        }
        tell(caster, "started_friend", "Mending %s's armor. Stand still.", true, target.getDisplayName());
        tell(target, "mended_by", "%s is mending your armor.", true, caster.getDisplayName());
        return "friend";
    }

    /** The party member the caster is looking at, within mend's friend range, or null. */
    static ServerPlayer aimedFriend(ServerPlayer caster) {
        if (!ZeroConfig.SERVER.mendFriends.get()) return null;
        double range = ZeroConfig.SERVER.mendFriendRange.get();
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getViewVector(1f).scale(range));
        BlockHitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = caster.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, end, box,
                e -> e instanceof ServerPlayer p && p != caster && p.isAlive() && !p.isSpectator(), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof ServerPlayer p && mayMend(caster, p) ? p : null;
    }

    /** Whether the caster may mend friend's armor: a party member, unless the server lets anyone. */
    public static boolean mayMend(ServerPlayer caster, ServerPlayer friend) {
        return !ZeroConfig.SERVER.mendFriendsPartyOnly.get() || PartyManager.areInSameParty(caster, friend);
    }

    @Override
    public boolean busy(ServerPlayer caster) {
        return MENDING.containsKey(caster.getUUID());
    }

    @Override
    public void end(ServerPlayer caster, long now) {
        if (MENDING.remove(caster.getUUID()) != null) tell(caster, "stopped", "You stop mending.", false);
    }

    /** The worn armor that has lost durability. */
    public static List<ItemStack> damaged(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.isDamaged()) out.add(stack);
        }
        return out;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || MENDING.isEmpty()) return;
        long start = TickCost.start();
        step();
        TickCost.add(COST, start);
    }

    /** One tick of every mend. The self-test steps it by hand. */
    public static void step() {
        Iterator<Mending> it = MENDING.values().iterator();
        while (it.hasNext()) {
            Mending m = it.next();
            String end = keep(m);
            if (end == null) continue;
            it.remove();
            lastEnd = end;
            switch (end) {
                case "done" -> {
                    if (m.friend()) {
                        tell(m.caster, "done_friend", "%s's armor is mended.", true, m.target.getDisplayName());
                        tell(m.target, "done", "Your armor is mended.", true);
                    } else {
                        tell(m.caster, "done", "Your armor is mended.", true);
                    }
                    m.level.playSound(null, m.target.getX(), m.target.getY(), m.target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.5f);
                }
                case "moved" -> tell(m.caster, "moved", "You moved. The mend stops.", false);
                case "out_of_ki" -> tell(m.caster, "out_of_ki", "Out of ki. The mend stops.", false);
                case "out_of_reach" -> tell(m.caster, "out_of_reach", "%s is out of reach. The mend stops.", false, m.target.getDisplayName());
                default -> {
                }
            }
        }
    }

    // null to keep mending, else why it ends
    private static String keep(Mending m) {
        ServerPlayer caster = m.caster;
        StatsData data = stats(caster);
        if (!Magic.on() || !Spells.on(SPELL.id()) || caster.isRemoved() || caster.level() != m.level || !Magic.canCast(caster, data)) return "lost";
        if (MendMath.moved(caster.position().distanceTo(m.start), MOVE_ALLOWED)) return "moved";
        ServerPlayer target = m.target;
        if (m.friend() && (target.isRemoved() || !target.isAlive() || target.level() != m.level
                || !MendMath.inReach(caster.distanceTo(target), ZeroConfig.SERVER.mendFriendRange.get(), FRIEND_SLACK))) return "out_of_reach";
        m.age++;
        if (m.age % 4 == 0) {
            m.level.sendParticles(LILAC, target.getX(), target.getY() + 1, target.getZ(), 3, 0.4, 0.6, 0.4, 0.01);
            m.level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.2, target.getZ(), 4, 0.5, 0.5, 0.5, 0.5);
        }
        if (m.age % 20 != 0) return null;
        if (m.friend() && !mayMend(caster, target)) return "out_of_reach";
        if (!pay(caster, data, SpellMath.cost(data.getMaxEnergy(), ZeroConfig.SERVER.mendKiPerSecond.get()))) return "out_of_ki";
        double share = ZeroConfig.SERVER.mendShare.get();
        for (ItemStack stack : damaged(target)) stack.setDamageValue(MendMath.repaired(stack.getDamageValue(), stack.getMaxDamage(), share));
        m.pulses++;
        m.level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7f, 1.6f);
        if (damaged(target).isEmpty() || m.pulses >= ZeroConfig.SERVER.mendSeconds.get()) return "done";
        return null;
    }

    private static String tell(ServerPlayer player, String why, String english, boolean bright, Object... args) {
        return SpellKit.tell(player, "mend", why, english, bright, args);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MENDING.clear();
    }

    /** Self-test only: stop every mend. */
    public static void clearAll() {
        MENDING.clear();
    }

    public static boolean mending(ServerPlayer caster) {
        return MENDING.containsKey(caster.getUUID());
    }

    /** Whose armor the caster is mending, or null. */
    public static ServerPlayer mendingFor(ServerPlayer caster) {
        Mending m = MENDING.get(caster.getUUID());
        return m == null ? null : m.target;
    }

    public static String lastEnd() {
        return lastEnd;
    }
}
