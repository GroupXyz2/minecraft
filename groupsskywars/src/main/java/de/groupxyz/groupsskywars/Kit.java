package de.groupxyz.groupsskywars;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Kit {
    private final String name;
    private final String description;
    private final Material iconMaterial;
    private final List<ItemStack> items;
    private final List<PotionEffect> effects;
    private final KitAbility ability;
    private final int requiredLevel;

    public Kit(String name, String description, Material iconMaterial, List<ItemStack> items,
               List<PotionEffect> effects, KitAbility ability, int requiredLevel) {
        this.name = name;
        this.description = description;
        this.iconMaterial = iconMaterial;
        this.items = items;
        this.effects = effects;
        this.ability = ability;
        this.requiredLevel = requiredLevel;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public void applyKit(Player player) {
        for (ItemStack item : items) {
            player.getInventory().addItem(item.clone());
        }

        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }

        if (ability != null) {
            ability.register(player);
        }
    }

    public KitAbility getAbility() {
        return ability;
    }

    public ItemStack getDisplayItem() {
        ItemStack display = new ItemStack(iconMaterial);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + description);
            lore.add("");
            if (ability != null) {
                lore.add("§e⚡ Fähigkeit: §f" + ability.getDescription());
                lore.add("");
            }
            lore.add("§7Level benötigt: §e" + requiredLevel);
            lore.add("");
            lore.add("§aKlicke zum Auswählen!");
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    public static ItemStack createItem(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, amount);
        if (name != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    public static ItemStack createEnchantedItem(Material material, int amount, String name, Enchantment enchantment, int level) {
        ItemStack item = createItem(material, amount, name);
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }
}

