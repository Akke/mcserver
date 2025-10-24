package com.akkelw.professions;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class Storage {
    private final File dataFile;
    private FileConfiguration cfg;

    public Storage(File dataFolder) {
        dataFolder.mkdirs();
        this.dataFile = new File(dataFolder, "data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        this.cfg = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save(UUID id, ProfData data) {
        String base = id.toString();
        for (Profession p : Profession.values()) {
            cfg.set(base + ".xp." + p.name(), data.get(p).getXp());
        }
        cfg.set(base + ".advanced", data.getChosenAdvanced() == null ? null : data.getChosenAdvanced().name());
        try { cfg.save(dataFile); } catch (IOException ignored) {}
    }

    public ProfData load(UUID id) {
        ProfData pd = new ProfData();
        String base = id.toString();

        for (Profession p : Profession.values()) {
            long xp = cfg.getLong(base + ".xp." + p.name(), 0L);
            pd.get(p).setXp(xp);
        }
        String adv = cfg.getString(base + ".advanced", null);
        if (adv != null) {
            try { pd.chooseAdvanced(Profession.valueOf(adv)); } catch (Exception ignored) {}
        }
        return pd;
    }
}
