package com.dmzzero.chamber;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.chamber.TrainingMath.Beaten;
import com.dmzzero.chamber.TrainingMath.Id;
import com.dmzzero.network.TrainingMenuS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.rival.RivalShadows;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.entities.ai.AiTierResolver;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.quest.objectives.KillObjective;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.server.world.dimension.HTCDimension;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

// training in the time chamber: a menu lists the bosses of the quests you finished and the mobs you've beaten, and puts
// a training copy in front of you. copies carry dmz's quest tags under a quest key no quest has, so dmz scales them like
// the quest did and credits no quest. they drop nothing, and a blow of theirs that would kill you knocks you down instead
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Training {
    /** Persistent data on a training copy: its owner's UUID. */
    public static final String TAG = "dmzzero_training";
    private static final String QUEST_KEY = "dmzzero_training";
    private static final String ROOT = "dmzzero";
    private static final String LOG = "trainingLog";
    private static final int MENU_COOLDOWN = 10;
    private static final int FIGHT_COOLDOWN = 20;
    private static final double AHEAD = 8.0;
    private static final double CLOSE = 3.0;

    // the copies each player has out, so clearing never scans the world; anything with the tag that isn't here is removed
    private static final Map<UUID, Out> OUT = new HashMap<>();
    private static final Map<UUID, Long> LAST_MENU = new HashMap<>();
    private static final Map<UUID, Long> LAST_FIGHT = new HashMap<>();
    // where copies died this tick or the last: items and orbs other mods drop there by hand are stopped too
    private static final List<Fallen> FALLEN = new ArrayList<>();
    private static int spawned;
    private static int knockouts;

    private Training() {
    }

    private record Out(ResourceKey<Level> level, Set<UUID> ids) {
    }

    // copy: a training copy fell here; otherwise a real mob or player died next to one, and its loot is its own
    private record Fallen(ResourceKey<Level> level, Vec3 at, long tick, boolean copy) {
    }

    /** One opponent the menu offers. {@code saga} is the id of the saga its quest is in, or null. */
    public record Opponent(Id id, EntityType<?> type, double health, double melee, double ki, int aiTier, boolean noTransform,
                           int variant, int count, String saga, String quest, int times) {
    }

    /** What a fight request came to: "ok", or why not. {@code made} copies came out. */
    public record Result(String reason, int made, Component line) {
    }

    private static StatsData stats(ServerPlayer p) {
        return StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
    }

    public static boolean isCopy(Entity e) {
        return !(e instanceof Player) && e.getPersistentData().contains(TAG);
    }

    // ---- what you have fought

    /** DMZ's sagas in story order. */
    public static List<String> storyOrder() {
        Map<String, String> follows = new HashMap<>();
        QuestRegistry.getAllSagas().forEach((id, saga) -> follows.put(id, saga.getRequirements() == null ? null : saga.getRequirements().previousSagaId()));
        return TrainingMath.story(follows);
    }

    /**
     * The bosses of every quest you finished, each kind and strength once: in story order (by saga, then quest), then
     * side quests and other quests in the order you finished them.
     */
    public static List<Opponent> sagaBosses(ServerPlayer player) {
        List<Opponent> out = new ArrayList<>();
        StatsData data = stats(player);
        PlayerQuestData quests = data == null ? null : data.getPlayerQuestData();
        if (quests == null) return out;
        List<String> order = storyOrder();
        Map<String, long[]> places = new HashMap<>();
        for (int s = 0; s < order.size(); s++) {
            Saga saga = QuestRegistry.getSaga(order.get(s));
            if (saga == null) continue;
            List<Quest> list = saga.getQuests();
            for (int q = 0; q < list.size(); q++) places.put(saga.getId() + ":" + list.get(q).getId(), new long[]{s, q});
        }
        List<String> done = new ArrayList<>(quests.getCompletedQuestIds());
        Map<String, Integer> finished = new HashMap<>();
        for (int i = 0; i < done.size(); i++) finished.put(done.get(i), i);
        long last = Long.MAX_VALUE / 4;
        done.sort(Comparator.<String>comparingLong(q -> places.containsKey(q) ? places.get(q)[0] : last)
                .thenComparingLong(q -> places.containsKey(q) ? places.get(q)[1] : finished.get(q)));
        int most = ZeroConfig.SERVER.trainingMaxOpponents.get();
        Set<String> seen = new HashSet<>();
        for (String questId : done) {
            Quest quest = QuestRegistry.getQuest(questId);
            if (quest == null) continue;
            String saga = places.containsKey(questId) ? order.get((int) places.get(questId)[0]) : null;
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                if (!(objectives.get(i) instanceof KillObjective kill)) continue;
                EntityType<?> type = kill.resolveEntityType();
                if (type == null || !seen.add(ForgeRegistries.ENTITY_TYPES.getKey(type) + "@" + Math.round(kill.getHealth()))) continue;
                int tier = kill.getAiTier() > 0 ? kill.getAiTier() : AiTierResolver.storyTier(quests.getDifficulty(), type);
                out.add(new Opponent(new Id(TrainingMath.QUEST, questId, i), type, kill.getHealth(), kill.getMeleeDamage(), kill.getKiDamage(),
                        tier, !kill.isCanTransform(), kill.getTextureVariant(), Math.max(1, Math.min(kill.getCount(), most)), saga,
                        quest.getTitle() == null ? "" : quest.getTitle(), 0));
            }
        }
        return out;
    }

    public static List<Beaten> log(ServerPlayer player) {
        ListTag list = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT).getList(LOG, Tag.TAG_COMPOUND);
        List<Beaten> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            out.add(new Beaten(t.getString("id"), t.getDouble("hp"), t.getDouble("melee"), t.getDouble("ki"), Math.max(1, t.getInt("times"))));
        }
        return out;
    }

    public static void saveLog(ServerPlayer player, List<Beaten> log) {
        ListTag list = new ListTag();
        for (Beaten e : log) {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.id());
            t.putDouble("hp", e.health());
            t.putDouble("melee", e.melee());
            t.putDouble("ki", e.ki());
            t.putInt("times", e.times());
            list.add(t);
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        root.put(LOG, list);
        persisted.put(ROOT, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** The mobs you've beaten, the strongest of each kind, most recent first. */
    public static List<Opponent> beaten(ServerPlayer player) {
        List<Opponent> out = new ArrayList<>();
        for (Beaten e : log(player)) {
            ResourceLocation key = ResourceLocation.tryParse(e.id());
            EntityType<?> type = key == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(key);
            if (type == null || !key.equals(ForgeRegistries.ENTITY_TYPES.getKey(type))) continue;
            out.add(new Opponent(new Id(TrainingMath.KILL, e.id(), 0), type, e.health(), e.melee(), e.ki(), 0, false, -1, 1, null, "", e.times()));
        }
        return out;
    }

    /** Whether beating {@code dead} goes in the log: a mob of its own, not a copy, a rival's shadow, a pet or a Sparking fighter. */
    static boolean loggable(LivingEntity dead) {
        if (!(dead instanceof Mob) || isCopy(dead) || dead.getPersistentData().contains(RivalShadows.TAG)) return false;
        if (dead instanceof OwnableEntity pet && pet.getOwnerUUID() != null) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());
        // sparking's fighters and companions are people with stats of their own, not a kind of mob a copy could match
        return id != null && !"dmzsparking".equals(id.getNamespace());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKill(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (event.isCanceled() || dead.level().isClientSide() || !ZeroConfig.SERVER.training.get() || !loggable(dead)) return;
        Entity source = event.getSource().getEntity();
        if (source instanceof Projectile p && p.getOwner() != null) source = p.getOwner();
        if (!(source instanceof ServerPlayer player)) return;
        double melee = dead.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 1.0 : dead.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double ki = dead.getPersistentData().contains("dmz_quest_ki") ? dead.getPersistentData().getDouble("dmz_quest_ki") : melee;
        String id = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(dead.getType()));
        saveLog(player, TrainingMath.add(log(player), new Beaten(id, dead.getMaxHealth(), melee, ki, 1)));
    }

    // ---- training copies

    public static Opponent find(ServerPlayer player, String idText) {
        Id id = Id.parse(idText);
        if (id == null) return null;
        for (Opponent o : TrainingMath.QUEST.equals(id.kind()) ? sagaBosses(player) : beaten(player)) {
            if (o.id().equals(id)) return o;
        }
        return null;
    }

    /** The player's copies still out and loaded. */
    public static List<LivingEntity> opponents(ServerPlayer player) {
        Out out = OUT.get(player.getUUID());
        if (out == null) return List.of();
        ServerLevel level = player.server.getLevel(out.level());
        List<LivingEntity> alive = new ArrayList<>();
        if (level != null) {
            for (UUID id : out.ids()) {
                if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()) alive.add(e);
            }
        }
        // a copy in an unloaded chunk drops out here, and the join check removes it when it loads
        out.ids().removeIf(id -> alive.stream().noneMatch(e -> e.getUUID().equals(id)));
        if (out.ids().isEmpty()) OUT.remove(player.getUUID());
        return alive;
    }

    /** Sends the player's copies away. Returns how many went. */
    public static int clear(ServerPlayer player) {
        List<LivingEntity> all = opponents(player);
        all.forEach(Entity::discard);
        OUT.remove(player.getUUID());
        return all.size();
    }

    private static Result refuse(String reason, String key, String english, Object... args) {
        return new Result(reason, 0, Component.translatableWithFallback(key, english, args).withStyle(ChatFormatting.GRAY));
    }

    /** Why the player can't train right now, or null. */
    static Result blocked(ServerPlayer player) {
        if (!ZeroConfig.SERVER.training.get()) {
            return refuse("off", "message.dmzzero.training.off", "Time Chamber training is off on this server.");
        }
        StatsData data = stats(player);
        if (!player.isAlive() || player.isSpectator() || data == null || !data.getStatus().isHasCreatedCharacter()) {
            return refuse("cannot", "message.dmzzero.training.cannot", "You can't train right now.");
        }
        if (ZeroConfig.SERVER.trainingOnlyInChamber.get() && !ChamberDepths.inChamber(player)) {
            return refuse("not_here", "message.dmzzero.training.not_here", "Training copies only come in the Hyperbolic Time Chamber.");
        }
        return null;
    }

    private static boolean cooling(Map<UUID, Long> last, ServerPlayer player, int ticks) {
        long now = player.server.getTickCount();
        Long was = last.get(player.getUUID());
        if (was != null && now - was < ticks && now >= was) return true;
        last.put(player.getUUID(), now);
        return false;
    }

    /** Puts a training copy of the opponent in front of the player. */
    public static Result fight(ServerPlayer player, String idText) {
        Result no = blocked(player);
        if (no != null) return no;
        if (cooling(LAST_FIGHT, player, FIGHT_COOLDOWN)) {
            return refuse("cooldown", "message.dmzzero.training.cooldown", "Catch your breath first.");
        }
        Opponent o = find(player, idText);
        if (o == null) return refuse("unknown", "message.dmzzero.training.unknown", "You haven't fought that one yet.");
        int most = ZeroConfig.SERVER.trainingMaxOpponents.get();
        int room = most - opponents(player).size();
        if (room <= 0) {
            return refuse("full", "message.dmzzero.training.full", "%s training copies are out already. Beat them or send them away first.", most);
        }
        ServerLevel level = player.serverLevel();
        StatsData data = stats(player);
        Difficulty difficulty = data == null || data.getPlayerQuestData() == null ? Difficulty.NORMAL : data.getPlayerQuestData().getDifficulty();
        Out out = OUT.computeIfAbsent(player.getUUID(), k -> new Out(level.dimension(), new LinkedHashSet<>()));
        if (!out.level().equals(level.dimension())) {
            clear(player);
            out = new Out(level.dimension(), new LinkedHashSet<>());
            OUT.put(player.getUUID(), out);
        }
        int made = 0;
        boolean peaceful = false;
        int count = Math.min(o.count(), room);
        for (int i = 0; i < count; i++) {
            Entity e = o.type().create(level);
            if (!(e instanceof LivingEntity living) || e instanceof Player) {
                if (e != null) e.discard();
                continue;
            }
            // vanilla removes a monster on its first tick on peaceful, persistent or not
            if (living instanceof com.dmzzero.mixin.MobAccess m && m.dmzzero$despawnsInPeaceful() && level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
                peaceful = true;
                e.discard();
                continue;
            }
            BlockPos at = spot(level, player, i, living);
            living.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
            mark(living, player, o, difficulty);
            if (living instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.COMMAND, null, null);
                mob.setPersistenceRequired();
                mob.setTarget(player);
            }
            out.ids().add(living.getUUID());
            if (level.addFreshEntity(living)) made++;
            else out.ids().remove(living.getUUID());
        }
        if (out.ids().isEmpty()) OUT.remove(player.getUUID());
        if (made == 0 && peaceful) {
            return refuse("peaceful", "message.dmzzero.training.peaceful", "Monsters can't appear on Peaceful. Raise the difficulty to train against them.");
        }
        if (made == 0) return refuse("no_room", "message.dmzzero.training.no_room", "Your opponent couldn't appear here.");
        spawned += made;
        DMZZero.LOG.info("[Training] {} trains against {} x{} ({} HP).", player.getGameProfile().getName(), o.id().text(), made, Math.round(o.health()));
        Component name = Component.translatable(o.type().getDescriptionId());
        Component line = made > 1
                ? Component.translatableWithFallback("message.dmzzero.training.fight_many", "Training: %s x%s. Fight!", name, made)
                : Component.translatableWithFallback("message.dmzzero.training.fight", "Training: %s. Fight!", name);
        return new Result("ok", made, line.copy().withStyle(ChatFormatting.AQUA));
    }

    private static void mark(LivingEntity living, ServerPlayer player, Opponent o, Difficulty difficulty) {
        CompoundTag p = living.getPersistentData();
        p.putString(TAG, player.getStringUUID());
        p.putString(QuestService.QUEST_KEY_TAG, QUEST_KEY);
        p.putString(QuestService.QUEST_OWNER_TAG, player.getStringUUID());
        if (difficulty != Difficulty.NORMAL) p.putString("dmz_difficulty", difficulty.name());
        p.putDouble("dmz_quest_hp", Math.max(1.0, o.health()));
        p.putDouble("dmz_quest_melee", Math.max(0.0, o.melee()));
        p.putDouble("dmz_quest_ki", Math.max(0.0, o.ki()));
        if (o.variant() >= 0) p.putInt("dmz_quest_texture_variant", o.variant());
        p.putInt("dmz_quest_ai_tier", o.aiTier() > 0 ? o.aiTier() : AiTierResolver.storyTier(difficulty, o.type()));
        if (o.noTransform()) p.putBoolean("dmz_quest_no_transform", true);
    }

    // in front of the player, closer when that spot isn't ticking, on the player's own block when neither is loaded
    private static BlockPos spot(ServerLevel level, ServerPlayer player, int index, LivingEntity body) {
        for (double distance : new double[]{AHEAD, CLOSE}) {
            double[] xz = TrainingMath.inFront(player.getX(), player.getZ(), player.getYRot(), distance, index);
            BlockPos at = BlockPos.containing(xz[0], player.getY(), xz[1]);
            if (level.isPositionEntityTicking(at)) return ground(level, at, body);
        }
        return player.blockPosition();
    }

    // standing room above and something solid below, a few blocks up or down; never looks into an unloaded chunk
    private static BlockPos ground(ServerLevel level, BlockPos at, LivingEntity body) {
        int height = Math.max(1, (int) Math.ceil(body.getBbHeight()));
        for (int dy = 0; dy <= 6; dy++) {
            for (int sign : new int[]{1, -1}) {
                BlockPos p = at.above(dy * sign);
                if (!level.hasChunkAt(p)) continue;
                boolean room = true;
                for (int h = 0; h < height && room; h++) room = level.getBlockState(p.above(h)).getCollisionShape(level, p.above(h)).isEmpty();
                if (room && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) return p;
            }
        }
        return at;
    }

    /** The training copy behind a blow (its own hit, or a blast or projectile it fired), or null. */
    public static LivingEntity trainer(DamageSource source) {
        for (Entity e : new Entity[]{source.getEntity(), source.getDirectEntity()}) {
            Entity who = e instanceof Projectile p && p.getOwner() != null ? p.getOwner() : e;
            if (who instanceof LivingEntity l && isCopy(l)) return l;
        }
        return null;
    }

    // a copy's blow that would kill you knocks you down instead, the way a lost spar ends, and your copies leave
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLoss(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        LivingEntity winner = trainer(event.getSource());
        StatsData data = winner == null ? null : stats(player);
        if (data == null) return;
        event.setCanceled(true);
        player.setHealth(1f);
        int ticks = Math.max(1, ConfigManager.getCombatConfig().getKnockdownDurationSeconds()) * 20;
        data.getStatus().setKnockedDown(true);
        data.getCooldowns().setCooldown(Cooldowns.KNOCKDOWN_DURATION, ticks);
        data.getCooldowns().setCooldown(Cooldowns.KNOCKDOWN_INVULN, ticks);
        if (player.connection != null) NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        Component name = winner.getDisplayName();
        clear(player);
        knockouts++;
        player.displayClientMessage(Component.translatableWithFallback("message.dmzzero.training.lost", "%s knocked you out. Training's over.", name)
                .withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCopyDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !isCopy(dead)) return;
        long now = dead.level().getGameTime();
        FALLEN.removeIf(f -> now - f.tick() > 2L);
        FALLEN.add(new Fallen(dead.level().dimension(), dead.position(), now, true));
    }

    // a mob or player that really dies right after a copy (one blast, say) drops its loot inside the death; only then is
    // it worth noting where, and only while a copy's spot is still listed
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRealDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (FALLEN.isEmpty() || event.isCanceled() || dead.level().isClientSide() || isCopy(dead)) return;
        long now = dead.level().getGameTime();
        FALLEN.removeIf(f -> now - f.tick() > 2L);
        if (!FALLEN.isEmpty()) FALLEN.add(new Fallen(dead.level().dimension(), dead.position(), now, false));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
            if (FALLEN.isEmpty()) return;
            // an item someone threw or dropped is theirs, not the copy's
            if (e instanceof com.dmzzero.mixin.ItemEntityAccess item && item.dmzzero$thrower() != null) return;
            long now = event.getLevel().getGameTime();
            FALLEN.removeIf(f -> now - f.tick() > 2L);
            // stopped when the nearest death within 4 blocks is a copy's; a real death as near or nearer keeps it
            Fallen nearest = null;
            for (Fallen f : FALLEN) {
                if (!f.level().equals(event.getLevel().dimension()) || !f.at().closerThan(e.position(), 4.0)) continue;
                double d = f.at().distanceToSqr(e.position());
                if (nearest == null || d < nearest.at().distanceToSqr(e.position()) || d == nearest.at().distanceToSqr(e.position()) && !f.copy()) nearest = f;
            }
            if (nearest != null && nearest.copy()) event.setCanceled(true);
            return;
        }
        // a copy left over from before a restart, or from a player who has left, has no one to train
        if (!isCopy(e)) return;
        Out out = OUT.get(ownerOf(e));
        if (out == null || !out.level().equals(event.getLevel().dimension()) || !out.ids().contains(e.getUUID())) event.setCanceled(true);
    }

    private static UUID ownerOf(Entity e) {
        try {
            return UUID.fromString(e.getPersistentData().getString(TAG));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (isCopy(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (isCopy(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clear(player);
        if (!ZeroConfig.SERVER.training.get() || !event.getTo().equals(HTCDimension.HTC_KEY)) return;
        Component link = Component.translatableWithFallback("message.dmzzero.training.link", "[Training]").withStyle(s -> s
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dmzzero training"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatableWithFallback("message.dmzzero.training.link_hover", "Refight the bosses and mobs you've beaten"))));
        player.sendSystemMessage(ZeroNetwork.reachable(player)
                ? Component.translatableWithFallback("message.dmzzero.training.enter", "Press %s or click %s to refight a boss or mob you've beaten.",
                        Component.keybind("key.dmzzero.training"), link)
                : Component.translatableWithFallback("message.dmzzero.training.enter_chat", "Click %s to refight a boss or mob you've beaten.", link));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    /** Sends the player's copies away and forgets their cooldowns. */
    public static void forget(ServerPlayer player) {
        clear(player);
        resetCooldowns(player);
    }

    public static void resetCooldowns(ServerPlayer player) {
        LAST_MENU.remove(player.getUUID());
        LAST_FIGHT.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OUT.clear();
        LAST_MENU.clear();
        LAST_FIGHT.clear();
        FALLEN.clear();
    }

    // ---- the menu

    /** Everything the player can refight, in sections: each saga in story order, side quests, then beaten mobs. */
    public static TrainingMenuS2C menu(ServerPlayer player) {
        List<TrainingMenuS2C.Section> sections = new ArrayList<>();
        for (TrainingMath.Group<Opponent> group : TrainingMath.group(sagaBosses(player), Opponent::saga, storyOrder())) {
            Saga saga = group.saga() == null ? null : QuestRegistry.getSaga(group.saga());
            String name = saga == null ? "" : saga.getName() == null || saga.getName().isBlank() ? saga.getId() : saga.getName();
            sections.add(new TrainingMenuS2C.Section(saga == null ? TrainingMenuS2C.SIDE : TrainingMenuS2C.SAGA, name, foes(group.items())));
        }
        sections.add(new TrainingMenuS2C.Section(TrainingMenuS2C.MOBS, "", foes(beaten(player))));
        return new TrainingMenuS2C(opponents(player).size(), ZeroConfig.SERVER.trainingMaxOpponents.get(), sections);
    }

    private static List<TrainingMenuS2C.Foe> foes(List<Opponent> list) {
        List<TrainingMenuS2C.Foe> out = new ArrayList<>(list.size());
        for (Opponent o : list) {
            out.add(new TrainingMenuS2C.Foe(o.id().text(), o.type().getDescriptionId(), o.count(), o.quest(), Math.round(o.health()), o.times()));
        }
        return out;
    }

    /** Opens the menu (in chat for a player without DMZ Zero). Returns "ok" or why not. */
    public static Result open(ServerPlayer player) {
        Result no = blocked(player);
        if (no != null) return no;
        if (cooling(LAST_MENU, player, MENU_COOLDOWN)) return new Result("cooldown", 0, Component.empty());
        if (ZeroNetwork.reachable(player)) ZeroNetwork.send(player, menu(player));
        else lines(player, "").forEach(player::sendSystemMessage);
        return new Result("ok", 0, Component.empty());
    }

    /** The menu as chat lines with clickable [Fight] links; {@code search} works as in the menu's search box. */
    public static List<Component> lines(ServerPlayer player, String search) {
        TrainingMenuS2C menu = menu(player);
        List<Component> out = new ArrayList<>();
        out.add(TrainingText.out(menu.out(), menu.max()).copy().withStyle(ChatFormatting.AQUA));
        Function<String, Component> readable = Component::translatable;
        var shown = TrainingMath.filter(menu.sections(), TrainingMenuS2C.Section::foes,
                s -> TrainingText.header(s, s.foes().size(), readable).getString(), f -> TrainingText.line(f, readable).getString(), search);
        for (var section : shown) {
            out.add(TrainingText.header(section.section(), section.foes().size(), readable).copy().withStyle(ChatFormatting.GOLD));
            for (TrainingMenuS2C.Foe f : section.foes()) {
                MutableComponent fight = Component.translatableWithFallback("message.dmzzero.training.fight_link", "[Fight]").withStyle(s -> s
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dmzzero training fight " + f.id())));
                out.add(Component.literal(" ").append(fight).append(" ").append(TrainingText.line(f, readable)));
            }
        }
        if (shown.isEmpty()) {
            out.add(Component.translatableWithFallback("gui.dmzzero.training.no_match", "Nothing matches \"%s\".", search.trim())
                    .withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    // ---- commands

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("dmzzero").then(Commands.literal("training")
                .executes(c -> {
                    Result r = open(c.getSource().getPlayerOrException());
                    if (!"ok".equals(r.reason()) && !"cooldown".equals(r.reason())) c.getSource().sendFailure(r.line());
                    return "ok".equals(r.reason()) ? 1 : 0;
                })
                .then(Commands.literal("fight").then(Commands.argument("id", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    Result r = fight(player, StringArgumentType.getString(c, "id"));
                    player.displayClientMessage(r.line(), true);
                    return r.made();
                })))
                .then(Commands.literal("clear").executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    int n = clear(player);
                    player.displayClientMessage(Component.translatableWithFallback("message.dmzzero.training.cleared",
                            "Sent %s training copies away.", n).withStyle(ChatFormatting.GRAY), true);
                    // a menu left open shows the empty arena
                    if (ZeroNetwork.reachable(player) && blocked(player) == null) ZeroNetwork.send(player, menu(player));
                    return n;
                }))
                .then(Commands.literal("list")
                        .executes(c -> list(c.getSource(), ""))
                        .then(Commands.argument("search", StringArgumentType.greedyString())
                                .executes(c -> list(c.getSource(), StringArgumentType.getString(c, "search")))))));
    }

    private static int list(CommandSourceStack source, String search) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Result no = blocked(player);
        if (no != null) {
            source.sendFailure(no.line());
            return 0;
        }
        List<Component> lines = lines(player, search);
        lines.forEach(player::sendSystemMessage);
        return lines.size();
    }

    /** For /dmzzero status and the self-test: copies made and knockouts since the server started. */
    public static int spawned() {
        return spawned;
    }

    public static int knockouts() {
        return knockouts;
    }

    /** For the self-test: how many players have copies out. */
    static int tracked() {
        return OUT.size();
    }
}
