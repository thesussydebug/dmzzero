package com.dmzzero.network;

import com.dmzzero.DMZZero;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ZeroNetwork {
    // 11: the server settings sent to players are keyed by their regrouped paths ("fighting.swat.reach")
    private static final String PROTOCOL = "11";

    // either side may lack dmz zero, a server-only install still works
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DMZZero.MOD_ID, "main"),
            () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL),
            NetworkRegistry.acceptMissingOr(PROTOCOL));

    private ZeroNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(PotentialSyncS2C.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PotentialSyncS2C::encode)
                .decoder(PotentialSyncS2C::decode)
                .consumerMainThread(PotentialSyncS2C::handle)
                .add();
        CHANNEL.messageBuilder(ImpactPulseS2C.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ImpactPulseS2C::encode)
                .decoder(ImpactPulseS2C::decode)
                .consumerMainThread(ImpactPulseS2C::handle)
                .add();
        CHANNEL.messageBuilder(PowerSenseS2C.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PowerSenseS2C::encode)
                .decoder(PowerSenseS2C::decode)
                .consumerMainThread(PowerSenseS2C::handle)
                .add();
        CHANNEL.messageBuilder(TitleCardS2C.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TitleCardS2C::encode)
                .decoder(TitleCardS2C::decode)
                .consumerMainThread(TitleCardS2C::handle)
                .add();
        CHANNEL.messageBuilder(KiTossC2S.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KiTossC2S::encode)
                .decoder(KiTossC2S::decode)
                .consumerNetworkThread(KiTossC2S::handle)
                .add();
        CHANNEL.messageBuilder(ChamberStateS2C.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChamberStateS2C::encode)
                .decoder(ChamberStateS2C::decode)
                .consumerNetworkThread(ChamberStateS2C::handle)
                .add();
        CHANNEL.messageBuilder(ScouterPingC2S.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScouterPingC2S::encode)
                .decoder(ScouterPingC2S::decode)
                .consumerNetworkThread(ScouterPingC2S::handle)
                .add();
        CHANNEL.messageBuilder(ScouterPingS2C.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScouterPingS2C::encode)
                .decoder(ScouterPingS2C::decode)
                .consumerMainThread(ScouterPingS2C::handle)
                .add();
        CHANNEL.messageBuilder(MoonS2C.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MoonS2C::encode)
                .decoder(MoonS2C::decode)
                .consumerMainThread(MoonS2C::handle)
                .add();
        CHANNEL.messageBuilder(BeamCancelC2S.class, 9, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BeamCancelC2S::encode)
                .decoder(BeamCancelC2S::decode)
                .consumerNetworkThread(BeamCancelC2S::handle)
                .add();
        CHANNEL.messageBuilder(KamiWatchS2C.class, 10, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KamiWatchS2C::encode)
                .decoder(KamiWatchS2C::decode)
                .consumerMainThread(KamiWatchS2C::handle)
                .add();
        CHANNEL.messageBuilder(ServerSettingsS2C.class, 11, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerSettingsS2C::encode)
                .decoder(ServerSettingsS2C::decode)
                .consumerNetworkThread(ServerSettingsS2C::handle)
                .add();
        CHANNEL.messageBuilder(CalloutS2C.class, 12, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CalloutS2C::encode)
                .decoder(CalloutS2C::decode)
                .consumerMainThread(CalloutS2C::handle)
                .add();
        CHANNEL.messageBuilder(SpellC2S.class, 13, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SpellC2S::encode)
                .decoder(SpellC2S::decode)
                .consumerNetworkThread(SpellC2S::handle)
                .add();
    }

    /** The channel version: a client and server connect only when theirs match, or one of them has no DMZ Zero. */
    public static String protocol() {
        return PROTOCOL;
    }

    public static boolean reachable(ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.connection != null && CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void send(ServerPlayer player, Object message) {
        if (reachable(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendPotential(ServerPlayer player, int potential) {
        send(player, new PotentialSyncS2C(potential));
    }

    public static void sendAround(ServerLevel level, Vec3 at, double range, Object message) {
        double r2 = range * range;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) <= r2) send(player, message);
        }
    }
}
