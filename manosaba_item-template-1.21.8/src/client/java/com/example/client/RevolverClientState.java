package com.example.client;

public final class RevolverClientState {
    private static boolean reloading;
    private static int ticks;
    private static boolean magazine;

    private RevolverClientState() {}

    public static void update(boolean active, int remainingTicks, boolean isMagazine) {
        reloading = active;
        ticks = Math.max(0, remainingTicks);
        magazine = isMagazine;
    }

    public static boolean isReloading() { return reloading; }
    public static int ticks() { return ticks; }
    public static boolean isMagazine() { return magazine; }
}
