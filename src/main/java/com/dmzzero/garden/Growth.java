package com.dmzzero.garden;

import com.dmzzero.ZeroConfig;
import com.dmzzero.saibamen.SproutSave;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Growth(Kind kind, long pos, ServerLevel level, long readyAt) {

    public enum Kind { BUSH, SPROUT, TREE, FRUIT }

    public static List<Growth> of(ServerPlayer player) {
        UUID id = player.getUUID();
        List<Growth> out = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            if (ZeroConfig.SERVER.senzuGarden.get()) {
                for (SenzuSave.Bush b : SenzuSave.get(level).all()) {
                    if (b.owner().equals(id)) out.add(new Growth(Kind.BUSH, b.pos(), level, b.since() + SenzuGarden.ripen(b)));
                }
            }
            if (ZeroConfig.SERVER.saibamanSeeds.get()) {
                for (SproutSave.Sprout s : SproutSave.get(level).all()) {
                    if (s.owner().equals(id)) out.add(new Growth(Kind.SPROUT, s.soil(), level, s.readyAt()));
                }
            }
            if (ZeroConfig.SERVER.mightTree.get()) {
                for (MightTreeSave.Tree t : MightTreeSave.get(level).all()) {
                    if (t.owner().equals(id)) out.add(tree(t, level));
                }
            }
        }
        return out;
    }

    private static Growth tree(MightTreeSave.Tree t, ServerLevel level) {
        return t.stage() == MightTreeMath.GROWN
                ? new Growth(Kind.FRUIT, t.base(), level, t.lastFruit() + SenzuMath.ripenTicks(ZeroConfig.SERVER.mightFruitDays.get()))
                : new Growth(Kind.TREE, t.base(), level, t.planted() + SenzuMath.ripenTicks(ZeroConfig.SERVER.mightGrowDays.get()));
    }

    public long ticksLeft() {
        return Math.max(0, readyAt - level.getGameTime());
    }
}
