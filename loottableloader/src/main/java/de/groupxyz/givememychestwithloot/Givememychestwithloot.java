package de.groupxyz.givememychestwithloot;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class Givememychestwithloot extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<Player, Boolean> awaitingLootTableKey = new HashMap<>();

    @Override
    public void onEnable() {
        getCommand("loottablechest").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("LootTableChestPlugin aktiviert!");
    }

    @Override
    public void onDisable() {
        getLogger().info("LootTableChestPlugin deaktiviert!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl ausführen.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("gmmcwl.use")) {
            player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung, diesen Befehl zu verwenden.");
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Bitte gib jetzt den Loot-Table-Key im Chat ein.");
        awaitingLootTableKey.put(player, true);
        return true;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!awaitingLootTableKey.containsKey(player) || !awaitingLootTableKey.get(player)) {
            return;
        }

        event.setCancelled(true);

        String lootTableKey = event.getMessage().trim().toUpperCase();

        LootTable lootTable;
        try {
            NamespacedKey key = new NamespacedKey("minecraft", lootTableKey.toLowerCase());
            lootTable = Bukkit.getLootTable(key);

            //lootTable = LootTables.valueOf(lootTableKey);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Ungueltiger Loot-Table-Key. Bitte versuche es erneut.");
            awaitingLootTableKey.remove(player);
            return;
        }

        ItemStack chestItem = new ItemStack(Material.CHEST);
        BlockStateMeta meta = (BlockStateMeta) chestItem.getItemMeta();

        if (meta != null) {
            Chest chest = (Chest) meta.getBlockState();
            chest.setLootTable(lootTable);
            meta.setBlockState(chest);
            chestItem.setItemMeta(meta);

            player.getInventory().addItem(chestItem);
            player.sendMessage(ChatColor.GREEN + "Du hast eine Truhe mit dem Loot-Table " + lootTableKey + " erhalten!");
        }

        awaitingLootTableKey.remove(player);
    }
}

