package com.dmzzero.diary;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.chamber.ChamberDepths;
import com.dmzzero.diary.DiaryMath.Days;
import com.dmzzero.diary.DiaryMath.Frame;
import com.dmzzero.diary.DiaryMath.Kind;
import com.dragonminez.common.events.DMZEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDate;

// the training diary: every tp gain, by where it came from, for today and yesterday
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Diaries {
    private static final String ROOT = "dmzzero";
    private static final String KEY = "diary";
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final int MAX_FRAMES = 64;

    private Diaries() {
    }

    // last in line, so it records what the player really gets after every multiplier; cancelled gains never arrive
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTpGain(DMZEvent.TPGainEvent event) {
        int gain = event.getTpGain();
        if (gain <= 0 || !(event.getPlayer() instanceof ServerPlayer player) || !ZeroConfig.SERVER.diary.get()) return;
        record(player, today(), DiaryMath.place(caller(), ChamberDepths.inChamber(player)), gain);
    }

    static Kind caller() {
        return WALKER.walk(s -> {
            Iterable<Frame> frames = s.map(f -> new Frame(f.getClassName(), f.getMethodName())).limit(MAX_FRAMES)::iterator;
            return DiaryMath.kind(frames);
        });
    }

    public static long today() {
        return LocalDate.now().toEpochDay();
    }

    public static void record(Player player, long day, Kind kind, long amount) {
        Days days = load(player);
        days.add(day, kind, amount);
        save(player, days);
    }

    /** The diary as of the given day: today is empty if nothing came in yet. */
    public static Days view(Player player, long day) {
        Days days = load(player);
        days.roll(day);
        return days;
    }

    public static Days load(Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT).getCompound(KEY);
        return new Days(tag.getLong("day"), amounts(tag.getCompound("today")), amounts(tag.getCompound("yesterday")));
    }

    private static long[] amounts(CompoundTag tag) {
        long[] out = new long[Kind.values().length];
        for (Kind k : Kind.values()) out[k.ordinal()] = tag.getLong(k.key());
        return out;
    }

    private static CompoundTag tag(long[] amounts) {
        CompoundTag tag = new CompoundTag();
        for (Kind k : Kind.values()) if (amounts[k.ordinal()] > 0) tag.putLong(k.key(), amounts[k.ordinal()]);
        return tag;
    }

    public static void save(Player player, Days days) {
        CompoundTag diary = new CompoundTag();
        diary.putLong("day", days.day());
        diary.put("today", tag(days.today()));
        diary.put("yesterday", tag(days.yesterday()));
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        root.put(KEY, diary);
        persisted.put(ROOT, root);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static void clear(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        root.remove(KEY);
        persisted.put(ROOT, root);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DiaryCommand.register(event.getDispatcher());
    }
}
