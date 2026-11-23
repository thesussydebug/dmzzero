package com.dmzzero.potential;

public final class ClientPotential {
    private static volatile int potential;

    private ClientPotential() {
    }

    public static int get() {
        return potential;
    }

    public static void set(int value) {
        potential = Potential.isValid(value) ? value : 0;
    }
}
