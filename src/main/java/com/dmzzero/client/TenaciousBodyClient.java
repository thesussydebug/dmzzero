package com.dmzzero.client;

import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.passive.TenaciousBody;
import com.dragonminez.client.gui.character.util.RacialSkillParts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class TenaciousBodyClient {
    // dmz's skills list names racial rows by this key, not by the part's own name
    private static final String KEY = "skill.dragonminez." + TenaciousBody.PART_ID;

    private TenaciousBodyClient() {
    }

    // only show what the server really gives: it needs dmz zero and the passive turned on
    public static boolean active() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ZeroNetwork.CHANNEL.isRemotePresent(connection.getConnection()) && TenaciousBody.enabled();
    }

    public static void addPart(List<RacialSkillParts.Part> parts, String racialSkill, boolean android, boolean allVariants) {
        if (!TenaciousBody.humanSkill(racialSkill) || !TenaciousBody.listed(android, allVariants) || !active()) return;
        RacialSkillParts.Part part = new RacialSkillParts.Part(TenaciousBody.PART_ID,
                Component.translatableWithFallback(KEY, "Tenacious Body"),
                Component.translatable(KEY + ".desc", TenaciousBody.bonus()));
        TenaciousBody.insert(parts, part, RacialSkillParts.Part::id);
    }
}
