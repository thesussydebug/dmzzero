package com.dmzzero.diet;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.diet.DietMath.Diet;
import com.dmzzero.diet.DietMath.Food;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Racial diets: Namekians live on water and get half from food, Majins get double from sweets.
 * Only the hunger bar changes; DMZ's own food recovery, Water Affinity and Sweet Tooth read the food as it is.
 */
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Diets {
    private static final int COST = TickCost.id("world.diets");
    public static final TagKey<Item> SWEETS = ItemTags.create(new ResourceLocation(DMZZero.MOD_ID, "sweets"));

    // a placed cake feeds through FoodData.eat(int, float), which doesn't say who is eating
    private static Player cakeEater;
    // farmer's delight pies eat through FoodData.eat(Item, ItemStack) without the eater, so remember who used a block this tick
    private static Player clicker;
    // eat() is called again with the adjusted values; don't adjust those a second time
    private static boolean adjusting;
    // the self-test switches the diets off here rather than in the config: setting a config value writes the file,
    // and forge's file watcher can load a half-way copy back after the test restores it
    private static boolean testOff;

    private Diets() {
    }

    public static boolean namekianOn() {
        return !testOff && ZeroConfig.read(ZeroConfig.SERVER.namekianDiet, true);
    }

    public static boolean majinOn() {
        return !testOff && ZeroConfig.read(ZeroConfig.SERVER.majinDiet, true);
    }

    public static void setTestOff(boolean off) {
        testOff = off;
    }

    public static double namekianFactor() {
        return ZeroConfig.read(ZeroConfig.SERVER.namekianFoodFactor, 0.5);
    }

    public static double sweetsFactor() {
        return ZeroConfig.read(ZeroConfig.SERVER.majinSweetsFactor, 2.0);
    }

    public static int waterHunger() {
        return ZeroConfig.read(ZeroConfig.SERVER.waterHunger, 4);
    }

    public static double waterSaturation() {
        return ZeroConfig.read(ZeroConfig.SERVER.waterSaturation, 0.3);
    }

    public static Diet dietOf(Player player) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || !data.getStatus().isHasCreatedCharacter()) return Diet.NONE;
        String race = data.getCharacter().getRaceName();
        RaceCharacterConfig config = race == null ? null : ConfigManager.getRaceCharacter(race.toLowerCase(Locale.ROOT));
        return DietMath.dietOf(config == null ? null : config.getRacialSkill(), race, namekianOn(), majinOn());
    }

    /** What this player really gets from food with these values, or null when their diet changes nothing. */
    @Nullable
    public static Food adjustedFor(Player player, ItemStack stack, int hunger, float saturationModifier) {
        Diet diet = dietOf(player);
        boolean sweet = stack.is(SWEETS);
        if (!DietMath.changes(diet, sweet)) return null;
        return DietMath.adjust(diet, sweet, hunger, saturationModifier, namekianFactor(), sweetsFactor());
    }

    /** FoodData.eat(Item, ItemStack, LivingEntity), Forge's version that every food item goes through. True = handled. */
    public static boolean onEat(FoodData food, Item item, ItemStack stack, @Nullable LivingEntity entity) {
        if (adjusting || !item.isEdible()) return false;
        Player player = entity instanceof Player p ? p : (clicker != null && clicker.getFoodData() == food ? clicker : null);
        if (player == null || player.getFoodData() != food) return false;
        FoodProperties properties = stack.getFoodProperties(player);
        if (properties == null) return false;
        Food adjusted = adjustedFor(player, stack, properties.getNutrition(), properties.getSaturationModifier());
        if (adjusted == null) return false;
        eatAs(food, adjusted);
        return true;
    }

    /** FoodData.eat(int, float), only while a placed cake is being eaten. True = handled. */
    public static boolean onEatRaw(FoodData food, int hunger, float saturationModifier) {
        if (adjusting || cakeEater == null || cakeEater.getFoodData() != food) return false;
        Food adjusted = adjustedFor(cakeEater, new ItemStack(Items.CAKE), hunger, saturationModifier);
        if (adjusted == null) return false;
        eatAs(food, adjusted);
        return true;
    }

    public static void startCake(Player player) {
        cakeEater = player;
    }

    public static void endCake() {
        cakeEater = null;
    }

    private static void eatAs(FoodData food, Food values) {
        adjusting = true;
        try {
            food.eat(values.hunger(), values.saturationModifier());
        } finally {
            adjusting = false;
        }
    }

    /** A Namekian drinking plain water is fed by it. True if it fed them. */
    public static boolean drinkWater(Player player) {
        Food water = DietMath.water(dietOf(player), waterHunger(), waterSaturation());
        if (water == null) return false;
        eatAs(player.getFoodData(), water);
        return true;
    }

    public static boolean isWater(ItemStack stack) {
        return stack.getItem() instanceof PotionItem && PotionUtils.getPotion(stack) == Potions.WATER;
    }

    // same event dmz's own water affinity listens to; the item is the copy taken before drinking
    @SubscribeEvent
    public static void onDrink(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && isWater(event.getItem())) drinkWater(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide) clicker = event.getEntity();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) clicker = null;
    }
}
