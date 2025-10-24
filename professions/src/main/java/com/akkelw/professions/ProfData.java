package com.akkelw.professions;

import java.util.EnumMap;
import java.util.Map;

public class ProfData {
    private final Map<Profession, SkillData> skills = new EnumMap<>(Profession.class);
    private Profession chosenAdvanced = null; // DARK_ARTS or TRANSFIGURATION

    public ProfData() {
        for (Profession p : Profession.values()) skills.put(p, new SkillData());
    }

    public SkillData get(Profession p) { return skills.get(p); }

    public Profession getChosenAdvanced() { return chosenAdvanced; }
    public boolean chooseAdvanced(Profession p) {
        if (p == null || !p.isAdvancedCombat()) return false;
        if (chosenAdvanced != null && !chosenAdvanced.equals(p)) return false; // locked
        chosenAdvanced = p;
        return true;
    }

    public Map<Profession, SkillData> all() { return skills; }
}
