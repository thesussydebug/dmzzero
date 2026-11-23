package com.dmzzero.swat;

// arrows glance off a fighter far stronger than whoever shot them
public final class ArrowMath {

    private ArrowMath() {
    }

    /** An arrow nobody shot (a dispenser's) bounces off anyone at minPower; a shooter's needs you ratio times stronger too. */
    public static boolean bounces(double targetPower, double shooterPower, boolean shooterRated, double ratio, double minPower) {
        if (!Double.isFinite(targetPower) || targetPower < minPower) return false;
        if (!shooterRated) return true;
        if (!Double.isFinite(shooterPower) || shooterPower < 0) return false;
        return targetPower >= shooterPower * ratio;
    }

    /** Roughly what an arrow would deal: vanilla multiplies its damage by its speed. */
    public static double hit(double damage, double speed) {
        if (!Double.isFinite(damage) || !Double.isFinite(speed) || damage < 0 || speed < 0) return 0;
        return damage * speed;
    }
}
