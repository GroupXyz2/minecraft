package de.groupxyz.itemshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Itemshop extends JavaPlugin implements Listener {
    private Economy economy;
    private boolean useSimpleEcoAPI;

    private Map<Integer, ItemStack> adminItems;
    private Map<Integer, Double> adminPrices;
    private Map<Player, Boolean> openBuyInventory = new HashMap<>();
    private Map<Player, Integer> playerCurrentPage = new HashMap<>();
    private Map<String, List<String>> pendingPurchases = new HashMap<>();
    private File playerShopFile;
    private FileConfiguration playerShopConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        useSimpleEcoAPI = getConfig().getBoolean("useSimpleEcoAPI");

        if (!setupEconomy()) {
            getLogger().severe("Vault oder SimpleEcoAPI nicht gefunden! Deaktiviere Plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        loadAdminShopItems();
        loadPlayerShopData();

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("CustomItemShop erfolgreich aktiviert.");
    }

    private boolean setupEconomy() {
        if (useSimpleEcoAPI) {
            try {
                Class<?> simpleEcoAPIClass = Class.forName("de.groupxyz.simpleeco.SimpleEcoAPI");
                Class<?> economyManagerClass = Class.forName("de.groupxyz.simpleeco.EconomyManager");
                Object economyManagerInstance = economyManagerClass.getDeclaredConstructor().newInstance();
                simpleEcoAPIClass.getMethod("initialize", economyManagerClass).invoke(null, economyManagerInstance);
                getLogger().info("SimpleEcoAPI successfully initialized.");
                return true;
            } catch (Exception e) {
                getLogger().severe("Failed to initialize SimpleEcoAPI: " + e.getMessage());
                return false;
            }
        } else {
            getLogger().info("Attempting to set up Vault economy...");
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                getLogger().severe("Vault not found!");
                return false;
            }
            economy = rsp.getProvider();
            if (economy == null) {
                getLogger().severe("Failed to get Vault economy provider!");
                return false;
            }
            getLogger().info("Vault economy successfully set up.");
            return true;
        }
    }

    private void loadAdminShopItems() {
        adminItems = new HashMap<>();
        adminPrices = new HashMap<>();
        FileConfiguration config = getConfig();

        for (String key : config.getConfigurationSection("items").getKeys(false)) {
            int slot = config.getInt("items." + key + ".slot");
            String materialName = config.getString("items." + key + ".material");
            if (materialName == null) {
                getLogger().warning("Material nicht gefunden für Item: " + key);
                continue;
            }
            Material material = Material.matchMaterial(materialName);

            if (material == null) {
                getLogger().warning("Ungültiges Material: " + materialName);
                continue;
            }

            ItemStack item = new ItemStack(material, config.getInt("items." + key + ".amount", 1));
            ItemMeta meta = item.getItemMeta();

            if (config.contains("items." + key + ".name")) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("items." + key + ".name")));
            } else {
                meta.setDisplayName(material.name());
            }

            if (config.contains("items." + key + ".lore")) {
                List<String> lore = config.getStringList("items." + key + ".lore");
                meta.setLore(lore);
            }

            if (config.contains("items." + key + ".enchantments")) {
                for (String enchant : config.getStringList("items." + key + ".enchantments")) {
                    String[] parts = enchant.split(":");
                    Enchantment ench = Enchantment.getByName(parts[0].toUpperCase());
                    if (ench != null) {
                        int level = Integer.parseInt(parts[1]);
                        meta.addEnchant(ench, level, true);
                    }
                }
            }

            getLogger().info("Lade Item: " + materialName + ", Menge: " + config.getInt("items." + key + ".amount", 1));
            item.setItemMeta(meta);
            adminItems.put(slot, item);
            adminPrices.put(slot, config.getDouble("items." + key + ".price"));
        }
    }

    private void loadPlayerShopData() {
        playerShopFile = new File(getDataFolder(), "player_shops.yml");
        if (!playerShopFile.exists()) {
            try {
                playerShopFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        playerShopConfig = YamlConfiguration.loadConfiguration(playerShopFile);
    }

    private void savePlayerShopData() {
        try {
            playerShopConfig.save(playerShopFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl nutzen!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("shop")) {
            openAdminShop(player, 0);
            return true;
        } else if (command.getName().equalsIgnoreCase("playershop")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("add")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Bitte gib den Preis des Items an!");
                    return true;
                }

                double price;
                try {
                    price = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Der Preis muss eine gueltige Zahl sein!");
                    return true;
                }

                addItemToPlayerShop(player, price);
                return true;
            }

            openPlayerShop(player, 0);
            return true;
        } else if (command.getName().equalsIgnoreCase("shopreload")) {
            reloadConfig();
            adminItems.clear();
            adminPrices.clear();
            loadAdminShopItems();
            loadPlayerShopData();
            sender.sendMessage("reloaded.");
            return true;
        }

        return false;
    }

    private void addItemToPlayerShop(Player player, double price) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Du haeltst kein Item in der Hand!");
            return;
        }

        String key = UUID.randomUUID().toString();
        playerShopConfig.set(key + ".item", item);
        playerShopConfig.set(key + ".price", price);
        playerShopConfig.set(key + ".owner", player.getName());
        savePlayerShopData();

        player.getInventory().setItemInMainHand(null);
        player.sendMessage(ChatColor.GREEN + "Dein Item wurde für " + price + " in den Shop gestellt!");
    }

    private void openAdminShop(Player player, int page) {
        playerCurrentPage.put(player, page);
        int itemsPerPage = 21;
        Inventory shop = Bukkit.createInventory(null, 27, "Admin Shop - Seite " + (page + 1));

        List<Integer> slots = new ArrayList<>(adminItems.keySet());
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, slots.size());

        getLogger().info("Öffne Admin Shop Seite: " + (page + 1) + ", Startindex: " + start + ", Endindex: " + end);

        for (int i = start; i < end; i++) {
            int slot = slots.get(i);
            ItemStack item = adminItems.get(slot).clone();
            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : new ArrayList<>());
            if (lore.stream().noneMatch(line -> line.contains("Preis:"))) {
                lore.add(ChatColor.YELLOW + "Preis: " + adminPrices.get(slot));
            }
            //OLD: meta.setLore(Collections.singletonList(ChatColor.YELLOW + "Preis: " + adminPrices.get(slot)));
            meta.setLore(lore);
            item.setItemMeta(meta);
            shop.setItem(i - start, item);
            getLogger().info("Item zum Adminshop geaddet: " + item.getAmount() + "x " + item.getItemMeta().getDisplayName() + ", Preis: " + adminPrices.get(slot));
        }

        if (page > 0) {
            shop.setItem(21, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Vorherige Seite"));
        }
        if (end < slots.size()) {
            shop.setItem(23, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Nächste Seite"));
        }

        player.openInventory(shop);
    }

    private void openPlayerShop(Player player, int page) {
        playerCurrentPage.put(player, page);
        int itemsPerPage = 21;
        Inventory shop = Bukkit.createInventory(null, 27, "Player Shop - Seite " + (page + 1));

        List<String> keys = new ArrayList<>(playerShopConfig.getKeys(false));
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, keys.size());

        getLogger().info("Öffne Player Shop Seite: " + (page + 1) + ", Startindex: " + start + ", Endindex: " + end);

        for (int i = start; i < end; i++) {
            String key = keys.get(i);
            ItemStack item = playerShopConfig.getItemStack(key + ".item");
            double price = playerShopConfig.getDouble(key + ".price");
            String owner = playerShopConfig.getString(key + ".owner");
            ItemMeta meta = item.getItemMeta();

            List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : new ArrayList<>());
            if (lore.stream().noneMatch(line -> line.contains("Preis:"))) {
                lore.add(ChatColor.YELLOW + "Preis: " + price);
            }

            if (lore.stream().noneMatch(line -> line.contains("Anbieter:"))) {
                lore.add(ChatColor.GRAY + "Anbieter: " + owner);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);

            shop.setItem(i - start, item);
        }

        if (page > 0) {
            shop.setItem(21, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Vorherige Seite"));
        }
        if (end < keys.size()) {
            shop.setItem(23, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Nächste Seite"));
        }

        player.openInventory(shop);
    }


    private ItemStack createNavigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getClickedInventory();
        if (inv == null) return;

        String title = event.getView().getTitle();
        if (title.startsWith("Admin Shop")) {
            event.setCancelled(true);
            int currentPage = playerCurrentPage.getOrDefault(player, 0);

            if (event.getSlot() == 21 && inv.getItem(21) != null) {
                if (currentPage > 0) {
                    openAdminShop(player, currentPage - 1);
                }
            } else if (event.getSlot() == 23 && inv.getItem(23) != null) {
                openAdminShop(player, currentPage + 1);
            } else if (event.getSlot() < 21) {
                int relativeSlot = event.getSlot();
                List<Integer> slots = new ArrayList<>(adminItems.keySet());
                int absoluteIndex = currentPage * 21 + relativeSlot;
                if (absoluteIndex < slots.size()) {
                    int itemSlot = slots.get(absoluteIndex);
                    handleAdminShopClick(player, itemSlot);
                }
            }
        } else if (title.startsWith("Player Shop")) {
            event.setCancelled(true);
            int currentPage = playerCurrentPage.getOrDefault(player, 0);

            if (event.getSlot() == 21 && inv.getItem(21) != null) {
                if (currentPage > 0) {
                    openPlayerShop(player, currentPage - 1);
                }
            } else if (event.getSlot() == 23 && inv.getItem(23) != null) {
                openPlayerShop(player, currentPage + 1);
            } else if (event.getSlot() < 21) {
                int itemSlot = event.getSlot() + currentPage * 21;
                if (itemSlot < playerShopConfig.getKeys(false).size()) {
                    //getLogger().info("Itemslot: " + itemSlot);
                    handlePlayerShopClick(player, itemSlot, currentPage);
                }
            }
        }
    }


    private void handleAdminShopClick(Player player, int slot) {
        if (!adminItems.containsKey(slot)) return;

        ItemStack item = adminItems.get(slot);
        double price = adminPrices.get(slot);

        if (useSimpleEcoAPI) {
            try {
                Class<?> simpleEcoAPIClass = Class.forName("de.groupxyz.simpleeco.SimpleEcoAPI");
                UUID playerUUID = player.getUniqueId();
                double balance = (double) simpleEcoAPIClass.getMethod("getBalance", UUID.class).invoke(null, playerUUID);
                getLogger().info("Spieler " + player.getName() + " hat " + balance + " auf dem Konto.");
                if (balance < price) {
                    player.sendMessage(ChatColor.RED + "Du hast nicht genug Geld!");
                    return;
                }
                simpleEcoAPIClass.getMethod("removeBalance", UUID.class, double.class).invoke(null, playerUUID, price);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        } else {
            if (economy.getBalance(player) < price) {
                player.sendMessage(ChatColor.RED + "Du hast nicht genug Geld!");
                return;
            }
            economy.withdrawPlayer(player, price);
        }

        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : new ArrayList<>());
        Optional<String> matchingLine = lore.stream()
                .filter(line -> line.contains("Preis:"))
                .findFirst();
        if (matchingLine.isPresent()) {
            String lineToRemove = matchingLine.get();
            lore.remove(lineToRemove);
        }

        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.GREEN + "Du hast erfolgreich " + item.getItemMeta().getDisplayName() + " gekauft!");
    }

    private void handlePlayerShopClick(Player player, int slot, int currentPage) {
        int calculatedSlot = slot - currentPage * 21;
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(calculatedSlot);
        if (item == null) return;

        String key = playerShopConfig.getKeys(false).stream()
                .filter(k -> playerShopConfig.getItemStack(k + ".item").equals(item))
                .findFirst().orElse(null);

        if (key == null) return;

        double price = playerShopConfig.getDouble(key + ".price");
        String seller = playerShopConfig.getString(key + ".owner");

        if (useSimpleEcoAPI) {
            try {
                Class<?> simpleEcoAPIClass = Class.forName("de.groupxyz.simpleeco.SimpleEcoAPI");
                UUID playerUUID = player.getUniqueId();
                double balance = (double) simpleEcoAPIClass.getMethod("getBalance", UUID.class).invoke(null, playerUUID);
                if (balance < price) {
                    player.sendMessage(ChatColor.RED + "Du hast nicht genug Geld!");
                    return;
                }
                simpleEcoAPIClass.getMethod("removeBalance", UUID.class, double.class).invoke(null, playerUUID, price);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        } else {
            if (economy.getBalance(player) < price) {
                player.sendMessage(ChatColor.RED + "Du hast nicht genug Geld!");
                return;
            }
            economy.withdrawPlayer(player, price);
        }

        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : new ArrayList<>());
        Optional<String> matchingLine = lore.stream()
                .filter(line -> line.contains("Preis:"))
                .findFirst();
        if (matchingLine.isPresent()) {
            String lineToRemove = matchingLine.get();
            lore.remove(lineToRemove);
        }
        Optional<String> matchingLine2 = lore.stream()
                .filter(line -> line.contains("Anbieter:"))
                .findFirst();
        if (matchingLine2.isPresent()) {
            String lineToRemove = matchingLine2.get();
            lore.remove(lineToRemove);
        }
        item.setLore(lore);

        player.getInventory().addItem(item);

        playerShopConfig.set(key, null);
        savePlayerShopData();

        if (Bukkit.getPlayer(seller) != null) {
            Player sellerPlayer = Bukkit.getPlayer(seller);
            if (useSimpleEcoAPI) {
                try {
                    Class<?> simpleEcoAPIClass = Class.forName("de.groupxyz.simpleeco.SimpleEcoAPI");
                    simpleEcoAPIClass.getMethod("addBalance", UUID.class, double.class).invoke(null, sellerPlayer.getUniqueId(), price);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                economy.depositPlayer(sellerPlayer, price);
            }
            sellerPlayer.sendMessage(ChatColor.GREEN + "Ein Spieler hat dein Item gekauft!");
        } else {
            if (useSimpleEcoAPI) {
                try {
                    Class<?> simpleEcoAPIClass = Class.forName("de.groupxyz.simpleeco.SimpleEcoAPI");
                    simpleEcoAPIClass.getMethod("addBalance", UUID.class, double.class).invoke(null, UUID.fromString(seller), price);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                economy.depositPlayer(seller, price);
            }
            String itemName = item.getType().name();
            if (itemName.isEmpty()) {
            }
            pendingPurchases.computeIfAbsent(seller, k -> new ArrayList<>()).add(player.getName() + " hat " + itemName + " gekauft.");
        }

        player.sendMessage(ChatColor.GREEN + "Du hast das Item gekauft!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        if (pendingPurchases.containsKey(playerName)) {
            List<String> purchases = pendingPurchases.get(playerName);
            player.sendMessage(ChatColor.GOLD + "Du hast neue Verkeaufe: ");
            for (String purchase : purchases) {
                player.sendMessage(ChatColor.GREEN + purchase);
            }

            pendingPurchases.remove(playerName);
        }
    }
}
