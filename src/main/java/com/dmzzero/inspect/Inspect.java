package com.dmzzero.inspect;

import com.dmzzero.DMZZero;
import com.dmzzero.chamber.ChamberDepths;
import com.dmzzero.diet.DietMath;
import com.dmzzero.diet.Diets;
import com.dmzzero.duel.DuelMath;
import com.dmzzero.duel.Duels;
import com.dmzzero.fusion.FusionRecord;
import com.dmzzero.impact.FirstForms;
import com.dmzzero.impact.ImpactData;
import com.dmzzero.potential.Awakening;
import com.dmzzero.potential.PotentialData;
import com.dmzzero.realm.BattleMath;
import com.dmzzero.realm.Battles;
import com.dmzzero.realm.NullRealm;
import com.dmzzero.rival.RivalData;
import com.dmzzero.rival.RivalShadows;
import com.dmzzero.scouter.ScouterMath;
import com.dmzzero.scouter.Scouters;
import com.dmzzero.sense.KnownKi;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

// /dmzzero inspect <player> (op): everything DMZ Zero keeps on a player, online or from their saved file
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Inspect {

    private Inspect() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzzero")
                .then(Commands.literal("inspect")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> inspect(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player"))))));
    }

    private static int inspect(CommandSourceStack src, Collection<GameProfile> profiles) throws CommandSyntaxException {
        if (profiles.size() != 1) {
            src.sendFailure(text(InspectReport.pickOne()));
            return 0;
        }
        GameProfile profile = profiles.iterator().next();
        List<InspectReport.Line> lines = report(src.getServer(), profile);
        if (lines == null) {
            src.sendFailure(text(InspectReport.noData(profile.getName())));
            return 0;
        }
        boolean first = true;
        for (InspectReport.Line line : lines) {
            MutableComponent text = text(line).withStyle(first ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            first = false;
            src.sendSuccess(() -> text, false);
        }
        return 1;
    }

    static MutableComponent text(InspectReport.Line line) {
        return Component.translatableWithFallback(line.key(), line.fallback(), line.args().toArray());
    }

    /** The report for that player, or null when they're offline and have never played here. */
    public static List<InspectReport.Line> report(MinecraftServer server, GameProfile profile) {
        ServerPlayer online = profile.getId() == null ? null : server.getPlayerList().getPlayer(profile.getId());
        if (online != null) return report(online);
        CompoundTag persisted = savedPersisted(server, profile);
        if (persisted == null) return null;
        return InspectReport.lines(saved(InspectReport.Facts.builder(profile.getName()), persisted).build());
    }

    /** The report for a player who is in the world (the self-test's fake players aren't in the player list). */
    public static List<InspectReport.Line> report(ServerPlayer online) {
        CompoundTag persisted = online.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        InspectReport.Facts.Builder facts = saved(InspectReport.Facts.builder(online.getGameProfile().getName()), persisted);
        live(facts, online);
        return InspectReport.lines(facts.build());
    }

    static InspectReport.Facts.Builder saved(InspectReport.Facts.Builder f, CompoundTag persisted) {
        f.potential(PotentialData.read(persisted), Awakening.awakened(persisted));
        if (RivalData.rival(persisted) != null) {
            f.rival(RivalData.name(persisted), RivalData.wins(persisted), RivalData.losses(persisted),
                    date(RivalData.since(persisted)), RivalData.snapshot(persisted) != null);
        }
        int[] duels = DuelMath.score(persisted);
        int[] battles = BattleMath.score(persisted);
        return f.duels(duels[0], duels[1], duels[2])
                .battles(battles[0], battles[1], battles[2])
                .knownKi(KnownKi.names(persisted))
                .firstForms(FirstForms.count(persisted))
                .fusions(FusionRecord.count(persisted, FusionRecord.FUSED))
                .destructionOff(ImpactData.destructionOff(persisted))
                .realmReturn(NullRealm.hasReturn(persisted));
    }

    private static void live(InspectReport.Facts.Builder f, ServerPlayer p) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        f.online(data == null ? "" : data.getCharacter().getRaceName(), data == null ? "" : data.getCharacter().getActiveForm());
        String opponent = Duels.opponentName(p);
        if (opponent != null) f.now(InspectReport.nowDuel(opponent));
        if (Battles.involved(p)) f.now(InspectReport.nowBattle());
        if (ChamberDepths.inChamber(p)) f.now(InspectReport.nowChamber(ChamberDepths.zoneOf(p)));
        ScouterMath.Channel channel = Scouters.wearing(p);
        if (channel != null) f.now(InspectReport.nowScouter(channel.id()));
        DietMath.Diet diet = Diets.dietOf(p);
        if (diet != DietMath.Diet.NONE) f.now(InspectReport.nowDiet(diet.name()));
        if (RivalShadows.active(p)) f.now(InspectReport.nowShadow());
    }

    // forge keeps PlayerPersisted under ForgeData in the player's file
    private static CompoundTag savedPersisted(MinecraftServer server, GameProfile profile) {
        if (profile.getId() == null) return null;
        File file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(profile.getId() + ".dat").toFile();
        if (!file.isFile()) return null;
        try {
            CompoundTag root = NbtIo.readCompressed(file);
            return root.getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG);
        } catch (Exception e) {
            DMZZero.LOG.warn("Couldn't read the saved data of {}", profile.getName(), e);
            return null;
        }
    }

    private static String date(long epochMs) {
        if (epochMs <= 0) return "?";
        return DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
    }
}
