package com.akkelw.professions;

import com.akkelw.professions.api.ProfessionsAPI;
import java.util.UUID;

public class ProfessionsApiImpl implements ProfessionsAPI {
    private final ProfPlugin plugin;

    public ProfessionsApiImpl(ProfPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getPotionMakingLevel(UUID playerId) {
        ProfData data = plugin.dataOf(playerId);
        SkillData sd = data.get(Profession.POTION_MAKING);

        Integer level = sd.getLevel();
        int lvl = (level != null) ? level : 0;

        return lvl;
    }
}
