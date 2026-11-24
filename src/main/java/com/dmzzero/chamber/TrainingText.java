package com.dmzzero.chamber;

import com.dmzzero.network.TrainingMenuS2C;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

// the training menu's words, the same in chat and on the screen. dmz's saga names and quest titles may be lang keys
// or plain text, so the caller says how to read them
public final class TrainingText {
    private TrainingText() {
    }

    public static Component out(int out, int max) {
        return Component.translatableWithFallback("gui.dmzzero.training.out", "Training copies out: %s of %s", out, max);
    }

    /** A section's header with how many opponents it holds. */
    public static Component header(TrainingMenuS2C.Section s, int n, Function<String, Component> readable) {
        return switch (s.kind()) {
            case TrainingMenuS2C.SAGA -> Component.translatableWithFallback("gui.dmzzero.training.saga", "%s: bosses (%s)", readable.apply(s.name()), n);
            case TrainingMenuS2C.SIDE -> Component.translatableWithFallback("gui.dmzzero.training.side", "Side quests: bosses (%s)", n);
            default -> Component.translatableWithFallback("gui.dmzzero.training.mobs", "Mobs you've beaten (%s)", n);
        };
    }

    public static Component name(TrainingMenuS2C.Foe f) {
        Component name = Component.translatable(f.entity());
        return f.count() > 1 ? Component.translatableWithFallback("gui.dmzzero.training.group", "%s x%s", name, f.count()) : name;
    }

    public static Component detail(TrainingMenuS2C.Foe f, Function<String, Component> readable) {
        if (!f.quest().isEmpty()) {
            return Component.translatableWithFallback("gui.dmzzero.training.boss", "%s, %s HP", readable.apply(f.quest()), f.health());
        }
        return f.times() == 1
                ? Component.translatableWithFallback("gui.dmzzero.training.beaten_once", "%s HP, beaten once", f.health())
                : Component.translatableWithFallback("gui.dmzzero.training.beaten", "%s HP, beaten %s times", f.health(), f.times());
    }

    /** Name and detail on one line, as the search reads them and chat shows them. */
    public static Component line(TrainingMenuS2C.Foe f, Function<String, Component> readable) {
        return Component.empty().append(name(f)).append(" ").append(detail(f, readable).copy().withStyle(ChatFormatting.GRAY));
    }
}
