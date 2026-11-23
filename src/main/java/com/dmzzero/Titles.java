package com.dmzzero;

import com.dmzzero.network.TitleCardS2C;
import com.dmzzero.network.ZeroNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

// dmz zero's own title card, which fits long names; vanilla's title for players without dmz zero
public final class Titles {

    private Titles() {
    }

    public static void show(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        if (player.connection == null) return;
        if (ZeroNetwork.reachable(player)) {
            ZeroNetwork.send(player, new TitleCardS2C(title, subtitle, stay));
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
