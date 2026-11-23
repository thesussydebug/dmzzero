package com.dmzzero.chamber;

// how the time chamber gets harsher the farther you walk from the house
public final class ChamberMath {
    public static final int HOUSE = 0;
    public static final int OUT = 1;
    public static final int DEEP = 2;
    public static final int FAR = 3;
    // what a player hears on the action bar when a step takes them deeper
    public static final int SILENT = 0;
    public static final int HEAVY = 1;
    public static final int HEAVIER = 2;
    public static final double COMFORT = 20;
    public static final double HOT_SWING = 30;
    public static final double COLD_SWING = 60;
    public static final double HOT_AT = 35;
    public static final double HOT_MAX = 50;
    public static final double COLDEST = -40;
    public static final int FULL_FREEZE = 140;
    public static final int FROST_CAP = 130;
    public static final int MAX_AIR = 300;
    public static final int WINDED_UNTIL = 90;
    private static final double[] SWING = {0, 0.35, 0.7, 1.0};
    private static final double SLOWEST_STAMINA = 0.4;
    private static final float MOST_EXHAUSTION = 0.4f;

    private ChamberMath() {
    }

    public record Box(double minX, double minZ, double maxX, double maxZ) {
        public double distance(double x, double z) {
            double dx = Math.max(0, Math.max(minX - x, x - maxX));
            double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    public static int zone(double distance, int outAt, int deepAt, int farAt) {
        if (distance >= farAt) return FAR;
        if (distance >= deepAt) return DEEP;
        if (distance >= outAt) return OUT;
        return HOUSE;
    }

    // one hot spell and one cold spell per cycle, wider the deeper you go; an aura takes half of it
    public static double temperature(long time, int cycleTicks, int zone, boolean aura) {
        double s = Math.sin(2 * Math.PI * Math.floorMod(time, cycleTicks) / cycleTicks);
        double swing = SWING[clampZone(zone)] * (aura ? 0.5 : 1.0);
        return COMFORT + swing * (s >= 0 ? HOT_SWING * s : COLD_SWING * s);
    }

    public static double staminaFactor(double temp) {
        if (temp <= HOT_AT) return 1.0;
        return Math.max(SLOWEST_STAMINA, 1.0 - (1.0 - SLOWEST_STAMINA) * (temp - HOT_AT) / (HOT_MAX - HOT_AT));
    }

    // hunger per second on top of what you'd use anyway
    public static float exhaustion(double temp) {
        if (temp <= HOT_AT) return 0f;
        return (float) Math.min(MOST_EXHAUSTION, MOST_EXHAUSTION * (temp - HOT_AT) / (HOT_MAX - HOT_AT));
    }

    // vanilla's frozen ticks, held short of fully frozen so it never hurts
    public static int frostTarget(double temp) {
        if (temp >= 0) return 0;
        return (int) Math.min(FROST_CAP, Math.round(FROST_CAP * -temp / -COLDEST));
    }

    // vanilla thaws 2 a tick before this runs, so +3 is a net +1
    public static int frostStep(int frozen, int target) {
        return frozen < target ? Math.min(target, frozen + 3) : frozen;
    }

    public static boolean thinAir(int zone) {
        return zone >= DEEP;
    }

    public static int airStep(int air, int zone, boolean exerting, boolean breathless, long tick) {
        if (breathless || !thinAir(zone)) return Math.min(MAX_AIR, air + 4);
        if (exerting) {
            int drain = zone >= FAR ? 1 : (tick % 2 == 0 ? 1 : 0);
            return Math.max(0, air - drain);
        }
        return Math.min(MAX_AIR, air + (zone >= FAR ? 1 : 2));
    }

    // run out and you're winded until you've got a good part of your breath back
    public static boolean winded(boolean wasWinded, int air) {
        return wasWinded ? air < WINDED_UNTIL : air <= 0;
    }

    // frost demons and majins get by in space, so thin air is nothing to them
    public static boolean breathless(String race) {
        return "frostdemon".equals(race) || "majin".equals(race);
    }

    public static double tp(int zone, double out, double deep, double far) {
        return switch (clampZone(zone)) {
            case OUT -> out;
            case DEEP -> deep;
            case FAR -> far;
            default -> 1.0;
        };
    }

    // the first step out of the house gets heavy, every step deeper after it heavier; going back says nothing
    public static int descentLine(int from, int to) {
        if (to <= HOUSE || to <= from) return SILENT;
        return from <= HOUSE ? HEAVY : HEAVIER;
    }

    // dmz adds its time chamber bonus into its sum of tp bonuses; this multiplies it over the rest instead
    public static double htcOnTop(double withoutHtc, double htc) {
        return Math.max(0, withoutHtc) * Math.max(0, htc);
    }

    // goku and gohan stayed transformed all day in the chamber: dmz's passive form mastery, faster in here
    public static double passiveMastery(double amount, boolean inChamber, double multiplier) {
        if (!inChamber || !Double.isFinite(multiplier) || multiplier <= 1 || !(amount > 0)) return amount;
        return amount * multiplier;
    }

    private static int clampZone(int zone) {
        return Math.max(HOUSE, Math.min(FAR, zone));
    }
}
