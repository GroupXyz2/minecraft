package de.groupxyz.groupsskywars;

import org.bukkit.entity.Player;

public abstract class KitAbility {
    private final String name;
    private final String description;
    private final int cooldownSeconds;

    public KitAbility(String name, String description, int cooldownSeconds) {
        this.name = name;
        this.description = description;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public abstract void register(Player player);
    public abstract void unregister(Player player);
    public abstract void onActivate(Player player);
}

