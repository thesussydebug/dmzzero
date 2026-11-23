package com.dmzzero;

import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.settings.ConfigFiles;
import com.dmzzero.settings.ServerSettings;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

@Mod(DMZZero.MOD_ID)
public final class DMZZero {
    public static final String MOD_ID = "dmzzero";
    public static final Logger LOG = LogUtils.getLogger();

    public DMZZero() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // every toml file lives in config/dmzzero/; the server one is common so it isn't kept per world
        ServerSettings.prepare(FMLPaths.CONFIGDIR.get());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ZeroConfig.SERVER_SPEC, ConfigFiles.SERVER);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ZeroConfig.CLIENT_SPEC, ConfigFiles.CLIENT);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ServerSettings::onReload);
        // the diets tell appleskin their food numbers; the class is only loaded when appleskin is there
        if (FMLEnvironment.dist == Dist.CLIENT && ModList.get().isLoaded("appleskin")) com.dmzzero.client.AppleSkinCompat.register();
        // the config button in the mods menu; the screen class is only loaded on the client
        if (FMLEnvironment.dist == Dist.CLIENT) com.dmzzero.client.SettingsScreen.register();
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(ZeroNetwork::register);
        // after dmz's own racial abilities, which dmz registers before this runs
        event.enqueueWork(() -> {
            if (com.dmzzero.magic.GlindMagic.install()) LOG.info("DMZ Zero: Glinds can cast spells");
        });
        ModList mods = ModList.get();
        LOG.info("DMZ Zero {} with DragonMineZ {}, Overhaul {}, Sparking {}",
                version(mods, MOD_ID), version(mods, "dragonminez"), version(mods, "dmzrevamp"), version(mods, "dmzsparking"));
    }

    private static String version(ModList mods, String id) {
        return mods.getModContainerById(id)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("not installed");
    }
}
