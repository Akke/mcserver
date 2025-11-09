package com.akkelw.professions;

import org.bukkit.Material;

public enum Profession {
    // Combat – Basics
    // endurance - hit points (hp), armor (overall damage reduction)
    // magic - max mana for magic plugin (make the server run a command to increase max mana, instead of using magic API?)
    // broomriding - speed of broom, type of broom you can use
    // gear requirement should be based on total skill levels, total level of all professions combined
    // using potions should have a level requirement too, same as above ^
    // spells & charms and defensive arts and dark arts etc the magic plugin should require their levels to be cast
    SPELLS_CHARMS("Spells & Charms", Category.COMBAT, false, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
    DEFENSIVE_ARTS("Defensive Arts", Category.COMBAT, false, Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
    // Combat – Advanced (choose one)
    DARK_ARTS("Dark Arts", Category.COMBAT, true, Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE),
    TRANSFIGURATION("Transfiguration", Category.COMBAT, true, Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),

    // Gathering
    HERBOLOGY("Herbology", Category.GATHERING, false, Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE),
    MAGICAL_CREATURE_CARE("Care for Magical Creatures", Category.GATHERING, false, Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE),

    // Crafting
    TAILORING("Tailoring", Category.CRAFTING, false, Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE),
    POTION_MAKING("Potion Making", Category.CRAFTING, false, Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE),
    COOKING("Cooking", Category.CRAFTING, false, Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE),
    WANDCRAFTING("Wandcrafting", Category.CRAFTING, false, Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE),
    BROOMCRAFTING("Broomcrafting", Category.CRAFTING, false, Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE),
    RUNECRAFTING("Runecrafting", Category.CRAFTING, false, Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE);

    public enum Category { COMBAT, GATHERING, CRAFTING }

    private final String display;
    private final Category category;
    private final boolean advancedCombat;
    private final Material icon;

    Profession(String display, Category category, boolean advancedCombat, Material icon) {
        this.display = display;
        this.category = category;
        this.advancedCombat = advancedCombat;
        this.icon = icon;
    }

    public String display() { return display; }
    public Category category() { return category; }
    public boolean isAdvancedCombat() { return advancedCombat; }
    public Material icon() { return icon; }

    // For command parsing
    public static Profession byName(String s) {
        String n = s.replace("&", "and").replace("+","and").replace(" ", "_")
                .replace("-", "_").toUpperCase();
        for (Profession p : values()) {
            if (p.name().equalsIgnoreCase(n)) return p;
            if (p.display.equalsIgnoreCase(s)) return p;
        }
        return null;
    }
}
