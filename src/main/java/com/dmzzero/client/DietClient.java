package com.dmzzero.client;

import com.dmzzero.diet.Diets;
import com.dmzzero.network.ZeroNetwork;
import com.dragonminez.client.gui.character.util.RacialSkillParts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DietClient {
    public static final String NAMEKIAN_PART = "racial_namekian.dmzzero_diet";
    public static final String MAJIN_PART = "racial_majin.dmzzero_diet";
    private static final Set<String> NAMEKIAN_SKILLS = Set.of("namekian", "namekianrevamp");
    private static final Set<String> MAJIN_SKILLS = Set.of("majin", "majinrevamp");

    private DietClient() {
    }

    // only what the server really does: it needs dmz zero
    public static boolean active() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ZeroNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }

    public static void addParts(List<RacialSkillParts.Part> parts, String racialSkill) {
        if (racialSkill == null || !active()) return;
        String skill = racialSkill.toLowerCase(Locale.ROOT);
        if (NAMEKIAN_SKILLS.contains(skill) && Diets.namekianOn()) {
            add(parts, NAMEKIAN_PART, "Namekian Diet", Component.translatable(key(NAMEKIAN_PART) + ".desc",
                    Diets.waterHunger(), Math.round(Diets.namekianFactor() * 100)));
        }
        if (MAJIN_SKILLS.contains(skill) && Diets.majinOn()) {
            add(parts, MAJIN_PART, "Majin Diet", Component.translatable(key(MAJIN_PART) + ".desc", number(Diets.sweetsFactor())));
        }
    }

    // dmz's skills list names racial rows by this key, not by the part's own name
    private static String key(String part) {
        return "skill.dragonminez." + part;
    }

    private static void add(List<RacialSkillParts.Part> parts, String id, String fallback, net.minecraft.network.chat.MutableComponent desc) {
        for (RacialSkillParts.Part p : parts) if (p.id().equals(id)) return;
        parts.add(new RacialSkillParts.Part(id, Component.translatableWithFallback(key(id), fallback), desc));
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
