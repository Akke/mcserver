package com.akkelw.professions;

public class SkillData {
    private long xp; // total XP (0..13034431)
    public SkillData() { this.xp = 0L; }
    public long getXp() { return xp; }
    public void setXp(long xp) { this.xp = Math.max(0, xp); }

    public int getLevel() { return XpTable.levelFor(xp); }
    public long xpForNext() {
        int lvl = getLevel();
        if (lvl >= 99) return XpTable.XP_99;
        return XpTable.xpForLevel(lvl + 1);
    }
    public long xpIntoLevel() {
        int lvl = getLevel();
        return xp - XpTable.xpForLevel(lvl);
    }
    public long xpNeededThisLevel() {
        int lvl = getLevel();
        if (lvl >= 99) return 0;
        return XpTable.xpForLevel(lvl + 1) - XpTable.xpForLevel(lvl);
    }
    public double progressPct() {
        long need = xpNeededThisLevel();
        if (need <= 0) return 100.0;
        return 100.0 * xpIntoLevel() / (double) need;
    }
}
