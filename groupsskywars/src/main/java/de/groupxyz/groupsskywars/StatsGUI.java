package de.groupxyz.groupsskywars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

public class StatsGUI {
    private final StatsManager statsManager;

    public StatsGUI(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    public void openStatsGUI(Player player, Player target) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "📊 Statistiken: " + target.getName());
        StatsManager.PlayerStats stats = statsManager.getStats(target.getUniqueId());

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(target);
            skullMeta.setDisplayName(ChatColor.GOLD + "§l" + target.getName());
            List<String> skullLore = new ArrayList<>();
            skullLore.add(ChatColor.GRAY + "════════════════════");
            skullLore.add(ChatColor.YELLOW + "📈 Gesamtpunktzahl: " + ChatColor.WHITE + stats.getScore());
            skullLore.add(ChatColor.YELLOW + "⏱ Spielzeit: " + ChatColor.WHITE + stats.getPlayTimeFormatted());
            skullLore.add(ChatColor.GRAY + "════════════════════");
            skullMeta.setLore(skullLore);
            skull.setItemMeta(skullMeta);
        }
        inv.setItem(4, skull);

        ItemStack winsItem = createStatItem(
            Material.GOLDEN_APPLE,
            ChatColor.GREEN + "🏆 Siege",
            ChatColor.WHITE + "Siege: " + ChatColor.GOLD + stats.wins,
            ChatColor.WHITE + "Teilnahmen: " + ChatColor.GRAY + stats.participations,
            ChatColor.WHITE + "Winrate: " + ChatColor.AQUA + String.format("%.1f%%", stats.getWinRate()),
            "",
            ChatColor.GRAY + "Deine Gesamtanzahl an Siegen!"
        );
        inv.setItem(10, winsItem);

        ItemStack killsItem = createStatItem(
            Material.DIAMOND_SWORD,
            ChatColor.RED + "⚔ Kampfstatistiken",
            ChatColor.WHITE + "Kills: " + ChatColor.RED + stats.kills,
            ChatColor.WHITE + "Deaths: " + ChatColor.DARK_RED + stats.deaths,
            ChatColor.WHITE + "K/D: " + ChatColor.GOLD + String.format("%.2f", stats.getKDRatio()),
            ChatColor.WHITE + "Assists: " + ChatColor.YELLOW + stats.assists,
            "",
            ChatColor.GRAY + "Deine Kampfleistung!"
        );
        inv.setItem(12, killsItem);

        ItemStack damageItem = createStatItem(
            Material.IRON_SWORD,
            ChatColor.DARK_RED + "💥 Schadenstatistiken",
            ChatColor.WHITE + "Schaden verursacht: " + ChatColor.RED + stats.damageDealt,
            ChatColor.WHITE + "Schaden erhalten: " + ChatColor.DARK_RED + stats.damageTaken,
            "",
            ChatColor.GRAY + "Dein verursachter und erhaltener Schaden!"
        );
        inv.setItem(14, damageItem);

        ItemStack bowItem = createStatItem(
            Material.BOW,
            ChatColor.AQUA + "🏹 Bogenstatistiken",
            ChatColor.WHITE + "Pfeile geschossen: " + ChatColor.YELLOW + stats.arrowsShot,
            ChatColor.WHITE + "Pfeile getroffen: " + ChatColor.GREEN + stats.arrowsHit,
            ChatColor.WHITE + "Genauigkeit: " + ChatColor.GOLD + String.format("%.1f%%", stats.getArrowAccuracy()),
            "",
            ChatColor.GRAY + "Deine Präzision mit dem Bogen!"
        );
        inv.setItem(16, bowItem);

        ItemStack buildItem = createStatItem(
            Material.BRICKS,
            ChatColor.GOLD + "🏗 Baustatistiken",
            ChatColor.WHITE + "Blöcke platziert: " + ChatColor.GREEN + stats.blocksPlaced,
            ChatColor.WHITE + "Blöcke abgebaut: " + ChatColor.RED + stats.blocksBroken,
            "",
            ChatColor.GRAY + "Deine Baufähigkeiten!"
        );
        inv.setItem(28, buildItem);

        ItemStack chestItem = createStatItem(
            Material.CHEST,
            ChatColor.YELLOW + "📦 Erkundung",
            ChatColor.WHITE + "Kisten geöffnet: " + ChatColor.GOLD + stats.chestsOpened,
            "",
            ChatColor.GRAY + "Wie viele Kisten du geöffnet hast!"
        );
        inv.setItem(30, chestItem);

        ItemStack gamesItem = createStatItem(
            Material.CLOCK,
            ChatColor.LIGHT_PURPLE + "🎮 Spielstatistiken",
            ChatColor.WHITE + "Spiele gespielt: " + ChatColor.AQUA + stats.participations,
            ChatColor.WHITE + "Siege: " + ChatColor.GREEN + stats.wins,
            ChatColor.WHITE + "Niederlagen: " + ChatColor.RED + (stats.participations - stats.wins),
            ChatColor.WHITE + "Spielzeit: " + ChatColor.YELLOW + stats.getPlayTimeFormatted(),
            "",
            ChatColor.GRAY + "Deine gesamte Spielaktivität!"
        );
        inv.setItem(32, gamesItem);

        ItemStack rankItem = createStatItem(
            Material.EMERALD,
            ChatColor.GREEN + "⭐ Rang & Punkte",
            ChatColor.WHITE + "Gesamtpunktzahl: " + ChatColor.GOLD + stats.getScore(),
            "",
            ChatColor.GRAY + "Berechnung:",
            ChatColor.GRAY + "• Sieg: " + ChatColor.GREEN + "+10 Punkte",
            ChatColor.GRAY + "• Kill: " + ChatColor.YELLOW + "+3 Punkte",
            ChatColor.GRAY + "• Assist: " + ChatColor.AQUA + "+1 Punkt",
            ChatColor.GRAY + "• Tod: " + ChatColor.RED + "-2 Punkte"
        );
        inv.setItem(34, rankItem);

        ItemStack closeItem = createStatItem(
            Material.BARRIER,
            ChatColor.RED + "❌ Schließen",
            ChatColor.GRAY + "Klicke zum Schließen"
        );
        inv.setItem(49, closeItem);

        ItemStack glassPane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glassPane.setItemMeta(glassMeta);
        }

        int[] frameSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53};
        for (int slot : frameSlots) {
            inv.setItem(slot, glassPane);
        }

        player.openInventory(inv);
    }

    private ItemStack createStatItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> loreList = new ArrayList<>();
            loreList.add("");
            for (String line : lore) {
                loreList.add(line);
            }
            loreList.add("");
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openLeaderboardGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.GOLD + "🏆 Globale Rangliste");

        Map<UUID, StatsManager.PlayerStats> allStats = statsManager.getAllStats();

        List<Map.Entry<UUID, StatsManager.PlayerStats>> sortedStats = allStats.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().getScore(), a.getValue().getScore()))
            .limit(10)
            .collect(Collectors.toList());

        ItemStack infoItem = createStatItem(
            Material.BOOK,
            ChatColor.GOLD + "📊 Rangliste",
            ChatColor.GRAY + "Top 10 Spieler nach Gesamtpunktzahl",
            "",
            ChatColor.YELLOW + "Berechnung:",
            ChatColor.GRAY + "• Sieg: " + ChatColor.GREEN + "+10 Punkte",
            ChatColor.GRAY + "• Kill: " + ChatColor.YELLOW + "+3 Punkte",
            ChatColor.GRAY + "• Assist: " + ChatColor.AQUA + "+1 Punkt",
            ChatColor.GRAY + "• Tod: " + ChatColor.RED + "-2 Punkte"
        );
        inv.setItem(4, infoItem);

        int[] slotPositions = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

        for (int i = 0; i < Math.min(sortedStats.size(), 10); i++) {
            Map.Entry<UUID, StatsManager.PlayerStats> entry = sortedStats.get(i);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            StatsManager.PlayerStats stats = entry.getValue();

            String rank = getRankPrefix(i + 1);
            Material skullMaterial = Material.PLAYER_HEAD;

            ItemStack skull = new ItemStack(skullMaterial);
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(offlinePlayer);
                skullMeta.setDisplayName(rank + " " + ChatColor.WHITE + offlinePlayer.getName());
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.YELLOW + "⭐ Punkte: " + ChatColor.GOLD + stats.getScore());
                lore.add(ChatColor.GREEN + "🏆 Siege: " + ChatColor.WHITE + stats.wins);
                lore.add(ChatColor.RED + "⚔ Kills: " + ChatColor.WHITE + stats.kills);
                lore.add(ChatColor.AQUA + "💀 K/D: " + ChatColor.WHITE + String.format("%.2f", stats.getKDRatio()));
                lore.add(ChatColor.LIGHT_PURPLE + "🎮 Spiele: " + ChatColor.WHITE + stats.participations);
                lore.add(ChatColor.YELLOW + "⏱ Spielzeit: " + ChatColor.WHITE + stats.getPlayTimeFormatted());
                lore.add("");
                skullMeta.setLore(lore);
                skull.setItemMeta(skullMeta);
            }
            inv.setItem(slotPositions[i], skull);
        }

        ItemStack closeItem = createStatItem(
            Material.BARRIER,
            ChatColor.RED + "❌ Schließen",
            ChatColor.GRAY + "Klicke zum Schließen"
        );
        inv.setItem(49, closeItem);

        ItemStack glassPane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glassPane.setItemMeta(glassMeta);
        }

        int[] frameSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 50, 51, 52, 53};
        for (int slot : frameSlots) {
            inv.setItem(slot, glassPane);
        }

        player.openInventory(inv);
    }

    private String getRankPrefix(int rank) {
        return switch (rank) {
            case 1 -> ChatColor.GOLD + "🥇";
            case 2 -> ChatColor.GRAY + "🥈";
            case 3 -> ChatColor.RED + "🥉";
            default -> ChatColor.YELLOW + "#" + rank;
        };
    }
}

