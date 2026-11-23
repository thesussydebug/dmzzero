package com.dmzzero.moon;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.network.ImpactPulseS2C;
import com.dmzzero.network.MoonS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.util.TransformationsHelper;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// a strong enough ki attack fired at the moon destroys it: no oozaru by the moon in that dimension until it comes back
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Moon {
    private static final int COST = TickCost.id("world.moon");
    private static final int SKY_STEPS = 512;
    private static final int FAKE_MOON_RANGE = 200;
    private static int destroyed;

    private Moon() {
    }

    // dmz posts this on the server once a charged ki attack has really gone off; it aims where the shooter looks
    @SubscribeEvent
    public static void onKiFire(DMZEvent.KiAttackFireEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p) || !ZeroConfig.SERVER.moonDestroyable.get()) return;
        KiAttackData data = event.getKiAttack();
        if (data == null || data.getKiType() == null || !MoonMath.destroys(data.getKiType().name())) return;
        tryHit(p, p.getLookAngle());
    }

    /** Destroys the moon when that shot hits it through open sky. Returns whether it did. */
    public static boolean tryHit(ServerPlayer p, Vec3 aim) {
        ServerLevel level = p.serverLevel();
        if (!MoonState.hasMoon(level) || MoonSave.get(level).gone()) return false;
        if (!MoonMath.hit(new V(aim.x, aim.y, aim.z), level.getTimeOfDay(1f))) return false;
        if (!openSky(level, p.getEyePosition(), aim.normalize())) return false;
        destroy(level, p.getGameProfile().getName());
        return true;
    }

    // walks the shot up to the top of the world; anything solid in the way stops it. past the loaded world it's open sky
    static boolean openSky(ServerLevel level, Vec3 from, Vec3 dir) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= SKY_STEPS; i++) {
            pos.set(from.x + dir.x * i, from.y + dir.y * i, from.z + dir.z * i);
            if (pos.getY() >= level.getMaxBuildHeight() || !level.isLoaded(pos)) return true;
            if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) continue;
            if (level.getBlockState(pos).blocksMotion()) return false;
        }
        return true;
    }

    public static void destroy(ServerLevel level, String by) {
        MoonSave.get(level).destroy(MoonMath.returnsAt(level.getGameTime(), ZeroConfig.SERVER.moonReturnDays.get()), by);
        destroyed++;
        boolean revert = ZeroConfig.SERVER.moonRevertsOozaru.get();
        Component told = Component.translatableWithFallback("dmzzero.moon.destroyed", "%s destroyed the moon!", by).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer pl : level.players()) {
            ZeroNetwork.send(pl, new MoonS2C(true, true));
            pl.sendSystemMessage(told);
            if (level.canSeeSky(pl.blockPosition())) {
                ZeroNetwork.send(pl, new ImpactPulseS2C(pl.getX(), pl.getY(), pl.getZ(), 0.35f, 64f, 0xE8EEFF, 0.5f, 0.05f));
                pl.playNotifySound(SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 0.8f, 0.5f);
            }
            if (revert) shrink(pl);
        }
        DMZZero.LOG.info("[DMZ Zero] {} destroyed the moon in {}", by, level.dimension().location());
    }

    public static void restore(ServerLevel level) {
        MoonSave.get(level).restore();
        Component told = Component.translatableWithFallback("dmzzero.moon.back", "The moon is back in the sky.").withStyle(ChatFormatting.AQUA);
        for (ServerPlayer pl : level.players()) {
            ZeroNetwork.send(pl, new MoonS2C(false, false));
            pl.sendSystemMessage(told);
        }
    }

    // with the moon gone, an oozaru by the moon shrinks back, as gohan did; one kept up by a fake moon nearby stays
    public static boolean shrink(ServerPlayer pl) {
        if (!MoonState.gone(pl.serverLevel())) return false;
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, pl).orElse(null);
        if (d == null || !oozaru(d.getCharacter().getActiveFormData())) return false;
        if (fakeMoonNear(pl)) return false;
        if (!TransformationsHelper.revertToBaseForm(pl, d)) return false;
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(pl), pl);
        pl.sendSystemMessage(Component.translatableWithFallback("dmzzero.moon.shrink", "Without the moon, you shrink back to normal.")
                .withStyle(ChatFormatting.GRAY));
        return true;
    }

    // dmz's moon forms: oozaru and golden oozaru
    static boolean oozaru(FormConfig.FormData form) {
        if (form == null || form.getName() == null) return false;
        String name = form.getName();
        return name.equalsIgnoreCase("oozaru") || name.equalsIgnoreCase("goldenoozaru");
    }

    private static boolean fakeMoonNear(ServerPlayer pl) {
        return !pl.serverLevel().getEntitiesOfClass(KiBlastEntity.class, pl.getBoundingBox().inflate(FAKE_MOON_RANGE),
                e -> e.getKiRenderType() == KiBlastEntity.RENDER_FAKE_MOON && !e.isRemoved()).isEmpty();
    }

    // once a second per dimension with a moon: has it come back yet
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        long start = TickCost.start();
        levelTick(event);
        TickCost.add(COST, start);
    }

    private static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % 20 != 0 || !MoonState.hasMoon(level)) return;
        tickReturn(level);
    }

    public static boolean tickReturn(ServerLevel level) {
        MoonSave s = MoonSave.get(level);
        if (!s.gone() || level.getGameTime() < s.returnsAt()) return false;
        restore(level);
        return true;
    }

    // every client hears the state of the level it's in: on joining, changing dimension and respawning
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    public static void sync(ServerPlayer p) {
        ZeroNetwork.send(p, new MoonS2C(MoonState.gone(p.serverLevel()), false));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzzero")
                .then(Commands.literal("moon")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("destroy")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> destroyCommand(ctx.getSource())))
                        .then(Commands.literal("restore")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> restoreCommand(ctx.getSource())))));
    }

    private static int status(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        if (!MoonState.hasMoon(level)) {
            src.sendFailure(Component.translatableWithFallback("dmzzero.moon.status.none", "There's no moon in this sky."));
            return 0;
        }
        MoonSave s = MoonSave.get(level);
        Component line = s.gone()
                ? Component.translatableWithFallback("dmzzero.moon.status.gone", "The moon is gone, destroyed by %s. It comes back in %s min.",
                s.by(), MoonMath.minutesLeft(level.getGameTime(), s.returnsAt()))
                : Component.translatableWithFallback("dmzzero.moon.status.up", "The moon is in the sky.");
        src.sendSuccess(() -> line, false);
        return 1;
    }

    private static int destroyCommand(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        if (!MoonState.hasMoon(level)) {
            src.sendFailure(Component.translatableWithFallback("dmzzero.moon.status.none", "There's no moon in this sky."));
            return 0;
        }
        if (MoonSave.get(level).gone()) {
            src.sendFailure(Component.translatableWithFallback("dmzzero.moon.already", "The moon is already gone."));
            return 0;
        }
        destroy(level, src.getTextName());
        return 1;
    }

    private static int restoreCommand(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        if (!MoonState.hasMoon(level) || !MoonSave.get(level).gone()) {
            src.sendFailure(Component.translatableWithFallback("dmzzero.moon.notgone", "The moon is already in the sky."));
            return 0;
        }
        restore(level);
        return 1;
    }

    public static int destroyedCount() {
        return destroyed;
    }
}
