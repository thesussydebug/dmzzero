package com.dmzzero.magic;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.kishare.TossMath;
import com.dmzzero.sense.CreaturePower;
import com.dmzzero.sense.PowerSense;
import com.dmzzero.smash.SmashMath;
import com.dmzzero.smash.Smashes;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dmzzero.magic.SpellKit.offLimits;
import static com.dmzzero.magic.SpellKit.pay;
import static com.dmzzero.magic.SpellKit.sparkle;
import static com.dmzzero.magic.SpellKit.stats;
import static com.dmzzero.magic.SpellKit.v;

// the first glind spell. cast at a weaker mob to hold it floating in front of you, cast again to throw it; cast at a
// dropped item or experience to pull it over. the server moves what's held every tick, so everyone sees it
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Telekinesis implements Spell {
    public static final Telekinesis SPELL = new Telekinesis();
    private static final int COST = TickCost.id("magic.telekinesis", () -> Telekinesis.HOLDS.size() + Telekinesis.THROWN.size());
    private static final int MAX_THROWN = 64;
    private static final int THROWN_TICKS = 40;
    private static final int FETCH_TICKS = 80;
    private static final double GRAB_SLACK = 2;
    private static final double PULL_GAIN = 0.35;
    private static final double PULL_MAX = 1.2;
    private static final double FETCH_MAX = 1.0;
    private static final double FETCH_DONE = 1.5;
    private static final double IMPACT_MIN = 0.3;
    private static final float FETCH_SHARE = 0.25f;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, UUID> HELD_BY = new HashMap<>();
    private static final List<Thrown> THROWN = new ArrayList<>();
    private static final TossMath.Cooldowns COOLDOWNS = new TossMath.Cooldowns();
    private static volatile Boolean testPlayers;
    private static int thrown;
    private static int impacts;
    private static String lastEnd = "";
    private static float lastImpact;

    private Telekinesis() {
    }

    static final class Hold {
        final ServerPlayer caster;
        final ServerLevel level;
        final Entity target;
        final boolean fetch;
        long age;
        int struggle;

        Hold(ServerPlayer caster, ServerLevel level, Entity target, boolean fetch) {
            this.caster = caster;
            this.level = level;
            this.target = target;
            this.fetch = fetch;
        }
    }

    static final class Thrown {
        final ServerPlayer caster;
        final ServerLevel level;
        final LivingEntity target;
        final SmashMath.Watch watch;
        Vec3 prev;
        int age;

        Thrown(ServerPlayer caster, ServerLevel level, LivingEntity target, double speed) {
            this.caster = caster;
            this.level = level;
            this.target = target;
            this.watch = new SmashMath.Watch(speed);
            this.prev = target.position();
        }
    }

    @Override
    public String id() {
        return "telekinesis";
    }

    @Override
    public String english() {
        return "Telekinesis";
    }

    @Override
    public ForgeConfigSpec.BooleanValue toggle() {
        return ZeroConfig.SERVER.telekinesis;
    }

    @Override
    public String cast(ServerPlayer caster, StatsData data, long now) {
        Hold held = HOLDS.remove(caster.getUUID());
        if (held != null) return release(held, held.fetch ? "dropped" : "thrown", now);
        long wait = COOLDOWNS.remaining(caster.getUUID(), now);
        if (wait > 0) return tell(caster, "cooldown", (wait + 19) / 20);
        return grab(caster, data, aim(caster), now);
    }

    @Override
    public boolean busy(ServerPlayer caster) {
        return HOLDS.containsKey(caster.getUUID());
    }

    @Override
    public void end(ServerPlayer caster, long now) {
        Hold held = HOLDS.remove(caster.getUUID());
        if (held != null) release(held, "dropped", now);
    }

    @Override
    public boolean holds(Entity entity) {
        return HELD_BY.containsKey(entity.getUUID());
    }

    /** What the caster is looking at: an entity along the look, else a dropped item or orb near where it ends. */
    static Entity aim(ServerPlayer caster) {
        double range = ZeroConfig.SERVER.tkRange.get();
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getViewVector(1f).scale(range));
        BlockHitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = caster.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, end, box, e -> e != caster && e.isAlive() && !e.isSpectator()
                && (e instanceof LivingEntity || thing(e)), eye.distanceToSqr(end));
        if (hit != null) return hit.getEntity();
        if (!items()) return null;
        Vec3 at = end;
        return caster.level().getEntities(caster, new AABB(at, at).inflate(1.5), e -> thing(e) && e.isAlive()).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(at))).orElse(null);
    }

    private static boolean thing(Entity e) {
        return e instanceof ItemEntity || e instanceof ExperienceOrb;
    }

    /** "ok" when the caster may take hold of target, or why not. */
    public static String check(ServerPlayer caster, StatsData data, Entity target) {
        if (!Spells.on(SPELL.id())) return "off";
        if (target == null || target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) return "no_target";
        if (target.level() != caster.level() || caster.distanceTo(target) > ZeroConfig.SERVER.tkRange.get() + GRAB_SLACK) return "no_target";
        if (HELD_BY.containsKey(target.getUUID()) || HOLDS.containsKey(target.getUUID())) return "taken";
        if (thing(target)) return items() ? "ok" : "no_target";
        if (!(target instanceof LivingEntity living) || offLimits(living, caster)) return "no_target";
        if (living.getType().is(Tags.EntityTypes.BOSSES)) return "too_strong";
        double theirs = CreaturePower.of(living);
        if (living instanceof Player player) {
            if (!players() || !SpellKit.mayHurt(caster, player)) return "players_off";
            StatsData their = stats(player);
            theirs = their == null ? 0 : PowerSense.fullPower(their);
        }
        if (!TelekinesisMath.canLift(PowerSense.fullPower(data), theirs, ZeroConfig.SERVER.tkStrengthRatio.get())) return "too_strong";
        return "ok";
    }

    /** Takes hold of target after the checks and the ki. The self-test calls it straight. */
    public static String grab(ServerPlayer caster, StatsData data, Entity target, long now) {
        String why = check(caster, data, target);
        if ("off".equals(why)) return why;
        if (!"ok".equals(why)) return tell(caster, why, target == null ? Component.empty() : target.getDisplayName());
        boolean fetch = thing(target);
        float cost = SpellMath.cost(data.getMaxEnergy(), ZeroConfig.SERVER.tkKiCost.get()) * (fetch ? FETCH_SHARE : 1f);
        if (!pay(caster, data, cost)) return tell(caster, "no_ki");
        if (target.isPassenger()) target.stopRiding();
        if (target.isVehicle()) target.ejectPassengers();
        HOLDS.put(caster.getUUID(), new Hold(caster, caster.serverLevel(), target, fetch));
        HELD_BY.put(target.getUUID(), caster.getUUID());
        caster.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.3f);
        sparkle(caster.serverLevel(), target, 12);
        if (fetch) {
            tell(caster, "fetching");
        } else {
            tell(caster, "holding", target.getDisplayName());
            if (target instanceof ServerPlayer held) {
                held.displayClientMessage(Component.translatableWithFallback("message.dmzzero.telekinesis.held",
                        "%s has you in telekinesis. Charge ki to break free.", caster.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        return "ok";
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HOLDS.isEmpty() && THROWN.isEmpty()) return;
        long start = TickCost.start();
        step(event.getServer().getTickCount());
        TickCost.add(COST, start);
    }

    /** One tick: move what's held, drop what can't be held any more, and watch thrown mobs land. */
    public static void step(long now) {
        if (!HOLDS.isEmpty()) {
            Iterator<Hold> it = HOLDS.values().iterator();
            List<Hold> ended = new ArrayList<>();
            List<String> why = new ArrayList<>();
            while (it.hasNext()) {
                Hold h = it.next();
                String end = keep(h);
                if (end != null) {
                    it.remove();
                    ended.add(h);
                    why.add(end);
                }
            }
            for (int i = 0; i < ended.size(); i++) release(ended.get(i), why.get(i), now);
        }
        if (!THROWN.isEmpty()) {
            Iterator<Thrown> it = THROWN.iterator();
            while (it.hasNext()) {
                Thrown f = it.next();
                if (land(f)) it.remove();
            }
        }
    }

    // null to keep holding, else why it ends
    private static String keep(Hold h) {
        ServerPlayer caster = h.caster;
        Entity target = h.target;
        if (!Magic.on() || !Spells.on(SPELL.id())) return "dropped";
        StatsData data = stats(caster);
        if (caster.isRemoved() || caster.level() != h.level || !Magic.canCast(caster, data)) return "lost";
        if (target.isRemoved() || !target.isAlive() || target.level() != h.level) return "gone";
        if (TelekinesisMath.slipped(caster.distanceTo(target), ZeroConfig.SERVER.tkRange.get())) return "slipped";
        h.age++;
        if (h.fetch) {
            Vec3 to = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
            if (target.position().distanceTo(to) < FETCH_DONE) return "fetched";
            if (h.age > FETCH_TICKS) return "gone";
            V v = TelekinesisMath.pull(v(target.position()), v(to), PULL_GAIN, FETCH_MAX);
            target.setDeltaMovement(v.x(), v.y() + 0.04, v.z());
            target.hurtMarked = true;
            if (h.age % 3 == 0) sparkle(h.level, target, 2);
            return null;
        }
        if (TelekinesisMath.expired(h.age, ZeroConfig.SERVER.tkMaxSeconds.get())) return "tired";
        if (h.age % 20 == 0 && !pay(caster, data, SpellMath.cost(data.getMaxEnergy(), ZeroConfig.SERVER.tkKiPerSecond.get()))) return "out_of_ki";
        if (target instanceof ServerPlayer held) {
            StatsData theirs = stats(held);
            h.struggle = theirs != null && theirs.getStatus().isChargingKi() ? h.struggle + 1 : Math.max(0, h.struggle - 1);
            if (TelekinesisMath.breaksFree(h.struggle, ZeroConfig.SERVER.tkBreakFreeSeconds.get() * 20)) return "broke_free";
        }
        V point = TelekinesisMath.holdPoint(v(caster.getEyePosition()), v(caster.getViewVector(1f)),
                ZeroConfig.SERVER.tkHoldDistance.get(), target.getBbHeight() * 0.5);
        V pull = TelekinesisMath.pull(v(target.position()), point, PULL_GAIN, PULL_MAX);
        // gravity takes 0.08 a tick off a living entity's motion
        target.setDeltaMovement(pull.x(), pull.y() + 0.08, pull.z());
        target.fallDistance = 0;
        target.hurtMarked = true;
        if (target instanceof Mob mob) mob.getNavigation().stop();
        if (h.age % 3 == 0) sparkle(h.level, target, 4);
        return null;
    }

    private static String release(Hold h, String why, long now) {
        HELD_BY.remove(h.target.getUUID());
        ServerPlayer caster = h.caster;
        Entity target = h.target;
        lastEnd = why;
        if (!"fetched".equals(why) && !"lost".equals(why)) COOLDOWNS.start(caster.getUUID(), now, ZeroConfig.SERVER.tkCooldown.get() * 20L);
        target.fallDistance = 0;
        if ("thrown".equals(why)) {
            V toss = TelekinesisMath.toss(v(caster.getViewVector(1f)), ZeroConfig.SERVER.tkThrowSpeed.get());
            Vec3 motion = new Vec3(toss.x(), toss.y(), toss.z());
            target.setDeltaMovement(motion);
            target.hurtMarked = true;
            if (target instanceof LivingEntity living) {
                Smashes.launched(living, motion);
                if (THROWN.size() < MAX_THROWN) THROWN.add(new Thrown(caster, h.level, living, motion.length()));
            }
            thrown++;
            h.level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 1.2f);
            sparkle(h.level, target, 10);
            return tell(caster, "thrown");
        }
        if (target instanceof ServerPlayer held && !held.onGround()) {
            held.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
        }
        if ("broke_free".equals(why)) {
            if (target instanceof ServerPlayer held) {
                held.displayClientMessage(Component.translatableWithFallback("message.dmzzero.telekinesis.you_broke_free", "You broke free.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            h.level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 1f);
            return tell(caster, why, target.getDisplayName());
        }
        if ("fetched".equals(why) || "lost".equals(why) || "gone".equals(why)) return why;
        h.level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.PLAYERS, 1f, 0.8f);
        return tell(caster, why);
    }

    // true when this thrown mob is done: it hit something, or flew long enough
    private static boolean land(Thrown f) {
        LivingEntity e = f.target;
        f.age++;
        if (e.isRemoved() || !e.isAlive() || e.level() != f.level || f.age > THROWN_TICKS) return true;
        Vec3 pos = e.position();
        double moved = pos.distanceTo(f.prev);
        f.prev = pos;
        if (f.watch.tick(moved, e.horizontalCollision, e.verticalCollision, IMPACT_MIN) == SmashMath.Watch.NONE) return false;
        StatsData data = stats(f.caster);
        float damage = TelekinesisMath.impact(data == null ? 0 : data.getMeleeDamage(), ZeroConfig.SERVER.tkThrowDamage.get(),
                f.watch.speed(), ZeroConfig.SERVER.tkThrowSpeed.get());
        if (damage > 0) {
            DamageSource source = new DamageSource(f.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DamageTypes.FLY_INTO_WALL), f.caster);
            e.hurt(source, damage);
        }
        impacts++;
        lastImpact = damage;
        return true;
    }

    private static String tell(ServerPlayer caster, String why, Object... args) {
        caster.displayClientMessage(Component.translatableWithFallback("message.dmzzero.telekinesis." + why, english(why), args)
                .withStyle("holding".equals(why) || "fetching".equals(why) || "thrown".equals(why) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
        return why;
    }

    private static String english(String why) {
        return switch (why) {
            case "no_target" -> "Nothing to lift there.";
            case "too_strong" -> "%s is too strong to lift.";
            case "taken" -> "Someone else is already holding that.";
            case "players_off" -> "You can't lift other players here.";
            case "no_ki" -> "Not enough ki.";
            case "cooldown" -> "Telekinesis ready in %ss.";
            case "holding" -> "Holding %s. Cast again to throw it.";
            case "fetching" -> "Pulling it to you.";
            case "thrown" -> "Thrown.";
            case "tired" -> "You can't hold it any longer.";
            case "out_of_ki" -> "Out of ki. You let go.";
            case "slipped" -> "It slipped out of your grip.";
            case "broke_free" -> "%s broke free.";
            default -> "You let go.";
        };
    }

    private static boolean players() {
        Boolean test = testPlayers;
        return test != null ? test : ZeroConfig.SERVER.tkPlayers.get();
    }

    private static boolean items() {
        return ZeroConfig.SERVER.tkItems.get();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        long now = event.getEntity().getServer() == null ? 0 : event.getEntity().getServer().getTickCount();
        Hold mine = HOLDS.remove(id);
        if (mine != null) release(mine, "lost", now);
        UUID holder = HELD_BY.get(id);
        Hold theirs = holder == null ? null : HOLDS.remove(holder);
        if (theirs != null) release(theirs, "gone", now);
        THROWN.removeIf(f -> f.caster.getUUID().equals(id) || f.target.getUUID().equals(id));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HOLDS.clear();
        HELD_BY.clear();
        THROWN.clear();
        COOLDOWNS.clear();
        testPlayers = null;
    }

    /** Self-test only: whether players can be lifted, without writing server.toml. Null clears it. */
    public static void setTestPlayers(Boolean on) {
        testPlayers = on;
    }

    /** Self-test only: drop everything and forget the cooldowns. */
    public static void clearAll() {
        HOLDS.clear();
        HELD_BY.clear();
        THROWN.clear();
        COOLDOWNS.clear();
    }

    public static boolean holding(ServerPlayer caster, Entity target) {
        Hold h = HOLDS.get(caster.getUUID());
        return h != null && h.target == target;
    }

    public static int throwsMade() {
        return thrown;
    }

    public static int impacts() {
        return impacts;
    }

    public static float lastImpact() {
        return lastImpact;
    }

    public static String lastEnd() {
        return lastEnd;
    }
}
