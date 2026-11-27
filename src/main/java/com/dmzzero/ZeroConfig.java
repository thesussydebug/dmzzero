package com.dmzzero;

import com.dmzzero.potential.Potential;
import com.dmzzero.settings.SettingsTree;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.IdentityHashMap;
import java.util.Map;

// comments are the English descriptions on the settings screen too, so each stays one short line
public final class ZeroConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    // null unless this client is connected to a server in another process
    private static volatile Map<ForgeConfigSpec.ConfigValue<?>, Object> fromServer;

    static {
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private ZeroConfig() {
    }

    /**
     * Server settings as the server this client is connected to sent them, by setting path ("fighting.swat.reach").
     * Null goes back to the local server.toml, for singleplayer, hosting and the main menu.
     */
    public static void useServerValues(Map<String, ?> sent) {
        if (sent == null) {
            fromServer = null;
            return;
        }
        Map<ForgeConfigSpec.ConfigValue<?>, Object> byValue = new IdentityHashMap<>();
        for (SettingsTree.Item item : SettingsTree.of(SERVER_SPEC)) byValue.put(item.value(), sent.get(item.path()));
        fromServer = byValue;
    }

    /** Joined a server in another process: until it sends its settings, server settings read as their fallbacks. */
    public static void awaitServerValues() {
        if (fromServer == null) useServerValues(Map.of());
    }

    // safe from anywhere: a server setting on a client connected to a server is that server's value, or the fallback
    public static <T> T read(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        Map<ForgeConfigSpec.ConfigValue<?>, Object> sent = fromServer;
        if (sent != null && sent.containsKey(value)) {
            Object v = sent.get(value);
            return v != null && fallback != null && fallback.getClass().isInstance(v) ? cast(v) : fallback;
        }
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object v) {
        return (T) v;
    }

    public static final class Server {
        public final ForgeConfigSpec.BooleanValue transformations;
        public final ForgeConfigSpec.BooleanValue auras;
        public final ForgeConfigSpec.BooleanValue lightning;
        public final ForgeConfigSpec.BooleanValue cracks;
        public final ForgeConfigSpec.BooleanValue destruction;
        public final ForgeConfigSpec.BooleanValue masteryCalms;
        public final ForgeConfigSpec.DoubleValue smoothAt;
        public final ForgeConfigSpec.DoubleValue calmAt;
        public final ForgeConfigSpec.BooleanValue firstTime;
        public final ForgeConfigSpec.BooleanValue clashes;
        public final ForgeConfigSpec.BooleanValue water;
        public final ForgeConfigSpec.BooleanValue standoffs;
        public final ForgeConfigSpec.DoubleValue standoffRange;
        public final ForgeConfigSpec.BooleanValue wake;
        public final ForgeConfigSpec.BooleanValue sonicBoom;
        public final ForgeConfigSpec.DoubleValue wakeSpeed;
        public final ForgeConfigSpec.IntValue wakeHeight;
        public final ForgeConfigSpec.DoubleValue boomSpeed;
        public final ForgeConfigSpec.BooleanValue takeoffs;
        public final ForgeConfigSpec.DoubleValue takeoffSpeed;
        public final ForgeConfigSpec.BooleanValue landings;
        public final ForgeConfigSpec.DoubleValue landingSpeed;
        public final ForgeConfigSpec.BooleanValue throughTrees;
        public final ForgeConfigSpec.DoubleValue throughSpeed;
        public final ForgeConfigSpec.IntValue throughAhead;
        public final ForgeConfigSpec.BooleanValue runningDust;
        public final ForgeConfigSpec.DoubleValue runningSpeed;
        public final ForgeConfigSpec.BooleanValue lightFeet;
        public final ForgeConfigSpec.IntValue lightFeetFlyLevel;
        public final ForgeConfigSpec.BooleanValue senseNames;
        public final ForgeConfigSpec.BooleanValue rivals;
        public final ForgeConfigSpec.DoubleValue rivalBonus;
        public final ForgeConfigSpec.IntValue rivalCooldown;
        public final ForgeConfigSpec.BooleanValue rivalShadows;
        public final ForgeConfigSpec.DoubleValue shadowStrength;
        public final ForgeConfigSpec.IntValue shadowCooldown;
        public final ForgeConfigSpec.IntValue shadowMinutes;
        public final ForgeConfigSpec.BooleanValue teachKi;
        public final ForgeConfigSpec.BooleanValue groupShare;
        public final ForgeConfigSpec.BooleanValue transfusion;
        public final ForgeConfigSpec.DoubleValue transfusionSeconds;
        public final ForgeConfigSpec.DoubleValue transfusionHealth;
        public final ForgeConfigSpec.IntValue maxDonors;
        public final ForgeConfigSpec.BooleanValue toss;
        public final ForgeConfigSpec.DoubleValue tossShare;
        public final ForgeConfigSpec.IntValue tossRange;
        public final ForgeConfigSpec.IntValue tossCooldown;
        public final ForgeConfigSpec.BooleanValue senzuToss;
        public final ForgeConfigSpec.IntValue senzuTossCooldown;
        public final ForgeConfigSpec.BooleanValue senzuEatOnCatch;
        public final ForgeConfigSpec.BooleanValue nature;
        public final ForgeConfigSpec.IntValue natureRange;
        public final ForgeConfigSpec.DoubleValue natureBars;
        public final ForgeConfigSpec.IntValue natureCooldown;
        public final ForgeConfigSpec.IntValue natureArea;
        public final ForgeConfigSpec.BooleanValue fusionMoments;
        public final ForgeConfigSpec.BooleanValue duels;
        public final ForgeConfigSpec.IntValue duelRadius;
        public final ForgeConfigSpec.IntValue duelMinutes;
        public final ForgeConfigSpec.IntValue challengeRange;
        public final ForgeConfigSpec.IntValue challengeSeconds;
        public final ForgeConfigSpec.IntValue ringOutSeconds;
        public final ForgeConfigSpec.BooleanValue duelOptOut;
        public final ForgeConfigSpec.IntValue duelDeclineWait;
        public final ForgeConfigSpec.DoubleValue potentialTp;
        public final ForgeConfigSpec.DoubleValue potentialMastery;
        public final ForgeConfigSpec.DoubleValue zenkaiPerLevel;
        public final ForgeConfigSpec.BooleanValue sense;
        public final ForgeConfigSpec.IntValue senseRange;
        public final ForgeConfigSpec.DoubleValue senseRatio;
        public final ForgeConfigSpec.BooleanValue awakening;
        public final ForgeConfigSpec.BooleanValue tenaciousBody;
        public final ForgeConfigSpec.IntValue tenaciousBodyBonus;
        public final ForgeConfigSpec.BooleanValue chamberDepths;
        public final ForgeConfigSpec.IntValue chamberOutAt;
        public final ForgeConfigSpec.IntValue chamberDeepAt;
        public final ForgeConfigSpec.IntValue chamberFarAt;
        public final ForgeConfigSpec.DoubleValue chamberOutTp;
        public final ForgeConfigSpec.DoubleValue chamberDeepTp;
        public final ForgeConfigSpec.DoubleValue chamberFarTp;
        public final ForgeConfigSpec.IntValue chamberCycleMinutes;
        public final ForgeConfigSpec.BooleanValue chamberThinAir;
        public final ForgeConfigSpec.BooleanValue htcOnTop;
        public final ForgeConfigSpec.BooleanValue scouterChannels;
        public final ForgeConfigSpec.BooleanValue scouterPings;
        public final ForgeConfigSpec.IntValue scouterPingRange;
        public final ForgeConfigSpec.BooleanValue scouterSpikes;
        public final ForgeConfigSpec.IntValue scouterSpikeRange;
        public final ForgeConfigSpec.DoubleValue scouterSpikeRatio;
        public final ForgeConfigSpec.IntValue scouterSpikeMin;
        public final ForgeConfigSpec.BooleanValue scouterMute;
        public final ForgeConfigSpec.BooleanValue battles;
        public final ForgeConfigSpec.IntValue stageRadius;
        public final ForgeConfigSpec.IntValue battleMinutes;
        public final ForgeConfigSpec.IntValue battleJoinSeconds;
        public final ForgeConfigSpec.IntValue battleMaxFighters;
        public final ForgeConfigSpec.IntValue battleAnnounceRange;
        public final ForgeConfigSpec.BooleanValue namekianDiet;
        public final ForgeConfigSpec.IntValue waterHunger;
        public final ForgeConfigSpec.DoubleValue waterSaturation;
        public final ForgeConfigSpec.DoubleValue namekianFoodFactor;
        public final ForgeConfigSpec.BooleanValue majinDiet;
        public final ForgeConfigSpec.DoubleValue majinSweetsFactor;
        public final ForgeConfigSpec.BooleanValue combinedBeams;
        public final ForgeConfigSpec.IntValue beamFireWindow;
        public final ForgeConfigSpec.IntValue beamMaxAngle;
        public final ForgeConfigSpec.IntValue beamMaxCombined;
        public final ForgeConfigSpec.DoubleValue beamDamageShare;
        public final ForgeConfigSpec.DoubleValue beamSizeShare;
        public final ForgeConfigSpec.BooleanValue beamRecoil;
        public final ForgeConfigSpec.DoubleValue recoilStrength;
        public final ForgeConfigSpec.BooleanValue smash;
        public final ForgeConfigSpec.DoubleValue smashSpeed;
        public final ForgeConfigSpec.BooleanValue smashDestruction;
        public final ForgeConfigSpec.BooleanValue techniqueNames;
        public final ForgeConfigSpec.BooleanValue moonDestroyable;
        public final ForgeConfigSpec.IntValue moonReturnDays;
        public final ForgeConfigSpec.BooleanValue moonRevertsOozaru;
        public final ForgeConfigSpec.BooleanValue moonOozaruAnyMoon;
        public final ForgeConfigSpec.DoubleValue moonOozaruLookDegrees;
        public final ForgeConfigSpec.BooleanValue clashColors;
        public final ForgeConfigSpec.BooleanValue swat;
        public final ForgeConfigSpec.DoubleValue swatReach;
        public final ForgeConfigSpec.DoubleValue swatMaxShare;
        public final ForgeConfigSpec.DoubleValue swatMaxSize;
        public final ForgeConfigSpec.IntValue swatMissTicks;
        public final ForgeConfigSpec.DoubleValue swatBoost;
        public final ForgeConfigSpec.BooleanValue healing;
        public final ForgeConfigSpec.IntValue healMinutes;
        public final ForgeConfigSpec.IntValue healPerSecond;
        public final ForgeConfigSpec.IntValue healMaxBlocks;
        public final ForgeConfigSpec.BooleanValue healKiBlasts;
        public final ForgeConfigSpec.BooleanValue healImpacts;
        public final ForgeConfigSpec.BooleanValue healFallen;
        public final ForgeConfigSpec.BooleanValue healWish;
        public final ForgeConfigSpec.IntValue healWishPerSecond;
        public final ForgeConfigSpec.DoubleValue healMillisPerTick;
        public final ForgeConfigSpec.BooleanValue transformBurst;
        public final ForgeConfigSpec.DoubleValue burstRadius;
        public final ForgeConfigSpec.DoubleValue burstStrength;
        public final ForgeConfigSpec.IntValue burstCooldown;
        public final ForgeConfigSpec.BooleanValue burstBlasts;
        public final ForgeConfigSpec.BooleanValue giants;
        public final ForgeConfigSpec.DoubleValue giantScale;
        public final ForgeConfigSpec.BooleanValue giantStomp;
        public final ForgeConfigSpec.DoubleValue stompStrength;
        public final ForgeConfigSpec.IntValue stompCooldown;
        public final ForgeConfigSpec.BooleanValue stompCraters;
        public final ForgeConfigSpec.BooleanValue giantSteady;
        public final ForgeConfigSpec.DoubleValue giantSteadyRatio;
        public final ForgeConfigSpec.BooleanValue fightMastery;
        public final ForgeConfigSpec.DoubleValue masteryDuel;
        public final ForgeConfigSpec.DoubleValue masteryBattle;
        public final ForgeConfigSpec.DoubleValue masteryRival;
        public final ForgeConfigSpec.IntValue masteryCooldown;
        public final ForgeConfigSpec.BooleanValue beamCancel;
        public final ForgeConfigSpec.BooleanValue beamDeflect;
        public final ForgeConfigSpec.DoubleValue deflectRatio;
        public final ForgeConfigSpec.DoubleValue deflectShare;
        public final ForgeConfigSpec.IntValue deflectAngle;
        public final ForgeConfigSpec.BooleanValue villages;
        public final ForgeConfigSpec.DoubleValue heroDiscount;
        public final ForgeConfigSpec.DoubleValue villainMarkup;
        public final ForgeConfigSpec.BooleanValue villagersFlee;
        public final ForgeConfigSpec.IntValue fleeRange;
        public final ForgeConfigSpec.BooleanValue golemsAttack;
        public final ForgeConfigSpec.BooleanValue animalFear;
        public final ForgeConfigSpec.DoubleValue animalFearRadius;
        public final ForgeConfigSpec.DoubleValue animalFearRatio;
        public final ForgeConfigSpec.DoubleValue animalFearSpeed;
        public final ForgeConfigSpec.BooleanValue lifts;
        public final ForgeConfigSpec.DoubleValue liftReach;
        public final ForgeConfigSpec.IntValue liftFloatSeconds;
        public final ForgeConfigSpec.BooleanValue carryAnimals;
        public final ForgeConfigSpec.BooleanValue carryVillagers;
        public final ForgeConfigSpec.DoubleValue carryMaxWidth;
        public final ForgeConfigSpec.BooleanValue shoulder;
        public final ForgeConfigSpec.DoubleValue shoulderReach;
        public final ForgeConfigSpec.IntValue shoulderMost;
        public final ForgeConfigSpec.DoubleValue shoulderKiCost;
        public final ForgeConfigSpec.BooleanValue shoulderPartyOnly;
        public final ForgeConfigSpec.BooleanValue diary;
        public final ForgeConfigSpec.BooleanValue kamiWatch;
        public final ForgeConfigSpec.IntValue watchCrouchSeconds;
        public final ForgeConfigSpec.DoubleValue watchShare;
        public final ForgeConfigSpec.IntValue watchMax;
        public final ForgeConfigSpec.IntValue newsRange;
        public final ForgeConfigSpec.IntValue statusPermission;
        public final ForgeConfigSpec.BooleanValue statusTiming;
        public final ForgeConfigSpec.IntValue statusSeconds;
        public final ForgeConfigSpec.BooleanValue leaves;
        public final ForgeConfigSpec.BooleanValue leafStrip;
        public final ForgeConfigSpec.IntValue leafStripTier;
        public final ForgeConfigSpec.BooleanValue arrivals;
        public final ForgeConfigSpec.IntValue arrivalCooldown;
        public final ForgeConfigSpec.IntValue chamberLimitDays;
        public final ForgeConfigSpec.BooleanValue chamberWish;
        public final ForgeConfigSpec.IntValue chamberWishDays;
        public final ForgeConfigSpec.DoubleValue chamberMastery;
        public final ForgeConfigSpec.BooleanValue training;
        public final ForgeConfigSpec.BooleanValue trainingOnlyInChamber;
        public final ForgeConfigSpec.IntValue trainingMaxOpponents;
        public final ForgeConfigSpec.IntValue trainingTpPercent;
        public final ForgeConfigSpec.IntValue trainingMasteryPercent;
        public final ForgeConfigSpec.BooleanValue overcharge;
        public final ForgeConfigSpec.DoubleValue overchargePerLevel;
        public final ForgeConfigSpec.DoubleValue overchargeMax;
        public final ForgeConfigSpec.IntValue overchargeFillSeconds;
        public final ForgeConfigSpec.IntValue overchargeFadeSeconds;
        public final ForgeConfigSpec.IntValue senzuDays;
        public final ForgeConfigSpec.BooleanValue strikeNames;
        public final ForgeConfigSpec.BooleanValue techniqueSets;
        public final ForgeConfigSpec.IntValue techniqueSetMax;
        public final ForgeConfigSpec.IntValue techniqueSetSeconds;
        public final ForgeConfigSpec.BooleanValue arrowBounce;
        public final ForgeConfigSpec.DoubleValue arrowRatio;
        public final ForgeConfigSpec.IntValue arrowMinPower;
        public final ForgeConfigSpec.BooleanValue arrowCatch;
        public final ForgeConfigSpec.BooleanValue watchCreatures;
        public final ForgeConfigSpec.BooleanValue underdog;
        public final ForgeConfigSpec.DoubleValue underdogMinRatio;
        public final ForgeConfigSpec.DoubleValue underdogPerRatio;
        public final ForgeConfigSpec.DoubleValue underdogMax;
        public final ForgeConfigSpec.IntValue underdogCooldown;
        public final ForgeConfigSpec.BooleanValue cannonDrill;
        public final ForgeConfigSpec.IntValue cannonDepth;
        public final ForgeConfigSpec.DoubleValue cannonHardness;
        public final ForgeConfigSpec.BooleanValue saibamanSeeds;
        public final ForgeConfigSpec.DoubleValue seedChance;
        public final ForgeConfigSpec.DoubleValue sproutedSeedChance;
        public final ForgeConfigSpec.IntValue sproutSeconds;
        public final ForgeConfigSpec.IntValue sproutsPerPlayer;
        public final ForgeConfigSpec.BooleanValue punchRecords;
        public final ForgeConfigSpec.IntValue punchWindow;
        public final ForgeConfigSpec.BooleanValue glindMagic;
        public final ForgeConfigSpec.BooleanValue telekinesis;
        public final ForgeConfigSpec.DoubleValue tkRange;
        public final ForgeConfigSpec.DoubleValue tkHoldDistance;
        public final ForgeConfigSpec.IntValue tkMaxSeconds;
        public final ForgeConfigSpec.DoubleValue tkKiCost;
        public final ForgeConfigSpec.DoubleValue tkKiPerSecond;
        public final ForgeConfigSpec.DoubleValue tkStrengthRatio;
        public final ForgeConfigSpec.BooleanValue tkPlayers;
        public final ForgeConfigSpec.BooleanValue tkItems;
        public final ForgeConfigSpec.DoubleValue tkThrowSpeed;
        public final ForgeConfigSpec.DoubleValue tkThrowDamage;
        public final ForgeConfigSpec.IntValue tkCooldown;
        public final ForgeConfigSpec.IntValue tkBreakFreeSeconds;
        public final ForgeConfigSpec.BooleanValue glindTransmission;
        public final ForgeConfigSpec.BooleanValue rockLift;
        public final ForgeConfigSpec.DoubleValue rockHardness;
        public final ForgeConfigSpec.DoubleValue rockKiCost;
        public final ForgeConfigSpec.DoubleValue rockThrowSpeed;
        public final ForgeConfigSpec.DoubleValue rockDamage;
        public final ForgeConfigSpec.IntValue rockCooldown;
        public final ForgeConfigSpec.IntValue rockClusterBlocks;
        public final ForgeConfigSpec.IntValue rockClusterTicks;
        public final ForgeConfigSpec.IntValue rockClusterRadius;
        public final ForgeConfigSpec.DoubleValue rockClusterKiCost;
        public final ForgeConfigSpec.DoubleValue rockClusterDamage;
        public final ForgeConfigSpec.IntValue rockClusterPerTier;
        public final ForgeConfigSpec.IntValue rockClusterMost;
        public final ForgeConfigSpec.BooleanValue wave;
        public final ForgeConfigSpec.DoubleValue waveRange;
        public final ForgeConfigSpec.DoubleValue waveAngle;
        public final ForgeConfigSpec.DoubleValue waveStrength;
        public final ForgeConfigSpec.DoubleValue waveKiCost;
        public final ForgeConfigSpec.IntValue waveCooldown;
        public final ForgeConfigSpec.BooleanValue mend;
        public final ForgeConfigSpec.IntValue mendSeconds;
        public final ForgeConfigSpec.DoubleValue mendShare;
        public final ForgeConfigSpec.DoubleValue mendKiPerSecond;
        public final ForgeConfigSpec.BooleanValue mendFriends;
        public final ForgeConfigSpec.DoubleValue mendFriendRange;
        public final ForgeConfigSpec.BooleanValue mendFriendsPartyOnly;
        public final ForgeConfigSpec.BooleanValue kiMastery;
        public final ForgeConfigSpec.DoubleValue kiMasteryCost;
        public final ForgeConfigSpec.DoubleValue kiMasteryDamage;
        public final ForgeConfigSpec.BooleanValue patternWish;
        public final ForgeConfigSpec.IntValue patternPicks;
        public final ForgeConfigSpec.BooleanValue senzuGarden;
        public final ForgeConfigSpec.DoubleValue senzuRipenDays;
        public final ForgeConfigSpec.IntValue senzuBeans;
        public final ForgeConfigSpec.IntValue senzuBushes;
        public final ForgeConfigSpec.IntValue korinRange;
        public final ForgeConfigSpec.DoubleValue korinSpeed;
        public final ForgeConfigSpec.BooleanValue senzuRipeNotice;
        public final ForgeConfigSpec.BooleanValue gardenList;
        public final ForgeConfigSpec.BooleanValue awayNotice;
        public final ForgeConfigSpec.IntValue awayNoticeMax;
        public final ForgeConfigSpec.BooleanValue plowing;
        public final ForgeConfigSpec.DoubleValue plowTpShare;
        public final ForgeConfigSpec.IntValue plowBlocksPerDay;
        public final ForgeConfigSpec.BooleanValue waterfall;
        public final ForgeConfigSpec.IntValue waterfallHeight;
        public final ForgeConfigSpec.IntValue waterfallSeconds;
        public final ForgeConfigSpec.DoubleValue waterfallTpShare;
        public final ForgeConfigSpec.IntValue waterfallPaysPerDay;
        public final ForgeConfigSpec.BooleanValue ajissaGrowth;
        public final ForgeConfigSpec.DoubleValue ajissaHours;
        public final ForgeConfigSpec.BooleanValue mightTree;
        public final ForgeConfigSpec.DoubleValue mightGrowDays;
        public final ForgeConfigSpec.DoubleValue mightFruitDays;
        public final ForgeConfigSpec.IntValue mightDrainRadius;
        public final ForgeConfigSpec.IntValue mightDrainSeconds;
        public final ForgeConfigSpec.IntValue mightMaxDrained;
        public final ForgeConfigSpec.IntValue mightTrees;

        Server(ForgeConfigSpec.Builder b) {
            b.push("growth");
            b.push("potential");
            awakening = b.comment("The first master to unlock your power (Guru, Elder Kai or Babidi) raises Potential by 1, up to 5.")
                    .define("awakening", true);
            potentialTp = b.comment("TP bonus for each Potential level above 1. At 0.5, Potential 3 earns x2.")
                    .defineInRange("tpPerLevel", Potential.TP_PER_LEVEL, 0.0, 10.0);
            potentialMastery = b.comment("Form mastery bonus for each Potential level above 1.")
                    .defineInRange("masteryPerLevel", Potential.MASTERY_PER_LEVEL, 0.0, 10.0);
            zenkaiPerLevel = b.comment("Zenkai boost added for each Potential level above 1, in normal boosts. 0 turns it off.")
                    .defineInRange("zenkaiPerLevel", 1.0, 0.0, 4.0);
            b.pop();

            b.push("tenaciousBody");
            tenaciousBody = b.comment("Human passive: faster stamina regen. Lost when turned into an android.")
                    .define("enabled", true);
            tenaciousBodyBonus = b.comment("Extra stamina regen with Tenacious Body, in percent.")
                    .defineInRange("bonus", 25, 0, 500);
            b.pop();

            b.push("kiMastery");
            kiMastery = b.comment("In a fully mastered transformation, ki techniques cost less ki and ki blasts hit harder.")
                    .define("enabled", true);
            kiMasteryCost = b.comment("What a ki technique's ki cost is multiplied by at full mastery. 0.5 is half.")
                    .defineInRange("costMultiplier", 0.5, 0.0, 1.0);
            kiMasteryDamage = b.comment("Extra ki blast damage at full mastery, as a share. 0.15 is 15% more.")
                    .defineInRange("damageBonus", 0.15, 0.0, 2.0);
            b.pop();

            b.push("formMastery");
            fightMastery = b.comment("Winning a duel or battle, or beating your rival, while in a form gives that form mastery.")
                    .define("enabled", true);
            masteryDuel = b.comment("Mastery for a duel win, in percent of the form's max mastery.")
                    .defineInRange("duelWin", 5.0, 0.0, 100.0);
            masteryBattle = b.comment("Mastery for a battle win, in percent of the form's max mastery.")
                    .defineInRange("battleWin", 8.0, 0.0, 100.0);
            masteryRival = b.comment("Mastery for beating your rival, in percent of the form's max mastery.")
                    .defineInRange("rivalWin", 10.0, 0.0, 100.0);
            masteryCooldown = b.comment("Minutes before the same player earns mastery from a win again.")
                    .defineInRange("cooldownMinutes", 10, 0, 1440);
            b.pop();

            b.push("underdog");
            underdog = b.comment("Beating something with more battle power than you pays extra TP, more the bigger the gap.")
                    .define("enabled", true);
            underdogMinRatio = b.comment("How many times your battle power the foe needs before it pays extra.")
                    .defineInRange("minRatio", 1.25, 1.0, 100.0);
            underdogPerRatio = b.comment("Extra TP for each time stronger the foe is, as a share of DMZ's TP for the kill.")
                    .defineInRange("perRatio", 0.5, 0.0, 10.0);
            underdogMax = b.comment("Most extra TP from one kill, as a share of DMZ's TP for the kill.")
                    .defineInRange("maxBonus", 2.0, 0.0, 100.0);
            underdogCooldown = b.comment("Minutes before beating the same player pays extra again. Creatures have no wait.")
                    .defineInRange("playerCooldownMinutes", 10, 0, 10080);
            b.pop();

            b.push("rivals");
            rivals = b.comment("Whoever last beat you becomes your rival. Beating them back pays a title and bonus TP.")
                    .define("enabled", true);
            rivalBonus = b.comment("Bonus TP for beating your rival, as a share of DMZ's TP for that kill.")
                    .defineInRange("bonus", 1.0, 0.0, 100.0);
            rivalCooldown = b.comment("Minutes before the same rival pays a bonus again.")
                    .defineInRange("cooldownMinutes", 30, 0, 10080);
            rivalShadows = b.comment("/dmzzero rival shadow calls up a copy of your rival that knocks down instead of killing.")
                    .define("shadows", true);
            shadowStrength = b.comment("Shadow strength compared with the real rival. 1 is equal.")
                    .defineInRange("shadowStrength", 1.0, 0.25, 2.0);
            shadowCooldown = b.comment("Minutes between rival shadows.")
                    .defineInRange("shadowCooldownMinutes", 10, 0, 1440);
            shadowMinutes = b.comment("Minutes before a rival shadow fades.")
                    .defineInRange("shadowMinutes", 5, 1, 60);
            b.pop();

            b.push("diary");
            diary = b.comment("Keep a training diary: /dmzzero today shows the TP you earned today and yesterday, by where it came from.")
                    .define("enabled", true);
            b.pop();
            b.pop();

            b.push("fighting");
            b.push("impacts");
            transformations = b.comment("Transforming cracks the ground, strikes lightning and sends out a shockwave.")
                    .define("transformations", true);
            auras = b.comment("Charging ki cracks the ground. Longer charges and stronger forms crack more.")
                    .define("auras", true);
            lightning = b.comment("Lightning near players in forms that have it. Visual only: no fire, no damage.")
                    .define("lightning", true);
            cracks = b.comment("Crack marks on the ground around charging and transforming players. Visual only.")
                    .define("groundCracks", true);
            destruction = b.comment("Auras and transformations break glass, plants and stone. Follows DMZ's ki griefing rules.")
                    .define("destruction", true);
            masteryCalms = b.comment("Mastered forms stop flickering past smoothAt and stop breaking things at calmAt.")
                    .define("masteryCalms", true);
            smoothAt = b.comment("Share of max mastery at which a form stops flickering.")
                    .defineInRange("smoothAt", 0.5, 0.0, 1.0);
            calmAt = b.comment("Share of max mastery at which a form stops cracking and breaking things. Above 1 means never.")
                    .defineInRange("calmAt", 1.0, 0.0, 2.0);
            firstTime = b.comment("A character's first time in a form gets a bigger burst and the form's name on screen.")
                    .define("firstTimeMoments", true);
            clashes = b.comment("Beam and strike clashes crack the ground, shake the camera and explode when they end.")
                    .define("clashes", true);
            water = b.comment("Charging and transforming over water throws spray and splashes.")
                    .define("waterReactions", true);
            standoffs = b.comment("Two players facing each other with auras up throw sparks and crack the ground between them.")
                    .define("standoffs", true);
            standoffRange = b.comment("Most blocks between two players for a standoff.")
                    .defineInRange("standoffRange", 8.0, 2.0, 32.0);
            clashColors = b.comment("Clash and standoff sparks use each side's color.")
                    .define("clashColors", true);
            leaves = b.comment("Shockwaves, sonic booms and smashes near trees tear leaves loose. Particles only.")
                    .define("leaves", true);
            leafStrip = b.comment("With destruction on, strong forms' shockwaves strip leaf blocks. They heal like other ki damage.")
                    .define("leafStrip", true);
            leafStripTier = b.comment("Weakest form tier whose shockwave strips leaf blocks. Super Saiyan 3 is 4.")
                    .defineInRange("leafStripTier", 4, 1, 5);
            b.pop();

            b.push("transformBurst");
            transformBurst = b.comment("Finishing a transformation sends a shockwave that pushes enemies and ki blasts away. Party members are spared.")
                    .define("enabled", true);
            burstRadius = b.comment("Burst radius in a tier 1 form, in blocks. Each tier above adds 1.")
                    .defineInRange("radius", 5.0, 1.0, 32.0);
            burstStrength = b.comment("Burst push strength. 1 is normal.")
                    .defineInRange("strength", 1.0, 0.0, 5.0);
            burstCooldown = b.comment("Seconds before the same player's next burst.")
                    .defineInRange("cooldownSeconds", 10, 0, 600);
            burstBlasts = b.comment("The burst also throws incoming ki blasts back the way they came.")
                    .define("deflectBlasts", true);
            b.pop();

            b.push("giants");
            giants = b.comment("Giant forms like the Great Ape stomp when they land and shrug off knockback from smaller fighters.")
                    .define("enabled", true);
            giantScale = b.comment("Size, in times normal, that counts as a giant. The Great Ape is 3.8, the Orange Giant 2.8.")
                    .defineInRange("minScale", 2.5, 1.1, 20.0);
            giantStomp = b.comment("A giant landing from a fall pushes away everyone nearby that it may hit.")
                    .define("stomp", true);
            stompStrength = b.comment("Stomp push strength. 1 is normal.")
                    .defineInRange("stompStrength", 1.0, 0.0, 5.0);
            stompCooldown = b.comment("Seconds between one giant's stomps.")
                    .defineInRange("stompCooldownSeconds", 2, 0, 60);
            stompCraters = b.comment("Stomps leave a small crater that heals like other ki damage. Follows DMZ's ki griefing rules.")
                    .define("craters", true);
            giantSteady = b.comment("Fighters much smaller than a giant can't knock it back.")
                    .define("steady", true);
            giantSteadyRatio = b.comment("Attacker size, as a share of the giant's, needed to knock it back.")
                    .defineInRange("steadyRatio", 0.6, 0.0, 1.0);
            b.pop();

            b.push("smash");
            smash = b.comment("Heavy knockback slams the target into the block it hits, with dust and cracks.")
                    .define("enabled", true);
            smashSpeed = b.comment("Knockback speed that counts as a smash, in blocks per tick. DMZ's momentum punch is 1.8.")
                    .defineInRange("speed", 1.0, 0.2, 10.0);
            smashDestruction = b.comment("A smash a player causes breaks glass and plants and cracks the block hit. Follows [fighting.impacts] destruction.")
                    .define("destruction", true);
            b.pop();

            b.push("swat");
            swat = b.comment("Punch an incoming ki ball at the right moment to knock it where you look. Not beams or Spirit Bombs.")
                    .define("enabled", true);
            swatReach = b.comment("Most blocks from you a ki ball can be when you punch it.")
                    .defineInRange("reach", 3.5, 1.5, 6.0);
            swatMaxShare = b.comment("Strongest ki ball you can swat: one dealing at most this share of your max health.")
                    .defineInRange("maxShare", 0.5, 0.0, 10.0);
            swatMaxSize = b.comment("Biggest ki ball you can swat. DMZ's small ki ball is 3, medium 10, giant 17.5.")
                    .defineInRange("maxSize", 10.0, 0.0, 1000.0);
            swatMissTicks = b.comment("Ticks after a missed swat before the next punch can swat.")
                    .defineInRange("missTicks", 8, 0, 100);
            swatBoost = b.comment("Speed of a swatted ki ball, in times its incoming speed.")
                    .defineInRange("speedBoost", 1.2, 0.5, 4.0);
            b.pop();

            b.push("arrows");
            arrowBounce = b.comment("Arrows and tridents glance off fighters far stronger than whoever shot them.")
                    .define("bounce", true);
            arrowRatio = b.comment("Times the shooter's battle power you need for its arrows to bounce off. DMZ gives every mob one.")
                    .defineInRange("ratio", 10.0, 1.0, 1000.0);
            arrowMinPower = b.comment("Battle power you need before any arrow bounces off, skeletons' included.")
                    .defineInRange("minPower", 1000, 0, Integer.MAX_VALUE);
            arrowCatch = b.comment("Punch an incoming arrow at the right moment to catch it. Uses the [fighting.swat] reach.")
                    .define("catching", true);
            b.pop();

            b.push("callouts");
            techniqueNames = b.comment("Fired ki attacks carry their technique name, shown over the caster on DMZ Zero clients.")
                    .define("techniqueNames", true);
            strikeNames = b.comment("Strike attacks send their technique name to DMZ Zero clients nearby, shown over the striker.")
                    .define("strikeNames", true);
            b.pop();

            b.push("techniqueSets");
            techniqueSets = b.comment("Save what's in your technique slots as named sets and swap them: /dmzzero techniques, or the ; key.")
                    .define("enabled", true);
            techniqueSetMax = b.comment("Sets each character can keep.")
                    .defineInRange("maxSets", 4, 1, 16);
            techniqueSetSeconds = b.comment("Seconds between two switches. 0 is no wait.")
                    .defineInRange("switchSeconds", 3, 0, 600);
            b.pop();
            b.pop();

            b.push("beams");
            b.push("cancel");
            beamCancel = b.comment("The Cancel beam key (M) ends your own ki wave early.")
                    .define("enabled", true);
            b.pop();

            b.push("deflect");
            beamDeflect = b.comment("Blocking a much weaker fighter's beam knocks it into the sky. You have to face it.")
                    .define("enabled", true);
            deflectRatio = b.comment("Times the caster's battle power you need to deflect a player's beam.")
                    .defineInRange("ratio", 3.0, 1.0, 100.0);
            deflectShare = b.comment("A creature's beam can be deflected if its hit would take at most this share of your max health.")
                    .defineInRange("share", 0.25, 0.0, 10.0);
            deflectAngle = b.comment("Most degrees you can look away from a beam and still deflect it.")
                    .defineInRange("angle", 60, 10, 120);
            b.pop();

            b.push("combined");
            combinedBeams = b.comment("Party members' beams fired together in the same direction merge into one bigger beam.")
                    .define("enabled", true);
            beamFireWindow = b.comment("Most ticks between beams for them to merge. 20 ticks is 1 second.")
                    .defineInRange("fireWindowTicks", 20, 1, 200);
            beamMaxAngle = b.comment("Most degrees between beam directions for them to merge.")
                    .defineInRange("maxAngle", 75, 1, 120);
            beamMaxCombined = b.comment("Most beams in one combined beam.")
                    .defineInRange("maxBeams", 4, 2, 16);
            beamDamageShare = b.comment("Share of each joining beam's damage the combined beam gains.")
                    .defineInRange("damageShare", 1.0, 0.0, 5.0);
            beamSizeShare = b.comment("Share of each joining beam's size the combined beam gains.")
                    .defineInRange("sizeShare", 0.5, 0.0, 5.0);
            b.pop();

            b.push("recoil");
            beamRecoil = b.comment("Firing a beam pushes you back. Waves push the most, ki balls not at all.")
                    .define("enabled", true);
            recoilStrength = b.comment("Recoil strength. At 1, a full wave fired straight down lifts you about 5 blocks.")
                    .defineInRange("strength", 1.0, 0.0, 5.0);
            b.pop();

            b.push("cannon");
            cannonDrill = b.comment("A player's Special Beam Cannon bores a one-block tunnel through what it hits and keeps going.")
                    .define("enabled", true);
            cannonDepth = b.comment("Most blocks one Special Beam Cannon bores through.")
                    .defineInRange("depth", 8, 1, 64);
            cannonHardness = b.comment("Hardest block it bores through. Stone is 1.5, obsidian 50. Bedrock never gives.")
                    .defineInRange("maxHardness", 50.0, 0.0, 1000.0);
            b.pop();
            b.pop();

            b.push("ki");
            b.push("share");
            teachKi = b.comment("Giving ki teaches both players each other's ki, so Power Sense can name them.")
                    .define("teachKi", true);
            groupShare = b.comment("Three or more players giving ki to one crack the ground around them. Visual only.")
                    .define("groupShare", true);
            b.pop();

            b.push("transfusion");
            transfusion = b.comment("Giving ki to a knocked-down player gets them up when the bar fills. Uses a World Boss revive.")
                    .define("enabled", true);
            transfusionSeconds = b.comment("Seconds a transfusion takes with one donor. Each extra donor makes it faster.")
                    .defineInRange("seconds", 8.0, 1.0, 120.0);
            transfusionHealth = b.comment("Share of max health a player gets up with.")
                    .defineInRange("health", 0.25, 0.05, 1.0);
            maxDonors = b.comment("Most donors that speed up one transfusion.")
                    .defineInRange("maxDonors", 5, 1, 32);
            b.pop();

            b.push("toss");
            toss = b.comment("Ctrl+Alt+left-click a player to throw them some of your ki.")
                    .define("enabled", true);
            tossShare = b.comment("Share of your max ki in one toss.")
                    .defineInRange("share", 0.15, 0.01, 1.0);
            tossRange = b.comment("Most blocks a toss reaches.")
                    .defineInRange("range", 16, 4, 64);
            tossCooldown = b.comment("Seconds between tosses.")
                    .defineInRange("cooldownSeconds", 8, 0, 600);
            b.pop();

            b.push("senzuToss");
            senzuToss = b.comment("With a senzu bean in your hand, the same Ctrl+Alt+left-click throws the bean instead. Reaches as far as tossRange.")
                    .define("enabled", true);
            senzuTossCooldown = b.comment("Seconds between senzu throws.")
                    .defineInRange("cooldownSeconds", 2, 0, 600);
            senzuEatOnCatch = b.comment("Whoever catches a senzu eats it at once if it restores something they're missing. Otherwise it goes in their inventory.")
                    .define("eatOnCatch", true);
            b.pop();

            b.push("nature");
            nature = b.comment("Animals, villagers and plants near a charging Spirit Bomb add energy to it. Nothing is harmed.")
                    .define("enabled", true);
            natureRange = b.comment("Blocks around the caster that give energy.")
                    .defineInRange("range", 24, 4, 64);
            natureBars = b.comment("Most energy nature adds to one Spirit Bomb, in the caster's ki bars.")
                    .defineInRange("bars", 1.0, 0.05, 10.0);
            natureCooldown = b.comment("Minutes before an area gives energy again.")
                    .defineInRange("cooldownMinutes", 10, 0, 1440);
            natureArea = b.comment("Blocks around a spot that gave energy that count as drained.")
                    .defineInRange("area", 64, 8, 512);
            b.pop();

            b.push("powerSense");
            sense = b.comment("Weaker players nearby feel a much stronger player transform, with the direction.")
                    .define("enabled", true);
            senseRange = b.comment("Blocks it's felt from. Doubled with DMZ's Ki Sense skill.")
                    .defineInRange("range", 160, 16, 1024);
            senseRatio = b.comment("How many times stronger in battle power the transforming player must be.")
                    .defineInRange("ratio", 1.5, 1.0, 1000.0);
            senseNames = b.comment("Name the player when you know their ki: you fought, partied or are rivals.")
                    .define("names", true);
            arrivals = b.comment("You feel it when a DMZ saga fighter or world boss far stronger than you appears nearby.")
                    .define("arrivals", true);
            arrivalCooldown = b.comment("Seconds before the same player feels another arrival.")
                    .defineInRange("arrivalCooldownSeconds", 30, 0, 3600);
            b.pop();

            b.push("kamiWatch");
            kamiWatch = b.comment("Crouch still on Kami's Lookout to sense every strong power in the dimension, with direction and distance.")
                    .define("enabled", true);
            watchCrouchSeconds = b.comment("Seconds to crouch still before the watch starts.")
                    .defineInRange("crouchSeconds", 3, 0, 30);
            watchShare = b.comment("Weakest power sensed, as a share of your own battle power.")
                    .defineInRange("minShare", 0.25, 0.0, 10.0);
            watchMax = b.comment("Most powers shown at once, strongest first.")
                    .defineInRange("maxShown", 12, 1, 32);
            watchCreatures = b.comment("The watch also senses DMZ's strong creatures and saga fighters, not only players.")
                    .define("creatures", true);
            b.pop();

            b.push("scouters");
            scouterChannels = b.comment("Scouters work as radios: /sc <message> or a chat line starting with # reaches the same scouter color.")
                    .define("channels", true);
            scouterPings = b.comment("DMZ Zero players can ping what they aim at (Y) for their scouter channel.")
                    .define("pings", true);
            scouterPingRange = b.comment("Most blocks a ping reaches.")
                    .defineInRange("pingRange", 128, 16, 512);
            scouterSpikes = b.comment("A scouter warns you when a power level nearby suddenly jumps.")
                    .define("spikeAlerts", true);
            scouterSpikeRange = b.comment("Most blocks a scouter picks up a power spike from.")
                    .defineInRange("spikeRange", 192, 16, 1024);
            scouterSpikeRatio = b.comment("How many times higher a reading must get within 3 seconds to count as a spike.")
                    .defineInRange("spikeRatio", 1.5, 1.1, 1000.0);
            scouterSpikeMin = b.comment("Readings below this never count as a spike.")
                    .defineInRange("spikeMin", 1000, 0, Integer.MAX_VALUE);
            scouterMute = b.comment("Players can mute someone's scouter talk and pings with /dmzzero scouter mute <player>.")
                    .define("mute", true);
            b.pop();

            b.push("overcharge");
            overcharge = b.comment("With DMZ's Ki Boost skill, ki charges past full during DMZ's ki overcharge (the surge) and fades back after.")
                    .define("enabled", true);
            overchargePerLevel = b.comment("Percent past full that each Ki Boost level adds.")
                    .defineInRange("percentPerLevel", 12.5, 0.0, 100.0);
            overchargeMax = b.comment("Most percent past full, whatever the Ki Boost level.")
                    .defineInRange("maxPercent", 50.0, 0.0, 400.0);
            overchargeFillSeconds = b.comment("Charging in an overcharge fills the whole extra at least this fast. 0 leaves it to DMZ's own charging.")
                    .defineInRange("fillSeconds", 8, 0, 600);
            overchargeFadeSeconds = b.comment("Seconds the whole extra takes to fade back to full after the overcharge. 0 drops it at once.")
                    .defineInRange("fadeSeconds", 10, 0, 600);
            b.pop();
            b.pop();

            b.push("travel");
            b.push("flight");
            wake = b.comment("Fast, low flight leaves a wake of spray, leaves, dust or snow.")
                    .define("wake", true);
            wakeSpeed = b.comment("Flight speed that leaves a wake, in blocks per tick. DMZ's flight tops out near 2.")
                    .defineInRange("wakeSpeed", 0.6, 0.1, 10.0);
            wakeHeight = b.comment("Most blocks above the ground that still leave a wake.")
                    .defineInRange("wakeHeight", 5, 1, 16);
            sonicBoom = b.comment("Flying faster than boomSpeed sets off a sonic boom. With destruction on, it breaks glass nearby.")
                    .define("sonicBoom", true);
            boomSpeed = b.comment("Flight speed that sets off a sonic boom, in blocks per tick.")
                    .defineInRange("boomSpeed", 1.5, 0.2, 20.0);
            takeoffs = b.comment("Flying off the ground fast kicks up a ring of dust. With destruction on, it blows nearby grass away.")
                    .define("takeoffs", true);
            takeoffSpeed = b.comment("Speed, in blocks per tick, that counts as taking off. Only the first few ticks off the ground count.")
                    .defineInRange("takeoffSpeed", 0.5, 0.1, 10.0);
            landings = b.comment("A hard landing from a dive or a long fall kicks up dust with a thud. DMZ Zero players crouch a moment.")
                    .define("landings", true);
            landingSpeed = b.comment("Falling speed, in blocks per tick, that counts as a hard landing. 0.9 is about a 7 block fall.")
                    .defineInRange("landingSpeed", 0.9, 0.2, 10.0);
            throughTrees = b.comment("Fast flight breaks leaves and glass in your path instead of stopping you. Needs destruction on.")
                    .define("throughTrees", true);
            throughSpeed = b.comment("Flight speed, in blocks per tick, that breaks through leaves and glass.")
                    .defineInRange("throughSpeed", 0.6, 0.1, 10.0);
            throughAhead = b.comment("Ticks of flight cleared ahead of a flyer, plus their ping. Raise it if flyers still bump into leaves.")
                    .defineInRange("throughAhead", 6, 0, 10);
            b.pop();

            b.push("running");
            runningDust = b.comment("Running faster than vanilla sprinting kicks up dust, sand, snow or bits of grass behind you. No blocks change.")
                    .define("dust", true);
            runningSpeed = b.comment("Running speed, in blocks per tick, that kicks up dust. Vanilla sprinting is about 0.28, sprint jumping 0.36.")
                    .defineInRange("speed", 0.4, 0.1, 10.0);
            b.pop();

            b.push("lightFeet");
            lightFeet = b.comment("Fighters who can fly don't trample farmland when they jump or land.")
                    .define("enabled", true);
            lightFeetFlyLevel = b.comment("DragonMineZ Fly skill level needed to step lightly.")
                    .defineInRange("flyLevel", 1, 1, 100);
            b.pop();

            b.push("lifts");
            lifts = b.comment("Crouch and right-click a party member with an empty hand, then stand up, to ride on their back.")
                    .define("enabled", true);
            liftReach = b.comment("Most blocks away a party member can be to climb on.")
                    .defineInRange("reach", 4.0, 1.0, 8.0);
            liftFloatSeconds = b.comment("Seconds of slow falling after you let go in the air. 0 turns it off.")
                    .defineInRange("floatSeconds", 5, 0, 30);
            carryAnimals = b.comment("Crouch and right-click an animal with an empty hand to carry it over your head. Pets only by their owner.")
                    .define("carryAnimals", true);
            carryVillagers = b.comment("The same for villagers and wandering traders.")
                    .define("carryVillagers", true);
            carryMaxWidth = b.comment("Widest animal or villager you can pick up, in blocks. A cow is 0.9, a polar bear 1.4.")
                    .defineInRange("carryMaxWidth", 1.5, 0.3, 4.0);
            b.pop();

            b.push("shoulder");
            shoulder = b.comment("Party members crouching right next to someone using DragonMineZ's Instant Transmission go along.")
                    .define("enabled", true);
            shoulderReach = b.comment("Most blocks from the traveller someone can crouch and still go along.")
                    .defineInRange("reach", 1.5, 0.5, 4.0);
            shoulderMost = b.comment("Most people who can go along at once.")
                    .defineInRange("most", 4, 0, 8);
            shoulderKiCost = b.comment("Share of the traveller's max ki each person taken along costs.")
                    .defineInRange("kiCost", 0.05, 0.0, 1.0);
            shoulderPartyOnly = b.comment("Only the traveller's DragonMineZ party can go along.")
                    .define("partyOnly", true);
            b.pop();
            b.pop();

            b.push("versus");
            b.push("duels");
            duels = b.comment("/dmzzero duel <player> starts a one-on-one fight in a ring. Lethal hits knock down instead.")
                    .define("enabled", true);
            duelRadius = b.comment("Ring radius, in blocks.")
                    .defineInRange("radius", 24, 8, 128);
            duelMinutes = b.comment("Minutes before a duel ends in a draw.")
                    .defineInRange("minutes", 5, 1, 60);
            challengeRange = b.comment("Most blocks away you can challenge someone from.")
                    .defineInRange("challengeRange", 64, 8, 1024);
            challengeSeconds = b.comment("Seconds a challenge stays open.")
                    .defineInRange("challengeSeconds", 60, 10, 600);
            ringOutSeconds = b.comment("Seconds outside the ring before you lose by ring-out.")
                    .defineInRange("ringOutSeconds", 5, 1, 60);
            duelOptOut = b.comment("Players can turn duel challenges away with /dmzzero duel off.")
                    .define("optOut", true);
            duelDeclineWait = b.comment("Seconds before someone you declined can challenge you again. 0 turns the wait off.")
                    .defineInRange("declineWaitSeconds", 60, 0, 3600);
            b.pop();

            b.push("battles");
            battles = b.comment("/dmzzero battle open starts a free-for-all on a Null Realm stage. The last one standing wins.")
                    .define("enabled", true);
            stageRadius = b.comment("Stage radius, in blocks.")
                    .defineInRange("stageRadius", 28, 12, 64);
            battleMinutes = b.comment("Minutes before a battle ends in a draw.")
                    .defineInRange("minutes", 10, 1, 60);
            battleJoinSeconds = b.comment("Seconds to join an open battle.")
                    .defineInRange("joinSeconds", 60, 10, 600);
            battleMaxFighters = b.comment("Most fighters in one battle.")
                    .defineInRange("maxFighters", 16, 2, 64);
            battleAnnounceRange = b.comment("Blocks the battle announcement reaches from the opener. 0 is the whole server.")
                    .defineInRange("announceRange", 0, 0, 100000);
            b.pop();

            b.push("fusion");
            fusionMoments = b.comment("Fusing and splitting up throw aura bursts, and a pair's first fusion shows the fused name.")
                    .define("moments", true);
            b.pop();
            b.pop();

            b.push("world");
            b.push("healing");
            healing = b.comment("Blocks broken in ki fights come back after healMinutes, unless something was placed there since.")
                    .define("enabled", true);
            healMinutes = b.comment("Minutes before damaged blocks start coming back.")
                    .defineInRange("delayMinutes", 10, 0, 10080);
            healPerSecond = b.comment("Most blocks restored per second in each dimension.")
                    .defineInRange("blocksPerSecond", 160, 1, 4000);
            healMaxBlocks = b.comment("Most damaged blocks remembered per dimension.")
                    .defineInRange("maxBlocks", 50000, 0, 1000000);
            healKiBlasts = b.comment("Heal craters, cut terrain and laser explosions from DMZ's ki attacks. Those blocks drop nothing.")
                    .define("kiBlasts", true);
            healImpacts = b.comment("Heal DMZ Zero's own damage: glass, plants and roughened ground.")
                    .define("impacts", true);
            healFallen = b.comment("Sand and gravel that fall into a hole waiting to heal go back up when it heals, and drop nothing.")
                    .define("fallenBlocks", true);
            healWish =b.comment("Shenron and Porunga can grant Heal the land: all damage in the wisher's dimension comes back fast.")
                    .define("wish", true);
            healWishPerSecond = b.comment("Blocks restored per second after the wish.")
                    .defineInRange("wishBlocksPerSecond", 2000, 1, 20000);
            healMillisPerTick = b.comment("Most server time healing may use each tick, in ms, shared by every dimension. The rest waits a tick.")
                    .defineInRange("millisPerTick", 1.0, 0.05, 50.0);
            b.pop();

            b.push("moon");
            moonDestroyable = b.comment("Ki attacks fired at the moon through open sky destroy it. No Oozaru by moonlight until it returns.")
                    .define("destroyable", true);
            moonReturnDays = b.comment("In-game days until a destroyed moon returns. One day is 20 minutes.")
                    .defineInRange("returnDays", 3, 1, 100);
            moonRevertsOozaru = b.comment("Destroying the moon reverts Oozaru in that dimension, unless a Fake Moon is near them.")
                    .define("revertsOozaru", true);
            moonOozaruAnyMoon = b.comment("Every moon but the new moon lets a Saiyan go Oozaru. Off: the full moon only, 1 night in 8, as in DragonMineZ.")
                    .define("oozaruAnyMoon", true);
            moonOozaruLookDegrees = b.comment("How far off the moon you can look to start the Oozaru charge. Straight up always counts. 0 leaves only that.")
                    .defineInRange("oozaruLookDegrees", 18.0, 0.0, 45.0);
            b.pop();

            b.push("villages");
            villages = b.comment("Villagers react to DMZ alignment: good fighters pay less, evil ones pay more and are feared.")
                    .define("enabled", true);
            heroDiscount = b.comment("Price cut for good fighters, in percent.")
                    .defineInRange("heroDiscount", 20.0, 0.0, 90.0);
            villainMarkup = b.comment("Price increase for evil fighters, in percent.")
                    .defineInRange("villainMarkup", 40.0, 0.0, 400.0);
            villagersFlee = b.comment("Villagers flee evil fighters and call iron golems when enough of them panic.")
                    .define("villagersFlee", true);
            fleeRange = b.comment("Blocks an evil fighter has to come within before villagers flee.")
                    .defineInRange("fleeRange", 8, 1, 16);
            golemsAttack = b.comment("Iron golems attack evil fighters on sight.")
                    .define("golemsAttack", true);
            b.pop();

            b.push("animals");
            animalFear = b.comment("Animals run from a raised aura far stronger than they are. Horses rear, wolves whine, and your own pets stay.")
                    .define("enabled", true);
            animalFearRadius = b.comment("Blocks around a raised aura that animals run from.")
                    .defineInRange("radius", 12.0, 2.0, 48.0);
            animalFearRatio = b.comment("How many times stronger than an animal you have to be before it runs. DMZ gives a cow about 13, a horse 56.")
                    .defineInRange("ratio", 10.0, 1.0, 1000.0);
            animalFearSpeed = b.comment("How fast they run, as a share of their walking speed.")
                    .defineInRange("runSpeed", 1.6, 0.5, 3.0);
            b.pop();

            b.push("diets");
            namekianDiet = b.comment("Water bottles feed Namekians, and other food fills them less.")
                    .define("namekianDiet", true);
            waterHunger = b.comment("Hunger a water bottle fills for a Namekian.")
                    .defineInRange("waterHunger", 4, 0, 20);
            waterSaturation = b.comment("Saturation modifier of a water bottle for a Namekian. Bread is 0.6.")
                    .defineInRange("waterSaturation", 0.3, 0.0, 5.0);
            namekianFoodFactor = b.comment("Share of the usual hunger and saturation other food gives a Namekian. At least 1 per bite.")
                    .defineInRange("namekianFoodFactor", 0.5, 0.0, 1.0);
            majinDiet = b.comment("Food in the dmzzero:sweets tag fills Majins more.")
                    .define("majinDiet", true);
            majinSweetsFactor = b.comment("Times the usual hunger and saturation sweets give a Majin.")
                    .defineInRange("majinSweetsFactor", 2.0, 1.0, 10.0);
            senzuDays = b.comment("In-game days one senzu bean keeps your hunger bar full. One day is 20 minutes. 0 turns it off.")
                    .defineInRange("senzuDays", 10, 0, 100);
            b.pop();

            b.push("timeChamber");
            chamberDepths = b.comment("Farther from the Time Chamber house the air gets hotter, colder and thinner, and TP rises. No damage.")
                    .define("depths", true);
            chamberOutAt = b.comment("Blocks from the house where depth 1 starts.")
                    .defineInRange("outAt", 64, 8, 100000);
            chamberDeepAt = b.comment("Blocks from the house where depth 2 and thin air start.")
                    .defineInRange("deepAt", 192, 16, 100000);
            chamberFarAt = b.comment("Blocks from the house where depth 3 starts.")
                    .defineInRange("farAt", 384, 32, 100000);
            chamberOutTp = b.comment("TP multiplier at depth 1, on top of every other TP bonus.")
                    .defineInRange("outTp", 1.25, 1.0, 100.0);
            chamberDeepTp = b.comment("TP multiplier at depth 2.")
                    .defineInRange("deepTp", 1.5, 1.0, 100.0);
            chamberFarTp = b.comment("TP multiplier at depth 3.")
                    .defineInRange("farTp", 2.0, 1.0, 100.0);
            chamberCycleMinutes = b.comment("Minutes for one hot and one cold spell.")
                    .defineInRange("cycleMinutes", 6, 1, 120);
            chamberThinAir = b.comment("From depth 2, exertion uses up air and running out leaves you winded. Frost Demons and Majins don't need it.")
                    .define("thinAir", true);
            htcOnTop = b.comment("DMZ's Time Chamber TP bonus multiplies with its other TP bonuses instead of adding to them.")
                    .define("htcOnTop", true);
            chamberLimitDays = b.comment("In-game days each character may spend in the Time Chamber, as in the show. One day is 20 minutes. 0 is no limit.")
                    .defineInRange("limitDays", 2, 0, 1000);
            chamberWish = b.comment("Shenron and Porunga can grant more time in the Time Chamber.")
                    .define("wish", true);
            chamberWishDays = b.comment("In-game days each wish adds.")
                    .defineInRange("wishDays", 2, 1, 1000);
            chamberMastery = b.comment("DMZ's passive form mastery is multiplied by this while you stay transformed in the Time Chamber. 1 is off.")
                    .defineInRange("masteryMultiplier", 3.0, 1.0, 10.0);

            b.push("training");
            training = b.comment("Refight the saga bosses and mobs you've beaten as training copies that drop nothing (key K, /dmzzero training).")
                    .define("enabled", true);
            trainingOnlyInChamber = b.comment("Training copies only come in the Time Chamber.")
                    .define("onlyInChamber", true);
            trainingMaxOpponents = b.comment("Training copies each player can have out at once. Every player training adds this many mobs.")
                    .defineInRange("maxOpponents", 3, 1, 20);
            trainingTpPercent = b.comment("Percent of the usual TP a fight with a training copy pays, after every other bonus.")
                    .defineInRange("tpPercent", 50, 0, 100);
            trainingMasteryPercent = b.comment("Percent of the usual form mastery a fight with a training copy pays, hits dealt and taken.")
                    .defineInRange("masteryPercent", 50, 0, 100);
            b.pop();
            b.pop();

            b.push("saibamen");
            saibamanSeeds = b.comment("DMZ's Saibamen drop seeds. Plant one in farmland or dirt and a Saibaman climbs out.")
                    .define("enabled", true);
            seedChance = b.comment("Chance a wild Saibaman drops a seed when a player kills it.")
                    .defineInRange("dropChance", 0.35, 0.0, 1.0);
            sproutedSeedChance = b.comment("Chance a Saibaman grown from a seed drops one. Above 0 lets a player keep a supply going.")
                    .defineInRange("sproutedDropChance", 0.0, 0.0, 1.0);
            sproutSeconds = b.comment("Seconds from planting until the Saibaman climbs out.")
                    .defineInRange("sproutSeconds", 60, 1, 3600);
            sproutsPerPlayer = b.comment("Most seeds one player can have growing at once.")
                    .defineInRange("maxPerPlayer", 3, 1, 32);
            b.pop();

            b.push("garden");
            gardenList = b.comment("/dmzzero garden lists your senzu bushes, Saibaman seeds and Trees of Might, and where they are.")
                    .define("enabled", true);
            awayNotice = b.comment("On joining, a chat line names what of yours became ready while you were away.")
                    .define("awayNotice", true);
            awayNoticeMax = b.comment("Most things that line names. The rest are only counted.")
                    .defineInRange("awayNoticeMax", 6, 1, 64);
            b.pop();

            b.push("senzuGarden");
            senzuGarden = b.comment("A senzu bean planted in farmland grows a bush that gives senzu beans.")
                    .define("enabled", true);
            senzuRipenDays = b.comment("In-game days a bush takes to ripen, after planting and after each picking. One day is 20 minutes.")
                    .defineInRange("daysToRipen", 3.0, 0.1, 30.0);
            senzuBeans = b.comment("Senzu beans each ripe bush gives when picked.")
                    .defineInRange("beansPerPick", 1, 1, 8);
            senzuBushes = b.comment("Senzu bushes each player can have growing at once.")
                    .defineInRange("bushesPerPlayer", 3, 1, 16);
            korinRange = b.comment("A bush planted within this many blocks of DragonMineZ's Korin ripens faster. 0 turns it off.")
                    .defineInRange("korinRange", 32, 0, 128);
            korinSpeed = b.comment("How many times faster a bush near Korin ripens.")
                    .defineInRange("korinSpeed", 2.0, 1.0, 10.0);
            senzuRipeNotice = b.comment("A chat line tells you when one of your bushes ripens while you're online.")
                    .define("ripeNotice", true);
            b.pop();

            b.push("plowing");
            plowing = b.comment("Crouching and punching grass or dirt with an empty hand plows it into farmland, for a little TP.")
                    .define("enabled", true);
            plowTpShare = b.comment("TP for each plowed block, as a share of DMZ's TP per hit. 0 pays nothing.")
                    .defineInRange("tpShare", 0.5, 0.0, 5.0);
            plowBlocksPerDay = b.comment("Plowed blocks a day that pay TP. Plowing past it still works.")
                    .defineInRange("blocksPerDay", 64, 0, 1000);
            b.pop();

            b.push("waterfall");
            waterfall = b.comment("Standing still under a waterfall pays a little TP every so often, up to a daily count.")
                    .define("enabled", true);
            waterfallHeight = b.comment("Blocks of falling water, counted up from your head, that make a waterfall.")
                    .defineInRange("height", 3, 1, 16);
            waterfallSeconds = b.comment("Seconds of standing still under the falls for each payment.")
                    .defineInRange("seconds", 10, 1, 600);
            waterfallTpShare = b.comment("TP for each payment, as a share of DMZ's TP per hit. 0 pays nothing.")
                    .defineInRange("tpShare", 0.5, 0.0, 5.0);
            waterfallPaysPerDay = b.comment("Payments a day. Staying under the falls past it pays nothing until the next in-game day.")
                    .defineInRange("paysPerDay", 30, 0, 1000);
            b.pop();

            b.push("ajissa");
            ajissaGrowth = b.comment("An Ajissa sapling a Namekian plants grows into a tree within an in-game day.")
                    .define("enabled", true);
            ajissaHours = b.comment("Most in-game hours a sapling takes to grow. It grows somewhere between a quarter of this and all of it.")
                    .defineInRange("maxHours", 24.0, 1.0, 240.0);
            b.pop();

            b.push("treeOfMight");
            mightTree = b.comment("A Might Tree Fruit planted in grass grows a Tree of Might that drains the land and bears fruit.")
                    .define("enabled", true);
            mightGrowDays = b.comment("In-game days the tree takes to grow.")
                    .defineInRange("daysToGrow", 3.0, 0.1, 30.0);
            mightFruitDays = b.comment("In-game days between its fruits. At most 3 hang at once.")
                    .defineInRange("fruitDays", 2.0, 0.1, 30.0);
            mightDrainRadius = b.comment("Blocks around the grown tree whose grass it drains to coarse dirt. 0 drains nothing.")
                    .defineInRange("drainRadius", 8, 0, 12);
            mightDrainSeconds = b.comment("Seconds between drained blocks.")
                    .defineInRange("drainSeconds", 30, 5, 600);
            mightMaxDrained = b.comment("Most grass blocks one tree drains.")
                    .defineInRange("maxDrained", 120, 0, 400);
            mightTrees = b.comment("Trees of Might each player can have at once.")
                    .defineInRange("treesPerPlayer", 1, 1, 8);
            b.pop();

            b.push("punchMachine");
            punchRecords = b.comment("DMZ's Punch Machine remembers each player's best hit and best total over a few seconds.")
                    .define("enabled", true);
            punchWindow = b.comment("Seconds a total runs from your first hit. Changing it starts the totals' records over.")
                    .defineInRange("windowSeconds", 10, 1, 60);
            b.pop();

            b.push("patternWish");
            patternWish = b.comment("Shenron can grant clothing patterns of the wisher's choice, picked from a list in chat.")
                    .define("enabled", true);
            patternPicks = b.comment("Clothing patterns each wish lets you pick.")
                    .defineInRange("picks", 3, 1, 20);
            b.pop();
            b.pop();

            b.push("magic");
            glindMagic = b.comment("Glinds cast spells with DMZ's Action key in Racial mode. Needs DMZ Zero on the player's client.")
                    .define("enabled", true);
            glindTransmission = b.comment("Glinds have DMZ's Instant Transmission at its top level from the start.")
                    .define("instantTransmission", true);

            b.push("telekinesis");
            telekinesis = b.comment("A Glind spell: lift a weaker mob, or pull a dropped item to you. Cast again to throw what you hold.")
                    .define("enabled", true);
            tkRange = b.comment("Most blocks away you can grab something.")
                    .defineInRange("range", 16.0, 2.0, 48.0);
            tkHoldDistance = b.comment("Blocks in front of you that a held mob floats.")
                    .defineInRange("holdDistance", 4.0, 2.0, 10.0);
            tkMaxSeconds = b.comment("Longest you can hold something before it drops.")
                    .defineInRange("maxSeconds", 10, 1, 60);
            tkKiCost = b.comment("Share of your max ki a grab costs. 0.05 is 5%.")
                    .defineInRange("kiCost", 0.05, 0.0, 1.0);
            tkKiPerSecond = b.comment("Share of your max ki each second of holding costs.")
                    .defineInRange("kiPerSecond", 0.02, 0.0, 1.0);
            tkStrengthRatio = b.comment("You can lift things up to this many times your own power. 1 is as strong as you.")
                    .defineInRange("strengthRatio", 1.0, 0.1, 10.0);
            tkPlayers = b.comment("Glinds can also lift weaker players, where PvP is on. A held player breaks free by charging ki.")
                    .define("players", false);
            tkItems = b.comment("Telekinesis pulls dropped items and experience to you.")
                    .define("items", true);
            tkThrowSpeed = b.comment("Blocks a tick a thrown mob flies.")
                    .defineInRange("throwSpeed", 1.8, 0.2, 4.0);
            tkThrowDamage = b.comment("Share of your melee damage a thrown mob takes when it slams into something at full speed.")
                    .defineInRange("throwDamage", 0.5, 0.0, 5.0);
            tkCooldown = b.comment("Seconds after a throw or a drop before you can grab again.")
                    .defineInRange("cooldownSeconds", 2, 0, 60);
            tkBreakFreeSeconds = b.comment("Seconds a held player has to charge ki to break free.")
                    .defineInRange("breakFreeSeconds", 2, 1, 30);
            b.pop();

            b.push("rockLift");
            rockLift = b.comment("A Glind spell: tear a block with open air above it out of the ground, hold it, and throw it.")
                    .define("enabled", true);
            rockHardness = b.comment("Hardest block it can tear out. Stone is 1.5, deepslate 3, obsidian 50.")
                    .defineInRange("maxHardness", 3.0, 0.0, 50.0);
            rockKiCost = b.comment("Share of your max ki tearing a block out costs. Holding it costs what telekinesis does.")
                    .defineInRange("kiCost", 0.06, 0.0, 1.0);
            rockThrowSpeed = b.comment("Blocks a tick a thrown rock flies.")
                    .defineInRange("throwSpeed", 2.0, 0.2, 4.0);
            rockDamage = b.comment("Share of your melee damage a rock deals when it hits at full speed.")
                    .defineInRange("damage", 0.75, 0.0, 5.0);
            rockCooldown = b.comment("Seconds after a throw before you can tear out another.")
                    .defineInRange("cooldownSeconds", 2, 0, 60);
            rockClusterBlocks = b.comment("Most blocks one lift holds. Keep the key down to pull more around the first. 1 is a single rock.")
                    .defineInRange("clusterBlocks", 20, 1, 40);
            rockClusterTicks = b.comment("Ticks between each extra block while you hold the key. 20 ticks is 1 second.")
                    .defineInRange("clusterTicks", 4, 1, 40);
            rockClusterRadius = b.comment("How far from the first block, in blocks, the others can come from.")
                    .defineInRange("clusterRadius", 3, 1, 5);
            rockClusterKiCost = b.comment("Share of your max ki each extra block costs.")
                    .defineInRange("clusterKiCost", 0.015, 0.0, 1.0);
            rockClusterDamage = b.comment("How many times harder a full cluster hits than one rock. Each mob is hit once a throw.")
                    .defineInRange("clusterDamage", 2.5, 1.0, 10.0);
            rockClusterPerTier = b.comment("Extra blocks one lift holds for each tier of the form you're in. 0 keeps every form at clusterBlocks.")
                    .defineInRange("clusterPerTier", 4, 0, 20);
            rockClusterMost = b.comment("Most blocks one lift ever holds, in any form.")
                    .defineInRange("clusterMost", 40, 1, 120);
            b.pop();

            b.push("telekineticWave");
            wave = b.comment("A Glind spell: shove every mob in front of you back. It deals no damage itself.")
                    .define("enabled", true);
            waveRange = b.comment("Blocks the wave reaches.")
                    .defineInRange("range", 8.0, 2.0, 24.0);
            waveAngle = b.comment("Degrees either side of where you look that the wave covers.")
                    .defineInRange("angle", 40.0, 5.0, 90.0);
            waveStrength = b.comment("Blocks a tick something right in front of you is shoved; half that at the edge of the range.")
                    .defineInRange("strength", 1.6, 0.2, 4.0);
            waveKiCost = b.comment("Share of your max ki a wave costs.")
                    .defineInRange("kiCost", 0.08, 0.0, 1.0);
            waveCooldown = b.comment("Seconds between waves.")
                    .defineInRange("cooldownSeconds", 4, 0, 60);
            b.pop();

            b.push("mend");
            mend = b.comment("A Glind spell: stand still and spend ki to repair the armor you're wearing.")
                    .define("enabled", true);
            mendSeconds = b.comment("Longest a mend lasts. It stops early once everything is repaired.")
                    .defineInRange("seconds", 4, 1, 30);
            mendShare = b.comment("Share of each piece's durability repaired every second.")
                    .defineInRange("repairShare", 0.25, 0.01, 1.0);
            mendKiPerSecond = b.comment("Share of your max ki each second of mending costs.")
                    .defineInRange("kiPerSecond", 0.04, 0.0, 1.0);
            mendFriends = b.comment("Mend the armor of the player you're looking at instead of your own, with your ki.")
                    .define("friends", true);
            mendFriendRange = b.comment("Blocks away a player can be to have their armor mended.")
                    .defineInRange("friendRange", 8.0, 1.0, 32.0);
            mendFriendsPartyOnly = b.comment("Only players in your DragonMineZ party can have their armor mended by you.")
                    .define("friendsPartyOnly", true);
            b.pop();
            b.pop();

            b.push("server");
            b.push("messages");
            newsRange = b.comment("Blocks within which others see duel news, first transformations and combined beams. 0 is nobody.")
                    .defineInRange("newsRange", 64, 0, 1024);
            b.pop();

            b.push("status");
            statusPermission = b.comment("Permission level needed for /dmzzero status. 2 is ops, 0 lets every player use it.")
                    .defineInRange("permission", 2, 0, 4);
            statusTiming = b.comment("Time each feature's work every server tick, for /dmzzero status.")
                    .define("timing", true);
            statusSeconds = b.comment("Seconds of server ticks each /dmzzero status report covers.")
                    .defineInRange("seconds", 60, 5, 600);
            b.pop();
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue shake;
        public final ForgeConfigSpec.DoubleValue shakeStrength;
        public final ForgeConfigSpec.BooleanValue flashes;
        public final ForgeConfigSpec.BooleanValue hairFlicker;
        public final ForgeConfigSpec.BooleanValue sky;
        public final ForgeConfigSpec.BooleanValue senseMarker;
        public final ForgeConfigSpec.BooleanValue auraLight;
        public final ForgeConfigSpec.BooleanValue hexagonFormColor;
        public final ForgeConfigSpec.BooleanValue auraTint;
        public final ForgeConfigSpec.BooleanValue pingLabels;
        public final ForgeConfigSpec.BooleanValue chamberReadout;
        public final ForgeConfigSpec.BooleanValue chargePoseLog;
        public final ForgeConfigSpec.BooleanValue cloudHoles;
        public final ForgeConfigSpec.BooleanValue callouts;
        public final ForgeConfigSpec.BooleanValue ownCallouts;
        public final ForgeConfigSpec.BooleanValue kiLight;
        public final ForgeConfigSpec.BooleanValue beamWake;
        public final ForgeConfigSpec.BooleanValue kiTrails;
        public final ForgeConfigSpec.BooleanValue watchMarkers;
        public final ForgeConfigSpec.BooleanValue chant;
        public final ForgeConfigSpec.IntValue chantTicks;
        public final ForgeConfigSpec.BooleanValue stackForms;
        public final ForgeConfigSpec.BooleanValue gather;
        public final ForgeConfigSpec.DoubleValue gatherAt;
        public final ForgeConfigSpec.BooleanValue landingCrouch;

        Client(ForgeConfigSpec.Builder b) {
            b.push("fighting");
            b.push("impacts");
            shake = b.comment("Shake the camera when someone nearby transforms into a heavy form.").define("screenShake", true);
            shakeStrength = b.comment("Camera shake strength.").defineInRange("screenShakeStrength", 1.0, 0.0, 3.0);
            flashes = b.comment("Flash the screen when someone nearby reaches a bright or dark form.").define("screenFlashes", true);
            hairFlicker = b.comment("Hair and eyes flicker between colors while transforming, instead of fading.").define("hairFlicker", true);
            sky = b.comment("Clouds and storms gather around the strongest transformations. Your screen only.")
                    .define("skyReactions", true);
            landingCrouch = b.comment("Landing hard puts you in a crouch for a moment. Players around you see it too.")
                    .define("landingCrouch", true);
            b.pop();

            b.push("callouts");
            callouts = b.comment("Show a technique's name over whoever fires it.").define("enabled", true);
            ownCallouts = b.comment("Show your own callouts in third person.").define("own", true);
            chant = b.comment("While a technique charges, its name builds up: Ka... me... ha... me... and HA! on release.")
                    .define("chant", true);
            chantTicks = b.comment("Ticks between chanted parts. 20 ticks is 1 second.")
                    .defineInRange("chantTicks", 8, 1, 40);
            stackForms = b.comment("A stacked form like Kaioken leads the callout: Kaioken x3 Kamehameha!")
                    .define("stackForms", true);
            b.pop();
            b.pop();

            b.push("beams");
            b.push("effects");
            cloudHoles = b.comment("A beam through the clouds leaves a hole that slowly closes. Not with Fabulous graphics or shaders.")
                    .define("cloudHoles", true);
            beamWake = b.comment("Low beams stir up water, plants and dust along their path. Your screen only.")
                    .define("wake", true);
            b.pop();
            b.pop();

            b.push("ki");
            b.push("light");
            auraLight = b.comment("Auras light up the area around them. Your screen only.")
                    .define("auraLight", true);
            kiLight = b.comment("Ki attacks light up the area as they fly. Your screen only.")
                    .define("kiLight", true);
            b.pop();

            b.push("gathering");
            gather = b.comment("Overcharging a technique pulls DragonMineZ's ki specks and dust in toward the hands. Your screen only.")
                    .define("enabled", true);
            gatherAt = b.comment("Charge, in percent, where gathering starts. Overcharging starts past 100.")
                    .defineInRange("startAt", 100.0, 0.0, 175.0);
            b.pop();

            b.push("share");
            auraTint = b.comment("A player receiving ki has their aura tinted by the donors' colors.").define("auraTint", true);
            b.pop();

            b.push("powerSense");
            senseMarker = b.comment("A marker at the screen edge points to a much stronger power you feel.").define("marker", true);
            kiTrails = b.comment("With DMZ's ki sense on, fighters leave a trail where they flew in the last 5 seconds.")
                    .define("kiTrails", true);
            b.pop();

            b.push("kamiWatch");
            watchMarkers = b.comment("During Kami's watch, markers at the screen edge show each power and its distance.")
                    .define("markers", true);
            b.pop();

            b.push("scouters");
            pingLabels = b.comment("Show scouter pings and power spikes as labels, with an arrow when off screen.")
                    .define("pingLabels", true);
            b.pop();
            b.pop();

            b.push("world");
            b.push("timeChamber");
            chamberReadout = b.comment("Show the Time Chamber depth's TP multiplier at the top of the screen.")
                    .define("readout", true);
            b.pop();
            b.pop();

            b.push("menus");
            hexagonFormColor = b.comment("Fill the hexagon stats view with your current form's aura color.")
                    .define("hexagonFormColor", true);
            b.pop();

            b.push("debug");
            chargePoseLog = b.comment("Log why your charge pose dropped while you were still charging, at most once a minute.")
                    .define("chargePoseLog", false);
            b.pop();
        }
    }
}
