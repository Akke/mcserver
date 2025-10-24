package com.akkelw.professions;

// RuneScape-like XP table (classic formula).
public final class XpTable {
    public static final long XP_99 = 13034431L;
    private static final long[] LEVEL_TO_XP = new long[100]; // index = level

    static {
        long points = 0;
        for (int lvl = 1; lvl <= 99; lvl++) {
            points += Math.floor(lvl + 300.0 * Math.pow(2.0, lvl / 7.0));
            LEVEL_TO_XP[lvl] = (long) Math.floor(points / 4.0);
        }
        LEVEL_TO_XP[0] = 0L;
        LEVEL_TO_XP[1] = 0L; // Level 1 starts at 0 xp
        LEVEL_TO_XP[99] = XP_99;
    }

    public static long xpForLevel(int level) {
        if (level <= 1) return 0;
        if (level >= 99) return XP_99;
        return LEVEL_TO_XP[level];
    }

    public static int levelFor(long xp) {
        if (xp <= 0) return 1;
        if (xp >= XP_99) return 99;
        int lo = 1, hi = 99;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (xpForLevel(mid) <= xp) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }
}
