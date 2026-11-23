package com.dmzzero.magic;

/** Pure maths for mend: how much a pulse repairs, and when moving breaks the spell. */
public final class MendMath {

    private MendMath() {
    }

    /** The damage left after one pulse repairs share of the piece's durability, always at least 1. */
    public static int repaired(int damage, int maxDamage, double share) {
        if (maxDamage <= 0 || damage <= 0) return Math.max(0, damage);
        if (!Double.isFinite(share) || share <= 0) return damage;
        int amount = (int) Math.max(1, Math.ceil(maxDamage * share));
        return Math.max(0, damage - amount);
    }

    public enum Target { FRIEND, FRIEND_FINE, SELF, NOTHING }

    /** Whose armor a cast mends: the party member you look at, else your own. */
    public static Target target(boolean friendInSight, boolean friendDamaged, boolean ownDamaged) {
        if (friendInSight) return friendDamaged ? Target.FRIEND : Target.FRIEND_FINE;
        return ownDamaged ? Target.SELF : Target.NOTHING;
    }

    public static boolean inReach(double distance, double range, double slack) {
        return Double.isFinite(distance) && distance <= range + slack;
    }

    public static boolean moved(double distance, double allowed) {
        return !Double.isFinite(distance) || distance > allowed;
    }
}
