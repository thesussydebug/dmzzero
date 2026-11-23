package com.dmzzero.settings;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.ServerSettingsS2C;
import com.dmzzero.network.ZeroNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Path;

// config/dmzzero/server.toml: carried over from a world's old file while it's untouched, and sent to every player who joins
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class ServerSettings {

    private ServerSettings() {
    }

    /** Runs in the mod constructor, before forge reads any config file. */
    public static void prepare(Path configDir) {
        try {
            if (ConfigFiles.moveOldClientFile(configDir)) DMZZero.LOG.info("Moved dmzzero-client.toml to config/{}", ConfigFiles.CLIENT);
        } catch (IOException e) {
            DMZZero.LOG.warn("Couldn't move dmzzero-client.toml to config/{}: {}", ConfigFiles.CLIENT, e.toString());
        }
    }

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        Path old = event.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(ConfigFiles.OLD_SERVER);
        try {
            int copied = ConfigFiles.copyIfUntouched(old, ZeroConfig.SERVER_SPEC);
            if (copied < 0) return;
            ZeroConfig.SERVER_SPEC.save();
            DMZZero.LOG.info("Copied {} settings from {} to config/{}; the old file is no longer read", copied, old, ConfigFiles.SERVER);
        } catch (IOException e) {
            DMZZero.LOG.warn("Couldn't read the old settings in {}: {}", old, e.toString());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ZeroNetwork.send(player, ServerSettingsS2C.of(ZeroConfig.SERVER_SPEC));
    }

    /** Mod bus. Forge reloads the file when it changes on disk, on its own watcher thread. */
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ZeroConfig.SERVER_SPEC) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> sendToAll(server));
    }

    private static void sendToAll(MinecraftServer server) {
        ServerSettingsS2C msg = ServerSettingsS2C.of(ZeroConfig.SERVER_SPEC);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) ZeroNetwork.send(player, msg);
    }
}
